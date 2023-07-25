Traits
======
Overview
--------

Data is collected in Field Book by defining different traits. Each trait layout is optimized for a specific type of data collection. The traits screen allows new traits to be defined and existing traits to be managed.

.. figure:: /_static/images/traits/traits_framed.png
   :width: 40%
   :align: center
   :alt: Traits layout with samples imported

   The Traits screen layout with sample data loaded.

Creating a Trait
----------------
Traits can be created by pressing the large |add| icon at the bottom right of the screen, or the same icon in the toolbar. For each format, the creation screen adjusts to indicate which fields are required. Trait names must be unique. Each trait is created by specifying a trait ``format``, a trait ``name``, optional ``details``, and format-dependent fields such as ``min``, ``max``, and ``default``.

.. figure:: /_static/images/traits/traits_create_joined.png
   :width: 80%
   :align: center
   :alt: Trait creation process

   Trait creation for a `numeric` trait, and other format options.

Managing Traits
---------------
Once created, traits can be manipulated using the following features:

Single trait changes
~~~~~~~~~~~~~~~~~~~~

  * Reorder an individual trait by pressing and dragging the |reorder| icon on the far left of its trait line.
  * Copy, edit, or delete an individual trait by pressing the |menu| icon on its trait line, then selecting the desired operation from a list.
  * Hide or unhide an individual trait from the |collect| :doc:`collect` screen by checking/unchecking the checkbox on each trait line.

.. figure:: /_static/images/traits/single_trait_menu_framed.png
   :width: 40%
   :align: center
   :alt: Traits screen single trait menu

   Single trait management menu.

All trait changes
~~~~~~~~~~~~~~~~~~

To make all traits active or hidden, press the |check-all| double check icon in the toolbar.

Other changes affecting the whole trait list can be made by accessing the trait menu using the |menu| icon on right side of the toolbar

.. figure:: /_static/images/traits/traits_menu_framed.png
   :width: 40%
   :align: center
   :alt: Traits screen all traits menu

   All traits mangement menu.

* Reorder all traits by selecting **Sort**, then chosing your sort criterion (options include trait `Name`, `Format`, or `Checked` status)
* Remove all traits by selecting **Delete all traits**, then confirming the operation.
* Transfer traits in and out by selecting the **Import/Export** option.

Trait imports and exports are similar to field imports/exports in that they rely on files stored in a dedicated folder, or on communication with a designated server using a |brapi| :doc:`brapi` connection.

When using local storage, trait lists are stored as ``.trt`` files in the ``trait`` folder. Internally, ``.trt`` files store their data in a CSV format, but it is not recommended to manually edit these files.

.. |add| image:: /_static/icons/traits/plus-circle.png
  :width: 20

.. |collect| image:: /_static/icons/home/barley.png
  :width: 20

.. |check-all| image:: /_static/icons/traits/check-all.png
  :width: 20

.. |menu| image:: /_static/icons/traits/dots-vertical.png
  :width: 20

.. |reorder| image:: /_static/icons/traits/reorder-horizontal.png
  :width: 20

.. |brapi| image:: /_static/icons/settings/main/server-network.png
  :width: 20