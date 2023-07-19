Beta Settings
=============

.. figure:: /_static/images/settings/beta/settings_beta_framed.png
   :width: 40%
   :align: center
   :alt: Beta settings screen layout

   Beta settings screen layout

|flask| Repeated Measures 
~~~~~~~~~~~~~~~~~~~~~~~~~~

Turns on repeated measures. When turned on, a green plus symbol appears next to the trait value entry box on the collect screen.

.. figure:: /_static/images/settings/beta/settings_beta_repeated_icon.png
   :width: 60%
   :align: center
   :alt: Repeated measurement example collect screen

   Collect screen value entry with repeated measurements enabled

When pressed it creates a new entry field for collecting an additional observation on the same plot for the same trait.

To export data that includes repeated measures make sure to chose the **Database** format or to use BrAPI. These formats allow repeated measures to be differentiated by timestamp. If exporting in **Table** format then only the latest measurement will be included.

.. |flask| image:: /_static/icons/settings/beta/flask-outline.png
  :width: 20
