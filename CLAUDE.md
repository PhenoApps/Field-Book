# CLAUDE.md

@.claude/skills/andrej-karpathy-skills/CLAUDE.md
@.claude/skills/andrej-karpathy-skills/EXAMPLES.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Field Book is an Android app for collecting phenotypic (observable trait) data in agricultural field research. It replaces paper field books. Developed by the PhenoApps organization, GPL v2 license.

- **Package:** `com.fieldbook.tracker`
- **Languages:** Kotlin (primary), some legacy Java
- **Min SDK:** 24 | **Target/Compile SDK:** 36
- **UI:** XML layouts + Jetpack Compose (incremental adoption)
- **DI:** Dagger Hilt
- **Database:** SQLite via `DataHelper.java` (NOT Room) with manual migrations in `Migrator.kt`

## Build Commands

```bash
./gradlew app:assembleDebug          # Debug APK
./gradlew app:assembleRelease        # Release APK (unsigned)
./gradlew app:test                   # Run unit tests (Robolectric + JUnit 4/5)
./gradlew app:connectedAndroidTest   # Run instrumented tests (needs device/emulator)
./gradlew app:lint                   # Run Android lint
```

- Uses Gradle 8.11.1, AGP 8.9.3, Kotlin 2.1.10/2.2.10
- CI expects Java 17 (Temurin)
- `version.properties` drives version name/code
- `local.properties` needed for `NIX_LICENSE` build config field

## Key Architecture Patterns

### Core Screens (Activities)

| Activity | File | Purpose |
|----------|------|---------|
| `ConfigActivity` | `activities/ConfigActivity.java` | Main launcher / home screen |
| `CollectActivity` | `activities/CollectActivity.java` | Core data collection (~156KB, most complex file) |
| `FieldEditorActivity` | `activities/FieldEditorActivity.kt` | Field CRUD and import |
| `TraitActivity` | `activities/TraitActivity.java` | Trait (phenotype) definition management |
| `DataGridActivity` | `activities/DataGridActivity.kt` | Tabular data view |

### Database Layer

- `DataHelper.java` — monolithic SQLite helper, NOT using Room
- `Migrator.kt` — manual schema migration logic
- DAO classes in `database/` follow a repository pattern: `GroupDao`, `ObservationDao`, `StudyDao`, `ObservationVariableDao`
- Data objects in `objects/`: `FieldObject`, `TraitObject`, `RangeObject`, etc.

### Trait System

Each trait data type has a layout class in `traits/`:
`NumericTraitLayout`, `CategoricalTraitLayout`, `TextTraitLayout`, `DateTraitLayout`, `PhotoTraitLayout`, `AudioTraitLayout`, `BarcodeTraitLayout`, `GNSSTraitLayout`, and ~15 more for sensors (Nix, InnoSpectra, GreenSeeker, GoPro, Canon, etc.)

### Compose Adoption

Newer UI is in `ui/` package: theme, screens, and navigation components. Existing Activities use data binding + view binding; Compose is being adopted incrementally.

### BrAPI Integration

BrAPI (Breeding API) client in `brapi/` package for exchanging data with breeding databases. Activities for BrAPI workflows in `activities/brapi/`. Uses `brapi-java-client:2.1.0`.

### External Device Integrations

`devices/` package handles camera, PTP/IP, spectrometers, and other sensors. Zebra SDK JARs in `app/libs/`.

## Translations

Uses Crowdin for localization. Config in `crowdin.yml`. Lint suppresses `MissingTranslation`.

## Release Cycle

- Play Store updates frozen Apr 15 – Sep 15 (field season)
- Version frozen between those dates; releases via GitHub Actions using `github-release.yml`
