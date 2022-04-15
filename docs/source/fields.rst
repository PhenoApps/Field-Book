Fields
======

Design
------
Field Book can load both CSV and XLS files. Filenames and column headers should exclude the following characters: / ?  < > \ * | ”.
 
Field Book import files require three columns: a unique identifier, a primary order, and a secondary order. Each entry should be assigned a unique identifier. This unique ID is used internally to associate data with a specific entry and should be globally unique.
 
The primary and secondary order are often used for field orientation (e.g. row/plot, row/column, range/plot, etc.), and can be whatever makes the most sense for a given experiment.
 
In addition, two columns must be included as a basic navigation ordering. These are referred to as the Primary and Secondary Order. These are often chosen based on a field walking order (e.g. row/plot, row/column, range/plot, etc.).
 
Other columns can be included in the field file and can be displayed in the InfoBars on the main screen.

Transfer
--------
To transfer files to Android devices, plug the phone/tablet into the computer and change the USB mode to allow files to be transferred. Each device manufacturer will likely have slight variations for the appearance of this dialog.

Importing
---------
To import files into Field Book, select the Fields and press the add button on the toolbar. Files can be imported via cloud storage (Dropbox, Google Drive, etc.) or locally. If local is chosen, a list of files in the /import_fields/ folder will be displayed. Selecting one of these files will allow you to assign columns as the unique ID, primary order, and secondary order. Field Book uses the same plot order as the imported file.

Managing
--------
Fields are selectable from the list of fields. Each row in the fields list displays the Date imported, Date edited, Date exported, and Number of entries. Fields can be deleted from the sub menu in each row.
