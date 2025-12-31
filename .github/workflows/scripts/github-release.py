#!/usr/bin/env python3
"""
This script is used with do-github-release.yml. The workflow is triggered on:
- Manual dispatch: Releases unconditionally with a specified version bump.
- Weekly schedule: Runs every Monday at 3:00 PM EST and performs a patch release if the app directory has changed since the last release.

The YAML workflow first checks if a release is needed by comparing app directory changes 
since the last git tag. If changes are found (or manual trigger), it bumps version numbers 
in version.properties and calls this script to process changelog content.

After that, the Python script executes in the following steps:
1. extract_unreleased_content: 
    find_unreleased_section: locate the start and end lines of the unreleased section in CHANGELOG.md
    extract the content between these lines
    skip the header line and parse content between [Unreleased] and next [v*] section
2. filter_sections_with_content: 
    only keep sections (### Added, ### Changed, ### Fixed) that 
    contain actual release notes with actual '- ' bullet points
3. update_changelog_md: 
    create new [vX.Y.Z] - YYYY-MM-DD section with filtered content above the template
    replace [Unreleased] section with fresh template (UNRELEASED_TEMPLATE)
    add GitHub release reference link at bottom: [vX.Y.Z]: https://github.com/repo/releases/tag/X.Y.Z
4. update_changelog_xml: 
    convert markdown sections to XML types (CHANGELOG_XML_TYPE_MAP)
    strip "- " bullet points and " (PR_URL)" links from release notes
    insert new <release version="X.Y.Z" versionCode="X0Y0Z" date="YYYY-MM-DD"> block at top
5. format_changelog_for_github: 
    replace "- " with "✔ " for better GitHub release formatting
    escape newlines with %0A for GitHub Actions multi-line output
6. set_multi_line_github_output: set changelog_additions and filtered_content for YAML workflow

YAML workflow continues with: commit changes → build APK → sign APK → create GitHub release → 
conditionally upload to Play Store (skipped April 15 - Sept 15 per date check)

Note: The workflow fails if no content is found to release (no unreleased notes).
"""
import sys
import os
import re
from datetime import datetime
from utils import read_file, write_file, set_multi_line_github_output, remove_trailing_empty_lines, has_trailing_empty_lines

# insert this at the top of CHANGELOG.md while updating
UNRELEASED_TEMPLATE = [
    "## [Unreleased]", "", "### Added", "", "### Changed", "", "### Fixed", ""
]

# Changelog.md section to XML item type mapping
CHANGELOG_XML_TYPE_MAP = {'### Added': 'new', '### Changed': 'info', '### Fixed': 'bugfix'}

def find_unreleased_section(lines):
    unreleased_start = None
    unreleased_end = None

    for index, line in enumerate(lines):
        if line.strip() == "## [Unreleased]":
            unreleased_start = index
        elif unreleased_start is not None and line.startswith("## [v"):
            unreleased_end = index
            break
    return unreleased_start, unreleased_end

def extract_unreleased_content(changelog_md_path):
    """Extract content from the Unreleased section of CHANGELOG.md"""
    content = read_file(changelog_md_path)
    lines = content.split('\n')
    
    unreleased_start, unreleased_end = find_unreleased_section(lines)
    
    if unreleased_start is None:
        print("Error: No Unreleased section found in CHANGELOG.md")
        sys.exit(1)
    
    if unreleased_end is None:
        unreleased_end = len(lines)
    
    # extract unreleased section content (skip header and first empty line)
    unreleased_lines = lines[unreleased_start + 2 : unreleased_end]
    
    unreleased_lines = remove_trailing_empty_lines(unreleased_lines)

    extracted_unreleased = '\n'.join(unreleased_lines)

    print(f"Extracted Unreleased content:\n{extracted_unreleased}")

    return extracted_unreleased

def filter_sections_with_content(unreleased_content):
    """Remove sections that have no content (no bullet points)"""
    if not unreleased_content.strip():
        return ""
    
    lines = unreleased_content.split('\n')
    filtered_lines = []
    previous_section_header = None
    section_content = []

    for line in lines:
        if line.strip().startswith('### '): # new section header encountered
            # save previous section if it had content
            if (previous_section_header and any(l.strip().startswith('- ') for l in section_content)):

                filtered_lines.append(previous_section_header)
                filtered_lines.extend(section_content)

                if has_trailing_empty_lines(filtered_lines):
                    pass  # keep trailing empty line for spacing
                else:
                    filtered_lines.append('')  # add spacing between sections
            
            # Start new section
            previous_section_header = line
            section_content = []
        else:
            section_content.append(line)
    
    # for last section
    if previous_section_header and any(l.strip().startswith('- ') for l in section_content):
        filtered_lines.append(previous_section_header)
        filtered_lines.extend(section_content)
    
    # remove trailing empty lines
    filtered_lines = remove_trailing_empty_lines(filtered_lines)
    
    filtered_joined = '\n'.join(filtered_lines)

    return filtered_joined

def update_changelog_md(filtered_content, github_repository, changelog_md_path):
    """Update CHANGELOG.md by moving unreleased content to new version"""
    if not filtered_content.strip():
        print("ERROR: No content to add to changelog. Skipping update.")
        sys.exit(1)
    
    version = os.environ.get('VERSION')
    
    if not version:
        print("ERROR: VERSION environment variable not set")
        sys.exit(1)
    
    today = datetime.now().strftime('%Y-%m-%d')

    content = read_file(changelog_md_path)
    lines = content.split('\n')
    
    # find unreleased section
    unreleased_start, unreleased_end = find_unreleased_section(lines)
    
    # create new version section
    new_version_section = [f"## [v{version}] - {today}", ""]
    new_version_section.extend(filtered_content.split('\n'))
    new_version_section.append("")
    
    # replace unreleased section with template + insert new version
    replacement = UNRELEASED_TEMPLATE + new_version_section
    lines[unreleased_start:unreleased_end] = replacement

    # add reference link at the end
    repo_url = f"https://github.com/{github_repository}/releases/tag"
    reference_link = f"\n[v{version}]: {repo_url}/{version}"

    # append to the very end of the file
    updated_content = '\n'.join(lines) + reference_link

    write_file(changelog_md_path, updated_content)

def update_changelog_xml(filtered_content, changelog_xml_path):
    """Update changelog.xml for Android app"""
    if not filtered_content.strip():
        print("ERROR: No content to add to changelog.xml. Skipping update.")
        sys.exit(1)
    
    version = os.environ.get('VERSION')
    version_code = os.environ.get('VERSION_CODE')
    
    if not version or not version_code:
        print("ERROR: VERSION or VERSION_CODE environment variables not set")
        sys.exit(1)
    
    today = datetime.now().strftime('%Y-%m-%d')
    
    # get notes categorized by type
    release_notes = []
    current_type = None
    
    for line in filtered_content.split('\n'):
        if line in CHANGELOG_XML_TYPE_MAP:
            current_type = CHANGELOG_XML_TYPE_MAP[line]
        elif line.startswith('- ') and current_type:
            clean_note = re.sub(r'^\s*-\s*|\s*\([^)]*\)\s*$', '', line)
            release_notes.append(f"        <{current_type}>{clean_note}</{current_type}>")
    
    xml_content = read_file(changelog_xml_path)
    lines = xml_content.split('\n')
    
    for index, line in enumerate(lines):
        if '<changelog>' in line:
            tab = '    ' # 4 spaces
            newline = '\n'

            content = f'''{newline}{newline.join(release_notes)}'''

            opening_tag = f'''{newline}{tab}<release version="{version}" versionCode="{version_code}" date="{today}">'''
            closing_tag = f'''{newline}{tab}</release>'''

            release_block = f'''{opening_tag}{content}{closing_tag}'''
            lines.insert(index + 1, release_block)

            write_file(changelog_xml_path, '\n'.join(lines))
            print("Updated changelog.xml")
            return
    
    print("ERROR: Could not find <changelog> tag in changelog.xml")


def format_changelog_for_github(filtered_content):
    """Format changelog content for GitHub release body"""
    if not filtered_content.strip():
        return "No release notes"
    
    # convert to GitHub-friendly format
    print("Replacing '- ' with '✔ ' for GitHub formatting")
    formatted = filtered_content.replace('- ', '✔ ')
    # escape newlines for GitHub Actions
    print("Replacing newlines with '%0A' for GitHub Actions output")
    formatted = formatted.replace('\n', '%0A')
    
    return formatted


def main():
    if len(sys.argv) != 4:
        print("Arguments required: <repo> <changelog_md_path> <changelog_xml_path>")
        sys.exit(1)

    github_repository = sys.argv[1]
    changelog_md_path = sys.argv[2]
    changelog_xml_path = sys.argv[3] 

    print("Processing CHANGELOG.md...")
    
    unreleased_content = extract_unreleased_content(changelog_md_path)

    filtered_content = filter_sections_with_content(unreleased_content)

    print(f"Filtered changelog content: {filtered_content}")

    update_changelog_md(filtered_content, github_repository, changelog_md_path)
    print("Updated CHANGELOG.md")
    
    update_changelog_xml(filtered_content, changelog_xml_path)
    print("Updated changelog.xml")
    
    # save outputs for next steps in GitHub Actions
    github_formatted = format_changelog_for_github(filtered_content)
    set_multi_line_github_output("changelog_additions", github_formatted)
    set_multi_line_github_output("filtered_content", filtered_content)
    
    print("Changelog processing completed successfully")


if __name__ == "__main__":
    main()