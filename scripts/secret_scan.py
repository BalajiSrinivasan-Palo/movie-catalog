#!/usr/bin/env python3
"""Fails (exit 1) if a tracked or untracked-but-not-ignored text file looks like it contains a secret.

No external tool is needed (S-002 AC7). Usage: secret_scan.py [paths...]  (default: whole repo)
A line can opt out with the marker `secret-scan: allow` when it is a documented fake.
"""
import pathlib
import re
import subprocess
import sys

PATTERNS = {
    "private key block": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY"),
    "AWS access key id": re.compile(r"\b(?:AKIA|ASIA)[0-9A-Z]{16}\b"),
    "GitHub token": re.compile(r"\bgh[pousr]_[A-Za-z0-9]{30,}\b"),
    "Slack token": re.compile(r"\bxox[abprs]-[A-Za-z0-9-]{10,}\b"),
    "Google API key": re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b"),
    "generic assignment": re.compile(
        r"""(?i)\b(?:api[_-]?key|secret|passwd|password|token)\b\s*[:=]\s*["'][A-Za-z0-9/+_\-]{16,}["']"""
    ),
}
SKIP_DIRS = {".git", "node_modules", "build", ".gradle", ".svelte-kit", "coverage", "data", ".kotlin"}
SKIP_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".pdf", ".jar", ".lock", ".tar", ".zip"}
ALLOW = "secret-scan: allow"


def candidate_files(args):
    if args:
        return [pathlib.Path(a) for a in args]
    root = pathlib.Path(__file__).resolve().parent.parent
    try:
        out = subprocess.run(
            ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
            cwd=root, capture_output=True, text=True, check=True,
        ).stdout.split("\n")
        return [root / p for p in out if p]
    except (subprocess.CalledProcessError, FileNotFoundError):
        return [p for p in root.rglob("*") if p.is_file()]


def scan(paths):
    findings = []
    for path in paths:
        if not path.is_file() or path.suffix.lower() in SKIP_SUFFIXES or SKIP_DIRS & set(path.parts):
            continue
        if path.name == "secret_scan.py" or path.name == "pnpm-lock.yaml":
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for n, line in enumerate(text.splitlines(), 1):
            if ALLOW in line:
                continue
            for name, rx in PATTERNS.items():
                if rx.search(line):
                    findings.append((str(path), n, name))
    return findings


def main(argv):
    findings = scan(candidate_files(argv))
    for f, n, name in findings:
        print(f"SECRET? {f}:{n}: {name}")
    if findings:
        print(f"FAIL: {len(findings)} possible secret(s)")
        return 1
    print("OK: no secrets found")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
