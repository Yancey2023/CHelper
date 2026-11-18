import json
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from analyze_performance import (
    distribution,
    frame_report,
    macro_report,
    regressions,
    main,
)
import run_android_performance as runner


class PerformanceReportTest(unittest.TestCase):
    def test_complete_session_has_actual_frame_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "frames.jsonl")
            rows = [
                {"type": "session", "schema": 1, "refreshHz": 120},
                {
                    "type": "frame",
                    "uiNs": 4_000_000,
                    "overrunNs": -2_000_000,
                    "jank": False,
                },
                {
                    "type": "frame",
                    "uiNs": 11_000_000,
                    "overrunNs": 1_000_000,
                    "jank": False,
                },
                {
                    "type": "summary",
                    "observed": 2,
                    "written": 2,
                    "lost": 0,
                    "truncated": False,
                },
            ]
            path.write_text("\n".join(map(json.dumps, rows)), encoding="utf-8")
            report = frame_report([path])
            self.assertTrue(report["complete"])
            self.assertEqual(2, next(iter(report["groups"].values()))["uiMs"]["count"])
            self.assertEqual(1, len(report["slowFrames"]))

    def test_failed_instrumentation_cannot_create_a_baseline(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            macro = root / "macro.json"
            macro.write_text(
                json.dumps(
                    {
                        "context": {"build": {"model": "test"}},
                        "benchmarks": [
                            {
                                "className": "Frames",
                                "name": "scroll",
                                "metrics": {"frameOverrunMs": {"runs": [1, 2]}},
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            metadata = root / "run.json"
            metadata.write_text(
                json.dumps({"testSucceeded": False, "artifactsComplete": True}),
                encoding="utf-8",
            )
            baseline = root / "baseline.json"
            with patch("sys.stderr", io.StringIO()), patch(
                "sys.argv",
                [
                    "analyze",
                    "--macro",
                    str(macro),
                    "--run-metadata",
                    str(metadata),
                    "--output",
                    str(root / "report.json"),
                    "--write-baseline",
                    str(baseline),
                ],
            ):
                with self.assertRaises(SystemExit) as failure:
                    main()
            self.assertEqual(2, failure.exception.code)
            self.assertFalse(baseline.exists())

    def test_percentiles_do_not_assume_a_fixed_refresh_rate(self):
        self.assertEqual(9.5, distribution(list(range(20)))["p50"])
        self.assertEqual(18.81, distribution(list(range(20)))["p99"])

    def test_missing_summary_is_not_a_clean_capture(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "frames.jsonl")
            path.write_text(
                json.dumps({"type": "session", "refreshHz": 120})
                + "\n"
                + json.dumps(
                    {
                        "type": "frame",
                        "uiNs": 9_000_000,
                        "overrunNs": 700_000,
                        "jank": False,
                        "states": {"screen": "SettingsScreenKey"},
                    }
                ),
                encoding="utf-8",
            )
            report = frame_report([path])
            self.assertFalse(report["complete"])
            self.assertEqual(1, len(report["slowFrames"]))
            self.assertEqual(1, next(iter(report["groups"].values()))["deadlineMisses"])

    def test_queue_loss_and_truncation_are_visible(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "frames.jsonl")
            rows = [
                {"type": "session"},
                {
                    "type": "summary",
                    "observed": 20,
                    "written": 0,
                    "lost": 20,
                    "truncated": True,
                },
            ]
            path.write_text("\n".join(map(json.dumps, rows)), encoding="utf-8")
            result = frame_report([path])
            self.assertFalse(result["complete"])
            self.assertEqual(20, result["sessions"][0]["lost"])

    def test_nested_macro_samples_are_read(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "macro.json")
            path.write_text(
                json.dumps(
                    {
                        "context": {"build": {"model": "test"}},
                        "benchmarks": [
                            {
                                "className": "Frames",
                                "name": "scroll",
                                "sampledMetrics": {
                                    "frameOverrunMs": {"runs": [[-2, -1], [0, 8]]}
                                },
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            report = macro_report([path])
            self.assertEqual(
                4, report["metrics"]["Frames#scroll/frameOverrunMs"]["count"]
            )

    def test_missing_metrics_and_negative_overruns_cannot_hide_regressions(self):
        before = {
            "macro": {
                "environments": [{"model": "test"}],
                "metrics": {"frameOverrunMs": {"p95": -2}, "startupMs": {"p50": 20}},
            }
        }
        after = {
            "macro": {
                "environments": [{"model": "test"}],
                "metrics": {"frameOverrunMs": {"p95": 1}},
            }
        }
        self.assertEqual(2, len(regressions(after, before)))
        after["macro"]["environments"] = [{"model": "other"}]
        with self.assertRaises(ValueError):
            regressions(after, before)

    def test_different_fixtures_cannot_be_compared(self):
        with self.assertRaises(ValueError):
            regressions(
                {"conditions": {"fixture": "v1"}}, {"conditions": {"fixture": "v2"}}
            )


class PerformanceInstallGuardTest(unittest.TestCase):
    def test_only_real_application_profile_rules_are_promoted(self):
        with tempfile.TemporaryDirectory() as directory:
            raw = Path(directory, "raw.txt")
            raw.write_text(
                "SLyancey/chelper/ui/Feature;->render()V\nLyancey/chelper/android/benchmark/Fixture;\nLio/ktor/client/engine/mock/MockEngine;\n",
                encoding="utf-8",
            )
            output = Path(directory, "baseline-prof.txt")
            self.assertEqual(1, runner.store_profile_rules([raw], output))
            self.assertIn("SLyancey/chelper/ui/Feature;->render()V", output.read_text())
            self.assertNotIn("MockEngine", output.read_text())
            with self.assertRaises(ValueError):
                runner.store_profile_rules([], output)

    def test_material_profile_owners_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            raw = Path(directory, "raw.txt")
            raw.write_text(
                "Landroidx/compose/material3/ButtonKt;\nLyancey/chelper/ui/Feature;\n",
                encoding="utf-8",
            )
            with self.assertRaises(ValueError):
                runner.store_profile_rules([raw], Path(directory, "baseline-prof.txt"))

    def test_production_package_cannot_be_installed_as_a_benchmark(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(
            runner, "PROJECT", Path(directory)
        ):
            output = Path(directory, "apps/android/build/outputs/apk/benchmarkRelease")
            output.mkdir(parents=True)
            (output / "output-metadata.json").write_text(
                json.dumps({"applicationId": "yancey.chelper", "elements": []}),
                encoding="utf-8",
            )
            with self.assertRaises(ValueError):
                runner.checked_apk("android", "benchmarkRelease", runner.APP)


if __name__ == "__main__":
    unittest.main()
