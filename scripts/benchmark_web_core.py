"""Run the Node/Wasm benchmark with explicit process-policy handshakes.

Example: python scripts/benchmark_web_core.py before.wasm after.wasm pack.cpack --output build/web-benchmark
"""

import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import subprocess
import threading
import time


class WindowsPolicy:
    """Controls only the owned child, while it awaits a measurement-boundary ACK."""

    class Power(ctypes.Structure):
        _fields_ = [
            ("Version", ctypes.c_ulong),
            ("ControlMask", ctypes.c_ulong),
            ("StateMask", ctypes.c_ulong),
        ]

    def __init__(self, pid, requested, cpu):
        self.requested = requested
        self.cpu = cpu
        self.stage = 0
        self.original = None
        self.original_affinity = None
        self.affinity = None
        self.handle = None
        self.supported = os.name == "nt"
        if not self.supported:
            return
        self.kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        self.kernel.OpenProcess.argtypes = [
            ctypes.c_ulong,
            ctypes.c_bool,
            ctypes.c_ulong,
        ]
        self.kernel.OpenProcess.restype = ctypes.c_void_p
        self.kernel.CloseHandle.argtypes = [ctypes.c_void_p]
        for name in ("GetProcessInformation", "SetProcessInformation"):
            getattr(self.kernel, name).argtypes = [
                ctypes.c_void_p,
                ctypes.c_int,
                ctypes.c_void_p,
                ctypes.c_ulong,
            ]
        self.kernel.GetProcessAffinityMask.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_size_t),
            ctypes.POINTER(ctypes.c_size_t),
        ]
        self.kernel.SetProcessAffinityMask.argtypes = [ctypes.c_void_p, ctypes.c_size_t]
        self.handle = self.kernel.OpenProcess(0x0600, False, pid)
        if not self.handle:
            raise ctypes.WinError(ctypes.get_last_error())

    def read(self):
        value = self.Power(1, 0, 0)
        if not self.kernel.GetProcessInformation(
            self.handle, 4, ctypes.byref(value), ctypes.sizeof(value)
        ):
            raise ctypes.WinError(ctypes.get_last_error())
        allowed, system = ctypes.c_size_t(), ctypes.c_size_t()
        if not self.kernel.GetProcessAffinityMask(
            self.handle, ctypes.byref(allowed), ctypes.byref(system)
        ):
            raise ctypes.WinError(ctypes.get_last_error())
        return dict(
            control=value.ControlMask,
            state=value.StateMask,
            affinity_mask=allowed.value,
        )

    def write(self, control, state, affinity):
        value = self.Power(1, control, state)
        if not self.kernel.SetProcessInformation(
            self.handle, 4, ctypes.byref(value), ctypes.sizeof(value)
        ):
            raise ctypes.WinError(ctypes.get_last_error())
        if not self.kernel.SetProcessAffinityMask(self.handle, affinity):
            raise ctypes.WinError(ctypes.get_last_error())

    def request(self, phase):
        start = time.perf_counter_ns()
        result = dict(
            phase=phase,
            requested=self.requested,
            supported=self.supported,
            ok=False,
            start_ns=start,
        )
        try:
            expected_phase = (
                ("begin", "end", "restore")[self.stage] if self.stage < 3 else None
            )
            if phase != expected_phase:
                raise RuntimeError(
                    f"Expected policy phase {expected_phase}, received {phase}"
                )
            self.stage += 1
            if not self.supported:
                result.update(ok=True, actual=None)
                return result
            if phase == "begin":
                self.original = self.read()
                self.original_affinity = self.original["affinity_mask"]
                self.affinity = (
                    (1 << self.cpu)
                    if self.cpu is not None
                    else 1 << (self.original_affinity.bit_length() - 1)
                )
                if not self.affinity & self.original_affinity:
                    raise RuntimeError(
                        "Requested logical CPU is outside the child's allowed affinity"
                    )
                control, state = {"high": (1, 0), "eco": (1, 1), "system": (0, 0)}[
                    self.requested
                ]
                self.write(
                    (self.original["control"] & ~1) | control,
                    (self.original["state"] & ~1) | state,
                    self.affinity,
                )
            elif phase == "restore" and self.original is not None:
                self.write(
                    self.original["control"],
                    self.original["state"],
                    self.original_affinity,
                )
            actual = self.read()
            result["actual"] = actual
            if phase == "restore":
                desired = (self.original["control"] & 1, self.original["state"] & 1)
                affinity = self.original_affinity
            else:
                desired = {"high": (1, 0), "eco": (1, 1), "system": (0, 0)}[
                    self.requested
                ]
                affinity = self.affinity
            if (actual["control"] & 1, actual["state"] & 1) != desired or actual[
                "affinity_mask"
            ] != affinity:
                raise RuntimeError(
                    "Benchmark execution-policy endpoint does not match the requested state"
                )
            result["ok"] = True
        except Exception as error:
            result["error"] = str(error)
        finally:
            result["end_ns"] = time.perf_counter_ns()
        return result

    def close(self):
        if self.handle:
            self.kernel.CloseHandle(self.handle)
            self.handle = None


def file_hash(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run_benchmark(
    baseline,
    current,
    pack,
    output,
    iterations=500,
    rounds=5,
    qos="high",
    cpu=None,
    v8_default=False,
    runner=None,
):
    root = Path(__file__).resolve().parent
    runner = (runner or root / "benchmark_web_core.mjs").resolve()
    output.mkdir(parents=True, exist_ok=True)
    sources = dict(
        runner=runner,
        launcher=Path(__file__).resolve(),
        glue=root.parent / "CHelper-Web/src/core/libCHelperWeb.js",
        baseline=baseline.resolve(),
        current=current.resolve(),
        pack=pack.resolve(),
    )
    before = {
        name: dict(path=str(path), sha256=file_hash(path))
        for name, path in sources.items()
    }
    flags = (
        []
        if v8_default
        else [
            "--no-liftoff",
            "--no-wasm-lazy-compilation",
            "--wasm-num-compilation-tasks=0",
        ]
    )
    env = os.environ.copy()
    env.update(CHELPER_WEB_BENCH_CONTROL="1", CHELPER_WEB_BENCH_ROUNDS=str(rounds))
    command = [
        "node",
        *flags,
        str(runner),
        str(baseline.resolve()),
        str(current.resolve()),
        str(pack.resolve()),
        str(iterations),
    ]
    stderr, observations, reader_errors = [], [], []
    child = subprocess.Popen(
        command,
        env=env,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    policy = None
    reader = None
    returncode = None
    stdout = ""
    try:
        policy = WindowsPolicy(child.pid, qos, cpu)

        def read_control():
            try:
                for line in child.stderr:
                    stderr.append(line)
                    if not line.startswith("CHelperBenchControl "):
                        continue
                    phase = json.loads(line.removeprefix("CHelperBenchControl "))[
                        "phase"
                    ]
                    observation = policy.request(phase)
                    observations.append(observation)
                    child.stdin.write(json.dumps(observation) + "\n")
                    child.stdin.flush()
            except Exception as error:
                reader_errors.append(str(error))
                if child.poll() is None:
                    child.terminate()

        reader = threading.Thread(target=read_control)
        reader.start()
        stdout = child.stdout.read()
        returncode = child.wait()
        reader.join()
    except Exception as error:
        reader_errors.append(str(error))
        if child.poll() is None:
            child.terminate()
        returncode = child.wait()
        if reader:
            reader.join()
        else:
            stderr.append(child.stderr.read())
        stdout += child.stdout.read()
    finally:
        if child.poll() is None:
            child.terminate()
            child.wait()
        if policy:
            policy.close()
        try:
            child.stdin.close()
        except BrokenPipeError:
            pass
        child.stdout.close()
        child.stderr.close()
        (output / "stdout.log").write_text(stdout, encoding="utf-8")
        (output / "stderr.log").write_text("".join(stderr), encoding="utf-8")
        (output / "windows-qos.json").write_text(
            json.dumps(observations, indent=2), encoding="utf-8"
        )
    after = {name: file_hash(path) for name, path in sources.items()}
    failures = []
    try:
        result = json.loads(stdout)
    except ValueError:
        result = {}
        failures.append("Node did not emit a valid benchmark JSON result")
    if not isinstance(result, dict):
        result = {"invalid_result": result}
        failures.append("Node benchmark result is not an object")
    if returncode or result.get("failure") or reader_errors:
        failures.append(
            f"Benchmark process failed: exit={returncode}, reader={reader_errors}, failure={result.get('failure')}"
        )
    if [row["phase"] for row in observations] != ["begin", "end", "restore"] or not all(
        row["ok"] for row in observations
    ):
        failures.append("Benchmark lacks valid execution-policy boundary handshakes")
    if result.get("policyObservations") != observations:
        failures.append("Node and launcher policy observations differ")
    if result.get("iterations") != iterations or result.get("rounds") != rounds:
        failures.append("Benchmark ignored requested iterations or rounds")
    if any(before[name]["sha256"] != value for name, value in after.items()):
        failures.append("An input or benchmark source changed during execution")
    if (
        result.get("wasmSha256")
        != {label: before[label]["sha256"] for label in ("baseline", "current")}
        or result.get("packSha256") != before["pack"]["sha256"]
    ):
        failures.append(
            "Loaded Wasm or resource bytes do not match the launcher snapshots"
        )
    fingerprints = result.get("fingerprints", {})
    if (
        set(fingerprints) != {"baseline", "current"}
        or len(set(fingerprints.values())) != 1
    ):
        failures.append("Semantic fingerprints are absent or differ")
    if not result.get("independentInstances"):
        failures.append(
            "Baseline and current adapters do not have independent instances"
        )
    result["launcher"] = dict(
        command=command,
        returncode=returncode,
        sources=before,
        sources_unchanged=all(
            before[name]["sha256"] == value for name, value in after.items()
        ),
        windows_qos_requested=qos,
        policy_observations=observations,
        policy_endpoints_verified=os.name == "nt"
        and len(observations) == 3
        and all(row["ok"] for row in observations),
        validation_failures=failures,
        note="Policy endpoints, input binding and result semantics only; not physical-core isolation or performance acceptance",
    )
    (output / "results.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    if failures:
        raise RuntimeError("; ".join(failures))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("current", type=Path)
    parser.add_argument("pack", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--iterations", type=int, default=500)
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument(
        "--windows-qos",
        choices=("high", "system", "eco"),
        default="high" if os.name == "nt" else "system",
    )
    parser.add_argument(
        "--cpu",
        type=int,
        help="Logical CPU within the Windows process's processor group; default: highest allowed",
    )
    parser.add_argument(
        "--v8-default",
        action="store_true",
        help="Keep default V8 compilation instead of eager TurboFan",
    )
    args = parser.parse_args()
    if (
        not 1 <= args.iterations <= (1 << 53) - 1
        or not 1 <= args.rounds <= (1 << 53) - 1
    ):
        parser.error("iterations and rounds must be positive JavaScript-safe integers")
    if args.cpu is not None and not 0 <= args.cpu < 64:
        parser.error("--cpu must be between 0 and 63")
    if os.name != "nt" and (args.windows_qos != "system" or args.cpu is not None):
        parser.error("Windows QoS or affinity options require Windows")
    result = run_benchmark(
        args.baseline,
        args.current,
        args.pack,
        args.output,
        args.iterations,
        args.rounds,
        args.windows_qos,
        args.cpu,
        args.v8_default,
    )
    print(
        f"Saved {len(result['records'])} records; policy endpoints verified={result['launcher']['policy_endpoints_verified']}; this is not complete performance acceptance"
    )


if __name__ == "__main__":
    main()
