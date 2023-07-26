|gopro| GoPro Trait
=========================
Overview
--------

The GoPro trait format is used to capture images with an external, Bluetooth-connected GoPro camera. It is created with a trait name and optional details.

On the collect screen, once a GoPro is connected via Bluetooth, it can be opened using the |connect| button. Once connected, press the |gopro| icon to access it and capture images. Multiple photos can be captured for each entry.

Photo files are named with the entry's unique identifier, the trait name, the photo number, and a timestamp. They are stored as .jpg files in a field-specific subdirectory (``plot_data/FIELD_NAME/gopro/PHOTO_FILE_NAME.jpg``).

Creation
--------

.. figure:: /_static/images/traits/formats/create_gopro.png
   :width: 40%
   :align: center
   :alt: GoPro trait creation fields

Collect layout
--------------

.. figure:: /_static/images/traits/formats/collect_gopro_framed.png
   :width: 40%
   :align: center
   :alt: GoPro trait collection

.. |gopro| image:: /_static/icons/formats/camera-gopro.png
  :width: 20

.. |connect| image:: /_static/icons/formats/connection.png
  :width: 20
