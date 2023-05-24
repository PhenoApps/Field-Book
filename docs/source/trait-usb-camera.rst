USB Camera Trait |camera|
=========================
Overview
--------

The usb camera trait format is used to capture images with an external camera. It is created with a trait name and optional details.

On the collect page, make sure the external camera is connected by usb. If it is not connected, the collect area will display a |connect| icon. Once connected, press the |camera| icon to access it and capture images. Multiple photos can be captured for each entry.

Photo files are named with the entry's unique_id, the trait name, the photo number, and a timestamp. They are stored as .jpg files within Field Book's **plot_data** directory and a field-specific subdirectory (plot_data/FIELD_NAME/picture/PHOTO_FILE_NAME.jpg).

Creation
--------

.. figure:: /_static/images/traits/formats/create_usb_camera.png
   :width: 40%
   :align: center
   :alt: USB camera trait creation fields

.. |camera| image:: /_static/icons/formats/webcam.png
  :width: 20

.. |connect| image:: /_static/icons/formats/connection.png
  :width: 20
