"""
R7 statistical analysis (LO4).

Reads the raw per-request latencies written by benchmark-valuation-r7.ps1
and reports, per run and pooled:
  * nearest-rank P50 / P95 / P99 (same definition as the benchmark),
  * a percentile-bootstrap 95% confidence interval for P95,
  * the number of requests above the 200 ms R7 threshold, with an exact
    one-sided 95% Clopper-Pearson upper bound on the exceedance rate.

Standard library only; the bootstrap uses a fixed seed so the output is
reproducible.

Usage:  python performance/r7_statistics.py docs/coursework/r7-F1F66185-raw.csv
"""

import csv
import math
import random
import sys
from collections import defaultdict

THRESHOLD_MS = 200.0
BOOTSTRAP_RESAMPLES = 10_000
SEED = 20261117


def nearest_rank(values, p):
    ordered = sorted(values)
    k = math.ceil(p / 100 * len(ordered))
    return ordered[k - 1]


def bootstrap_ci(values, p, rng, level=0.95):
    n = len(values)
    stats = sorted(
        nearest_rank([values[rng.randrange(n)] for _ in range(n)], p)
        for _ in range(BOOTSTRAP_RESAMPLES)
    )
    lo = stats[int((1 - level) / 2 * BOOTSTRAP_RESAMPLES)]
    hi = stats[int((1 + level) / 2 * BOOTSTRAP_RESAMPLES) - 1]
    return lo, hi


def binom_cdf(k, n, p):
    return sum(math.comb(n, i) * p ** i * (1 - p) ** (n - i) for i in range(k + 1))


def clopper_pearson_upper(k, n, alpha=0.05):
    """Exact one-sided (1 - alpha) upper bound for a binomial proportion."""
    if k == n:
        return 1.0
    lo, hi = 0.0, 1.0
    for _ in range(100):
        mid = (lo + hi) / 2
        if binom_cdf(k, n, mid) > alpha:
            lo = mid
        else:
            hi = mid
    return hi


def main(path):
    with open(path, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))

    runs = defaultdict(list)
    for row in rows:
        if row["Status"] == "200":
            runs[row["Run"]].append(float(row["DurationMs"]))

    rng = random.Random(SEED)
    groups = [(f"Run {r}", v) for r, v in sorted(runs.items())]
    groups.append(("Pooled", [x for v in runs.values() for x in v]))

    print(f"Source: {path}")
    print(f"Bootstrap: {BOOTSTRAP_RESAMPLES} resamples, seed {SEED}\n")
    print(f"{'Group':<8}{'n':>5}{'P50':>9}{'P95':>9}{'95% CI for P95':>22}"
          f"{'P99':>9}{'Max':>10}{'>200ms':>8}{'95% upper':>11}")
    for name, values in groups:
        lo, hi = bootstrap_ci(values, 95, rng)
        k = sum(x > THRESHOLD_MS for x in values)
        ub = clopper_pearson_upper(k, len(values))
        print(f"{name:<8}{len(values):>5}{nearest_rank(values, 50):>9.2f}"
              f"{nearest_rank(values, 95):>9.2f}{f'[{lo:.2f}, {hi:.2f}]':>22}"
              f"{nearest_rank(values, 99):>9.2f}{max(values):>10.2f}"
              f"{k:>8}{ub:>10.2%}")

    print("\nNotes: consecutive sequential requests are not fully independent,")
    print("so the intervals describe this local benchmark envelope only.")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "docs/coursework/r7-F1F66185-raw.csv")
