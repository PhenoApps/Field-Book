Traits
======
Overview
--------

Data is collected in Field Book by defining different traits which each have unique layouts. The traits screen allows new traits to be defined and existing traits to be managed. Each trait is created by specifying a trait format, a trait name, and additional fields such as ``min``, ``max``, or ``default``.

.. figure:: /_static/images/traits/traits_framed.png
   :width: 40%
   :align: center
   :alt: Traits layout with samples imported

   The Traits screen layout with sample data loaded.

Creating a Trait
----------------
Traits can be created by pressing the large Add icon (|add|) at the bottom right of the screen, or the same icon in the toolbar. For each format, the creation screen adjusts to indicate which fields are required. Trait names must be unique.

Managing Traits
---------------

Once created, traits can be manipulated on the trait screen using the following features:

  * Traits can be reordered by pressing and dragging the stacked line icon on the far left of each trait line.
  * Traits can be copied, edited, or deleted using the menu on each trait line.
  * Traits can be disabled from collection using the checkbox which will prevent them from showing up on the :doc:`collect` (|collect|) screen.

.. figure:: /_static/images/traits/traits_sort_framed.png
   :width: 40%
   :align: center
   :alt: Traits screen sort dialog

   Trait sorting options on the traits screen.

Traits can also be sorted by their name, format, or active status using the sort icon on the toolbar. To make all traits active or hidden, use the Double Check icon (|check-all|) on the toolbar.

Importing/Exporting Traits
--------------------------
Lists of traits can be created and transferred between different devices using the Import/Export option on the toolbar. Trait lists are stored as ``.trt`` files in the **trait** folder. Internally, ``.trt`` files store their data in a CSV format, but it is not recommended to manually edit these files.


.. |add| image:: /_static/icons/traits/plus-circle.png
  :width: 20

.. |collect| image:: /_static/icons/home/barley.png
  :width: 20

.. |check-all| image:: /_static/icons/traits/check-all.png
  :width: 20