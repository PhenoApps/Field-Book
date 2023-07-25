Storage
=======
Storage Location Definer
------------------------

After installing and opening the app, you will be asked to create a new folder and define it as the **Storage location** on the device where files will be written. In Field Book this step is required. The selected 'Storage location' can be changed in the |settings| :doc:`/settings-general`.

.. figure:: /_static/images/storage_definer_framed.png
   :width: 40%
   :align: center
   :alt: Storage location definer layout

   Storage location definer layout

Subdirectories
--------------

Fieldbook will create the following subdirectories within the selected 'Storage location' directory: 

* ``field_import``: contains files to be imported into Field Book

* ``field_export``: contains exported data files

* ``plot_data``: data associated with plots (e.g., audio, photos)

* ``resources``: allows the user to access photos/documents while collecting data (e.g., rating scales, field maps)

* ``database``: contains automatic and manual database export files

* ``trait``: contains trait files and the rust rating customizable scale

* ``archive``: contains backups when exported fields are overwritten

* ``geonav``: stores files created when using the geonav feature

* ``updates``: stores .apk files downloaded for app updates

File transfer
-------------

To add your own files to the appropriate subdirectories you may need to manually transfer files from a computer. You can do this by connecting your Android device via a USB cable and changing its USB mode to allow file transfer. Each device manufacturer has slight variations for the appearance of this dialog.

.. figure:: /_static/images/fields/fields_transfer.png
   :width: 40%
   :align: center
   :alt: USB file transfer settings

   Example settings for USB file transfer.

Database
--------

Field Book uses an internal SQLite database to store imported fields and traits, as well as all data collected with the app. The database schema is modelled after the Breeding API (BrAPI) standard, and is documented in the `Field Book Wiki <https://github.com/PhenoApps/Field-Book/wiki>`_

The database is automatically backed up to the ``database`` directory at regular intervals. It can also be manually exported and imported in |database| :doc:`settings-database`. This feature can be used to transfer data to a new device or for recreating issues when troubleshooting.

.. |database| image:: /_static/icons/settings/main/database.png
  :width: 20

.. |settings| image:: /_static/icons/settings/main/cog-outline.png
  :width: 20