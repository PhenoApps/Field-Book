### Description

<!--
Provide a summary of your changes including motivation and context.  
If these changes fix a bug or resolve a feature request, be sure to link to that issue.
-->



### Change Type

<!--
Select the most applicable option by placing an `x` in the box.
If you select `OTHER`, there is no need to fill in the **Release Note** section. For all other types, a release note is required.
-->

- [ ] **`ADDITION`** (non-breaking change that adds functionality)
- [ ] **`CHANGE`** (fix or feature that alters existing functionality)
- [ ] **`FIX`** (non-breaking change that resolves an issue)
- [ ] **`OTHER`** (use only for changes like tooling, build system, CI, docs, etc.)

### Release Note

<!--
Provide a brief, user-friendly release note in the fenced block below. This will be included in the changelog file during the release process.  
Release notes are **required** for all change types except `OTHER`.

Examples of release notes:
- Fixed a bug causing Field Book to crash when collecting categorical data.
- Added a new option to enable a sound when data is deleted.
- Modified the drag-and-drop behavior for traits.
-->

```release-note

```
___

### Release Type

_For internal use - please leave these unchecked unless you are the one completing the merge._

<!--
- On merge:
  - If `MAJOR` or `MINOR` is checked, a release action will be dispatched to create a release of the selected type.
  - If `WAIT` is checked, the release action will be skipped. The merged changes will later be included in the next dispatched or scheduled release (A scheduled check for unreleased changes runs every Monday at 3pm EST).
-->

- [ ] **`MAJOR`**
- [ ] **`MINOR`**
- [ ] **`WAIT`** (No immediate release, but the changelog will be still be updated if there is a release note.)