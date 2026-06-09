#!/usr/bin/env python3
"""Generate S57CatalogLookups.kt from OpenCPN-style S-57 CSV files.

Usage:
  python3 tools/generate_s57_catalog_lookup.py \
    --objects /path/to/s57objectclasses.csv \
    --attributes /path/to/s57attributes.csv \
    --output s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57CatalogLookups.kt

The CSV headers expected by this script are the same as the companion
s52-kotlin-webgl OpenCPN inventory:
  s57objectclasses.csv: Code,ObjectClass,Acronym,...
  s57attributes.csv:    Code,Attribute,Acronym,...
"""

from __future__ import annotations

import argparse
import csv
from pathlib import Path


def read_catalog(path: Path) -> list[tuple[int, str]]:
    entries: dict[int, str] = {}
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            raw_code = (row.get("Code") or "").strip()
            acronym = (row.get("Acronym") or "").strip()
            if not raw_code.isdigit() or not acronym:
                continue
            entries.setdefault(int(raw_code), acronym)
    return sorted(entries.items())


def render_map(entries: list[tuple[int, str]], indent: str = "        ") -> str:
    lines: list[str] = []
    for index, (code, acronym) in enumerate(entries):
        comma = "," if index < len(entries) - 1 else ""
        escaped = acronym.replace("\\", "\\\\").replace('"', '\\"')
        lines.append(f'{indent}{code} to "{escaped}"{comma}')
    return "\n".join(lines)


def render_kotlin(objects: list[tuple[int, str]], attributes: list[tuple[int, str]]) -> str:
    return f'''package io.github.s57.core.raw

/**
 * Generated-style S-57 object/attribute lookup used by the raw decoder.
 *
 * Phase 25 expands this table from the OpenCPN S-57 CSV inventory bundled with
 * the companion S-52 catalogue project. Unknown values remain stable as
 * OBJL_### / ATTL_### so unsupported catalogue rows never crash raw decode.
 */
object S57ObjectClassLookup {{
    private val byCode: Map<Int, String> = mapOf(
{render_map(objects)}
    )

    private val byAcronym: Map<String, Int> = byCode.entries.associate {{ (code, acronym) -> acronym.trim().uppercase() to code }}

    fun acronym(code: Int): String = byCode[code] ?: "OBJL_" + code

    fun code(acronym: String): Int? = byAcronym[acronym.trim().uppercase()]

    fun isKnown(code: Int): Boolean = code in byCode

    fun knownCount(): Int = byCode.size

    fun entries(): Map<Int, String> = byCode
}}

object S57AttributeLookup {{
    private val byCode: Map<Int, String> = mapOf(
{render_map(attributes)}
    )

    private val byAcronym: Map<String, Int> = byCode.entries.associate {{ (code, acronym) -> acronym.trim().uppercase() to code }}

    fun acronym(code: Int): String = byCode[code] ?: "ATTL_" + code

    fun code(acronym: String): Int? = byAcronym[acronym.trim().uppercase()]

    fun isKnown(code: Int): Boolean = code in byCode

    fun knownCount(): Int = byCode.size

    fun entries(): Map<Int, String> = byCode
}}
'''


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--objects", required=True, type=Path)
    parser.add_argument("--attributes", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    objects = read_catalog(args.objects)
    attributes = read_catalog(args.attributes)
    output = render_kotlin(objects, attributes)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(output, encoding="utf-8")
    print(f"wrote {args.output} objects={len(objects)} attributes={len(attributes)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
