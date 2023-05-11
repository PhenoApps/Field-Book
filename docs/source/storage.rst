Storage
=======
Storage Location Definer
------------------------

After installing and opening the app, you will be asked to define a 'Storage location' on the device. In Field Book this step is required. The selected 'Storage location' can be changed in the :doc:`/settings-general`.

.. figure:: /_static/images/storage_definer_framed.png
   :width: 40%
   :align: center
   :alt: Storage location definer layout

   Storage location definer layout

Subdirectories
--------------

Fieldbook will create the following subdirectories within the selected 'Storage location' directory: **field_import**, **field_export**, **plot_data**, **resources**, **database**, **trait**, and **archive**.

* **field_import**: contains field files that to be imported into Field Book

* **field_export**: contains exported data files

* **plot_data**: data associated with plots (audio and photos) are organized in this folder based on the field name

* **resources**: allows the user to access photos/documents while collecting data (e.g. rating scales, field maps, etc.)

* **database**: contains automatic and manual database export files

* **trait**: contains trait files and the rust rating customizable scale

* **archive**: contains backups when the user chooses to overwrite previously exported fields

File transfer
-------------

To add your own field, trait, and resource files to the appropriate subdirectories you may need to manually transfer files from a computer. You can do this by connecting your Android device via a USB cable and changing its USB mode to allow file transfer. Each device manufacturer has slight variations for the appearance of this dialog.

.. figure:: /_static/images/fields/fields_transfer.png
   :width: 40%
   :align: center
   :alt: USB file transfer settings

   Example settings for USB file transfer.