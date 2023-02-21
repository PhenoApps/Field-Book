Fields
======


.. figure:: /_static/images/fields/fields_framed.png
   :width: 40%
   :align: center
   :alt: Fields layout

   The Fields screen layout with sample data loaded.


Importing a field
======

To import a new field into Field Book press the Add icon (|add|) in the upper right-hand corner of the toolbar in the Fields section. Fields can be imported via cloud storage (Dropbox, Google Drive, etc.), a BrAPI connection, or from a local file on the device.

.. figure:: /_static/images/brapi/brapi-import-framed.png
   :width: 40%
   :align: center
   :alt: Field import option dialog

   Field import source options.

Local storage
---------

If local is chosen, a list of possible CSV, XLS, and XLSX files in the /import_fields/ folder will be displayed. Add files to this folder by downloading them or transferring them from a computer.

To transfer files from a computer, connect your Android device via a usb cable and change it's USB mode to allow file transfer. Each device manufacturer has slight variations for the appearance of this dialog.

.. figure:: /_static/images/fields/fields_transfer.png
   :width: 40%
   :align: center
   :alt: USB file transfer settings

   Example settings for USB file transfer.

Import files must be saved in CSV, XLS, or XLSX format. Filenames and column headers should exclude the following special characters: **/ ?  < > \ * | ”**.

Files must have at least three columns: a unique identifier, a primary identifier, and a secondary identifier. Each entry in the import file should be assigned a unique identifier. This unique ID is used internally to associate data with a specific entry and must be unique across all of your files.

.. figure:: /_static/images/fields/fields_identifier_selection.png
   :width: 40%
   :align: center
   :alt: Field import identifier selection dialog

   Chosing identfiers from imported columns.

The primary and secondary identifier are often used for field orientation (e.g., row/plot, row/column, range/plot, etc.), and can be whatever makes the most sense for your specific experiment.

Extra columns (e.g. name, pedigree) can optionally be included in the file, they become additional display options in the InfoBars on the main screen.


Cloud storage
---------

.. figure:: /_static/images/fields/fields_import_cloud.png
   :width: 40%
   :align: center
   :alt: Field import from drive

   Navigating to a google drive file for cloud import

If Cloud storage is chosen, Fieldbook will open the device file manager, allowing you to navigate to and select any file for import including from synched cloud storage.

BrAPI
---------

.. figure:: /_static/images/brapi/brapi_import_list_framed.png
   :width: 40%
   :align: center
   :alt: BrAPI field import screen

   The BrAPI field import screen.

If BrAPI is chosen, Fieldbook will import a list of possible fields from the BrAPI Base URL set in the :doc:`settings-brapi` (|brapi|).

.. figure:: /_static/images/brapi/brapi_import_filter_framed.png
   :width: 40%
   :align: center
   :alt: BrAPI field import levels

   Filtering BrAPI field import options by level.

You may filter the list of fields by any available groupings in the upper right toolbar menu (program, trial), or by observation level.

.. figure:: /_static/images/brapi/brapi_import_preview_framed.png
   :width: 40%
   :align: center
   :alt: BrAPI field import preview

   Previewing a BrAPI field's metadata.

Once you select your desired field can can preview its metadata and import it.

.. figure:: /_static/images/brapi/brapi_import_warning_framed.png
   :width: 40%
   :align: center
   :alt: BrAPI field import advisory

   BrAPI field import warning message.

Importing your field via BrAPI is a prerequiste to later export your collected data via BRAPI.


Creating a field
======

To create a new field directly within Field Book press the Create icon (|create|) in the center of the toolbar.

.. figure:: /_static/images/fields/fields_create_1_framed.png
   :width: 40%
   :align: center
   :alt: Creating a new field

   Set your field name and dimensions.

.. figure:: /_static/images/fields/fields_create_2_framed.png
   :width: 40%
   :align: center
   :alt: Creating a new field

   Choose which corner of the field will contain the first plot.

.. figure:: /_static/images/fields/fields_create_3_framed.png
   :width: 40%
   :align: center
   :alt: Creating a new field

   Select zigzag or serpentine plot numbering.


Managing fields
======

.. figure:: /_static/images/fields/fields_list_menu_framed.png
   :width: 40%
   :align: center
   :alt: Individual field options menu

   Options available for each field list item.

Fields are selectable from the list of fields. Each row in the fields list displays the Date imported, Date edited, Date exported, and Number of entries. Fields can be deleted and sorted from the sub menu in each row.

Sort
~~~~

.. figure:: /_static/images/fields/fields_list_sorting_framed.png
   :width: 40%
   :align: center
   :alt: Sorting an existing field

   Sorting an existing field


.. |add| image:: /_static/icons/fields/plus-circle.png
  :width: 20

.. |brapi| image:: /_static/icons/settings/main/server-network.png
  :width: 20

.. |create| image:: /_static/icons/fields/table-large-plus.png
  :width: 20
