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

Managing Traits
---------------
Once created, traits can be manipulated using the following features:

  * Traits can be reordered by pressing and dragging the stacked line icon on the far left of each trait line.
  * Traits can be copied, edited, or deleted using the menu on each trait line.
  * Traits can be hidden from the |collect| :doc:`collect` screen using the checkbox.

.. figure:: /_static/images/traits/traits_sort_framed.png
   :width: 40%
   :align: center
   :alt: Traits screen sort dialog

   Trait sorting options on the traits screen.

Traits can also be sorted by their name, format, or active status using the menu icon on the toolbar, and selecting sort. To make all traits active or hidden, press the |check-all| double check icon in the toolbar.

Importing/Exporting Traits
--------------------------
Lists of traits can be created and transferred between different devices using the Import/Export option on the toolbar. Trait lists are stored as ``.trt`` files in the ``trait`` folder. Internally, ``.trt`` files store their data in a CSV format, but it is not recommended to manually edit these files.


.. |add| image:: /_static/icons/traits/plus-circle.png
  :width: 20

.. |collect| image:: /_static/icons/home/barley.png
  :width: 20

.. |check-all| image:: /_static/icons/traits/check-all.png
  :width: 20