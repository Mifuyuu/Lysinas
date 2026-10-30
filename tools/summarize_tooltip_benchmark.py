#!/usr/bin/env python3
"""Summarize config/lysinas-benchmark.csv emitted by the in-game benchmark screen."""

from __future__ import annotations

import argparse
import csv
import statistics
from collections import defaultdict
from pathlib import Path


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    if not ordered:
        return 0.0
    index = min(len(ordered) - 1, max(0, int((len(ordered) * fraction + 0.999999)) - 1))
    return ordered[index]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", nargs="?", default="config/lysinas-benchmark.csv", type=Path,
                        help="CSV produced by the in-game benchmark")
    args = parser.parse_args()
    if not args.csv.is_file():
        parser.error(f"benchmark CSV not found: {args.csv}")

    groups: dict[tuple[str, int], list[float]] = defaultdict(list)
    with args.csv.open(newline="", encoding="utf-8") as stream:
        for row in csv.DictReader(stream):
            try:
                phase = row["phase"]
                tooltips = int(row["tooltips"])
                frame_ms = float(row["frame_ms"])
            except (KeyError, TypeError, ValueError) as exc:
                parser.error(f"invalid benchmark row: {row!r} ({exc})")
            if phase not in {"shader", "vanilla"} or frame_ms <= 0:
                continue
            groups[(phase, tooltips)].append(frame_ms)

    if not groups:
        parser.error("CSV contains no valid measurements")

    print("| Mode | Tooltips/frame | Samples | Mean ms | Median ms | P95 ms | P99 ms | Avg FPS |")
    print("|---|---:|---:|---:|---:|---:|---:|---:|")
    summaries: dict[tuple[str, int], tuple[float, float]] = {}
    for (phase, tooltips), values in sorted(groups.items(), key=lambda item: (item[0][1], item[0][0])):
        mean = statistics.fmean(values)
        median = statistics.median(values)
        p95 = percentile(values, 0.95)
        p99 = percentile(values, 0.99)
        fps = 1000.0 / mean if mean else 0.0
        summaries[(phase, tooltips)] = (mean, p95)
        print(f"| {phase} | {tooltips} | {len(values)} | {mean:.3f} | {median:.3f} | {p95:.3f} | {p99:.3f} | {fps:.1f} |")

    for tooltips in sorted({count for _, count in summaries}):
        shader = summaries.get(("shader", tooltips))
        vanilla = summaries.get(("vanilla", tooltips))
        if shader and vanilla and vanilla[0] > 0:
            overhead = (shader[0] / vanilla[0] - 1.0) * 100.0
            p95_delta = shader[1] - vanilla[1]
            print(f"\n{tooltips} tooltips/frame: shader mean overhead {overhead:+.1f}%; P95 delta {p95_delta:+.3f} ms")

    print("\nFrame-time samples include CPU scheduling and any GPU synchronization; they are not GPU-only timings.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
