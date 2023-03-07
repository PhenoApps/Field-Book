Exporting Data
==============
Collected data can be exported to CSV files. The Export dialog allows the user to customize how collected data is exported

.. figure:: /_static/images/export/export_framed.png
   :width: 40%
   :align: center
   :alt: Export layout

Options
-------

File Format
~~~~~~

One or both formats must be checked.

**Database** format exports each individual observation as a row, including all collected metadata such as the person and timestamp of each observation.

**Table** format exports each entry as a row with each trait as a column.

Included Columns
~~~~~~~~~~~~~~~~

The export file can include only the unique identifier, or all field columns that were imported.

The export file can contain all traits that are currently active, or the subset of traits for which data has been collected.

Save Details
~~~~~~~~~~~~

The default filename is automatically generated based on the current date and the name of the field.

After clicking save, you will see Fieldbook app citation information and an option to share the exported file with other apps or email.

Other Customizations
~~~~~~~~~~~~~~~~~~~~

**Bundle media data** produces a zipfile including the selected database and/or table CSV file as well as collected images and audio.

**Overwrite previous export** moves old files to /archive folder.

The default export location can be modified in :doc:`settings-general` (|settings|).

.. |settings| image:: /_static/icons/settings/main/cog-outline.png
  :width: 20