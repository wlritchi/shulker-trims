#!/usr/bin/env -S uv run -qs
# vim: filetype=python

# /// script
# requires-python = ">=3.12"
# dependencies = [
#   "lxml",
# ]
# ///

"""
Normalize Gradle verification-metadata.xml by sorting components alphabetically.

Sorts by: group, then name, then version.
Within each component, artifacts are sorted by name.
Preserves XML comments and formatting.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

from lxml import etree

NS = "https://schema.gradle.org/dependency-verification"
NSMAP = {None: NS}


def parse_version_key(version: str) -> tuple:
    """Parse version string for sorting, handling numeric components properly."""
    parts: list[tuple[int, int | str]] = []
    for part in re.split(r"([0-9]+)", version):
        if part.isdigit():
            parts.append((0, int(part)))  # Numbers sort first, by numeric value
        elif part:
            parts.append((1, part))  # Strings sort after, lexicographically
    return tuple(parts)


def component_sort_key(component: etree._Element) -> tuple:
    """Sort key for components: group, name, then version."""
    group = component.get("group", "")
    name = component.get("name", "")
    version = component.get("version", "")
    return (group, name, parse_version_key(version))


def artifact_sort_key(artifact: etree._Element) -> str:
    """Sort key for artifacts within a component."""
    return artifact.get("name", "")


def _serialize_element(
    elem: etree._Element | etree._Comment,
    lines: list[str],
    indent: int,
) -> None:
    """Serialize an element with 3-space indentation matching Gradle's format."""
    indent_str = "   " * indent

    # Handle comments
    if isinstance(elem, etree._Comment):
        # Preserve multi-line comment formatting
        comment_text = str(elem.text) if elem.text else ""
        lines.append(f"{indent_str}<!--{comment_text}-->")
        return

    # Build tag with attributes
    # Strip namespace from tag for output
    tag = elem.tag
    if tag.startswith("{"):
        tag = tag.split("}", 1)[1]

    # Format attributes - root element keeps namespace attrs, others don't
    attrs = []
    for key, value in elem.attrib.items():
        # Keep xmlns attributes only on root element
        if key.startswith("{"):
            # Handle namespaced attributes like xsi:schemaLocation
            ns, local = key[1:].split("}", 1)
            if ns == "http://www.w3.org/2001/XMLSchema-instance":
                attrs.append(f'xsi:{local}="{value}"')
            else:
                attrs.append(f'{local}="{value}"')
        else:
            attrs.append(f'{key}="{value}"')

    # Add xmlns declarations for root element
    if indent == 0:
        attrs.insert(0, f'xmlns="{NS}"')
        attrs.insert(
            1,
            'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
        )

    attr_str = " ".join(attrs)
    if attr_str:
        attr_str = " " + attr_str

    # Check for children
    children = list(elem)
    if children:
        lines.append(f"{indent_str}<{tag}{attr_str}>")
        for child in children:
            _serialize_element(child, lines, indent + 1)
        lines.append(f"{indent_str}</{tag}>")
    elif elem.text and elem.text.strip():
        # Element with text content
        lines.append(f"{indent_str}<{tag}{attr_str}>{elem.text}</{tag}>")
    else:
        # Self-closing tag
        lines.append(f"{indent_str}<{tag}{attr_str}/>")


def normalize_verification_metadata(path: Path) -> None:
    """Normalize the verification-metadata.xml file."""
    # Parse with comment preservation
    parser = etree.XMLParser(remove_blank_text=True, remove_comments=False)
    tree = etree.parse(str(path), parser)
    root = tree.getroot()

    # Find components section
    components = root.find(f"{{{NS}}}components")
    if components is None:
        print("No <components> section found", file=sys.stderr)
        sys.exit(1)

    # Separate comments from components
    comments_before_components: list[etree._Comment] = []
    component_list: list[etree._Element] = []

    for child in components:
        if isinstance(child, etree._Comment):
            # Comments at the start go before all components
            if not component_list:
                comments_before_components.append(child)
            # Comments after components are rare; we'll lose them
        elif child.tag == f"{{{NS}}}component":
            component_list.append(child)

    # Sort artifacts within each component
    for component in component_list:
        artifacts: list[etree._Element] = []
        other_children: list[etree._Element | etree._Comment] = []

        for child in list(component):
            if isinstance(child, etree._Comment):
                other_children.append(child)
            elif child.tag == f"{{{NS}}}artifact":
                artifacts.append(child)
                component.remove(child)
            else:
                other_children.append(child)

        # Sort and re-add artifacts
        artifacts.sort(key=artifact_sort_key)
        for artifact in artifacts:
            component.append(artifact)

    # Sort components
    component_list.sort(key=component_sort_key)

    # Clear and rebuild components section
    components.clear()

    # Re-add leading comments
    for comment in comments_before_components:
        components.append(comment)

    # Re-add sorted components
    for component in component_list:
        components.append(component)

    # Custom serialization to match Gradle's formatting (3-space indent, double quotes)
    output_lines = ['<?xml version="1.0" encoding="UTF-8"?>']
    _serialize_element(root, output_lines, indent=0)
    output = "\n".join(output_lines) + "\n"

    path.write_text(output)
    print(f"Normalized {path}")
    print(f"  {len(component_list)} components sorted")


def main() -> None:
    if len(sys.argv) > 1:
        path = Path(sys.argv[1])
    else:
        # Default path
        path = Path("gradle/verification-metadata.xml")

    if not path.exists():
        print(f"File not found: {path}", file=sys.stderr)
        sys.exit(1)

    normalize_verification_metadata(path)


if __name__ == "__main__":
    main()
