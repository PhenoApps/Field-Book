Fields
======
Overview
--------

Experiments are represented in Field Book as *fields*. Fields are either imported from a file, from a BrAPI-enabled database, or created from scratch. Each field has a unique name, an import date, and a set of *entries* which represent the experimental units on which data will be collected (e.g. plots or individual plants). Once data is collected and exported for a given field it will display the dates of last edit and export as well

.. figure:: /_static/images/fields/fields_list_item.png
   :width: 60%
   :align: center
   :alt: Field list item

   A sample field with a name, import/edit/export dates, and number of entries

When importing from a file, each row in the file reprents an *entry*. Each *entry* within a field must have the following: 

   * A *unique identifier*, which is used internally by Field Book to associate data with the specific entry. It must be unique across all of your fields.
   * A *primary identifier*, and a *secondary identifier*. These set the order of advancement through the field's entries, and can be whatever makes the most sense for your experiment. MOst often they are numbers from the experimental design (e.g., row/plot, row/column, range/plot). They can be reassigned at any time via the field submenu.

.. figure:: /_static/images/fields/fields_import_format.png
   :width: 90%
   :align: center
   :alt: Sample import file

   A sample field import file

Any further information for the entries such as variety name, pedigree, or treatment is optional, but can be included and viewed in the InfoBars or in the summary dialog on the collect screen if desired.

.. figure:: /_static/images/fields/fields_framed.png
   :width: 40%
   :align: center
   :alt: Fields layout

   The Fields screen layout with sample fields loaded.

A set of sample field import files are availble for import and inspection on install. field_samples 1-3 represent typical wheat breeding fields, while rtk_sample.csv demonstrates the import format for entry location data (an additional geo_coordinates column). Imported entry coordinates can then be used with the |geonav| :doc:`geonav` feature.   

Importing a new field
---------------------

To import a new field into Field Book press the Add icon (|add|) in the upper right-hand corner of the toolbar in the Fields section. Then, in the resulting dialog, select whether to import from a local file, from cloud storage (Dropbox, Google Drive, etc.) or via a BrAPI connection. If choosing thelast option see :doc:`brapi` (|brapi|) for more details.

A default import source can be set in :doc:`settings-general` (|settings|) to skip this dialog.

.. figure:: /_static/images/fields/fields_import_joined.png
   :width: 100%
   :align: center
   :alt: Field import screens

   Field import process.

If local is chosen, a list of possible files in the **field_import** folder will be displayed. Field import files must be saved in CSV, XLS, or XLSX format, or they will not show up in the import dialog. Filenames and column headers should exclude the following special characters:

.. figure:: /_static/images/fields/fields_illegal_characters.png
   :width: 40%
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

.. |geonav| image:: /_static/icons/settings/main/map-search.png
  :width: 20

.. |add| image:: /_static/icons/fields/plus-circle.png
  :width: 20

.. |settings| image:: /_static/icons/settings/main/cog-outline.png
  :width: 20

.. |brapi| image:: /_static/icons/settings/main/server-network.png
  :width: 20

.. |create| image:: /_static/icons/fields/table-large-plus.png
  :width: 20