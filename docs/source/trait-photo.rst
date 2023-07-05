|photo| Photo Trait
===================
Overview
--------

The photo trait format is used to capture images. It is created with a trait name and optional details.

On the collect page, the camera icon opens the device camera to capture images. Multiple photos can be captured for each entry. Photo files are named with the entry's **unique_id**, the trait name, the photo number, and a timestamp. They are stored as .jpg files in a field-specific subdirectory (``plot_data/FIELD_NAME/picture/PHOTO_FILE_NAME.jpg``).

Creation
--------

.. figure:: /_static/images/traits/formats/create_photo.png
   :width: 40%
   :align: center
   :alt: Photo trait creation fields

Collect layout
--------------

.. figure:: /_static/images/traits/formats/collect_photo_framed.png
   :width: 40%
   :align: center
   :alt: Photo trait collection

.. |photo| image:: /_static/icons/formats/camera.png
  :width: 20
