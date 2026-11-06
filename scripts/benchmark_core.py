"""Compare two Release builds using identical benchmark sources and resources.

Usage: python scripts/benchmark_core.py baseline-build current-build --output build/performance-audit/results
Timing uses CHelperLatencyBench (ordinary allocator); allocation counts use CHelperBench.
"""

import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import statistics
import subprocess


def build_metadata(directory):
    directory = directory.resolve()
    metadata = {"directory": str(directory), "executables": {}}
    for name in ("CHelperLatencyBench", "CHelperBench"):
        path = directory / (name + (".exe" if os.name == "nt" else ""))
        metadata["executables"][name] = hashlib.sha256(path.read_bytes()).hexdigest()
    cache_path = directory / "CMakeCache.txt"
    if cache_path.exists():
        cache = {}
        for line in cache_path.read_text(encoding="utf-8").splitlines():
            if line and not line.startswith(("#", "//")) and "=" in line:
                key, value = line.split("=", 1)
                cache[key.split(":", 1)[0]] = value
        metadata["cmake"] = {
            key: cache.get(key)
            for key in (
                "CMAKE_BUILD_TYPE",
                "CMAKE_CXX_COMPILER",
                "CMAKE_CXX_FLAGS",
                "CMAKE_CXX_FLAGS_RELEASE",
                "CMAKE_MSVC_DEBUG_INFORMATION_FORMAT",
                "CHELPER_ENABLE_SANITIZERS",
                "CHELPER_ENABLE_RESOURCE_AUDIT",
            )
        }
        source = cache.get("CMAKE_HOME_DIRECTORY")
        if source:
            metadata["benchmark_sources_at_run_time"] = {
                name: hashlib.sha256(
                    (Path(source) / "tests" / name).read_bytes().replace(b"\r\n", b"\n")
                ).hexdigest()
                for name in (
                    "BenchHelper.h",
                    "BenchTest.cpp",
                    "LongCommandBenchTest.cpp",
                )
                if (Path(source) / "tests" / name).is_file()
            }
            metadata["benchmark_sources_complete"] = (
                len(metadata["benchmark_sources_at_run_time"]) == 3
            )
    commands_path = directory / "compile_commands.json"
    if commands_path.exists():
        for entry in json.loads(commands_path.read_text(encoding="utf-8")):
            command = entry.get("command", " ".join(entry.get("arguments", [])))
            if (
                Path(entry["file"]).name == "Parser.cpp"
                and "CHelperCore.dir" in command
            ):
                metadata["parser_compile_command"] = command
                metadata["critical_flags"] = sorted(
                    re.findall(
                        r"(?i)(?<!\S)[/-](?:O[0123dgsyz]|Ofast|Ob[0-3]|MDd?|MTd?|Z[7i]|Zo-?|GL-?|EH(?:sc|s|a)|std[:=][^\s\"]+|fsanitize=[^\s\"]+|flto(?:=[^\s\"]+)?|g(?:gdb)?[0-3]?|f(?:no-)?(?:exceptions|rtti))(?=\s|$)",
                        command,
                    )
                )
                break
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("current", type=Path)
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--long-repeat", type=int, default=100)
    parser.add_argument("--long-warmup", type=int, default=1000)
    parser.add_argument(
        "--windows-qos",
        choices=("high", "system", "eco"),
        default="high" if os.name == "nt" else "system",
        help="Windows execution-speed policy, checked before and after benchmark tests; default: high on Windows",
    )
    parser.add_argument(
        "--require-long-fault-free",
        action="store_true",
        help="Fail after saving results if any timed long-command iteration faults",
    )
    parser.add_argument("--long-only", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.long_warmup < 0:
        parser.error("--long-warmup must be nonnegative")
    if args.rounds < 1 or args.long_repeat < 1:
        parser.error("--rounds and --long-repeat must be positive")
    if os.name != "nt" and args.windows_qos != "system":
        parser.error("--windows-qos high/eco is only available on Windows")
    args.output.mkdir(parents=True, exist_ok=True)
    affinity = None
    if os.name == "nt":
        kernel = ctypes.windll.kernel32
        kernel.GetCurrentProcess.restype = ctypes.c_void_p
        kernel.GetProcessAffinityMask.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_size_t),
            ctypes.POINTER(ctypes.c_size_t),
        ]
        kernel.SetProcessAffinityMask.argtypes = [ctypes.c_void_p, ctypes.c_size_t]
        process = kernel.GetCurrentProcess()
        allowed, system = ctypes.c_size_t(), ctypes.c_size_t()
        if kernel.GetProcessAffinityMask(
            process, ctypes.byref(allowed), ctypes.byref(system)
        ):
            affinity = 1 << (allowed.value.bit_length() - 1)
            if not kernel.SetProcessAffinityMask(process, affinity):
                raise ctypes.WinError()
    builds = {"baseline": args.baseline, "current": args.current}
    provenance = {label: build_metadata(path) for label, path in builds.items()}
    (args.output / "provenance.json").write_text(
        json.dumps(provenance, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    for label, metadata in provenance.items():
        configuration = metadata.get("cmake", {})
        for option in ("CHELPER_ENABLE_SANITIZERS", "CHELPER_ENABLE_RESOURCE_AUDIT"):
            if (configuration.get(option) or "").upper() in ("ON", "TRUE", "1", "YES"):
                raise RuntimeError(
                    f"{label}: disable {option} before latency measurement"
                )
    before_flags = provenance["baseline"].get("critical_flags")
    after_flags = provenance["current"].get("critical_flags")
    if (
        before_flags is not None
        and after_flags is not None
        and before_flags != after_flags
    ):
        raise RuntimeError(
            "Core compiler flags differ; rebuild both variants with matching flags before comparing timings: "
            f"{before_flags} != {after_flags}"
        )
    before_sources = (
        provenance["baseline"].get("benchmark_sources_at_run_time")
        if provenance["baseline"].get("benchmark_sources_complete")
        else None
    )
    after_sources = (
        provenance["current"].get("benchmark_sources_at_run_time")
        if provenance["current"].get("benchmark_sources_complete")
        else None
    )
    if (
        before_sources is not None
        and after_sources is not None
        and before_sources != after_sources
    ):
        raise RuntimeError(
            "Benchmark sources differ; rebuild both variants using the same harness"
        )
    pattern = re.compile(
        r"^(.+?)\s+n=(\d+)\s+avg\s+([\d.]+) ms\s+p50\s+([\d.]+)\s+p95\s+([\d.]+)\s+max\s+([\d.]+)\s+new\s+([\d.-]+)\s+net\s+([\d.-]+) KiB$",
        re.MULTILINE,
    )
    records = []
    fingerprints = {}
    long_page_faults = []
    windows_qos = []

    def run(label, executable, tests, suffix, environment=None):
        env = os.environ.copy()
        env.update(environment or {})
        env["CHELPER_BENCH_WINDOWS_QOS"] = args.windows_qos
        path = builds[label] / (executable + (".exe" if os.name == "nt" else ""))
        result = subprocess.run(
            [str(path.resolve()), "--gtest_filter=" + tests],
            env=env,
            text=True,
            encoding="utf-8",
            errors="replace",
            capture_output=True,
            check=False,
        )
        (args.output / f"{label}-{suffix}.log").write_text(
            result.stdout + result.stderr, encoding="utf-8"
        )
        policy_rows = [
            dict(
                phase=phase,
                requested=requested,
                supported=int(supported),
                control=int(control),
                state=int(state),
            )
            for phase, requested, supported, control, state in re.findall(
                r"^bench windows_qos phase=(begin|end|restore) requested=(\w+) supported=(\d+) control=(\d+) state=(\d+)$",
                result.stdout,
                re.MULTILINE,
            )
        ]
        policy_record = dict(
            variant=label,
            run=suffix,
            requested=args.windows_qos,
            returncode=result.returncode,
            observations=policy_rows,
            endpoints_verified=False,
        )
        windows_qos.append(policy_record)
        try:
            result.check_returncode()
            expected_phases = (
                ["begin", "end", "restore"] if os.name == "nt" else ["begin", "end"]
            )
            if [row["phase"] for row in policy_rows] != expected_phases:
                raise RuntimeError(
                    "Benchmark lacks execution-policy metadata; rebuild both benchmark targets with the current harness"
                )
            desired = {"high": (1, 0), "eco": (1, 1), "system": (0, 0)}[
                args.windows_qos
            ]
            if any(
                row["requested"] != args.windows_qos
                or row["supported"] != int(os.name == "nt")
                or (
                    os.name == "nt"
                    and row["phase"] != "restore"
                    and (row["control"] & 1, row["state"] & 1) != desired
                )
                for row in policy_rows
            ):
                raise RuntimeError(
                    "Benchmark execution-policy endpoints do not match the requested configuration"
                )
            policy_record["endpoints_verified"] = os.name == "nt"
        finally:
            # 旧程序缺元数据、程序失败或策略不符时也保留全部观测。
            (args.output / "windows-qos.json").write_text(
                json.dumps(windows_qos, ensure_ascii=False, indent=2), encoding="utf-8"
            )
        if tests == "Bench.LongCommand":
            match = re.search(
                r"^long page_faults tracking=(\d+) warmup=(\d+) measured=(\d+) fault_free=(\d+) faulting=(\d+) total_faults=(\d+)$",
                result.stdout,
                re.MULTILINE,
            )
            if not match:
                raise RuntimeError(
                    "Long-command benchmark lacks page-fault metadata; rebuild both benchmark targets with the current harness"
                )
            fields = dict(
                zip(
                    (
                        "tracking",
                        "warmup",
                        "measured",
                        "fault_free",
                        "faulting",
                        "total_faults",
                    ),
                    map(int, match.groups()),
                )
            )
            expected = {
                "LONG_WARMUP": "warmup",
                "LONG_REPEAT": "measured",
                "LONG_TRACK_PAGE_FAULTS": "tracking",
            }
            if any(
                fields[key] != int(environment[name]) for name, key in expected.items()
            ):
                raise RuntimeError(
                    "Long-command benchmark ignored the requested measurement configuration"
                )
            if (
                fields["tracking"]
                and fields["fault_free"] + fields["faulting"] != fields["measured"]
            ):
                raise RuntimeError(
                    "Long-command page-fault sample counts do not add up"
                )
            long_page_faults.append(dict(variant=label, run=suffix, **fields))
        for match in pattern.finditer(result.stdout):
            name, count, avg, p50, p95, maximum, allocations, net = match.groups()
            records.append(
                dict(
                    variant=label,
                    run=suffix,
                    name=name.strip(),
                    count=int(count),
                    avg_ms=float(avg),
                    p50_ms=float(p50),
                    p95_ms=float(p95),
                    max_ms=float(maximum),
                    allocations=float(allocations),
                    net_kib=float(net),
                )
            )
        for fingerprint in re.findall(
            r"(?:CommandContext results:|long results).*", result.stdout
        ):
            fingerprints.setdefault(suffix, {})[label] = fingerprint
        print(f"Completed {label} {suffix}", flush=True)

    tests = "Bench.LoadCPack:Bench.DecodeCPack:Bench.InitializeBlockIds:Bench.UnloadCPack:Bench.RequestPhases:Bench.CommandContextOperations:Bench.RequestPerCommand:Bench.CommandContextResults"
    for round_index in range(0 if args.long_only else args.rounds):
        order = (
            ("baseline", "current") if round_index % 2 == 0 else ("current", "baseline")
        )
        for label in order:
            run(label, "CHelperLatencyBench", tests, f"timing-{round_index}")
    if not args.long_only:
        for label in builds:
            run(label, "CHelperBench", tests, "allocations")
    for kind, count in (
        ("flat", 128),
        ("json", 128),
        ("selector", 128),
        ("nested", 32),
        ("components_unique", 256),
        ("components_alternating", 128),
    ):
        for round_index in range(args.rounds):
            for label in (
                ("baseline", "current")
                if round_index % 2 == 0
                else ("current", "baseline")
            ):
                run(
                    label,
                    "CHelperLatencyBench",
                    "Bench.LongCommand",
                    f"long-{kind}-{round_index}",
                    {
                        "LONG_KIND": kind,
                        "LONG_COUNT": str(count),
                        "LONG_REPEAT": str(args.long_repeat),
                        "LONG_APPLY": "0",
                        "LONG_WARMUP": str(args.long_warmup),
                        "LONG_TRACK_PAGE_FAULTS": "1",
                    },
                )
        for label in builds:
            run(
                label,
                "CHelperBench",
                "Bench.LongCommand",
                f"allocation-long-{kind}",
                {
                    "LONG_KIND": kind,
                    "LONG_COUNT": str(count),
                    "LONG_REPEAT": "3",
                    "LONG_APPLY": "0",
                    "LONG_WARMUP": "1",
                    "LONG_TRACK_PAGE_FAULTS": "0",
                },
            )
    grouped = {}
    for record in records:
        if record["run"].startswith("timing-"):
            grouped.setdefault(record["name"], {}).setdefault(
                record["variant"], []
            ).append(record["avg_ms"])
    comparison = {}
    for name, variants in grouped.items():
        before, after = (statistics.median(variants[label]) for label in builds)
        comparison[name] = dict(
            baseline_ms=before,
            current_ms=after,
            change_percent=(after / before - 1) * 100 if before else None,
        )
    if any(
        len(values) != 2 or len(set(values.values())) != 1
        for values in fingerprints.values()
    ):
        raise RuntimeError(
            "Result fingerprints differ between the two builds; inspect the logs before interpreting timings"
        )
    result = dict(
        machine=platform.platform(),
        processor=os.environ.get("PROCESSOR_IDENTIFIER", platform.processor()),
        affinity_mask=affinity,
        rounds=args.rounds,
        long_repeat=args.long_repeat,
        long_warmup=args.long_warmup,
        windows_qos_requested=args.windows_qos,
        windows_qos=windows_qos,
        windows_qos_endpoints_verified=(
            os.name == "nt"
            and bool(windows_qos)
            and all(row.get("endpoints_verified", False) for row in windows_qos)
        ),
        long_page_faults=long_page_faults,
        long_fault_free_qualified=(
            any(record["run"].startswith("long-") for record in long_page_faults)
            and all(
                record["tracking"] and record["faulting"] == 0
                for record in long_page_faults
                if record["run"].startswith("long-")
            )
        ),
        provenance=provenance,
        critical_compiler_flags_verified=(
            before_flags is not None and after_flags is not None
        ),
        benchmark_source_files_match=(
            before_sources is not None and after_sources is not None
        ),
        same_binary_calibration=(
            provenance["baseline"]["executables"]
            == provenance["current"]["executables"]
        ),
        comparison=comparison,
        fingerprints=fingerprints,
        allocation_note="Operator new hooks do not cover every PMR allocation in the Windows dynamic runtime; do not interpret net_kib as total memory",
        records=records,
    )
    (args.output / "results.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    for name, values in comparison.items():
        if values["change_percent"] is not None:
            print(
                f"{name:56} {values['baseline_ms']:12.9f} -> {values['current_ms']:12.9f} ms ({values['change_percent']:+.1f}%)"
            )
    timed_long = [
        record for record in long_page_faults if record["run"].startswith("long-")
    ]
    faulting_iterations = sum(record["faulting"] for record in timed_long)
    measured_iterations = sum(record["measured"] for record in timed_long)
    print(
        "Long-command page-fault check: "
        f"{faulting_iterations}/{measured_iterations} measured iterations faulted"
    )
    if args.require_long_fault_free and not result["long_fault_free_qualified"]:
        raise RuntimeError(
            "Long-command samples contain page faults; raw results were saved, but fault-free qualification failed"
        )


if __name__ == "__main__":
    main()
