Fields
======


.. figure:: /_static/images/fields/fields_framed.png
   :width: 40%
   :align: center
   :alt: Fields layout

   The Fields screen layout with sample data loaded.


Importing a field
-----------------

To import a new field into Field Book press the Add icon (|add|) in the upper right-hand corner of the toolbar in the Fields section. Fields can be imported from a local file, from cloud storage (Dropbox, Google Drive, etc.) or via a BrAPI connection (see :doc:`brapi` (|brapi|) for details).

.. figure:: /_static/images/fields/fields_import_joined.png
   :width: 40%
   :align: center
   :alt: Field import option dialog

   Field import process.

If local is chosen, a list of possible CSV, XLS, and XLSX files in the /import_fields/ folder will be displayed. Add files to this folder by downloading them or transferring them from a computer.

Import files must be saved in CSV, XLS, or XLSX format, or they will not show up in the import dialog. Filenames and column headers should exclude the following special characters: **/ ?  < > \ * | ”**.

Files must have at least three columns: a unique identifier, a primary identifier, and a secondary identifier. Each entry in the import file should be assigned a unique identifier. This unique ID is used internally to associate data with a specific entry and must be unique across all of your files.

The primary and secondary identifier are often used for field orientation (e.g., row/plot, row/column, range/plot, etc.), and can be whatever makes the most sense for your specific experiment.

Extra columns (e.g. name, pedigree) can optionally be included in the file, they become additional display options in the InfoBars on the main screen.

File Transfer
~~~~~~~~~~~~~

If you need to manually transfer files from a computer, connect your Android device via a usb cable and change it's USB mode to allow file transfer. Each device manufacturer has slight variations for the appearance of this dialog.

.. figure:: /_static/images/fields/fields_transfer.png
   :width: 40%
   :align: center
   :alt: USB file transfer settings

   Example settings for USB file transfer.

Cloud storage
~~~~~~~~~~~~~

If you choose to import from cloud storage, Fieldbook will open the device file manager, allowing you to navigate to the the file you would like to import.

.. figure:: /_static/images/fields/fields_import_cloud.png
   :width: 40%
   :align: center
   :alt: Field import from drive

   Navigating to a google drive file for cloud import

Creating a field
----------------

.. figure:: /_static/images/fields/fields_create_joined.png
   :width: 100%
   :align: center
   :alt: Creating a new field

   Field creation process

To create a new field directly within Field Book press the Create icon (|create|) in the center of the toolbar. Set your field name and dimensions, choose which corner of the field will contain the first plot, and select zigzag or serpentine plot numbering.

Managing fields
---------------

.. figure:: /_static/images/fields/fields_list_joined.png
   :width: 80%
   :align: center
   :alt: Individual field options menu

   Managing existing fields

Fields are selectable from the list of fields. Each row in the fields list displays the Date imported, Date edited, Date exported, and Number of entries. Fields can be deleted or resorted by different identifiers using the sub menu in each row.


.. |add| image:: /_static/icons/fields/plus-circle.png
  :width: 20

.. |brapi| image:: /_static/icons/settings/main/server-network.png
  :width: 20

.. |create| image:: /_static/icons/fields/table-large-plus.png
  :width: 20