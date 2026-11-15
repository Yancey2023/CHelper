"""Run isolated Android performance journeys, preserving raw reports and Perfetto traces."""

import argparse
import hashlib
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "CHelper-Compose"
APP = "yancey.chelper.benchmark"
TEST = APP + ".test"


def store_profile_rules(paths, destination):
    """Filter actual ART rules; never synthesize rules or promote an empty capture."""
    excluded = (
        "yancey/chelper/android/benchmark/",
        "io/ktor/client/engine/mock/",
        "androidx/compose/runtime/tracing/",
        "androidx/tracing/perfetto/",
    )
    rules = set()
    for path in paths:
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip() or line.startswith("#"):
                continue
            owner = re.match(r"^[HSP]*L([^;]+);", line)
            if owner is None:
                raise ValueError(f"Unknown ART rule in {path}: {line[:100]}")
            if owner[1].startswith("androidx/compose/material"):
                raise ValueError("Captured forbidden Compose Material dependency")
            if not owner[1].startswith(excluded):
                rules.add(line)
    if not any("Lyancey/chelper/" in line for line in rules):
        raise ValueError(
            "No application rules found; refusing an empty or unrelated profile"
        )
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        "# Generated from real BaselineProfileRule journeys; update with run_android_performance.py.\n"
        + "\n".join(sorted(rules))
        + "\n",
        encoding="utf-8",
    )
    return len(rules)


def checked_apk(module, variant, expected_id):
    directory = PROJECT / "apps" / module / "build/outputs/apk" / variant
    metadata = json.loads(
        (directory / "output-metadata.json").read_text(encoding="utf-8")
    )
    if metadata["applicationId"] != expected_id or len(metadata["elements"]) != 1:
        raise ValueError("Refusing to install an unexpected application or split APK")
    path = (directory / metadata["elements"][0]["outputFile"]).resolve()
    if not path.is_relative_to(directory.resolve()) or not path.is_file():
        raise ValueError("Invalid APK output path")
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--serial",
        required=True,
        help="Exact adb serial; no automatic device selection",
    )
    parser.add_argument(
        "--adb",
        type=Path,
        default=Path(os.environ.get("ANDROID_HOME", "")) / "platform-tools/adb.exe",
    )
    parser.add_argument(
        "--mode", choices=("measure", "profile", "verify", "collect"), default="measure"
    )
    parser.add_argument(
        "--allow-install",
        action="store_true",
        help="Allow installing only the isolated fixture and runner APKs",
    )
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument(
        "--use-installed",
        action="store_true",
        help="Measure already installed isolated packages and record their actual APK hashes",
    )
    parser.add_argument(
        "--runner-variant", choices=("benchmarkRelease", "nonMinifiedRelease")
    )
    parser.add_argument(
        "--collect-frames",
        action="store_true",
        help="Enable additional JankStats JSONL diagnostics; disabled for timing runs",
    )
    parser.add_argument(
        "--include-startup-profile",
        action="store_true",
        help="Also generate optional startup-only DEX layout rules",
    )
    parser.add_argument(
        "--scenario",
        choices=(
            "coldStartupWithoutProfile",
            "settingsAndScroll",
            "commandInput",
            "libraryRevealAndScroll",
        ),
        help="Run just one measured journey while investigating a hotspot",
    )
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.scenario and args.mode not in ("measure", "verify"):
        parser.error("--scenario requires --mode measure or --mode verify")
    if args.mode == "verify" and args.scenario == "coldStartupWithoutProfile":
        parser.error("Use --mode measure for coldStartupWithoutProfile")
    if args.include_startup_profile and args.mode != "profile":
        parser.error("--include-startup-profile requires --mode profile")
    if args.use_installed and args.mode not in ("measure", "verify"):
        parser.error("--use-installed requires --mode measure or --mode verify")
    if args.mode != "collect" and not args.use_installed and not args.allow_install:
        parser.error(
            "Device runs install test software: explicitly pass --allow-install after authorizing it"
        )
    if not re.fullmatch(r"[A-Za-z0-9_.:\-]+", args.serial):
        parser.error("Invalid adb serial")
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    output = args.output or PROJECT / "build/performance" / stamp
    output.mkdir(parents=True, exist_ok=False)

    def adb(*command, required=True):
        result = subprocess.run(
            [str(args.adb), "-s", args.serial, *map(str, command)],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        if required:
            result.check_returncode()
        return result

    if adb("get-state").stdout.strip() != "device":
        raise RuntimeError("Selected device is not ready")
    metadata = {
        "schema": 1,
        "mode": args.mode,
        "fixture": "offline-2000-v2",
        "scenario": args.scenario or "all",
        "frameTelemetry": "enabled" if args.collect_frames else "disabled",
        "startedUtc": stamp,
        "revision": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
        ).strip(),
        "dirty": bool(
            subprocess.check_output(
                ["git", "status", "--porcelain"], cwd=ROOT, text=True
            )
        ),
    }
    metadata["apks"] = {}
    for name, prop in (
        ("model", "ro.product.model"),
        ("sdk", "ro.build.version.sdk"),
        ("fingerprint", "ro.build.fingerprint"),
    ):
        metadata[name] = adb("shell", "getprop", prop).stdout.strip()
    metadata["refreshSettings"] = {
        key: adb("shell", "settings", "get", "system", key).stdout.strip()
        for key in ("peak_refresh_rate", "min_refresh_rate")
    }
    metadata["batteryBefore"] = adb("shell", "dumpsys", "battery").stdout
    (output / "run.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    def gradle(tasks, filename):
        # Run this script from an elevated terminal, as required by the repository's AGENTS.md.
        command = [
            str(PROJECT / "gradlew.bat"),
            "-p",
            str(PROJECT),
            "-I",
            str(ROOT / "scripts/maven_mirror.init.gradle"),
            "--console=plain",
            *tasks,
            f"-PcollectFrameMetrics={str(args.collect_frames).lower()}",
        ]
        with (output / filename).open("w", encoding="utf-8") as log:
            subprocess.run(
                command,
                cwd=ROOT,
                stdout=log,
                stderr=subprocess.STDOUT,
                check=True,
                env={**os.environ, "ANDROID_SERIAL": args.serial},
            )

    def installed_digest(application):
        existing = (
            adb("shell", "pm", "path", application, required=False)
            .stdout.strip()
            .splitlines()
        )
        if len(existing) == 1 and existing[0].startswith("package:"):
            remote_apk = existing[0].removeprefix("package:")
            if re.fullmatch(r"/data/app/[A-Za-z0-9_~./=+\-]+/base\.apk", remote_apk):
                current = adb(
                    "shell", "sha256sum", remote_apk, required=False
                ).stdout.split()
                if current and re.fullmatch(r"[a-f0-9]{64}", current[0]):
                    return current[0]
        return None

    def install_variants(app_variant, runner_variant):
        for module, application, variant in (
            ("android", APP, app_variant),
            ("benchmark", TEST, runner_variant),
        ):
            apk = checked_apk(module, variant, application)
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            metadata["apks"][application] = digest
            if installed_digest(application) == digest:
                (output / f"install-{module}.log").write_text(
                    "Already installed; APK SHA256 matches.\n", encoding="utf-8"
                )
                continue
            installed = adb("install", "-r", apk, required=False)
            (output / f"install-{module}.log").write_text(
                installed.stdout + installed.stderr, encoding="utf-8"
            )
            if installed.returncode or "Success" not in installed.stdout:
                raise RuntimeError(
                    f"Install rejected for {application}; stop without retrying"
                )

    metadata["runs"] = []

    def instrument(label, suite, expected, detailed, extra=()):
        remote = f"/sdcard/Android/media/{TEST}/performance-{stamp}/{label}"
        command = [
            str(args.adb),
            "-s",
            args.serial,
            "shell",
            "-T",
            "-n",
            "am",
            "instrument",
            "-w",
            "-r",
            "-e",
            "class",
            f"yancey.chelper.benchmark.{suite}",
            "-e",
            "additionalTestOutputDir",
            remote,
            "-e",
            "androidx.benchmark.perfettoSdkTracing.enable",
            str(detailed).lower(),
            "-e",
            "chelper.requireBaselineProfile",
            str(args.mode == "verify").lower(),
            *extra,
            f"{TEST}/androidx.test.runner.AndroidJUnitRunner",
        ]
        log_path = output / f"instrumentation-{label}.log"
        with log_path.open("w", encoding="utf-8") as log:
            result = subprocess.run(
                command,
                stdin=subprocess.DEVNULL,
                stdout=log,
                stderr=subprocess.STDOUT,
                timeout=1800,
            )
        result_text = log_path.read_text(encoding="utf-8")
        success = re.search(r"OK \((\d+) tests?\)", result_text)
        passed = (
            result.returncode == 0
            and success is not None
            and int(success[1]) == expected
            and not re.search(r"INSTRUMENTATION_STATUS_CODE: -[234]", result_text)
        )
        metadata["runs"].append(
            {
                "suite": suite,
                "expectedTests": expected,
                "sdkTracing": detailed,
                "passed": passed,
                "exitCode": result.returncode,
            }
        )
        local = output / f"raw-{label}"
        pulled = adb("pull", remote, local, required=False)
        (output / f"pull-{label}.log").write_text(
            pulled.stdout + pulled.stderr, encoding="utf-8"
        )
        metadata["testSucceeded"] = all(run["passed"] for run in metadata["runs"])
        (output / "run.json").write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        return passed and pulled.returncode == 0, local

    if args.mode == "profile":
        if not args.skip_build and not args.use_installed:
            gradle(
                [
                    ":apps:android:assembleNonMinifiedRelease",
                    ":apps:benchmark:assembleNonMinifiedRelease",
                ],
                "profile-build.log",
            )
        install_variants("nonMinifiedRelease", "nonMinifiedRelease")
        suite = (
            "BaselineProfileGenerator"
            if args.include_startup_profile
            else "BaselineProfileGenerator#criticalJourneys"
        )
        passed, raw = instrument(
            "profile",
            suite,
            2 if args.include_startup_profile else 1,
            False,
            (
                "-e",
                "androidx.benchmark.enabledRules",
                "BaselineProfile",
                "-e",
                "androidx.benchmark.saveProfileWaitMillis",
                "5000",
            ),
        )
        if not passed:
            raise RuntimeError(
                f"Profile generation failed; raw output preserved at {output}"
            )
        destination = PROJECT / "apps/android/src/main/generated/baselineProfiles"
        captures = sorted(raw.rglob("*-baseline-prof.txt"))
        rule_count = store_profile_rules(captures, destination / "baseline-prof.txt")
        if args.include_startup_profile:
            store_profile_rules(
                list(raw.rglob("*-startup-prof.txt")), destination / "startup-prof.txt"
            )
        elif (destination / "startup-prof.txt").exists():
            (destination / "startup-prof.txt").unlink()
        source = destination / "baseline-prof.txt"
        provenance = {
            "schema": 1,
            "generator": "androidx.benchmark.macro.junit4.BaselineProfileRule",
            "passedJourney": suite,
            "fixture": metadata["fixture"],
            "model": metadata["model"],
            "sdk": metadata["sdk"],
            "startedUtc": stamp,
            "apks": metadata["apks"],
            "rawCaptures": {
                path.name: hashlib.sha256(path.read_bytes()).hexdigest()
                for path in captures
            },
            "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
            "rules": rule_count,
            "applicationRules": sum(
                "Lyancey/chelper/" in line
                for line in source.read_text(encoding="utf-8").splitlines()
            ),
            "startupProfile": args.include_startup_profile,
            "rawEvidence": str(output),
        }
        evidence = PROJECT / "performance/profile-capture.json"
        evidence.parent.mkdir(parents=True, exist_ok=True)
        evidence.write_text(
            json.dumps(provenance, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"Generated real ART rules at {destination}; raw evidence: {output}")
        return 0

    status = 0
    if args.mode != "collect":
        runner_variant = args.runner_variant or "benchmarkRelease"
        if not args.skip_build and not args.use_installed:
            gradle(
                [
                    ":apps:android:assembleBenchmarkRelease",
                    ":apps:benchmark:assemble"
                    + runner_variant[0].upper()
                    + runner_variant[1:],
                ],
                "build.log",
            )
        if args.use_installed:
            for application in (APP, TEST):
                digest = installed_digest(application)
                if digest is None:
                    raise RuntimeError(f"Cannot verify installed APK: {application}")
                metadata["apks"][application] = digest
        else:
            install_variants("benchmarkRelease", runner_variant)
        if args.mode == "verify":
            runs = (
                [("interaction", "FrameBenchmarks#" + args.scenario, 1, True)]
                if args.scenario
                else [
                    ("interaction", "FrameBenchmarks", 3, True),
                    ("startup", "PackagedProfileBenchmark", 1, False),
                ]
            )
        elif args.scenario == "coldStartupWithoutProfile":
            runs = [("startup", "StartupBenchmarks", 1, False)]
        elif args.scenario:
            runs = [("interaction", "FrameBenchmarks#" + args.scenario, 1, True)]
        else:
            runs = [
                ("interaction", "FrameBenchmarks", 3, True),
                ("startup", "StartupBenchmarks", 1, False),
            ]
        for label, suite, expected, detailed in runs:
            passed, _ = instrument(label, suite, expected, detailed)
            if not passed:
                status = 1

    # Stop rendering normally so the writer can drain. Do not force-stop before pulling frames.
    if args.mode != "collect":
        adb("shell", "input", "keyevent", "KEYCODE_HOME", required=False)
    if args.collect_frames or args.mode == "collect":
        pulled = adb(
            "pull",
            f"/sdcard/Android/data/{APP}/files/performance",
            output / "frames",
            required=False,
        )
        (output / "pull-frames.log").write_text(
            pulled.stdout + pulled.stderr, encoding="utf-8"
        )
    macro = list(output.rglob("*benchmarkData.json"))
    started_ms = int(
        datetime.strptime(stamp, "%Y%m%dT%H%M%S%fZ")
        .replace(tzinfo=timezone.utc)
        .timestamp()
        * 1000
    )
    frames = [
        path
        for path in output.rglob("frames-*.jsonl")
        if args.mode == "collect" or int(path.name.split("-")[1]) >= started_ms
    ]
    metadata["artifactsComplete"] = status == 0 and bool(macro)
    (output / "run.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    if macro or frames:
        command = [
            sys.executable,
            str(ROOT / "scripts/analyze_performance.py"),
            "--output",
            str(output / "report.json"),
            "--run-metadata",
            str(output / "run.json"),
        ]
        if macro:
            command += ["--macro", *map(str, macro)]
        if frames:
            command += ["--frames", *map(str, frames)]
        status = max(status, subprocess.run(command, cwd=ROOT).returncode)
    else:
        status = 1
    print(
        f"Artifacts: {output}; status={status}. No application data was cleared or uninstalled."
    )
    return status


if __name__ == "__main__":
    raise SystemExit(main())
