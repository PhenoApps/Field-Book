Storage
=======

Setup
-----

One of the required steps when opening the app for the first time is to define the **Storage location** where Field Book app files will be written. This can be an existing folder, or a newly created one.

<figure align="center" class="image">
  <img src="_static/images/intro/defining_storage_location_joined.png" width="1100px"> 
  <figcaption><i>Using a new folder for storage</i></figcaption> 
</figure>

The selected **Storage location** can be changed in the
<a href="settings-storage.md"><img style="vertical-align: middle;" src="_static/icons/settings/main/database-cog.png" width="20px"></a> [Storage Settings](settings-storage.md).


Subfolders
----------

Fieldbook will create the following subfolders within the selected
**Storage location** folder:

-   `field_import`: contains files to be imported into Field Book
-   `field_export`: contains exported data files
-   `plot_data`: data associated with plots (e.g., audio, photos)
-   `resources`: allows the user to access photos/documents while
    collecting data (e.g., rating scales, field maps)
-   `database`: contains automatic and manual database export files
-   `trait`: contains trait files and the rust rating customizable scale
-   `archive`: contains backups when exported fields are overwritten
-   `geonav`: stores files created when using the geonav feature
-   `updates`: stores .apk files downloaded for app updates

File transfer
-------------

To add your own files to the appropriate subfolders you may need to
manually transfer files from a computer. You can do this by connecting
your Android device via a USB cable and changing its USB mode to allow
file transfer. Each device manufacturer has slight variations for the
appearance of this dialog.

<figure align="center" class="image">
  <img src="_static/images/fields/fields_transfer.png" width="325px"> 
  <figcaption><i>Example settings for USB file transfer</i></figcaption> 
</figure>

Database
--------

Field Book uses an internal SQLite database to store imported fields and
traits, as well as all data collected with the app. The database schema
is modelled after the Breeding API (BrAPI) standard, and is documented
in the [Field Book Wiki](https://github.com/PhenoApps/Field-Book/wiki)

The database is automatically backed up to the `database` folder at
regular intervals. It can also be manually exported and imported in <a href="settings-storage.md"><img style="vertical-align: middle;" src="_static/icons/settings/main/database-cog.png" width="20px"></a> [Storage Settings](settings-storage.md). This feature can be
used to transfer data to a new device or for recreating issues when
troubleshooting.
