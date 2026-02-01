#!/usr/bin/env -S uv run -qs
# vim: filetype=python

# /// script
# requires-python = ">=3.12"
# dependencies = [
#   "httpx",
#   "lxml",
# ]
# ///

"""
Add dependency hashes to Gradle verification-metadata.xml.

Fetches checksums from Maven repositories when available, or computes
SHA256 locally for trust-on-first-use when .sha256 files aren't published.

Usage:
    # Add specific artifacts
    ./add-verification-hashes.py com.google.code.gson:gson:2.10.1

    # Parse Gradle output and add missing dependencies
    ./gradlew build 2>&1 | ./add-verification-hashes.py --from-gradle-output

    # Dry run (show what would be added)
    ./add-verification-hashes.py --dry-run com.google.code.gson:gson:2.10.1
"""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import TextIO

import httpx
from lxml import etree

# Maven repositories to try, in order of preference
MAVEN_REPOS = [
    ("Maven Central", "https://repo1.maven.org/maven2"),
    ("Fabric Maven", "https://maven.fabricmc.net"),
    ("OpenCollab", "https://repo.opencollab.dev/main"),
    ("OpenCollab Snapshots", "https://repo.opencollab.dev/maven-snapshots"),
    ("Modrinth", "https://api.modrinth.com/maven"),
    # Gradle Plugin Portal for buildscript dependencies
    ("Gradle Plugin Portal", "https://plugins.gradle.org/m2"),
]

NS = "https://schema.gradle.org/dependency-verification"


@dataclass
class Artifact:
    """Represents a Maven artifact."""

    group: str
    name: str
    version: str
    artifact_name: str  # e.g., "gson-2.10.1.jar" or "gson-2.10.1.pom"

    @property
    def base_path(self) -> str:
        """Maven repository path to the artifact directory."""
        group_path = self.group.replace(".", "/")
        return f"{group_path}/{self.name}/{self.version}"

    @property
    def artifact_path(self) -> str:
        """Full path to the artifact file."""
        return f"{self.base_path}/{self.artifact_name}"

    def __str__(self) -> str:
        return f"{self.group}:{self.name}:{self.version}:{self.artifact_name}"


@dataclass
class HashResult:
    """Result of fetching or computing a hash."""

    sha256: str
    origin: str  # Description of how the hash was obtained


def fetch_sha256_from_repo(
    client: httpx.Client, repo_url: str, artifact: Artifact
) -> str | None:
    """Try to fetch .sha256 file from a Maven repository."""
    url = f"{repo_url}/{artifact.artifact_path}.sha256"
    try:
        response = client.get(url, follow_redirects=True)
        if response.status_code == 200:
            # SHA256 files typically contain just the hash, possibly with filename
            content = response.text.strip()
            # Handle formats like "abc123  filename" or just "abc123"
            sha256 = content.split()[0]
            if len(sha256) == 64 and all(
                c in "0123456789abcdef" for c in sha256.lower()
            ):
                return sha256.lower()
    except httpx.RequestError:
        pass
    return None


def compute_sha256_from_download(
    client: httpx.Client, repo_url: str, artifact: Artifact
) -> str | None:
    """Download artifact and compute SHA256 locally."""
    url = f"{repo_url}/{artifact.artifact_path}"
    try:
        response = client.get(url, follow_redirects=True)
        if response.status_code == 200:
            sha256 = hashlib.sha256(response.content).hexdigest()
            return sha256
    except httpx.RequestError:
        pass
    return None


def get_artifact_hash(client: httpx.Client, artifact: Artifact) -> HashResult | None:
    """Get SHA256 hash for an artifact, trying repos in order."""
    # First, try to fetch .sha256 files from all repos
    for repo_name, repo_url in MAVEN_REPOS:
        sha256 = fetch_sha256_from_repo(client, repo_url, artifact)
        if sha256:
            return HashResult(sha256=sha256, origin=f"Fetched from {repo_name}")

    # If no .sha256 file found, download and compute (trust-on-first-use)
    for repo_name, repo_url in MAVEN_REPOS:
        sha256 = compute_sha256_from_download(client, repo_url, artifact)
        if sha256:
            return HashResult(
                sha256=sha256, origin=f"Computed from download ({repo_name})"
            )

    return None


def parse_gradle_output(stream: TextIO) -> list[Artifact]:
    """Parse Gradle build output to find missing verification entries."""
    artifacts = []

    # Pattern matching Gradle's verification failure list format:
    # "    - artifact-name.jar (group:name:version) from repository ..."
    list_pattern = re.compile(
        r"^\s+-\s+(\S+)\s+\(([^:]+):([^:]+):([^)]+)\)\s+from repository",
        re.MULTILINE,
    )

    # Pattern matching Gradle's single-artifact error format:
    # "Artifact orthocamera-0.1.10+1.21.9.jar (maven.modrinth:orthocamera:0.1.10+1.21.9) checksum is missing"
    missing_pattern = re.compile(
        r"Artifact\s+(\S+)\s+\(([^:]+):([^:]+):([^)]+)\)\s+checksum is missing"
    )

    # General artifact pattern for other error formats
    artifact_pattern = re.compile(r"Artifact\s+(\S+)\s+\(([^:]+):([^:]+):([^)]+)\)")

    content = stream.read()

    # Try list pattern first (most common in multi-artifact failures)
    for match in list_pattern.finditer(content):
        artifact_name, group, name, version = match.groups()
        artifacts.append(
            Artifact(
                group=group,
                name=name,
                version=version,
                artifact_name=artifact_name,
            )
        )

    # Then try missing pattern
    for match in missing_pattern.finditer(content):
        artifact_name, group, name, version = match.groups()
        artifact = Artifact(
            group=group,
            name=name,
            version=version,
            artifact_name=artifact_name,
        )
        if artifact not in artifacts:
            artifacts.append(artifact)

    # Finally try general artifact pattern
    for match in artifact_pattern.finditer(content):
        artifact_name, group, name, version = match.groups()
        artifact = Artifact(
            group=group,
            name=name,
            version=version,
            artifact_name=artifact_name,
        )
        if artifact not in artifacts:
            artifacts.append(artifact)

    return artifacts


def parse_coordinates(coord: str) -> list[Artifact]:
    """Parse Maven coordinates into Artifact objects.

    Supports formats:
    - group:name:version (generates .jar and .pom artifacts)
    - group:name:version:artifact.jar (specific artifact)
    """
    parts = coord.split(":")
    if len(parts) < 3:
        raise ValueError(f"Invalid coordinates: {coord}")

    group, name, version = parts[0], parts[1], parts[2]

    if len(parts) > 3:
        # Specific artifact provided
        artifact_name = parts[3]
        return [
            Artifact(
                group=group, name=name, version=version, artifact_name=artifact_name
            )
        ]
    else:
        # Generate common artifacts
        base = f"{name}-{version}"
        return [
            Artifact(
                group=group, name=name, version=version, artifact_name=f"{base}.jar"
            ),
            Artifact(
                group=group, name=name, version=version, artifact_name=f"{base}.pom"
            ),
        ]


def parse_version_key(version: str) -> tuple:
    """Parse version string for sorting, handling numeric components properly."""
    parts: list[tuple[int, int | str]] = []
    for part in re.split(r"([0-9]+)", version):
        if part.isdigit():
            parts.append((0, int(part)))
        elif part:
            parts.append((1, part))
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
        comment_text = str(elem.text) if elem.text else ""
        lines.append(f"{indent_str}<!--{comment_text}-->")
        return

    # Build tag with attributes
    tag = elem.tag
    if tag.startswith("{"):
        tag = tag.split("}", 1)[1]

    attrs = []
    for key, value in elem.attrib.items():
        if key.startswith("{"):
            ns, local = key[1:].split("}", 1)
            if ns == "http://www.w3.org/2001/XMLSchema-instance":
                attrs.append(f'xsi:{local}="{value}"')
            else:
                attrs.append(f'{local}="{value}"')
        else:
            attrs.append(f'{key}="{value}"')

    if indent == 0:
        attrs.insert(0, f'xmlns="{NS}"')
        attrs.insert(1, 'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"')

    attr_str = " ".join(attrs)
    if attr_str:
        attr_str = " " + attr_str

    children = list(elem)
    if children:
        lines.append(f"{indent_str}<{tag}{attr_str}>")
        for child in children:
            _serialize_element(child, lines, indent + 1)
        lines.append(f"{indent_str}</{tag}>")
    elif elem.text and elem.text.strip():
        lines.append(f"{indent_str}<{tag}{attr_str}>{elem.text}</{tag}>")
    else:
        lines.append(f"{indent_str}<{tag}{attr_str}/>")


def load_verification_metadata(path: Path) -> etree._ElementTree:
    """Load and parse verification-metadata.xml."""
    parser = etree.XMLParser(remove_blank_text=True, remove_comments=False)
    return etree.parse(str(path), parser)


def find_or_create_component(
    components: etree._Element, group: str, name: str, version: str
) -> etree._Element:
    """Find existing component or create a new one."""
    for component in components.findall(f"{{{NS}}}component"):
        if (
            component.get("group") == group
            and component.get("name") == name
            and component.get("version") == version
        ):
            return component

    # Create new component
    component = etree.SubElement(
        components,
        f"{{{NS}}}component",
        {"group": group, "name": name, "version": version},
    )
    return component


def add_artifact_hash(
    component: etree._Element, artifact_name: str, sha256: str, origin: str
) -> bool:
    """Add artifact hash to component. Returns True if added, False if exists."""
    # Check if artifact already exists
    for existing in component.findall(f"{{{NS}}}artifact"):
        if existing.get("name") == artifact_name:
            # Check if hash already present
            for sha in existing.findall(f"{{{NS}}}sha256"):
                if sha.get("value") == sha256:
                    return False  # Already exists
            # Add hash to existing artifact
            sha_elem = etree.SubElement(existing, f"{{{NS}}}sha256")
            sha_elem.set("value", sha256)
            sha_elem.set("origin", origin)
            return True

    # Create new artifact entry
    artifact_elem = etree.SubElement(
        component, f"{{{NS}}}artifact", {"name": artifact_name}
    )
    sha_elem = etree.SubElement(artifact_elem, f"{{{NS}}}sha256")
    sha_elem.set("value", sha256)
    sha_elem.set("origin", origin)
    return True


def sort_and_save_verification_metadata(
    tree: etree._ElementTree, path: Path
) -> None:
    """Sort components and save verification-metadata.xml with proper formatting."""
    root = tree.getroot()
    components = root.find(f"{{{NS}}}components")
    if components is None:
        raise ValueError("No <components> section in metadata file")

    # Separate comments from components
    comments_before: list[etree._Comment] = []
    component_list: list[etree._Element] = []

    for child in components:
        if isinstance(child, etree._Comment):
            if not component_list:
                comments_before.append(child)
        elif child.tag == f"{{{NS}}}component":
            component_list.append(child)

    # Sort artifacts within each component
    for component in component_list:
        artifacts = [
            c for c in component if c.tag == f"{{{NS}}}artifact"
        ]
        for artifact in artifacts:
            component.remove(artifact)
        artifacts.sort(key=artifact_sort_key)
        for artifact in artifacts:
            component.append(artifact)

    # Sort components
    component_list.sort(key=component_sort_key)

    # Rebuild components section
    components.clear()
    for comment in comments_before:
        components.append(comment)
    for component in component_list:
        components.append(component)

    # Custom serialization
    output_lines = ['<?xml version="1.0" encoding="UTF-8"?>']
    _serialize_element(root, output_lines, indent=0)
    output = "\n".join(output_lines) + "\n"

    path.write_text(output)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Add dependency hashes to Gradle verification-metadata.xml"
    )
    parser.add_argument(
        "coordinates",
        nargs="*",
        help="Maven coordinates (group:name:version or group:name:version:artifact)",
    )
    parser.add_argument(
        "--from-gradle-output",
        action="store_true",
        help="Parse Gradle output from stdin to find missing dependencies",
    )
    parser.add_argument(
        "--metadata-file",
        type=Path,
        default=Path("gradle/verification-metadata.xml"),
        help="Path to verification-metadata.xml",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would be added without modifying the file",
    )
    parser.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="Show detailed progress",
    )

    args = parser.parse_args()

    # Collect artifacts to process
    artifacts: list[Artifact] = []

    if args.from_gradle_output:
        artifacts.extend(parse_gradle_output(sys.stdin))

    for coord in args.coordinates:
        try:
            artifacts.extend(parse_coordinates(coord))
        except ValueError as e:
            print(f"Error: {e}", file=sys.stderr)
            sys.exit(1)

    if not artifacts:
        print(
            "No artifacts to process. Provide coordinates or use --from-gradle-output"
        )
        sys.exit(0)

    # Deduplicate
    seen = set()
    unique_artifacts = []
    for artifact in artifacts:
        key = (artifact.group, artifact.name, artifact.version, artifact.artifact_name)
        if key not in seen:
            seen.add(key)
            unique_artifacts.append(artifact)
    artifacts = unique_artifacts

    print(f"Processing {len(artifacts)} artifact(s)...")

    # Load metadata file
    if not args.metadata_file.exists():
        print(f"Error: {args.metadata_file} not found", file=sys.stderr)
        sys.exit(1)

    tree = load_verification_metadata(args.metadata_file)
    root = tree.getroot()
    components = root.find(f"{{{NS}}}components")
    if components is None:
        print("Error: No <components> section in metadata file", file=sys.stderr)
        sys.exit(1)

    # Process artifacts
    added_count = 0
    failed_count = 0

    with httpx.Client(timeout=30.0) as client:
        for artifact in artifacts:
            if args.verbose:
                print(f"  Processing {artifact}...")

            result = get_artifact_hash(client, artifact)
            if result is None:
                print(f"  FAILED: Could not fetch {artifact}")
                failed_count += 1
                continue

            if args.dry_run:
                print(f"  Would add: {artifact}")
                print(f"    SHA256: {result.sha256}")
                print(f"    Origin: {result.origin}")
                added_count += 1
            else:
                component = find_or_create_component(
                    components, artifact.group, artifact.name, artifact.version
                )
                if add_artifact_hash(
                    component, artifact.artifact_name, result.sha256, result.origin
                ):
                    print(f"  Added: {artifact}")
                    if args.verbose:
                        print(f"    SHA256: {result.sha256}")
                        print(f"    Origin: {result.origin}")
                    added_count += 1
                else:
                    if args.verbose:
                        print(f"  Skipped (already exists): {artifact}")

    # Save if not dry run and changes were made
    if not args.dry_run and added_count > 0:
        sort_and_save_verification_metadata(tree, args.metadata_file)
        print(f"\nAdded {added_count} hash(es) to {args.metadata_file}")

    if failed_count > 0:
        print(f"\nWarning: {failed_count} artifact(s) could not be fetched")
        sys.exit(1)


if __name__ == "__main__":
    main()
