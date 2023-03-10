Fields
======
Overview
--------

Experiments are represented in Field Book via *fields*. Fields are either imported from a file or BrAPI enabled database, or created from scratch. Each field has a unique name, import, modification, and export dates, and a set of *entries* which represent the experimental units on which data will be collected (e.g. plots or individual plants).

Each *entry* within a field must have the following: 

   * A *unique identifier*, which is used internally to associate data with the specific entry. It must be unique across all of your fields.
   * A *primary identifier*, and a *secondary identifier* which set the order of advancement through the field's entries. These can be whatever makes the most sense for your experiment, but they are generally numbers from the experimental design (e.g., row/plot, row/column, range/plot). They can be reassigned at any time via the field submenu.
  
Any further information for the entries such as variety name, pedigree, or treatment is optional, but can be included and viewed in the InfoBars or in the summary dialog on the collect screen if desired.


.. figure:: /_static/images/fields/fields_framed.png
   :width: 40%
   :align: center
   :alt: Fields layout

   The Fields screen layout with sample fields loaded.


Importing a new field
---------------------

To import a new field into Field Book press the Add icon (|add|) in the upper right-hand corner of the toolbar in the Fields section. Then, in the resulting dialog, select whether to import from a local file, from cloud storage (Dropbox, Google Drive, etc.) or via a BrAPI connection (see :doc:`brapi` (|brapi|) for details).

A default import source can be set in :doc:`settings-general` (|settings|) to skip this dialog.

.. figure:: /_static/images/fields/fields_import_joined.png
   :width: 100%
   :align: center
   :alt: Field import screens

   Field import process.

If local is chosen, a list of possible files in the **field_import** folder will be displayed. Field import files must be saved in CSV, XLS, or XLSX format, or they will not show up in the import dialog. Filenames and column headers should exclude the following special characters:

.. figure:: /_static/images/fields/fields_illegal_characters.png
   :width: 80%
   :align: center
   :alt: Field file illegal characters

   Unallowed characters in file and column names

If you need to add files to the import folder, you can do so by downloading them or transferring them from a computer, as described in :doc:`storage`.

Once a file has been selected, use the dropdown menus in the final dialog to chose which column names from your file correspond to Field Book's required identifiers. Then press IMPORT to finish loading your field.

Cloud storage
~~~~~~~~~~~~~

If you choose to import from cloud storage, Field Book will open the device file manager, allowing you to navigate to the the file you would like to import.

.. figure:: /_static/images/fields/fields_cloud_import.png
   :width: 40%
   :align: center
   :alt: Field import from drive

   Navigating to a google drive file for cloud import

Creating a field
----------------

.. figure:: /_static/images/fields/fields_create_joined.png
   :width: 100%
   :align: center
   :alt: Field creation screens

   Field creation process

To create a new field directly within Field Book press the Create icon (|create|) in the center of the toolbar. Set your field name and dimensions, choose which corner of the field will contain the first plot, and select zigzag or serpentine plot numbering.

Managing fields
---------------

.. figure:: /_static/images/fields/fields_list_joined.png
   :width: 80%
   :align: center
   :alt: Field management screens

   Managing existing fields

Fields are selectable from the list of fields. Each row in the fields list displays the Date imported, Date edited, Date exported, and Number of entries. Fields can be deleted or resorted by different identifiers using the sub menu in each row.


.. |add| image:: /_static/icons/fields/plus-circle.png
  :width: 20

.. |settings| image:: /_static/icons/settings/main/cog-outline.png
  :width: 20

.. |brapi| image:: /_static/icons/settings/main/server-network.png
  :width: 20

.. |create| image:: /_static/icons/fields/table-large-plus.png
  :width: 20