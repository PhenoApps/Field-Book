Storage
=======
Storage Location Definer
------------------------
.. figure:: /_static/images/storage_definer_framed.png
   :width: 40%
   :align: center
   :alt: Storage location definer layout

   Storage location definer layout

After installing and opening the app, you will be asked to define a 'Storage location' on the device. In Field Book this step is required. The selected 'Storage location' can be changed in the :doc:`/settings-general`.

Subdirectories
--------------

Fieldbook will create the following subdirectories within the 'Storage location' directory: **field_import**, **field_export**, **plot_data**, **resources**, **database**, **trait**, and **archive**.

* **field_import**: contains files that can be imported into Field Book

* **field_export**: contains exported data files

* **plot_data**: data associated with plots (audio and photos) are organized into this folder based on the field name

* **resources**: allows the user to access photos/documents while collecting data (e.g. rating scales, field maps, etc.)

* **database**: contains automatic and manual database export files

* **trait**: contains trait files and the rust rating customizable scale

* **archive**: contains backups when the user chooses to overwrite previously exported fields
