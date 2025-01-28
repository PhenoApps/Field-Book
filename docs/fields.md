Fields
======

Overview
--------

Experiments are represented in Field Book as `fields`. Fields are either
imported from a file, from a BrAPI-enabled database, or created from
scratch.

Each field is displayed in the list with an icon that matches its import source, its name, and its number of `entries` which represent the experimental units on which data will be collected (e.g., plots, plants). The icon can be pressed to set the field as the active field. Any other part of the field item can be pressed to open the field detail view.

<figure align="center" class="image">
  <img src="_static/images/fields/fields_list_item.png" width="325px"> 
  <figcaption><i>A sample field with a name, number of
entries, and csv import icon</i></figcaption> 
</figure>

When importing from a file, each row in the file reprents an `entry`.
Each entry within a field must have the following:

- A `unique identifier`, which is used internally by Field Book to associate data with the specific entry. It must be unique across all of your fields. The sample field import file shown below contains a unique identifier called **plot_id** (highlighted in red).
- A `primary identifier`, and a `secondary identifier`. These remain visible while advancing through the field's entries, and can be whatever makes the most sense for your experiment. The optional Quick GoTo setting makes them editable, so it can be advantageous to make one of them the plot number if you plan to use it. That way a plot number edit can be used to jump to a specific plot. Common choices are row/plot, range/plot, rep/plot, etc. The sample field import file contains primary and secondary unique identifiers called **row** and **plot** (highlighted in blue).

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

Adding a field
--------------

To add a new field in Field Book press the floating <img ref="add" style="vertical-align: middle;" src="_static/icons/fields/plus-circle.png" width="20px"> button in the bottom righthand corner of the Fields section. Then, in
the resulting dialog, select whether to import from a local file, from
cloud storage (Dropbox, Google Drive, etc.), create a new field from scratch, or import via a <a href="brapi.md"><img style="vertical-align: middle;" src="_static/icons/settings/main/server-network.png" width="20px"></a> [Brapi](brapi.md) connection (if BrAPI is enabled).

A default import source can be set in
<a href="settings-system.md"><img style="vertical-align: middle;" src="_static/icons/settings/main/cog-outline.png" width="20px"></a> [System Settings](settings-system.md) to skip this dialog.

<figure align="center" class="image">
  <img src="_static/images/fields/fields_import_joined.png" width="1100px"> 
  <figcaption><i>The Field import process from local storage</i></figcaption>
</figure>

#### Local storage

Selecting local will display a list of files in the `field_import`
folder. Only `.csv`, `.xls`, or `.xlsx` files will appear in the import
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
device file manager.

<figure align="center" class="image">
  <img src="_static/images/fields/cloud_import_joined.png" width="700px"> 
  <figcaption><i>Cloud import file manager view</i></figcaption> 
</figure>

Using the file manager you can select a file anywhere on the device, including from cloud utilities like Google Drive (Press the <img ref="add" style="vertical-align: middle;" src="_static/icons/fields/menu.png" width="20px"> icon in the upper left of the file manager to access the "Open from" menu ).

#### Create New

If you choose to create a new field directly within Field Book you will be asked to fill out some basic information.

<figure align="center" class="image">
  <img src="_static/images/fields/fields_create_joined.png" width="1100px"> 
  <figcaption><i>The field creation process</i></figcaption> 
</figure>

Set your field name and dimensions, choose which corner of the field will contain the first plot, and select zigzag or
serpentine plot numbering. Unique IDs will be generated automatically. Confirm the planned settings are as expected, then press OK.

#### BrAPI

To import a field using BrAPI, first make sure BrAPI is enabled and configured in the <a href="settings-brapi.md"><img style="vertical-align: middle;" src="_static/icons/settings/main/server-network.png" width="20px"></a> [Brapi settings](settings-brapi.md)

Then the BrAPI Display Name of the server you connected to will show up as one of the choices in the add field options. Check out the <a href="brapi.md"></a> [BrAPI](brapi.md) section of the documentation for details of the field import process, as well as trait import, sync, and data export.

!> Any field can be exported locally, but only fields that have been imported via BrAPI are able to export data via BrAPI. And only if that data is also collected using BrAPI-imported traits.

Managing fields
---------------

To set or switch your active field, press the import source icon on the left side of the field item.

To use your location to set the active field, press the <img ref="add" style="vertical-align: middle;" src="_static/icons/fields/compass-outline.png" width="20px"> icon in the toolbar. This will set the nearest field as the active field (assuming your fields have location info). 

To reorder or sort the fields, press the <img ref="add" style="vertical-align: middle;" src="_static/icons/fields/sort.png" width="20px"> icon in the toolbar. Then choose which field attribute should be used to sort the list.

<figure align="center" class="image">
  <img src="_static/images/fields/fields_sort_framed.png" width="400px"> 
  <figcaption><i>Field list sort options</i></figcaption> 
</figure>

For batch operations, long press one or more field items. This opens the action menu. Use the action menu icons to select all, export selected fields, or delete selected fields.

<figure align="center" class="image">
  <img src="_static/images/fields/fields_delete_framed.png" width="400px"> 
  <figcaption><i>Delete fields confirmation</i></figcaption> 
</figure>

<img ref="add" style="vertical-align: middle;" src="_static/icons/fields/check-all.png" width="25px"> selects all of the fields in the list.
  
<img ref="add" style="vertical-align: middle;" src="_static/icons/fields/file-export-outline.png" width="25px"> initiates a data export for all selected fields.

<img ref="add" style="vertical-align: middle;" src="_static/icons/fields/delete.png" width="25px"> Deletes all selected fields. A confirmation message will be shown first, to confirm the list of fields to be deleted.

Field details
-------------

Pressing a field item in the fields list opens a detail view for the field.

<figure align="center" class="image">
  <img src="_static/images/fields/field_detail_framed.png" width="400px"> 
  <figcaption><i>Field detail view</i></figcaption> 
</figure>

The **<img ref="add" style="vertical-align: middle;" src="_static/icons/fields/delete.png" width="20px">** in the toolbar can be used to delete the field. A confirmation message will be shown first, to confirm that deletion is not triggered accidentally.

The first collapsible section includes metadata about the field (import source, entry count, attribute count), and buttons that can be used to rename the field or sort the entries.

  - **Rename** is used to edit the name displayed for the field throughout the app.

  - **Sort** provides a dialog to update the entry ordering. Populate the sort menu by pressing the <img ref="plus" style="vertical-align: middle;" src="_static/icons/fields/plus.png" width="20px"> icon, and selecting from the list of imported columns. Once columns have been added, change their relative priority by using the <img ref="reorder" style="vertical-align: middle;" src="_static/icons/traits/reorder-horizontal.png" width="20px"> icon to drag and reorder them. Press the <img ref="sort" style="vertical-align: middle;" src="_static/icons/fields/sort.png" width="20px"> icon to toggle the sort between ascending and descending. Press the <img ref="delete" style="vertical-align: middle;" src="_static/icons/settings/sounds/delete.png" width="20px"> icon to remove a column.

The following sections can be pressed to <a href="collect.md"><img style="vertical-align: middle;" src="_static/icons/home/barley.png" width="20px"></a>[Collect](collect.md) data for the field, <a href="export.md"><img style="vertical-align: middle;" src="_static/icons/home/save.png" width="20px"></a> [Export](export.md) the field's data, and (if the field was imported via BrAPI) <a href="brapi.md"><img style="vertical-align: middle;" src="_static/icons/settings/main/server-network.png.png" width="20px"></a> [Brapi](brapi.md) the field's data by pulling in any new observations.

The final collapsible section summarizes that data that has been collected for the field so far. It shows an overall trait and observation count, and then for each trait a collapsible card with the observation count for the trait, the percentage of entries with observations of the trait, and, if applicable for the trait type, a chart showing the distribution of observations.


