# Changelog

All notable changes to Field Book app will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Fixed
- Bug fixes and enhancements (https://github.com/PhenoApps/Field-Book/pull/1418)

## [v7.1.0] - 2026-02-18

### Added
- Bottom toolbar in Collect can now be used for barcode navigation (https://github.com/PhenoApps/Field-Book/pull/1390)
- Photos, videos, and audio files can now be attached to individual observations (https://github.com/PhenoApps/Field-Book/pull/1390)
- Timestamp can now be optionally displayed under phenotypes in Collect (https://github.com/PhenoApps/Field-Book/pull/1409)
- New video trait format (https://github.com/PhenoApps/Field-Book/pull/1390)

### Changed
- Nearest field finder now searches across all location data (https://github.com/PhenoApps/Field-Book/pull/1399)

### Fixed
- Camera previews no longer reload when navigating between entries (https://github.com/PhenoApps/Field-Book/pull/1405)
- GeoNav popup no longer causes a crash when navigating (https://github.com/PhenoApps/Field-Book/pull/1415)
- Correct value is now displayed for saved dates (https://github.com/PhenoApps/Field-Book/pull/1404)

## [v7.0.2] - 2026-02-10

### Fixed
- Internal GeoJSON data are now saved as doubles (https://github.com/PhenoApps/Field-Book/pull/1394)
- BrAPI improvements and optimizations (https://github.com/PhenoApps/Field-Book/pull/1396)

## [v7.0.1] - 2026-02-01

### Fixed
- BrAPI imports no longer always include "TEST" entry types (https://github.com/PhenoApps/Field-Book/pull/1387)
- Improvements for Canon integration (https://github.com/PhenoApps/Field-Book/pull/1389)

## [v7.0.0] - 2026-01-01

### Added
- Traits list now includes individual trait pages with options that are adjustable on a per trait basis
- Traits include an option to enable repeated measures
- Traits include an option to automatically switch to the next entry when data is input
- Traits include an option to choose a specific resource image that will be displayed in Collect
- Numeric traits include an option to ask if outlier values should be allowed
- Numeric traits include an option to limit decimal places for values
- Numeric traits include an option to disable the math symbol buttons
- Date traits include an option to convert between date and day of year
- Categorical traits include an option to collect multiple categories
- Categorical traits imported via BrAPI include an option to display either the label or value
- Traits can now be renamed with previous names appearing in a list
- BrAPI sync and export are now merged into a single screen
- BrAPI server compatibility can now be viewed in the BrAPI settings

### Changed
- Multicat trait format removed (replaced with categorical option)
- Removed the following settings (moved to traits): day of year, BrAPI label/value, enable repeated measures
- Copy, edit, and deleting traits is now within each individual trait
- Images can now be skipped during BrAPI export

### Fixed
- Date trait calendar now uses the correct day of year setting

## [v6.3.6] - 2025-12-02

### Added

### Changed

### Fixed
- BrAPI authorization redirect link updated

## [v6.3.5] - 2025-12-02

### Added

### Changed

### Fixed
- BrAPI now redirects and opens Field Book when authorizing

## [v6.3.4] - 2025-11-17

### Added

### Changed
- New BrAPI import now only displays active trials and studies

### Fixed
- GoPro no longer disconnects after image capture
- BMS studies now correctly import via BrAPI (observation level and additional info fixes)
- Numerous bug fixes and enhancements

## [v6.3.3] - 2025-11-03

### Added

### Changed

### Fixed
- GoPro preview is no longer blank on certain devices

## [v6.3.2] - 2025-10-20

### Added

### Changed

### Fixed
- GoPro Hero 13 no longer causes a crash
- Reduced installer size by using a different ffmpeg library

## [v6.3.1] - 2025-10-13

### Added

### Changed
- Field Book now supports 16 KB page sizes

### Fixed
- Bug fixes and enhancements

## [v6.3.0] - 2025-10-08

### Added
- Fields can now be grouped and archived
- New trait formats: Stopwatch, Nix, Greenseeker, Scales
- Trait name wrapping in Collect can now be toggled with a long-press
- GoPro cameras can now optionally send just the image name instead of transferring the entire image

### Changed
- Field Book now targets Android SDK 36
- Field Creator updated with new workflow and UI
- Trait arrows are now hidden if only one trait is active
- Boolean traits now have an unset state

### Fixed
- Improved workflow for logging out of BrAPI
- Settings are no longer reset on restart
- New traits from BrAPI now appear at the end of the trait list
- Long pressing image and spectral thumbnails now displays metadata dialog
- Importing traits from BrAPI without trait details no longer leads to a crash
- Repeated measures no longer ignore trait limits
- Keyboard backspace no longer moves cursor to the end of the current value in text traits
- Field name is no longer an option in the Collect Search dialog
- Exif user comment tag no longer populated by incorrect data
- Return character setting is now interpreted correctly
- Observation Variable Name is now searchable in the BrAPI trait importer
- Image file name sanitation now removes or replaces more characters

## [v6.2.5] - 2025-06-23

### Added

### Changed

### Fixed
- BrAPI Sync improved to avoid observation miscounts and other failures
- 0x00 character now removed from pasted text values

## [v6.2.4] - 2025-06-09

### Added

### Changed

### Fixed

## [v6.2.3] - 2025-06-02

### Added
- New option to disable progress bars in Collect

### Changed

### Fixed
- BrAPI min/max values are now imported correctly
- MLKit library no longer causes crashes on certain devices

## [v6.2.2] - 2025-04-16

### Added

### Changed

### Fixed
- Trait indicator icons in Collect now resize as expected
- Device dark mode no longer affects Field Book UI
- Traits with quotes now export correctly in table format

## [v6.2.1] - 2025-04-09

### Added

### Changed

### Fixed
- Date traits using day of year now correctly saves
- Fixed an issue with Settings search

## [v6.2.0] - 2025-04-03

### Added
- Data grid updated with new interface
- Device name can now be customized in Profile Settings
- User names are now saved in a list for rapid switching

### Changed

### Fixed
- Improvements for the GoPro Hero 9

## [v6.1.2] - 2025-03-31

### Added
- Additional attributes are now included when importing fields using BrAPI

### Changed
- Improvements to the trait import and creation process
- Angle trait animation has been reversed

### Fixed
- Nearest field snackbar no longer overlaps add field button
- BrAPI Import background color is now white
- Bundled media is now exported as expected for active and existing traits
- Exporting with 'Only unique identifier' no longer shifts data

## [v6.1.1] - 2025-03-24

### Added

### Changed

### Fixed
- Field imports from BrAPI now include the correct metadata

## [v6.1.0] - 2025-03-17

### Added
- Primary/Secondar Order are no longer required when importing files (https://github.com/PhenoApps/Field-Book/pull/1169)
- Labels between entry arrows can be swapped in Collect via new dialog (https://github.com/PhenoApps/Field-Book/pull/1169)
- New or edited data values are now italicized (https://github.com/PhenoApps/Field-Book/pull/1169)
- Settings can be shared between devices using Nearby Share (https://github.com/PhenoApps/Field-Book/pull/1162)
- New Angle trait that uses device accelerometer (https://github.com/PhenoApps/Field-Book/pull/1140)
- Barcode attribute can now be selected on a per field basis (https://github.com/PhenoApps/Field-Book/pull/1165)
- Incoming photos can now be cropped (enable when creating new photo traits) (https://github.com/PhenoApps/Field-Book/pull/1169)

### Changed
- Date trait layout modified to match other formats (https://github.com/PhenoApps/Field-Book/pull/1169)
- Audio trait layout modified to display more information (https://github.com/PhenoApps/Field-Book/pull/1169)
- Quick GoTo setting removed and functionality moved to separate dialog that's enabled by default (https://github.com/PhenoApps/Field-Book/pull/1169)

### Fixed
- GNSS trait no longer resets connection when moving between entries (https://github.com/PhenoApps/Field-Book/pull/1179)
- Manual trait order no longer reset when leaving screen (https://github.com/PhenoApps/Field-Book/pull/1169)

## [v6.0.8] - 2025-02-24

### Fixed
- Preferences are now correctly imported from database zip files
- BrAPI authorization fix for specific devices (https://github.com/PhenoApps/Field-Book/pull/1163)

## [v6.0.7] - 2025-02-20

### Added
- Access full add field menu via long-press (https://github.com/PhenoApps/Field-Book/pull/1149)
- geo_coordinates are now pulled in via BrAPI (https://github.com/PhenoApps/Field-Book/pull/1149)

### Changed

### Fixed
- Replaced ffmpeg library with local version (https://github.com/PhenoApps/Field-Book/pull/1166)
- Numerous bug fixes and enhancements (https://github.com/PhenoApps/Field-Book/pull/1149)

## [v6.0.6] - 2025-01-24

### Fixed
- Numerous bug fixes and enhancements 

## [v6.0.5] - 2025-01-22

### Added
- Search box added to field list (https://github.com/PhenoApps/Field-Book/pull/1125)

### Changed
- BrAPI export no longer crashes when authentication token is expired (https://github.com/PhenoApps/Field-Book/pull/1134)

### Fixed
- Numerous bug fixes and enhancements (https://github.com/PhenoApps/Field-Book/pull/1135)

## [v6.0.3] - 2025-01-06

### Changed
- Swap navigation now changes position of entry/trait navigation instead of only behavior (https://github.com/PhenoApps/Field-Book/pull/1123)

### Fixed
- Improved warnings for missing or corrupted storage directory (https://github.com/PhenoApps/Field-Book/pull/1122)
- Barcodes scanned for data entry are now correctly checked for valid values (https://github.com/PhenoApps/Field-Book/pull/1110)
- Percent traits imported via BrAPI without min and max values default to 0/100 (https://github.com/PhenoApps/Field-Book/pull/1127)

## [v6.0.2] - 2024-12-16

### Added
- Trial name included in BrAPI import and field details page (https://github.com/PhenoApps/Field-Book/pull/1121)
- Image EXIF tag metadata includes the device pitch, roll, and yaw at the time of capture (https://github.com/PhenoApps/Field-Book/pull/1115)
- Setting added to reset preferences to default (https://github.com/PhenoApps/Field-Book/pull/1118)

### Changed
- Fields with different BrAPI study IDs can now have the same name (https://github.com/PhenoApps/Field-Book/pull/1095)

### Fixed
- Min and max values now correctly saved for BrAPI traits (https://github.com/PhenoApps/Field-Book/pull/1103)
- Label print observations are now assigned to the correct entry when navigating before the print is complete (https://github.com/PhenoApps/Field-Book/pull/1109)
- Improved database export messaging (https://github.com/PhenoApps/Field-Book/pull/1112)
- Go to ID dialog now has cursor focus when opened (https://github.com/PhenoApps/Field-Book/pull/1108)

## [v6.0.1] - 2024-12-09

### Added
- Accession number now included for germplasm imported via BrAPI (https://github.com/PhenoApps/Field-Book/pull/1093)
- Option added at trait creation to keep keyboard closed when navigating to text traits (https://github.com/PhenoApps/Field-Book/pull/1102)
- Proximity check added to disable GeoNav when away from field (https://github.com/PhenoApps/Field-Book/pull/1098)
- Experimental setting to use media keys for entry/trait navigation and picture capture (https://github.com/PhenoApps/Field-Book/pull/1089)

### Changed
- Fields and traits that have already been imported are now hidden on the BrAPI import screen (https://github.com/PhenoApps/Field-Book/pull/1091)
- Long-pressing delete button on bottom toolbar will remove all observations for that trait (https://github.com/PhenoApps/Field-Book/pull/1099)
- Current time now used as lastSyncedTime when downloading Observations via BrAPI (https://github.com/PhenoApps/Field-Book/pull/1071)
- lastSyncedTime updated after creating/updating Observations (https://github.com/PhenoApps/Field-Book/pull/1071)
- Improved support for BrAPI servers not using https (https://github.com/PhenoApps/Field-Book/pull/1071)

### Fixed
- "observation_time_stamp" and "last_synced_time" now correctly saved in DAO (https://github.com/PhenoApps/Field-Book/pull/1071)
- Synced and updated records now correctly identified (https://github.com/PhenoApps/Field-Book/pull/1071)

## [v6.0.0] - 2024-12-02

### Added
- Fields list updated to include bulk actions and individual field pages
- Data statistics added to each field
- App-wide statistics added to main screen
- Trait progress bar in Collect now shows individual traits
- Camera expanded with embedded preview

### Changed
- Documentation updated
- App intro and setup process
- Preferences reorganized and better described
- InfoBars updated to support wrapping and prefix replacement

### Fixed
- Numerous bug fixes and enhancements
- GoPro, USB, and Canon camera improvements

## [v5.6.26] - 2024-11-06

### Added

### Changed
- Updated Field Book documentation (https://github.com/PhenoApps/Field-Book/pull/1072)

### Fixed
- Fix issue with importing traits (https://github.com/PhenoApps/Field-Book/pull/1074)

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
[v5.6.26]: https://github.com/PhenoApps/Field-Book/releases/tag/5.6.26

[v6.0.0]: https://github.com/PhenoApps/Field-Book/releases/tag/6.0.0

[v6.0.1]: https://github.com/PhenoApps/Field-Book/releases/tag/6.0.1
[v6.0.2]: https://github.com/PhenoApps/Field-Book/releases/tag/6.0.2
[v6.0.3]: https://github.com/PhenoApps/Field-Book/releases/tag/6.0.3
[v6.0.4]: https://github.com/PhenoApps/Field-Book/releases/tag/6.0.4

[v6.0.3]: https://github.com/PhenoApps/Field-Book/releases/tag/6.0.3
[v6.0.4]: https://github.com/PhenoApps/Field-Book/releases/tag/6.0.4

[v6.0.5]: https://github.com/PhenoApps/Field-Book/releases/tag/6.0.5
[v6.0.6]: https://github.com/PhenoApps/Field-Book/releases/tag/6.0.6

[v6.0.7]: https://github.com/PhenoApps/Field-Book/releases/tag/6.0.7

[v6.0.8]: https://github.com/PhenoApps/Field-Book/releases/tag/6.0.8

[v6.1.0]: https://github.com/PhenoApps/Field-Book/releases/tag/6.1.0
[v6.1.1]: https://github.com/PhenoApps/Field-Book/releases/tag/6.1.1
[v6.1.2]: https://github.com/PhenoApps/Field-Book/releases/tag/6.1.2


[v6.2.0]: https://github.com/PhenoApps/Field-Book/releases/tag/6.2.0
[v6.2.1]: https://github.com/PhenoApps/Field-Book/releases/tag/6.2.1
[v6.2.2]: https://github.com/PhenoApps/Field-Book/releases/tag/6.2.2
[v6.2.3]: https://github.com/PhenoApps/Field-Book/releases/tag/6.2.3
[v6.2.4]: https://github.com/PhenoApps/Field-Book/releases/tag/6.2.4
[v6.2.5]: https://github.com/PhenoApps/Field-Book/releases/tag/6.2.5




[v6.3.0]: https://github.com/PhenoApps/Field-Book/releases/tag/6.3.0
[v6.3.1]: https://github.com/PhenoApps/Field-Book/releases/tag/6.3.1


[v6.3.2]: https://github.com/PhenoApps/Field-Book/releases/tag/6.3.2
[v6.3.3]: https://github.com/PhenoApps/Field-Book/releases/tag/6.3.3


[v6.3.4]: https://github.com/PhenoApps/Field-Book/releases/tag/6.3.4
[v6.3.5]: https://github.com/PhenoApps/Field-Book/releases/tag/6.3.5


[v6.3.6]: https://github.com/PhenoApps/Field-Book/releases/tag/6.3.6


[v7.0.0]: https://github.com/PhenoApps/Field-Book/releases/tag/7.0.0
[v7.0.1]: https://github.com/PhenoApps/Field-Book/releases/tag/7.0.1
[v7.0.2]: https://github.com/PhenoApps/Field-Book/releases/tag/7.0.2

[v7.1.0]: https://github.com/PhenoApps/Field-Book/releases/tag/7.1.0