# Tree Architecture — closed design decisions (Step 0)

- **Derived trait:** read-only `TreeSummaryFormat`, **export-only** (`visible=false` —
  not in Collect carousel). `ExportUtil` still includes linked summaries when the
  source tree architecture trait is exported under Active traits.
- **TraitRefResolver:** resolve by `name`, then `alias`; return null on miss
- **Ontology for BrAPI:** `TraitObject.externalDbId` on observation variable (column
  `ontology_db_id` exists but is not wired through TraitObject / PhenotypingMapper yet).
  Tree BrAPI upload is **DONE** — mapper builds child units (root included) with
  plot→ancestor `observationLevelRelationships` + optional `studyDbId`;
  `TreeBrapiUploadSequence` POSTs child ObservationUnits then observations;
  per-node Observations when `externalDbId` present; local tree traits enter
  upload buckets. Residual: live BrAPI server proof; values without ontology id
  stay on unit `additionalInfo`. See `trait-tree/00-README.md` Step 10.
- **TraitRef rename (R-18):** on study-trait rename, `TreeSchemaLoader.repairTraitRefsAfterRename`
  rewrites matching refs in loadable/writable schemas (warn toast); alias fallback remains.
- **onPause flush:** included in feature branch (`CollectActivity.onPause` → `onExit`)
- **Flush persistence:** `TreeTraitLayout.flushPending` writes via
  `persistObservation(pending.studyId, pending.unitId, pending.traitId, …, pending.rep)` —
  never Collect's live `updateObservation` / current plot. Failed sidecar writes
  retain `failedFlushPending` for retry (cleared only when the successful flush
  matches the same unit/study/trait/rep identity).
- **Collect `block()`:** only `Issue.MissingRequired` on **TreeTraitLayout** blocks
  plot/trait navigation via `navigateIfDataIsValid` (tree toast + Overview).
  Date/Text `block()` stays for RepeatedValuesView only (main Collect behavior).
  Range/category warnings stay non-blocking (overview + field chrome).
- **File names (R-05):** `FileUtil.sanitizeFileName` replaces `/` (and other illegal
  chars) with `_`, matching `checkForIllegalCharacters`.
- **MTG encode:** multiple PRECEDES siblings under one parent are bracketed
  (`[<child][<child]`) so OpenAlea-style linear axis chaining is not implied.
- **Upstream issues:** no existing MTG/hierarchy trait format found in v7.2.3 codebase; `getExperimentalFormats()` empty hook used

## Trait library reuse (closed)

- **Never invent tree-owned format chrome** (no custom “Capture photo” buttons,
  date text stubs, or parallel keypads / `*_node_host` copies).
- Node fields inflate the **real** Field Book `trait_*` layouts via
  `TraitLayoutFactory` + a fresh `BaseTraitLayout` controller and
  `NodeTraitValueSession` (sidecar values — never the tree trait’s CollectInputView).
- Controllers bind with `init(Activity, View root)` / `findTraitView` (root-scoped),
  not activity-wide `findViewById`. Collect’s `inflateTrait` passes the inflated root.
- Photo still launches existing `CameraActivity` and uses `trait_tree_photo.xml` chrome
  (plain ViewGroup root; Collect’s CameraX path / `BasePhotoFormat.defaultLayoutId`
  remain `trait_camera`).
- Do **not** embed the Collect `LayoutCollections` singleton inside Compose.
- Node values come only from **TraitRefs to existing study traits**.
- Constructor’s searchable trait palette is the **trait finder**.
- Collect hosts study traits via those native layout XMLs (not a parallel Compose
  widget catalog).

## Export paths (closed)

- Prefer **relative portable paths** for node media in sidecar JSON and flatten CSV.
- Form: `\<sanitizedTraitName\>/\<filename\>` (matches zip trait media folder layout).
- `TreeCodec.encodeSidecar` portableizes trait values (not only Writer).
- Collect still stores SAF `content://` URI in `observations.value` for open-in-app;
  export adapter emits portable basename / relative form.

## Create entry (closed)

- Tree Architecture and Tree Summary appear **only** under **Experimental** in the New
  Trait format picker (`BASE_EXPERIMENTAL` → `getExperimentalFormats()`). They must
  **not** appear in `getMainFormats()` (top-level grid).
- **Preference:** Settings → Experimental → **Experimental Traits** must be enabled to
  show the Experimental category.
- Constructor opens from **Edit tree schema** / resource-file row on the Tree
  Architecture parameter screen — not inline in the format grid.
- Constructor is a **fullscreen** dialog (`TreeConstructorDialogFragment`), not a
  bottom sheet: content scrolls in-place; exit only via Close / Cancel / system Back
  (scrolling must not dismiss).
- Saving a Tree Architecture trait **auto-creates** a linked `tree summary` trait named
  `{name} (summary)` via `TreeDerivedTraitHelper`. **Tree Summary is not manually
  creatable** — use `getCreatableExperimentalFormats()` (Tree Architecture only) in
  `NewTraitDialog`; `getExperimentalFormats()` still includes Tree Summary for internal use.
- Summary is **export-only**: `visible=false` at create; `getVisibleTraits` also excludes
  format `tree summary` so Collect never shows it even if an older row was marked visible.

## Collect Overview (closed)

- Overview is a **ModalBottomSheet** inside the tree trait (`TreeOverviewSheet`), not a
  separate Collect carousel trait.
- **List | Graph** segmented toggle; default **Graph**. List is one tap away.
- **Graph:** Maven Central [GraphView](https://github.com/Team-Blox/GraphView) `0.8.1`
  (Buchheim–Walker) + [ZoomLayout](https://github.com/natario1/ZoomLayout) `1.9.0`
  around the `RecyclerView`, with library-advised separations (100) and
  `ORIENTATION_BOTTOM_TOP` (roots at bottom, children grow up — plant-intuitive
  default) / `TreeEdgeDecoration` / `useMaxSize = true`.
- Node shapes by schema class: **square** root, **circle** stem, **triangle** branch.
- Trait-completion fill: `TreeNodeCompletion` → `filled/total` sectors clockwise from
  12 o’clock; filled uses `PreferenceKeys.SAVED_DATA_COLOR` / `fb_value_saved_color`;
  non-blank values (including `NA`) count as filled.
- Tap a list row or graph node → jump + dismiss sheet. List keeps long-press timestamps.
- Footer summary line unchanged (`TreeSummary`: nodes / length|pod total / branches).

### New Trait parameter screens (verified spec)

| Format | Parameters shown |
|--------|------------------|
| **Tree Architecture** | Name, Additional info, Resource file (+ Edit tree schema), Repeated measures, Attachments |
| **Tree Summary** | Name, Additional info only (normally auto-created; not offered for manual create under Experimental) |

### Create press path (device/emulator)

1. Enable **Experimental Traits** in Settings.
2. **Traits** → **+** → **Create new trait**
3. Format grid: **Experimental** → **Tree Architecture** (not top-level)
4. **Next** → fill Name → tap **Edit tree schema** or resource-file row
5. Constructor fullscreen editor opens (must not crash from `TraitActivity`; scroll does not dismiss)
6. On Save trait: companion `{name} (summary)` tree summary trait is inserted

## Verification (closed)

- **JVM canonical scenario:** `TreeUserScenarioTest` (create schema data + collect + portable export)
- **JVM units:** codec MTG/round-trip, validator, mutations, sidecar photo I/O, zip export,
  `FormatsTreeRegistrationTest`, `NodeTraitChromeReuseTest` (Collect photo switch + CameraActivity extras)
- **Instrumented (manual emulator only, not CI):**
  `TreeTraitConstructorUxInstrumentedTest` (constructor schema);
  `TreeTraitCreateInstrumentedTest` (experimental picker + summary trait on save);
  `TreeTraitCollectInstrumentedTest` (**passes** — live CollectActivity root/stem/branch +
  photo via `onActivityResult(REQUEST_TREE_NODE_PHOTO)` + summary flush on `field1`/`sample1`)
- **Still OPEN-manual:** A→B flush/leak instrumented test; live CameraActivity shutter;
  physical-device chrome / TalkBack / timed technician pass
- **Docs-only screenshots:** `TreeIntegratedScreenshotTest` `00`–`08` excluded from
  `testDebugUnitTest`; refresh via `scripts/record-tree-ui-screenshots.sh` /
  `./gradlew recordTreeUiScreenshots`
- **BrAPI:** Step 10 **DONE** — child ObservationUnit POST + per-node Observations
  (when `externalDbId`) + local-tree upload routing + JVM orchestration tests;
  residual live-server proof / values without ontology id on unit `additionalInfo`;
  see `trait-tree/00-README.md`

Full living catalog: gitignored `trait-tree/00-README.md` (Round 2 table).
