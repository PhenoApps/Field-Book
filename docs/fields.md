Fields
======

Overview
--------

Experiments are represented in Field Book as `fields`. Fields are either
imported from a file, from a BrAPI-enabled database, or created from
scratch.

Each field has a unique name, an import date, and a set of `entries`
which represent the experimental units on which data will be collected
(e.g., plots, plants). Once data is collected and exported for a given
field it will display the dates of last edit and export.

<figure align="center" class="image">
  <img src="_static/images/fields/fields_list_item.png" width="325px"> 
  <figcaption><i>A sample field with a name, import/edit/export dates, and number of
entries</i></figcaption> 
</figure>

When importing from a file, each row in the file reprents an `entry`.
Each entry within a field must have the following:

- A `unique identifier`, which is used internally by Field Book to associate data with the specific entry. It must be unique across all of your fields.
- A `primary identifier`, and a `secondary identifier`. These set the order of advancement through the field's entries, and can be whatever makes the most sense for your experiment. Most often they are numbers from the experimental design (e.g., row/plot, row/column, range/plot).

<figure align="center" class="image">
  <img src="_static/images/fields/fields_import_format.png" width="900px"> 
  <figcaption><i>A sample field import file</i></figcaption> 
</figure>

Additional information such as variety name, pedigree, or treatments is
optional, but can be included and viewed in the InfoBars or in the
summary dialog on the collect screen.

<figure align="center" class="image">
  <img src="_static/images/fields/fields_framed.png" width="400px"> 
  <figcaption><i>The Fields screen layout with sample fields loaded</i></figcaption> 
</figure>

Field Book includes a set of sample field files. Samples
`field_sample.csv`, `field_sample2.csv`, and `field_sample3.csv`
represent typical wheat breeding fields, while `rtk_sample.csv`
demonstrates the import format for entry location data (an additional
`geo_coordinates` column). Imported entry coordinates can then be
used with the <a href="geonav.md"><img style="vertical-align: middle;" src="_static/icons/settings/main/map-search.png" width="20px"></a> [Geonav](geonav.md) feature.

Importing a new field
---------------------

To import a new field into Field Book press the <img ref="add" style="vertical-align: middle;" src="_static/icons/fields/plus-circle.png" width="20px"> icon in the
upper right-hand corner of the toolbar in the Fields section. Then, in
the resulting dialog, select whether to import from a local file, from
cloud storage (Dropbox, Google Drive, etc.), or via a <a href="brapi.md"><img style="vertical-align: middle;" src="_static/icons/settings/main/server-network.png" width="20px"></a> [Brapi](brapi.md) connection.

A default import source can be set in
<a href="settings-general.md"><img style="vertical-align: middle;" src="_static/icons/home/cog.png" width="20px"></a> [General Settings](settings-general.md) to skip this dialog.

<figure align="center" class="image">
  <img src="_static/images/fields/fields_import_joined.png" width="1100px"> 
  <figcaption><i>The Field import process</i></figcaption> 
</figure>

Selecting local will display a list of files in the `field_import`
directory. Only `.csv`, `.xls`, or `.xlsx` files will appear in the import
dialog. Filenames and column headers should exclude the following
special characters:

<figure align="center" class="image">
  <img src="_static/images/fields/fields_illegal_characters.png" width="250px"> 
  <figcaption><i>Characters that are not allowed in file and column
names</i></figcaption> 
</figure>

Files can be added to the import folder by downloading or transferring
from a computer, as described in <a href="#/storage">Storage</a>.

Once a file has been selected, the dropdown menus the dropdown menus
allow selection of the column names that correspond to Field Book's
required columns. Pressing **IMPORT** will finish importing the field.

#### Cloud storage

If you choose to import from cloud storage, Field Book will open the
device file manager allowing navigation to the the file for import.

<figure align="center" class="image">
  <img src="_static/images/fields/fields_cloud_import.png" width="325px"> 
  <figcaption><i>Navigating to a google drive file for cloud
import</i></figcaption> 
</figure>

Creating a field
----------------

<figure align="center" class="image">
  <img src="_static/images/fields/fields_create_joined.png" width="1100px"> 
  <figcaption><i>The Field creation process</i></figcaption> 
</figure>

To create a new field directly within Field Book press the <img ref="create" style="vertical-align: middle;" src="_static/icons/fields/table-large-plus.png" width="20px"> icon
on the toolbar. Set your field name and dimensions, choose which corner
of the field will contain the first plot, and select zigzag or
serpentine plot numbering. Unique IDs will be generated automatically.

Managing fields
---------------

To activate a field for data collection, select it from the list of
fields.

Each row in the fields list displays the Date imported, Date edited,
Date exported, and Number of entries.

<figure align="center" class="image">
  <img src="_static/images/fields/fields_sorting_example_joined.png" width="1100px"> 
  <figcaption><i>Field management sorting steps</i></figcaption> 
</figure>

Pressing the <img ref="menu" style="vertical-align: middle;" src="_static/icons/traits/dots-vertical.png" width="20px"> icon to the right of the trial info opens a list of options:

- **Sort** provides a dialog to update the plot ordering. Populate the sort menu by pressing the <img ref="plus" style="vertical-align: middle;" src="_static/icons/fields/plus.png" width="20px"> icon, and selecting from the list of imported columns. Once columns have been added, change their relative priority by using the <img ref="reorder" style="vertical-align: middle;" src="_static/icons/traits/reorder-horizontal.png" width="20px"> icon to drag and reorder them. Press the <img ref="sort" style="vertical-align: middle;" src="_static/icons/fields/sort-ascending.png" width="20px"> icon to toggle the sort between ascending and descending. Press the <img ref="delete" style="vertical-align: middle;" src="_static/icons/settings/sounds/delete.png" width="20px"> icon to remove a column.

- **Sync** connects to the server specified in <a href="settings-brapi.md"><img style="vertical-align: middle;" src="_static/icons/settings/main/server-network.png" width="20px"></a> [Brapi settings](settings-brapi.md), updating the trial with new data that has been added from other sources since the last sync or import. This option will not work for trials imported from a file.

- **Delete** removes the trial and all associated data. A confirmation message will be shown first, to confirm that deletion is not triggered accidentally.
