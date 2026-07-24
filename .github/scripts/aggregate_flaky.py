#!/usr/bin/env python3
"""Aggregate flaky-test-repeat results into a per-OS flakiness report.

Reads artifacts produced by .github/workflows/flaky-test-repeat.yml. Each
artifact directory is named ``flaky-results-<os>-shard-<n>`` and contains:

  * ``manifest.csv``   -- one ``iteration,exit_code`` row per test run
  * ``iter-<i>/*.xml`` -- JUnit XML for that iteration (for failure detail)

Run-level flake rate is derived from the manifests (authoritative pass/fail per
Gradle invocation). Distinct failure messages are grouped from the JUnit XML.

Usage: aggregate_flaky.py <artifacts_dir>
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict


def parse_os_and_shard(artifact_name):
    body = artifact_name[len("flaky-results-"):]
    if "-shard-" in body:
        os_name, shard = body.rsplit("-shard-", 1)
        return os_name, shard
    return body, "?"


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "artifacts"

    # os -> counters
    stats = defaultdict(lambda: {"runs": 0, "failed_runs": 0,
                                 "tc_total": 0, "tc_failed": 0})
    # os -> failure message -> count
    failures = defaultdict(lambda: defaultdict(int))

    artifact_dirs = sorted(glob.glob(os.path.join(root, "flaky-results-*")))
    if not artifact_dirs:
        print(f"No flaky-results-* artifacts found under {root!r}")

    for artifact_dir in artifact_dirs:
        os_name, _shard = parse_os_and_shard(os.path.basename(artifact_dir))

        # Run-level pass/fail from the manifest.
        manifest = os.path.join(artifact_dir, "manifest.csv")
        if os.path.exists(manifest):
            with open(manifest) as fh:
                for line in fh:
                    line = line.strip()
                    if not line:
                        continue
                    parts = line.split(",")
                    if len(parts) < 2:
                        continue
                    stats[os_name]["runs"] += 1
                    if parts[1] != "0":
                        stats[os_name]["failed_runs"] += 1

        # Testcase-level detail from JUnit XML.
        for xml_file in glob.glob(os.path.join(artifact_dir, "**", "*.xml"),
                                  recursive=True):
            try:
                root_el = ET.parse(xml_file).getroot()
            except ET.ParseError:
                continue
            suites = [root_el] if root_el.tag == "testsuite" \
                else root_el.findall(".//testsuite")
            for suite in suites:
                for tc in suite.findall("testcase"):
                    stats[os_name]["tc_total"] += 1
                    node = tc.find("failure")
                    if node is None:
                        node = tc.find("error")
                    if node is not None:
                        stats[os_name]["tc_failed"] += 1
                        raw = (node.get("message") or node.text or "unknown")
                        msg = raw.strip().splitlines()[0][:200] if raw.strip() \
                            else "unknown"
                        failures[os_name][msg] += 1

    # Build the markdown report.
    lines = ["# Flaky Test Repeat Results", ""]
    lines.append("| OS | Runs | Failed runs | Flake rate | Testcases | TC failed |")
    lines.append("|---|---|---|---|---|---|")
    for os_name in sorted(stats):
        st = stats[os_name]
        rate = (st["failed_runs"] / st["runs"] * 100) if st["runs"] else 0.0
        lines.append(
            f"| {os_name} | {st['runs']} | {st['failed_runs']} | "
            f"{rate:.2f}% | {st['tc_total']} | {st['tc_failed']} |"
        )
    lines.append("")

    for os_name in sorted(failures):
        if not failures[os_name]:
            continue
        lines.append(f"## Distinct failures on {os_name}")
        for msg, count in sorted(failures[os_name].items(),
                                 key=lambda kv: -kv[1]):
            lines.append(f"- **{count}x** `{msg}`")
        lines.append("")

    report = "\n".join(lines)
    print(report)

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as fh:
            fh.write(report + "\n")


if __name__ == "__main__":
    main()
