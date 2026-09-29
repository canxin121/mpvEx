#!/usr/bin/env python3
"""Check Android translations for missing keys and broken format arguments."""

from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
BASE = ROOT / "values/strings.xml"
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")
QUANTITIES = {
    "zh-rCN": {"other"},
    "zh-rTW": {"other"},
    "ja": {"other"},
    "ko": {"other"},
    "es": {"one", "many", "other"},
    "fr": {"one", "many", "other"},
    "de": {"one", "other"},
    "pt-rBR": {"one", "many", "other"},
    "ru": {"one", "few", "many", "other"},
}


def load(path: Path):
    resources = ET.parse(path).getroot()
    strings = {}
    plurals = {}
    for item in resources:
        if item.tag == "string":
            if item.attrib["name"] in strings:
                raise ValueError(f"Duplicate string: {item.attrib['name']}")
            strings[item.attrib["name"]] = item
        elif item.tag == "plurals":
            if item.attrib["name"] in plurals:
                raise ValueError(f"Duplicate plurals: {item.attrib['name']}")
            plurals[item.attrib["name"]] = item
    return strings, plurals


def arguments(text: str):
    return Counter(token for token in FORMAT.findall(text) if token != "%%")


def main():
    original, original_plurals = load(BASE)
    expected = {key for key, item in original.items() if item.get("translatable") != "false"}
    errors = []
    for path in sorted(ROOT.glob("values-*/strings.xml")):
        strings, plurals = load(path)
        missing = expected - strings.keys()
        extra = strings.keys() - expected
        if missing:
            errors.append(f"{path.parent.name}: missing {sorted(missing)}")
        if extra:
            errors.append(f"{path.parent.name}: extra {sorted(extra)}")
        for key in expected & strings.keys():
            source = original[key].text or ""
            translated = strings[key].text or ""
            if source and not translated:
                errors.append(f"{path.parent.name}/{key}: empty translation")
            if arguments(source) != arguments(translated):
                errors.append(
                    f"{path.parent.name}/{key}: arguments {arguments(translated)} "
                    f"do not match {arguments(source)}"
                )
            if "MPVEXARG" in translated or "MPVEXSEP" in translated:
                errors.append(f"{path.parent.name}/{key}: translation marker remains")
        for key in original_plurals.keys():
            if key not in plurals:
                errors.append(f"{path.parent.name}: missing plural {key}")
                continue
            source_items = original_plurals[key].findall("item")
            translated_items = plurals[key].findall("item")
            quantities = {item.get("quantity") for item in translated_items}
            required = QUANTITIES.get(path.parent.name.removeprefix("values-"), {"other"})
            if required - quantities:
                errors.append(f"{path.parent.name}/{key}: missing quantities {sorted(required - quantities)}")
            source_args = arguments(source_items[-1].text or "")
            for item in translated_items:
                if arguments(item.text or "") != source_args:
                    errors.append(f"{path.parent.name}/{key}/{item.get('quantity')}: bad arguments")
        print(f"{path.parent.name}: {len(strings)} strings, {len(plurals)} plurals")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Validated {len(expected)} translated keys in each locale")
    return 0


if __name__ == "__main__":
    sys.exit(main())
