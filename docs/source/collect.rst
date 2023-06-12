Collect
=======
Overview
--------

Field Book aims to increase the rate at which data can be collected by tailoring the input screen to fit the exact task. Only a single entry and trait are visible at a time during collection. This reduces the risk of error and allows trait-specific layouts to be used for data input.

The small green arrows are used to navigate between traits. The large black arrows are used to navigate between entries. Data is entered in the bottom area of the screen using a layout determined by the current trait. Data is saved to an internal database as it is collected.

.. figure:: /_static/images/collect/collect_framed.png
   :width: 40%
   :align: center
   :alt: Collect layout

   Data collection screen

Collect Screen Details
----------------------

Top toolbar
~~~~~~~~~~~

By default there are four buttons at the top of the screen (in addtion to the back navigation arrow).

* |search| Search opens a dialog to search for a specific entry.

  .. figure:: /_static/images/collect/collect_search_dialog.png
   :width: 60%
   :align: center
   :alt: Collect Search Dialog

   The collect screen search tool

The search dialog provides a flexible interface for finding a specific entry within the current field. Select which imported data field to search by, what strategy to use to find a match, and enter a search string. Press Add to construct a complex search with an additional field and search string, or press OK to execute the search.

* |resources| Resources opens the ``resources`` directory and can be used to load reference images.
* |summary| Summary opens a dialog that displays all info for the current entry.

.. figure:: /_static/images/collect/collect_summary_screen.png
   :width: 40%
   :align: center
   :alt: Collect Summary

   The collect screen summary tool

Summary shows detailed information for the current entry. Arrows at the bottom navigate forwards or backwards to other entries. By default the summary shows all of the imported data fields from the field file, but none of the collected trait values. Pressing the edit icon in the top toolbar opens a dialog to customize which data fields and traits are shown. Selecting a trait from the summary screen navigates to that trait.

.. figure:: /_static/images/collect/collect_summary_edit.png
   :width: 40%
   :align: center
   :alt: Summary Tool Customization

   Customizing the summary display

* |unlocked| Locking adds restrictions about data input section to prevent accidental changes. Pressing this icon multiple times will cycle through three states:
  
  * |unlocked| Unlocked is the default, unfrozen state that allows trait values to be entered, edited, or deleted.
  * |locked| freezes the collect input so no values can be entered, modified, or deleted.
  * |partial| freezes existing data but allows entry of new values.

More features and tools can be added to the toolbar in the |settings| :doc:`settings-general`. Default icons can be removed from the toolbar in the :doc:`settings-appearance`.

InfoBars
~~~~~~~~

.. figure:: /_static/images/collect/collect_infobars_section.png
   :width: 60%
   :align: center
   :alt: Collect InfoBars

   The collect screen InfoBar section

InfoBars display information about the current plot. InfoBar prefixes can be pressed to adjust which data field is displayed.

.. figure:: /_static/images/collect/collect_infobar_menu_framed.png
   :width: 40%
   :align: center
   :alt: InfoBars dropdown

   Selecting which data field is shown in the InfoBars

Trait navigation
~~~~~~~~~~~~~~~~

.. figure:: /_static/images/collect/collect_trait_navigation_section.png
   :width: 60%
   :align: center
   :alt: Collect trait arrows

   The collect screen trait navigation section

The small, green arrows are used to move between the different traits that are currently active. Pressing the current trait will show a dropdown of all currently active traits.

.. figure:: /_static/images/collect/collect_trait_menu_framed.png
   :width: 40%
   :align: center
   :alt: Trait dropdown

   Pressing the active trait to see the trait dropdown

Entry navigation
~~~~~~~~~~~~~~~~

.. figure:: /_static/images/collect/collect_entry_navigation_section.png
   :width: 60%
   :align: center
   :alt: Collect entry arrows

   The collect screen entry navigation section

The large, black arrows navigate between different entries. Pressing and holding these arrows will continuously scroll. The longer the arrows are pressed, the faster the scrolling becomes.

Data input
~~~~~~~~~~
The bottom half of the screen is used to input data. The elements and layout of this area change based on the trait that is currently active. Information for each specific trait format can be found in the Trait Formats pages.

Bottom toolbar
~~~~~~~~~~~~~~
The bottom toolbar contains three buttons for data input:

* |scan| enters data by scanning a barcode.
* |na| enters NA for when a phenotype is not available.
* |delete| deletes the entered data.


.. |search| image:: /_static/icons/collect/magnify.png
  :width: 20

.. |resources| image:: /_static/icons/collect/folder-star.png
  :width: 20

.. |summary| image:: /_static/icons/collect/file-document.png
  :width: 20

.. |unlocked| image:: /_static/icons/collect/lock-open-outline.png
  :width: 20

.. |locked| image:: /_static/icons/collect/lock.png
  :width: 20

.. |partial| image:: /_static/icons/collect/lock-clock.png
  :width: 20

.. |settings| image:: /_static/icons/settings/main/cog-outline.png
  :width: 20

.. |scan| image:: /_static/icons/collect/barcode-scan.png
  :width: 20

.. |na| image:: /_static/icons/collect/not-applicable.png
  :width: 20

.. |delete| image:: /_static/icons/collect/delete-outline.png
  :width: 20