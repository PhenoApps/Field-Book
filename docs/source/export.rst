Export
======
Overview
--------

Collected data is exported to CSV files. The Export dialog allows the user to customize how collected data is exported.

.. figure:: /_static/images/export/export_framed.png
   :width: 40%
   :align: center
   :alt: Export layout

Options
-------

File Format
~~~~~~~~~~~

At least one format must be selected to export data. If both formats are selected, Field Book exports a single zipped file containing the files.

**Table** format exports each entry as a row and each trait as a column, creating a matrix of observed values. Metadata such as person and timestamp are excluded. If repeated measures have been collected, only the first value will be exported when using the table format.

.. figure:: /_static/images/export/export_file_formats.png
   :width: 100%
   :align: center
   :alt: Export file formats

   Sample data exported in both formats

**Database** format exports each individual observation as a spreadsheet row, and includes columns for collected metadata such as the name of the person who collected the data and the timestamp of when the observation was collected.

Included Columns
~~~~~~~~~~~~~~~~

The exported file can include only the unique identifier or all field columns that were imported.

The exported file can contain traits that are currently active or all traits that have been created.

Save Details
~~~~~~~~~~~~

The default filename is automatically generated based on the current date and the name of the field.

After clicking save, you will see Field Book app citation information and an option to share the exported file with other apps or email. The exported file is stored in the ``field_export`` directory.

Other Customizations
~~~~~~~~~~~~~~~~~~~~

**Bundle media data** produces a zipped file that contains the exported data along with images and audio files that have also been collected.

**Overwrite previous export** moves old files to ``archive`` directory.

The default export location can be modified in |settings| :doc:`settings-general`.

.. |settings| image:: /_static/icons/settings/main/cog-outline.png
  :width: 20