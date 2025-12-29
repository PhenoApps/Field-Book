<link rel="stylesheet" type="text/css" href="_styles/styles.css">

# Export

## Overview

The Export dialog allows the user to customize how data is exported.
Collected data is exported as `.csv` files.

<figure class="image">
  <img class="screenshot" src="_static/images/export/export_framed.png" width="350px" > 
  <figcaption class="screenshot-caption"><i>Export dialog details</i></figcaption> 
</figure>

## Options

#### File Format

At least one format must be selected to export data.
If both formats are selected, Field Book exports a single zipped file containing the files.

**Table** format exports each entry as a row and each trait as a column, creating a matrix of observed values.
Metadata such as person and timestamp are excluded.
If repeated measures have been collected, only the first value will be exported when using the table format.

**Database** format exports each individual phenotype as a row and includes metadata columns including the name of the person who collected the data and the timestamp for when it was collected.

<figure class="image">
  <img class="screenshot" src="_static/images/export/export_file_formats.png" width="1100px"> 
  <figcaption class="screenshot-caption"><i>Sample data exported in both formats</i></figcaption> 
</figure>

#### Included Columns

The exported file can include only the unique identifier or all field columns that were imported.
The exported file can contain traits that are currently active or all traits that have been created.

#### Details

The filename is generated based on the current date and the name of the field.
After pressing Save, the Field Book app citation will be displayed and an option to share the exported file with other apps will show.
Exported files are stored in the `field_export` folder.

#### Other customizations

- **Bundle media data** produces a zipped file that contains the exported data along with images and audio files that have also been collected.
- **Overwrite previous export** moves previous exports from the field to the `archive` folder.
- Export can be set to default to local or BrAPI in <img class="icon" src="_static/icons/settings/main/cog-outline.png"> [System Settings](settings-system.md).