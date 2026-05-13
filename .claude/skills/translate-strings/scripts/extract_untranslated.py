"""Extract untranslated Android strings from English source.

Compares values/strings.xml (English) against values-zh-rCN/strings.xml (Chinese),
outputs untranslated strings grouped by section. Supports filtering by section,
priority order (from Issue #1), and CSV output.
"""

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import OrderedDict
from pathlib import Path

ISSUE1_PRIORITY = [
    "BRAPI", "COLLECT", "GENERAL", "SETTINGS", "TRAITS",
    "FIELDS", "GeoNav", "IMPORT/EXPORT", "NearbyShare",
    "Trait Formats", "STATISTICS", "Data Grid", "ABOUT",
]


def find_strings_dir():
    """Locate the app/src/main/res directory relative to this script or CWD."""
    script_dir = Path(__file__).resolve().parent
    for anchor in [script_dir.parent.parent.parent.parent, Path.cwd()]:
        candidate = anchor / "fieldbook-android" / "app" / "src" / "main" / "res"
        if candidate.is_dir():
            return candidate
    print("ERROR: Cannot find fieldbook-android/app/src/main/res/", file=sys.stderr)
    sys.exit(1)


def parse_sections(filepath):
    """Parse a strings.xml and return OrderedDict of section_name -> [(name, value), ...].

    Section headers are XML comments like <!-- SECTION_NAME -->.
    Strings before the first section header go under "__header__".
    """
    sections = OrderedDict()
    current_section = "__header__"
    section_re = re.compile(r"<!--\s*(.+?)\s*-->")
    name_re = re.compile(r'name="([^"]+)"')
    translatable_re = re.compile(r'translatable="false"')

    with open(filepath, encoding="utf-8") as f:
        content = f.read()

    lines = content.split("\n")
    for line in lines:
        m = section_re.match(line.strip())
        if m:
            current_section = m.group(1).strip()
            if current_section not in sections:
                sections[current_section] = []
            continue

        if translatable_re.search(line):
            continue

        nm = name_re.search(line)
        if nm:
            name = nm.group(1)
            sections.setdefault(current_section, []).append(name)

    return sections


def extract_string_names(filepath):
    """Extract all translatable string names from a strings.xml file."""
    names = set()
    translatable_re = re.compile(r'translatable="false"')
    name_re = re.compile(r'name="([^"]+)"')
    with open(filepath, encoding="utf-8") as f:
        for line in f:
            if translatable_re.search(line):
                continue
            m = name_re.search(line)
            if m:
                names.add(m.group(1))
    return names


def extract_string_details(filepath):
    """Extract {name: (value, section)} for all translatable strings."""
    details = {}
    translatable_re = re.compile(r'translatable="false"')
    name_re = re.compile(r'name="([^"]+)"')
    section_re = re.compile(r"<!--\s*(.+?)\s*-->")

    current_section = "__header__"
    with open(filepath, encoding="utf-8") as f:
        lines = f.readlines()

    for line in lines:
        m = section_re.match(line.strip())
        if m:
            current_section = m.group(1).strip()
            continue
        if translatable_re.search(line):
            continue
        nm = name_re.search(line)
        if nm:
            name = nm.group(1)
            # Extract value between > and </
            val_match = re.search(r">([^<]*)<", line)
            value = val_match.group(1).strip() if val_match else ""
            details[name] = {"value": value, "section": current_section}

    return details


def priority_sort_key(section_name):
    """Sort sections by Issue #1 priority order."""
    try:
        return ISSUE1_PRIORITY.index(section_name)
    except ValueError:
        return len(ISSUE1_PRIORITY)


def main():
    parser = argparse.ArgumentParser(
        description="Extract untranslated Android strings.xml entries"
    )
    parser.add_argument(
        "--by-section",
        action="store_true",
        help="Output grouped by XML comment section",
    )
    parser.add_argument(
        "--priority",
        action="store_true",
        help="Sort sections by Issue #1 priority order",
    )
    parser.add_argument(
        "--section",
        type=str,
        help="Only output strings from a specific section",
    )
    parser.add_argument(
        "--missing-only",
        action="store_true",
        default=True,
        help="Only output missing strings (default)",
    )
    parser.add_argument(
        "--csv",
        action="store_true",
        help="Output in CSV format (name,english_value,section)",
    )

    args = parser.parse_args()
    res_dir = find_strings_dir()
    en_file = res_dir / "values" / "strings.xml"
    zh_file = res_dir / "values-zh-rCN" / "strings.xml"

    if not en_file.exists():
        print(f"ERROR: {en_file} not found", file=sys.stderr)
        sys.exit(1)
    if not zh_file.exists():
        print(f"WARNING: {zh_file} not found, treating all strings as untranslated", file=sys.stderr)

    en_names = extract_string_names(str(en_file))
    zh_names = extract_string_names(str(zh_file)) if zh_file.exists() else set()
    en_details = extract_string_details(str(en_file))
    en_sections = parse_sections(str(en_file))

    missing = sorted(en_names - zh_names)
    existing = sorted(en_names & zh_names)

    if args.csv:
        import csv
        writer = csv.writer(sys.stdout)
        writer.writerow(["name", "english_value", "section"])
        for name in missing:
            detail = en_details.get(name, {})
            section = detail.get("section", "")
            if args.section and args.section.upper() != section.upper():
                continue
            writer.writerow([name, detail.get("value", ""), section])
        return

    sections_to_show = list(en_sections.keys())
    if args.priority:
        sections_to_show.sort(key=priority_sort_key)

    total_missing = 0
    for section in sections_to_show:
        if args.section and args.section.upper() != section.upper():
            continue
        section_names = en_sections.get(section, [])
        section_missing = [n for n in section_names if n in missing]
        if not section_missing:
            continue
        total_missing += len(section_missing)

        if args.by_section:
            print(f"\n## {section} ({len(section_missing)} missing)")
            for name in section_missing:
                detail = en_details.get(name, {})
                print(f"  {name}: {detail.get('value', '')}")
        else:
            for name in section_missing:
                detail = en_details.get(name, {})
                print(f"{name}: {detail.get('value', '')}")

    if not args.by_section and not args.section:
        print(f"\n{'='*50}")
    print(f"Total: {len(en_names)} English, {len(zh_names)} Chinese, "
          f"{total_missing or len(missing)} missing, {len(existing)} translated "
          f"({len(existing)*100//len(en_names)}%)")


if __name__ == "__main__":
    main()
