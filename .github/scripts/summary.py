"""Writes a Markdown test summary for the GitHub Actions run page.

Usage: python3 summary.py backend|frontend   (run from that folder)
"""
import csv
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET


def junit_counts(files):
    total = failures = errors = skipped = 0
    suites = []
    for f in files:
        root = ET.parse(f).getroot()
        nodes = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for s in nodes:
            t, fl, er, sk = (int(s.get(k, 0)) for k in ("tests", "failures", "errors", "skipped"))
            total, failures, errors, skipped = total + t, failures + fl, errors + er, skipped + sk
            suites.append((s.get("name", "?").split(".")[-1], t, fl + er))
    return total, failures, errors, skipped, suites


def table(title, counts, coverage):
    total, failures, errors, skipped, suites = counts
    passed = total - failures - errors - skipped
    icon = "✅" if failures + errors == 0 and total > 0 else "❌"
    out = [f"## {icon} {title}", "",
           f"**{passed} passed**, {failures + errors} failed, {skipped} skipped — {total} tests", ""]
    if coverage:
        out += [f"Line coverage: **{coverage}**", ""]
    out += ["| Suite | Tests | Failed |", "|---|---:|---:|"]
    out += [f"| {n} | {t} | {f} |" for n, t, f in sorted(suites)]
    return "\n".join(out) + "\n"


def backend():
    counts = junit_counts(glob.glob("target/surefire-reports/TEST-*.xml"))
    cov = None
    if os.path.exists("target/site/jacoco/jacoco.csv"):
        missed = covered = 0
        with open("target/site/jacoco/jacoco.csv") as fh:
            for row in csv.DictReader(fh):
                missed += int(row["LINE_MISSED"])
                covered += int(row["LINE_COVERED"])
        if missed + covered:
            cov = f"{covered / (missed + covered) * 100:.1f}%"
    print(table("Backend – JUnit 5 / Mockito / MockMvc / H2", counts, cov))


def frontend():
    counts = junit_counts(glob.glob("test-results/junit.xml"))
    cov = None
    if os.path.exists("coverage/coverage-summary.json"):
        with open("coverage/coverage-summary.json") as fh:
            cov = f"{json.load(fh)['total']['lines']['pct']}%"
    print(table("Frontend – Vitest / React Testing Library", counts, cov))


if __name__ == "__main__":
    {"backend": backend, "frontend": frontend}[sys.argv[1]]()
