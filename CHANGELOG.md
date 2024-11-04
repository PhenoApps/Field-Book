# Changelog

All notable changes to Field Book app will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Fixed

## [v5.6] - 2024-10-02

### Added
- Trait Builder updated with new workflow approach
- GeoNav logging can support both limited and full logging
- Observation metadata can be viewed by long pressing the current value
- Microsoft OIDC support
- Individual fields pages (alpha)
- Automatic detection of Boox devices

### Changed
- Updated to API 34
- eInk devices theme improvements
- BrAPI Sync improvements
- Photo trait updated for devices without system camera app
- DataGrid and BrAPI sorting improvements

### Fixed
- Numerous bug fixes and enhancements
- XLSX import updated to include all cells even if empty

## [v5.5] - 2023-11-30

### Added
- GoPro trait
- Setting to return to first trait when navigating entries
- Progress bars added to Collect to indicate current entry and trait

### Changed
- In-app update mechanism improved
- Search options saved for subsequent searches
- String and settings updates
- Merged move to unique ID settings
- GeoNav improvements
- Require person on by default
- Coordinate order swapped to match ISO

### Fixed
- Bug fixes and performance improvements

## [v5.4] - 2023-07-20

### Added
- Repeated measures (beta)
- Themes option to adjust app appearance
- Summary view can be customized
- BrAPI studies can be imported at multiple observation levels
- Internal update mechanism
- InfoBar prefix selector updated

### Changed
- Field sorting improvements
- Trait creation workflow modifications
- BrAPI export, sync, and authorization adjustments
- GNSS/GeoNav improvements

### Fixed
- Numerous bug fixes and enhancements

## [v5.3] - 2022-09-07

### Added
- USB camera trait
- Text-to-speech setting for data input
- Anonymous identifier added to profile for debugging
- Locking now has three states: all data, new data, no data
- Scanning barcodes to navigate searches across all fields for matches

### Changed
- Location collection adjustments when collecting on a per phenotype basis
- GeoNav notification layout improved
- Language selection options modified
- Categorical/multicat trait creator modified
- Location and GNSS trait collection standardized

## [v5.2] - 2022-05-20

### Added
- Support for XLSX files
- New sound for missing barcodes when scanning to navigate
- Option to export ZIP bundle containing both data and media
- Database export exports to single ZIP file
- Added warnings for failed file imports
- Option to average points over time in GNSS trait

### Changed
- DataGrid now focuses on current entry/trait
- Resuming collection now navigates to the last selected trait
- InfoBars adjust based on prefix size
- Improved import file sanitation
- Expanded options available for next entry with no data setting
- Quick GoTo can now use both primary/secondary ID

### Fixed
- Numerous bug fixes

## [v5.1] - 2021-11-01

### Added
- Field Book GeoNav to identify and navigate to nearest plot
- Settings to adjust polling rate, angle, and GNSS device
- Identify and load nearest field via location icon in fields list

### Changed
- Internal GPS can be used with GNSS trait
- Increased required minimum Android version to 5.1

### Fixed
- Move to unique identifier setting bug

## [v5.0] - 2021-09-27

### Added
- BrAPI v2 support with page size and server timeout settings
- GNSS trait
- Setting to swap plot/trait arrows
- DataGrid v2
- Custom field designer
- Person confirmation every 24hrs
- Import trait files via cloud
- Calendar picker added to date trait

### Changed
- Table format export no longer has a maximum number of traits
- Major database overhaul
- Boolean trait layout updates
- Label Print trait improvements
- Printing a label adds an observation record

### Fixed
- Fixed issue with special characters in file headers
- Fixed issue with categorical trait layout
- File management and import fixes
- BrAPI pagination fixes

## [v4.5] - 2020-09-28

### Added
- Setting to choose custom storage directory (Android 5.0+ only)
- Setting for sound when navigating entries
- Setting for sound when cycling traits
- Theme setting to change saved data color
- Community BrAPI servers and scan by barcode option

### Changed
- Categorical trait layout now has unlimited number of categories
- Export options default to last used configuration
- Profile moved completely to Settings
- Several settings reorganized to different categories
- Disable entry arrows setting adjustments

### Fixed
- Database settings bug
- Profile not saving person or location bug
- Files with uppercase extensions now visible in file manager
- Collect screen no longer accessible without traits

## [v4.4] - 2020-09-02

### Added
- Data can be collected via bottom toolbar in Collect using barcodes
- BrAPI studies can be filtered by program and trial

### Changed
- BrAPI HTTP error handling improvements

### Fixed
- Zebra Label Print trait bug
- Search bug that resulted in data not being saved
- UTF-8-BOM import file bug

## [v4.3] - 2020-06-23

### Added
- Added search widget to settings
- Added translation link

### Changed
- Substantial refactoring
- Quick GoTo uses full keyboard

### Fixed
- Tutorial order corrected
- BMS BrAPI bug
- Database import bug
- Multicat layout bug
- Sample file bug
- Quick GoTo/MoveToSearch bug


## [v4.2] - 2020-03-10

### Added
- New tutorial
- Additional import sources
- Default import/export source option
- Icons on Collect can be customized
- Added list of libraries used in app

### Changed
- Multicat layout improvements
- Changelog updated
- About dialog updated
- Upgraded to Java 8
- Chinese and Italian translations updated

### Fixed
- Return key setting bug

## [v4.1] - 2020-02-21

### Added
- BrAPI integration
- Label printer trait
- Firebase Crashlytics

### Changed
- Workflow improvements
- Permission management updated
- Settings improvements

### Fixed
- Numerous bug fixes

## [v4.0] - 2017-10-19

### Added
- New field management
- New Collect screen layout

### Fixed
- Numerous bug fixes

## [v3.3.1] - 2017-03-07

### Fixed
- Bug fixes and performance enhancements

## [v3.3.0] - 2017-02-19

### Added
- Location trait
- Missing values button
- Citation dialog
- Dropbox import
- Dialog when importing trait lists

### Changed
- Photo names now include rep and trait name
- Replaced lots of words with icons
- Trait buttons updated
- 'Rust rating' trait changed to 'Disease rating'
- Disease rating buttons changed from R/MS/MR/S to R/M/S
- Numeric clear changed to backspace

### Fixed
- Spaces in column names bug
- Default trait value saving bug
- Trait visibility not saving bug
- Search bug

## [v3.2.0] - 2016-04-04

### Added
- Bengali translation

### Changed
- Dialog design updated
- Button design updated
- Internal database updated

### Fixed
- Several bugs causing crashes
- Numeric trait bug
- Reordering traits bug

## [v3.1.1] - 2015-12-12

### Fixed
- Multicat trait bug
- Trait creation bug


## [v3.1.0] - 2015-12-08

### Added
- Multicategorical trait format
- Export dialog includes option to overwrite previously exported files
- Field name included in Collect navigation drawer
- Database automatically backed up to internal storage

### Changed
- Location exports as null if not set
- Dialogs updated with titles

### Fixed
- Audio and rust traits bug



## [v3.0.0] - 2015-09-14

### Added
- Updated user interface
- Added support for phones
- Traits can be reordered with drag and drop
- Added `no date` button
- Updated Field Book manual

### Changed
- Setup renamed to Profile

### Fixed
- Text trait cursor bug

## [v2.4.1] - 2015-06-05

### Added
- DataGrid (beta) 
- Setting to open camera to scan barcodes for navigation
- Setting to disable left, right, or both entry arrows if no data has been collected
- Field Book checks for updates

### Changed
- Export layout reorganized
- Entry scrolling speed acceleration
- Spaces in imported column names replaced with underscores
- Default name for exported files modified
- Map removed
- Error logs saved to internal storage
- Updated strings for clarity

### Fixed
- Numerous bug fixes


## [v2.3.5] - 2015-05-28

### Added
- Rust Rating trait with customizable severity scale
- Counter trait
- Setting to disable the automatic file sharing
- Setting to disable plot navigation if no data has been collected

### Changed
- Automatic navigation to last active entry
- Updated tutorial
- Modified default timestamp format

## [v2.3.4] - 2015-04-22

### Added
- InfoBar text can be long pressed to see entire value

### Changed
- Quick GoTo disabled by default
- Adjusted timing for press and hold entry scrolling
- Only first photo name is included in table export
- Summary dialog updated
- Location code improvements
- Sample files updated

### Fixed
- Fixed issue with MediaContentScanner
- Search layout improvements

## [v2.3] - 2015-03-01

### Added
- New field import layout
- New photo trait
- Internal database can be imported/exported
- Visibility of all traits can be toggled

### Changed
- Audio trait improved
- InfoBar prefixes can be selected from Collect

### Fixed
- Table export speed improved

## [v2.2] - 2014-09-11

### Added
- New option to make sound when primary order changes
- New option to go to navigate directly to specific unique ID
- New option to go to next plot without data
- New file chooser
- Support for XLS readded

### Changed
- Traits visibility now changed in the Traits page
- Clear categorical traits by pressing the current choice
- Long press toolbar icons to get labels
- Changelog can be accessed from the About dialog

### Fixed
- Miscellaneous interface improvements
- Traits can now be made in other languages
- Field Book doesn't move to first entry when screen is turned off

## [v2.1.2] - 2014-09-03

### Changed
- Renamed "Qualitative" trait format "Categorical"
- Removed support for Android 3.0
- Removed ActionBarSherlock library

### Fixed
- Fixed issue with field folders
- Fixed issue with field file selection


## [v2.1.0] - 2014-08-19

### Added
- New changelog
- Exported files are shared
- Fields dialog subdirectory support
- Translations for Amharic, Arabic, Brazilian Portuguese, Chinese, French, Hindi, Japanese, Oromo, and Russian

### Changed
- Tutorial setup improved
- Language dialog
- Items disabled if not available
- Replaced toolbar text with icons
- Removed XLS support

### Fixed
- External hardware no longer moves to first entry
- Numeric minimum correctly enforced
- Text trait can now be completely deleted via keyboard
- Text trait cursor appears at end of current value
- Files visible on computer after export

## [v2.0.1] - 2014-05-19

### Added
- Initial GitHub release


[v2.0.1]: https://github.com/PhenoApps/Field-Book/releases/tag/v2.0.1
[v2.1.0]: https://github.com/PhenoApps/Field-Book/releases/tag/v2.1.0
[v2.1.2]: https://github.com/PhenoApps/Field-Book/releases/tag/v2.1.2
[v2.2]: https://github.com/PhenoApps/Field-Book/releases/tag/v2.2
[v2.3.4]: https://github.com/PhenoApps/Field-Book/releases/tag/v2.3.4
[v2.3.5]: https://github.com/PhenoApps/Field-Book/releases/tag/v2.3.5
[v2.3]: https://github.com/PhenoApps/Field-Book/releases/tag/v2.3
[v2.4.1]: https://github.com/PhenoApps/Field-Book/releases/tag/v2.4.1
[v3.0.0]: https://github.com/PhenoApps/Field-Book/releases/tag/v3.0.0
[v3.1.0]: https://github.com/PhenoApps/Field-Book/releases/tag/v3.1.0
[v3.1.1]: https://github.com/PhenoApps/Field-Book/releases/tag/v3.1.1
[v3.2.0]: https://github.com/PhenoApps/Field-Book/releases/tag/v3.2.0
[v3.3.0]: https://github.com/PhenoApps/Field-Book/releases/tag/v3.3.0
[v3.3.1]: https://github.com/PhenoApps/Field-Book/releases/tag/v3.3.1
[v4.0]: https://github.com/PhenoApps/Field-Book/releases/tag/v4.0.3
[v4.1]: https://github.com/PhenoApps/Field-Book/releases/tag/v4.1.3
[v4.2]: https://github.com/PhenoApps/Field-Book/releases/tag/v4.2.1
[v4.3]: https://github.com/PhenoApps/Field-Book/releases/tag/v4.3.3
[v4.4]: https://github.com/PhenoApps/Field-Book/releases/tag/v4.4
[v4.5]: https://github.com/PhenoApps/Field-Book/releases/tag/v4.5
[v5.0]: https://github.com/PhenoApps/Field-Book/releases/tag/v5.0.7
[v5.1]: https://github.com/PhenoApps/Field-Book/releases/tag/v5.1.0
[v5.2]: https://github.com/PhenoApps/Field-Book/releases/tag/525
[v5.3]: https://github.com/PhenoApps/Field-Book/releases/tag/530
[v5.4]: https://github.com/PhenoApps/Field-Book/releases/tag/5.4.18.23
[v5.5]: https://github.com/PhenoApps/Field-Book/releases/tag/5.5.26
[v5.6]: https://github.com/PhenoApps/Field-Book/releases/tag/5.6.25