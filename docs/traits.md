Traits
======

Overview
--------

Data is collected in Field Book by defining different traits. Each trait
layout is optimized for a specific type of data collection. The traits
screen allows new traits to be defined and existing traits to be
managed.

<figure align="center" class="image">
  <img src="/_static/images/traits/traits_framed.png" width="350px"> 
  <figcaption><i>The Traits screen layout with sample data
loaded</i></figcaption> 
</figure>

Creating a Trait
----------------

Traits can be created by pressing the large <img ref="add" style="vertical-align: middle;" src="/_static/icons/traits/plus-circle.png" width="20px"> icon at the
bottom right of the screen, or the same icon in the toolbar. For each
format, the creation screen adjusts to indicate which fields are
required. Trait names must be unique. Each trait is created by
specifying a trait `format`, a trait `name`, optional `details`, and
format-dependent fields such as `min`, `max`, and `default`.

<figure align="center" class="image">
  <img src="/_static/images/traits/traits_create_joined.png" width="700px"> 
  <figcaption><i>Trait creation for a <a href="#/trait-numeric">Numeric</a> trait, and other format options</i></figcaption> 
</figure>

Managing Traits
---------------

Once created, traits can be manipulated using the following features:

#### Single trait changes

- **Reorder** an individual trait by pressing and dragging the <img ref="reorder" style="vertical-align: middle;" src="/_static/icons/traits/reorder-horizontal.png" width="20px"> icon on the far left of its trait line.

- **Copy**, **Edit**, or **Delete** an individual trait by pressing the <img ref="menu" style="vertical-align: middle;" src="/_static/icons/traits/dots-vertical.png" width="20px"> icon on its trait line, then selecting the desired operation from a list.

- Set an individual trait to be **Visible** or **Invisible** on the <a href="collect.md"><img style="vertical-align: middle;" src="/_static/icons/home/barley.png" width="20px"></a>[Collect](collect.md) screen by checking/unchecking the checkbox on each trait line.

<figure align="center" class="image">
  <img src="/_static/images/traits/single_trait_menu_framed.png" width="350px"> 
  <figcaption><i>Single trait management menu</i></figcaption> 
</figure>

#### All trait changes

- Make all traits **Visible** or **Invisible** by pressing the <img ref="check-all" style="vertical-align: middle;" src="/_static/icons/traits/check-all.png" width="20px"> icon in the toolbar.

- Open the trait **Menu** using the <img ref="menu" style="vertical-align: middle;" src="/_static/icons/traits/dots-vertical.png" width="20px"> icon on right side of the toolbar to access additional operations affecting all traits.

- Within the menu, select **Sort** to reorder all traits according to one of the sort criterion (options include trait `Name`, `Format`, or `Visibility`)
  
- Select **Delete all traits**, then confirm, to remove every trait in the list.
  
- Select **Import/Export** to load new traits into Field Book and/or to save the current traits to a file.

<figure align="center" class="image">
  <img src="/_static/images/traits/traits_menu_framed.png" width="350px"> 
  <figcaption><i>All traits mangement menu</i></figcaption> 
</figure>

Trait imports and exports are similar to field imports/exports in that
they rely on files stored in a dedicated folder, or on communication
with a designated server using a <a href="brapi.md"><img style="vertical-align: middle;" src="/_static/icons/settings/main/server-network.png" width="20px"></a> [Brapi](brapi.md) connection.

?> When using local storage, trait lists are stored as `.trt` files in the
`trait` folder. Technically `.trt` files are just `.csv` files with the extension renamed; exported `.trt` files can be opened as `.csv` if desired. However it is not recommended to manually edit and reimport these files, trait edits are best done within the application.
