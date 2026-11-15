"""Summarize JankStats JSONL and Macrobenchmark JSON without treating missing samples as success."""

import argparse
from collections import defaultdict
import json
import math
from pathlib import Path


def percentile(values, quantile):
    if not values:
        return None
    ordered = sorted(values)
    index = (len(ordered) - 1) * quantile
    lower = math.floor(index)
    upper = math.ceil(index)
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (index - lower)


def distribution(values):
    return {
        "count": len(values),
        "p50": percentile(values, 0.5),
        "p95": percentile(values, 0.95),
        "p99": percentile(values, 0.99),
        "max": max(values) if values else None,
    }


def frame_report(paths):
    groups = defaultdict(list)
    sessions = []
    slow = []
    for path in paths:
        rows = []
        malformed = 0
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            try:
                rows.append((number, json.loads(line)))
            except json.JSONDecodeError:
                malformed += 1
        headers = [row for _, row in rows if row.get("type") == "session"]
        summaries = [row for _, row in rows if row.get("type") == "summary"]
        frames = [(number, row) for number, row in rows if row.get("type") == "frame"]
        summary = summaries[-1] if summaries else {}
        complete = (
            len(headers) == 1
            and len(summaries) == 1
            and not malformed
            and not summary.get("lost", 0)
            and not summary.get("truncated", False)
            and summary.get("written") == len(frames)
            and summary.get("observed") == len(frames)
        )
        sessions.append(
            {
                "file": str(path),
                "metadata": headers[0] if headers else None,
                "frames": len(frames),
                "complete": complete,
                "observed": summary.get("observed"),
                "written": summary.get("written"),
                "lost": summary.get("lost"),
                "truncated": summary.get("truncated"),
                "malformed": malformed,
            }
        )
        for number, row in frames:
            states = row.get("states", {})
            key = "/".join(
                str(states.get(name, "unknown"))
                for name in (
                    "screen",
                    "library_phase",
                    "library_highlight",
                    "command_analysis",
                    "interaction",
                )
            )
            groups[key].append(row)
            overrun = row.get("overrunNs")
            if row.get("jank") or (overrun is not None and overrun > 0):
                slow.append({"file": str(path), "line": number, **row})
    result = {}
    for name, frames in groups.items():
        overruns = [
            row["overrunNs"] / 1_000_000
            for row in frames
            if row.get("overrunNs") is not None
        ]
        result[name] = {
            "uiMs": distribution([row["uiNs"] / 1_000_000 for row in frames]),
            "overrunMs": distribution(overruns),
            "deadlineMisses": sum(value > 0 for value in overruns),
            "heuristicJank": sum(bool(row.get("jank")) for row in frames),
        }
    return {
        "sessions": sessions,
        "groups": result,
        "slowFrames": slow,
        "complete": bool(sessions) and all(row["complete"] for row in sessions),
    }


def macro_report(paths):
    metrics = {}
    environments = []
    for path in paths:
        payload = json.loads(path.read_text(encoding="utf-8"))
        context = payload.get("context", {})
        build = context.get("build", {})
        environments.append(
            {
                "model": build.get("model"),
                "device": build.get("device"),
                "sdk": build.get("version", {}).get("sdk"),
                "cores": context.get("cpuCoreCount"),
                "cpuLocked": context.get("cpuLocked"),
                "cpuMaxFreqHz": context.get("cpuMaxFreqHz"),
                "sustainedPerformance": context.get("sustainedPerformanceModeEnabled"),
                "artVersion": context.get("artMainlineVersion"),
            }
        )
        for benchmark in payload.get("benchmarks", []):
            identity = benchmark.get("className", "") + "#" + benchmark.get("name", "")
            for family in ("metrics", "sampledMetrics"):
                for metric, values in benchmark.get(family, {}).items():
                    runs = values.get("runs", [])
                    flat = []
                    for run in runs:
                        flat.extend(run if isinstance(run, list) else [run])
                    numeric = [
                        float(value)
                        for value in flat
                        if isinstance(value, (float, int))
                    ]
                    stats = distribution(numeric)
                    if not numeric:
                        for source, target in (
                            ("P50", "p50"),
                            ("P95", "p95"),
                            ("P99", "p99"),
                            ("median", "p50"),
                            ("maximum", "max"),
                        ):
                            if isinstance(values.get(source), (int, float)):
                                stats[target] = values[source]
                    if stats["p50"] is not None:
                        if metric == "frameOverrunMs" and numeric:
                            stats["deadlineMisses"] = sum(
                                value > 0 for value in numeric
                            )
                            stats["deadlineMissPercent"] = (
                                100 * stats["deadlineMisses"] / len(numeric)
                            )
                        metrics[identity + "/" + metric] = stats
    environments = sorted({json.dumps(env, sort_keys=True) for env in environments})
    return {
        "environments": [json.loads(env) for env in environments],
        "metrics": metrics,
    }


def regressions(current, baseline, percent=10.0, slack_ms=1.0):
    if current.get("conditions") != baseline.get("conditions"):
        raise ValueError("Fixture, mode, firmware or refresh settings differ")
    if current["macro"]["environments"] != baseline["macro"]["environments"]:
        raise ValueError(
            "Device/SDK/core configuration differs; do not compare these baselines"
        )
    previous = baseline["macro"]["metrics"]
    values = current["macro"]["metrics"]
    missing = sorted(set(previous) - set(values))
    result = [{"metric": key, "reason": "missing measured metric"} for key in missing]
    for name, old in previous.items():
        if name not in values or not name.endswith("Ms"):
            continue
        for percentile_name in ("p50", "p95", "p99"):
            before, after = old.get(percentile_name), values[name].get(percentile_name)
            if before is None or after is None:
                continue
            # Frame overruns may be negative: regressions are based on an absolute delta.
            limit = before + max(abs(before) * percent / 100, slack_ms)
            if after > limit:
                result.append(
                    {
                        "metric": name,
                        "percentile": percentile_name,
                        "baseline": before,
                        "current": after,
                        "limit": limit,
                    }
                )
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--frames", type=Path, nargs="*", default=[])
    parser.add_argument("--macro", type=Path, nargs="*", default=[])
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--write-baseline", type=Path)
    parser.add_argument(
        "--run-metadata", type=Path, help="run.json from the successful device runner"
    )
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--regression-percent", type=float, default=10)
    parser.add_argument("--slack-ms", type=float, default=1)
    parser.add_argument(
        "--require-p99-deadline",
        action="store_true",
        help="Fail if interactive P99 frame overrun exceeds zero",
    )
    args = parser.parse_args()
    report = {
        "schema": 1,
        "frames": frame_report(args.frames),
        "macro": macro_report(args.macro),
    }
    metadata = (
        json.loads(args.run_metadata.read_text(encoding="utf-8"))
        if args.run_metadata
        else {}
    )
    report["conditions"] = (
        {
            key: metadata.get(key)
            for key in (
                "mode",
                "fixture",
                "fingerprint",
                "refreshSettings",
                "frameTelemetry",
            )
        }
        if metadata
        else None
    )
    if metadata:
        report["conditions"]["scenario"] = metadata.get("scenario", "all")
    if not report["macro"]["metrics"] and not report["frames"]["groups"]:
        parser.error("No frame or macrobenchmark measurements were found")
    report["regressions"] = (
        regressions(
            report,
            json.loads(args.baseline.read_text(encoding="utf-8")),
            args.regression_percent,
            args.slack_ms,
        )
        if args.baseline
        else []
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    deadline_metrics = {
        name: stats
        for name, stats in report["macro"]["metrics"].items()
        if "FrameBenchmarks#" in name and name.endswith("/frameOverrunMs")
    }
    report["interactiveDeadlineTargets"] = {
        name: stats["p99"] is not None and stats["p99"] <= 0
        for name, stats in deadline_metrics.items()
    }
    deadline_failed = args.require_p99_deadline and (
        not deadline_metrics or not all(report["interactiveDeadlineTargets"].values())
    )
    report["comparedToBaseline"] = args.baseline is not None
    args.output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    if args.write_baseline:
        if (
            metadata.get("testSucceeded") is not True
            or metadata.get("artifactsComplete") is not True
            or not all(report["conditions"].values())
        ):
            parser.error(
                "A baseline requires --run-metadata from a successful device test"
            )
        if not report["macro"]["metrics"] or not any(
            env.get("model") for env in report["macro"]["environments"]
        ):
            parser.error(
                "A baseline requires actual macrobenchmark measurements and device metadata"
            )
        args.write_baseline.parent.mkdir(parents=True, exist_ok=True)
        baseline = {
            "schema": 1,
            "conditions": report["conditions"],
            "macro": report["macro"],
            "evidence": {
                key: metadata.get(key)
                for key in ("startedUtc", "revision", "dirty", "apks")
            },
        }
        args.write_baseline.write_text(
            json.dumps(baseline, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    comparison = (
        f"{len(report['regressions'])} regressions"
        if args.baseline
        else "no baseline comparison requested"
    )
    capture = (
        f"frame capture complete={report['frames']['complete']}"
        if args.frames
        else "JankStats capture not supplied"
    )
    print(
        f"{len(report['macro']['metrics'])} measured metrics; {len(report['frames']['slowFrames'])} slow-frame records; {comparison}; {capture}"
    )
    return int(bool(report["regressions"]) or deadline_failed)


if __name__ == "__main__":
    raise SystemExit(main())
