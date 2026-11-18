#!/usr/bin/env python3
"""Static integrity checks for versioned Nova SQL migrations (not a DB execution test)."""
from pathlib import Path
import re
import sys

ROOT = Path("supabase/migrations")
if not ROOT.is_dir():
    print("ERROR: supabase/migrations directory is missing")
    sys.exit(1)

files = sorted(ROOT.glob("*.sql"))
if not files:
    print("ERROR: no SQL migrations found")
    sys.exit(1)

errors = []
seen = set()
for path in files:
    name = path.name
    if not re.fullmatch(r"\d{14}_[a-z0-9_]+\.sql", name):
        errors.append(f"{name}: filename must start with a 14-digit timestamp")
    prefix = name[:14]
    if prefix in seen:
        errors.append(f"{name}: duplicate migration timestamp {prefix}")
    seen.add(prefix)

    sql = path.read_text(encoding="utf-8").strip()
    if not sql:
        errors.append(f"{name}: empty migration")
        continue
    if not re.search(r"\bBEGIN\s*;", sql, re.IGNORECASE):
        errors.append(f"{name}: missing BEGIN; transaction boundary")
    if not re.search(r"\bCOMMIT\s*;", sql, re.IGNORECASE):
        errors.append(f"{name}: missing COMMIT; transaction boundary")

    # PL/pgSQL bodies use $$ or tagged dollar-quoted strings. Each delimiter
    # must occur an even number of times; this catches common truncation errors.
    delimiters = re.findall(r"\$[A-Za-z_][A-Za-z_0-9]*\$|\$\$", sql)
    counts = {}
    for delimiter in delimiters:
        counts[delimiter] = counts.get(delimiter, 0) + 1
    for delimiter, count in counts.items():
        if count % 2:
            errors.append(f"{name}: unpaired dollar quote {delimiter}")

if errors:
    print("Static migration validation failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print(f"Validated {len(files)} SQL migration file(s): names, transaction boundaries and dollar-quote pairing.")
print("Note: this is a static check; it does not replace applying migrations to a disposable PostgreSQL test database.")
