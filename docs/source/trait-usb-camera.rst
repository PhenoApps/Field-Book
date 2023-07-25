|camera| USB Camera Trait
=========================
Overview
--------

The USB camera trait format is used to capture images with an external camera. It is created with a trait name and optional details.

On the collect screen, once a camera is connected via USB, it can be opened using |connect|. Once connected, press the |camera| icon to access it and capture images. Multiple photos can be captured for each entry.

Photo files are named with the entry's unique identifier, the trait name, the photo number, and a timestamp. They are stored as .jpg files in a field-specific subdirectory (``plot_data/FIELD_NAME/usb-camera/PHOTO_FILE_NAME.jpg``).

Creation
--------

.. figure:: /_static/images/traits/formats/create_usb_camera.png
   :width: 40%
   :align: center
   :alt: USB camera trait creation fields

Collect layout
--------------

.. figure:: /_static/images/traits/formats/collect_usb_camera_framed.png
   :width: 40%
   :align: center
   :alt: Usb camera trait collection

.. |camera| image:: /_static/icons/formats/webcam.png
  :width: 20

.. |connect| image:: /_static/icons/formats/connection.png
  :width: 20
