# Field-Book to KMP feature migration

*(incomplete list)*

- [Core features](#core-features)
- [BrAPI Workflow](#brapi-workflow)

## Core features

| Feature                          | KMP Android        | KMP iOS            |
|----------------------------------|--------------------|--------------------|
|                                  |                    |                    |
| **Onboarding**                   |                    |                    |
| Initialize storage               | :white_check_mark: | :white_check_mark: |
| Permissions                      | :white_check_mark: | :white_check_mark: |
| Load samples                     | :white_check_mark: | :white_check_mark: |
| Tutorial                         |                    |                    |
|                                  |                    |                    |
| **Fields**                       |                    |                    |
| View fields                      | :white_check_mark: | :white_check_mark: |
| Create new field                 | :white_check_mark: | :white_check_mark: |
| Edit field                       | :white_check_mark: | :white_check_mark: |
| Switch field                     | :white_check_mark: | :white_check_mark: |
| Delete field                     | :white_check_mark: |                    |
| Import from file                 | :white_check_mark: |                    |
|                                  |                    |                    |
| **Field Details**                |                    |                    |
| Edit field                       | :white_check_mark: | :white_check_mark: |
| Delete field                     | :white_check_mark: | :white_check_mark: |
| Collect                          |                    |                    |
| Export                           |                    |                    |
| Data                             |                    |                    |
|                                  |                    |                    |
| ...                              |                    |                    |
|                                  |                    |                    |
| **Traits**                       |                    |                    |
| View traits                      | :white_check_mark: | :white_check_mark: |
| Create new trait                 | :white_check_mark: | :white_check_mark: |
| Copy trait                       | :white_check_mark: | :white_check_mark: |
| Delete trait                     | :white_check_mark: | :white_check_mark: |
| Disable trait                    | :white_check_mark: | :white_check_mark: |
| Reorder                          | :white_check_mark: | :white_check_mark: |
| Edit trait                       | :white_check_mark: | :white_check_mark: |
| Export                           | :white_check_mark: | :white_check_mark: |
| Sort                             | :white_check_mark: | :white_check_mark: |
| Select all                       | :white_check_mark: | :white_check_mark: |
| Import from file                 | :white_check_mark: |                    |
|                                  |                    |                    |
| ...                              |                    |                    |
|                                  |                    |                    |
| **Trait formats**                |                    |                    |
| Audio                            |                    |                    |
| Boolean                          | :white_check_mark: | :white_check_mark: |
| Categorical                      | :white_check_mark: | :white_check_mark: |
| Multicategorial                  | :white_check_mark: | :white_check_mark: |
| Counter                          | :white_check_mark: | :white_check_mark: |
| Date                             | :white_check_mark: | :white_check_mark: |
| Location                         |                    |                    |
| Numeric                          | :white_check_mark: | :white_check_mark: |
| Percent                          | :white_check_mark: | :white_check_mark: |
| Text                             | :white_check_mark: | :white_check_mark: |
| Angle                            |                    |                    |
| Disease rating                   |                    |                    |
| GNSS                             |                    |                    |
| Zebra label print                |                    |                    |
|                                  |                    |                    |
| ...                              |                    |                    |
|                                  |                    |                    |
| **Photo formats**                |                    |                    |
| System                           | :white_check_mark: | :white_check_mark: |
| USB                              |                    |                    |
| GoPro                            |                    |                    |
| Canon                            |                    |                    |
|                                  |                    |                    |
| **Collect**                      |                    |                    |
| Switch trait                     | :white_check_mark: | :white_check_mark: |
| Go to trait                      |                    |                    |
| Switch obs unit                  | :white_check_mark: | :white_check_mark: |
| Quick goto obs unit              |                    |                    |
| Trait types                      | See Trait formats  | See Trait formats  |
| Customize Infobar                |                    |                    |
|                                  |                    |                    |
| **Collect actions**              |                    |                    |
| Search                           |                    |                    |
| Resources                        |                    |                    |
| Summary                          |                    |                    |
| Lock                             |                    |                    |
| Freeze                           |                    |                    |
| Scan                             |                    |                    |
| Set NA                           |                    |                    |
| Delete                           |                    |                    |
| Data grid                        | :white_check_mark: | :white_check_mark: |
|                                  |                    |                    |
| **Export**                       |                    |                    |
| Basic export all opts            | :white_check_mark: | :white_check_mark: |
| Share zip file                   | :white_check_mark: | :white_check_mark: |
|                                  |                    |                    |
|                                  |                    |                    |
| **Settings**                     |                    |                    |
| Search preferences               |                    |                    |
|                                  |                    |                    |
| **Settings/Share**               |                    |                    |
| Import preferences               |                    |                    |
| Export preferences               |                    |                    |
|                                  |                    |                    |
| **Settings/Profile**             |                    |                    |
| Person name                      |                    |                    |
| Verification interval            |                    |                    |
| Device name                      |                    |                    |
|                                  |                    |                    |
| **Settings/Features**            |                    |                    |
| Tutorial                         |                    |                    |
| Data grid                        | :white_check_mark: | :white_check_mark: |
| Next entry with no data          |                    |                    |
| Move to unique ID                |                    |                    |
|                                  |                    |                    |
| **Settings/Appearance**          |                    |                    |
| Theme                            |                    |                    |
| Language                         |                    |                    |
| Toolbar actions                  |                    |                    |
| Number of infobars               |                    |                    |
| Hide infobar prefix              |                    |                    |
| Entries progress bar             |                    |                    |
| Traits progress bar              |                    |                    |
|                                  |                    |                    |
| **Settings/Appearance/Theme**    |                    |                    |
| Theme                            |                    |                    |
| Text size                        |                    |                    |
| Saved data color                 |                    |                    |
| Restore default                  |                    |                    |
|                                  |                    |                    |
| **Settings/Appearance/Language** | :white_check_mark: | :white_check_mark: |
|                                  |                    |                    |
| **Settings/Behavior**            |                    |                    |
| Auto-advance entry               |                    |                    |
| Auto-reset traits                |                    |                    |
| Require data to move entry       |                    |                    |
| Skip entries                     |                    |                    |
| Swap navigation arrows           |                    |                    |
| Volume buttons navigate          |                    |                    |
| Return character action          |                    |                    |
| Use day number                   |                    |                    |
|                                  |                    |                    |
| **Settings/Location**            |                    |                    |
| Pair Bluetooth                   |                    |                    |
| Location collection              |                    |                    |
| Coordinate format                |                    |                    |
| Enable GeoNav                    |                    |                    |
| Search method                    |                    |                    |
| Search trapezoid D1              |                    |                    |
| Search trapezoid D2              |                    |                    |
| Search angle                     |                    |                    |
| Distance threshold               |                    |                    |
| GeoNav logging mode              |                    |                    |
| Update interval                  |                    |                    |
|                                  |                    |                    |
| **Settings/Sounds**              |                    |                    |
| Primary order sound              |                    |                    |
| Entry navigation sound           |                    |                    |
| Cycle traits sound               |                    |                    |
| Delete observation sound         |                    |                    |
| Text-to-speech                   |                    |                    |
| Text-to-speech language          |                    |                    |
|                                  |                    |                    |
| **Settings/System**              |                    |                    |
| Default import source            |                    |                    |
| Default export source            |                    |                    |
| Enable share                     |                    |                    |
| Reset preferences                |                    |                    |
| Crashlytics user ID              |                    |                    |
| Refresh Crashlytics user ID      |                    |                    |
|                                  |                    |                    |
| **Settings/Storage**             |                    |                    |
| Define storage location          | :white_check_mark: | :white_check_mark: |
| Import db (sample)               | :white_check_mark: | :white_check_mark: |
| Import db (other file)           |                    |                    |
| Export db                        |                    |                    |
| Delete db                        | :white_check_mark: | :white_check_mark: |
|                                  |                    |                    |
| **Settings/Experimental**        |                    |                    |
| Repeated measures                |                    |                    |
| Field audio                      |                    |                    |
| MLKit barcode scanner            |                    |                    |
| Enhanced BrAPI import            |                    |                    |
| Media keycode events             |                    |                    |
|                                  |                    |                    |
| **Statistics**                   |                    |                    |
|                                  |                    |                    |
| ...                              |                    |                    |
|                                  |                    |                    |
| **About**                        |                    |                    |
|                                  |                    |                    |
| ...                              |                    |                    |
|                                  |                    |                    |
| **Scan barcode**                 |                    |                    |
| Scan plot main page              | :white_check_mark: |                    |
|                                  |                    |                    |
| ...                              |                    |                    |

## BrAPI Workflow

| Feature                          | KMP Android        | KMP iOS            |
|----------------------------------|--------------------|--------------------|
|                                  |                    |                    |
| **Fields**                       |                    |                    |
| Import from BrAPI                | :white_check_mark: |                    |
| ...                              |                    |                    |
|                                  |                    |                    |
| **Export**                       |                    |                    |
| Export to BrAPI                  | :white_check_mark: |                    |
|                                  |                    |                    |
| ...                              |                    |                    |
|                                  |                    |                    |
| **Settings/BrAPI**               |                    |                    |
| Enable                           | :white_check_mark: | :white_check_mark: |
|                                  |                    |                    |
| **Settings/BrAPI/Navbar**        |                    |                    |
| Scan barcode                     |                    |                    |
| Authorize                        | :white_check_mark: |                    |
|                                  |                    |                    |
| **Settings/BrAPI/Server**        |                    |                    |
| Base URL                         | :white_check_mark: | :white_check_mark: |
| Authorize                        | :white_check_mark: |                    |
| Display name                     |                    |                    |
| Auto-configure                   |                    |                    |
| Logout                           | :white_check_mark: |                    |
|                                  |                    |                    |
| **Settings/BrAPI/Authorization** |                    |                    |
| OIDC Flow / Implicit Grant       | :white_check_mark: |                    |
| OIDC Flow / Authorization Code   |                    |                    |
| OIDC Discovery URL               | :white_check_mark: | :white_check_mark: |
| OIDC Client ID                   | :white_check_mark: | :white_check_mark: |
| OIDC Scope                       |                    |                    |
|                                  |                    |                    |
| **Settings/BrAPI/Advanced**      |                    |                    |
| BrAPI version / V1               | :white_check_mark: |                    |
| BrAPI version / V2               | :white_check_mark: |                    |
| Page size                        |                    |                    |
| Chunk size                       |                    |                    |
| Server timeout                   |                    |                    |
| Cache invalidation               |                    |                    |
|                                  |                    |                    |
| **Settings/BrAPI/Preferences**   |                    |                    |
| Value vs Label display           |                    |                    |
