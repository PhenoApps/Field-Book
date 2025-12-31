#!/usr/bin/env python3
"""
This script is used with process-pr-merge.yml. The workflow is triggered on pushes to main branch, 
checks if the commit came from a merged PR, and extracts the PR number. 

Sometimes commits to main are direct (by bots, crowdin updates, automated changelog updates, etc) 
and not from PRs; in such cases, the workflow exits gracefully.

If a PR is found, it calls process-pr-merge.py script to process the PR and update the changelog.

After that, the Python script executes in the following steps:
1. get_pr_body: fetch PR body content using GitHub CLI API
2. extract_release_note: 
    extract release note from release-note code block in PR description
    if no release note found → exit gracefully (expected for Crowdin updates, docs changes, workflow changes, etc)
3. extract_change_type: 
    extract change type from the list of checkboxes in PR description
    CHANGE_TYPE_MAPPING maps change types to changelog sections
    if change type is "OTHER" OR no change type found → exit gracefully (no changelog update needed)
4. update_changelog: 
    find the appropriate section in CHANGELOG.md under [Unreleased]
    insert the new release note with its PR link in brackets
5. set_github_output: Set success=true output for conditional commit step in YAML workflow

If success=true, YAML workflow commits the updated CHANGELOG.md on the same branch that triggered the workflow (main).

Graceful exits only for: Missing release notes, no change type, or "OTHER" type (normal for some PRs)
"""
import sys
import re
from utils import run_command, read_file, write_file, set_github_output

CHANGE_TYPE_MAPPING = {
    "ADDITION": "### Added",
    "CHANGE": "### Changed", 
    "FIX": "### Fixed",
    "OTHER": None # this won't be added to changelog
}

def get_pr_body(repo, pr_number):
    """Fetch PR body using GitHub CLI"""
    cmd = f'gh api -X GET "repos/{repo}/pulls/{pr_number}" --jq ".body"'
    return run_command(cmd).replace('\r', '')

def extract_release_note(pr_body):
    """Extract release note from PR body"""
    lines = pr_body.split('\n')
    is_line_inside_release_note = False
    release_notes = []
    
    for line in lines:
        if '```release-note' in line:
            is_line_inside_release_note = True
            continue
        elif line.strip() == '```' and is_line_inside_release_note:
            break
        elif is_line_inside_release_note and line.strip():
            release_notes.append(line)
    
    release_note = '\n'.join(release_notes).strip()

    if not release_note:
        print("No release note found. Skipping changelog update.")
        sys.exit(0)
    
    return release_note

def extract_change_type(pr_body):
    """Extract change type from PR body"""
    pattern = r'- \[[xX]\] \*\*`([A-Z]+)`\*\*'
    match = re.search(pattern, pr_body)
    
    if not match:
        print("No change type found.")
        sys.exit(0)
    
    change_type = match.group(1)
    
    section = CHANGE_TYPE_MAPPING.get(change_type)

    if section is None:
        print(f"Change type '{change_type}' does not require changelog update. Skipping.")
        sys.exit(0)

    return section

def update_changelog(section, release_note, pr_number, repo):
    """Update the changelog file"""
    pr_url = f"https://github.com/{repo}/pull/{pr_number}"
    full_release_note = f"- {release_note} ({pr_url})"
    
    print(f"Updating Unreleased changelog under section: {section}")
    
    content = read_file('CHANGELOG.md')
    lines = content.split('\n')
    
    # find the first occurrence of the section (in unreleased)
    for index, line in enumerate(lines):
        if line.strip() == section:
            # insert the release note after the section header
            lines.insert(index + 1, full_release_note)
            break
    else:
        print(f"Section '{section}' not found in changelog")
        return
    
    # write back the modified content
    updated_content = '\n'.join(lines)
    
    write_file('CHANGELOG.md', updated_content)
    
    print("Changelog updated successfully")

def main():
    if len(sys.argv) != 3:
        print("Arguments required: <repo> <pr_number>")
        sys.exit(1)
    
    repo = sys.argv[1]
    pr_number = sys.argv[2]
    
    print(f"Processing PR #{pr_number} for repository {repo}")
    
    # fetch PR body
    pr_body = get_pr_body(repo, pr_number)
    
    # extract details
    release_note = extract_release_note(pr_body)
    section = extract_change_type(pr_body)
    
    print(f"Release Note: {release_note}")
    print(f"Section: {section}")
    
    # update changelog
    update_changelog(section, release_note, pr_number, repo)
    
    # set success = true to check in next steps
    set_github_output("success", "true")

if __name__ == "__main__":
    main()