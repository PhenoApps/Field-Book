Fields
======

Design
------

.. figure:: /_static/images/fields/fields_framed.png
   :width: 40%
   :align: center
   :alt: Fields layout

   The Fields screen layout with sample data loaded.

Field Book can import CSV, XLS, and XLSX files. Filenames and column headers should exclude the following special characters: **/ ?  < > \ * | ”**.
 
Field Book requires import files to have at least three columns: a unique identifier, a primary identifier, and a secondary identifier. Each entry in the import file should be assigned a unique identifier. This unique ID is used internally to associate data with a specific entry and must be unique across all of your files.
 
The primary and secondary identifier are often used for field orientation (e.g., row/plot, row/column, range/plot, etc.), and can be whatever makes the most sense for your specific experiment.
 
Extra columns (e.g. name, pedigree) can optionally be included in the file, they become additional display options in the InfoBars on the main screen.

Transfer
--------
To transfer files to an Android device, plug the device into a computer and change it's USB mode to allow files to be transferred. Each device manufacturer has slight variations for the appearance of this dialog.

Importing
---------
To import files into Field Book, select the Fields section, then press the add button in the upper right-hand corner of the the screen in the toolbar. Files can be imported via cloud storage (Dropbox, Google Drive, etc.), a BrAPI connection, or a local file. If local is chosen, a list of files in the /import_fields/ folder will be displayed. Selecting one of these files will allow you to assign columns as the unique ID, primary order, and secondary order. Field Book uses the same plot order as the imported file.

List Item Layout
----------------

Managing
--------
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

Creating fields
---------------
.. figure:: /_static/images/fields/fields_create_1_framed.png
   :width: 40%
   :align: center
   :alt: Creating a new field
