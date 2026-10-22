#!/usr/bin/env python3
"""Fails when line coverage of a Kotlin module is below the gate (NFR-TEST-02, 80 percent).

Reads the XML report that `./gradlew koverXmlReport` writes for each module and looks at the report-level LINE counter.
Generated code (protobuf and gRPC stubs) lives in the `contracts` module, which is not measured.
Usage: python3 -I scripts/check_coverage.py [threshold]   (default 80)
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MODULES = ["service-support", "movie-service", "people-service", "api-gateway"]
threshold = float(sys.argv[1]) if len(sys.argv) > 1 else 80.0
root = Path(__file__).resolve().parent.parent
failed = False
for module in MODULES:
    report = root / module / "build" / "reports" / "kover" / "report.xml"
    if not report.is_file():
        print(f"{module}: NO REPORT at {report.relative_to(root)} (run: make quality)")
        failed = True
        continue
    counters = {c.get("type"): c for c in ET.parse(report).getroot().findall("counter")}
    line = counters.get("LINE")
    if line is None:
        print(f"{module}: report has no LINE counter")
        failed = True
        continue
    covered, missed = int(line.get("covered")), int(line.get("missed"))
    pct = 100.0 * covered / max(1, covered + missed)
    ok = pct >= threshold
    failed |= not ok
    print(f"{module}: {pct:.1f}% of {covered + missed} lines {'OK' if ok else f'BELOW {threshold:.0f}%'}")
sys.exit(1 if failed else 0)
