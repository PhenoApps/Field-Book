<img ref="usb_camera" style="vertical-align: middle;" src="_static/icons/formats/webcam.png" width="40px"> USB Camera Trait
===========================================================================

Overview
--------

The USB camera trait format is used to capture images with an external
camera. It is created with a trait name and optional details.

On the collect screen, once a camera is connected via USB, it can be
opened using
<img ref="connect" style="vertical-align: middle;" src="_static/icons/formats/connection.png" width="20px">. Once
connected, press the
<img ref="usb_camera" style="vertical-align: middle;" src="_static/icons/formats/webcam.png" width="20px"> icon to
access it and capture images. Multiple photos can be captured for each
entry.

Captured photos are stored in `.jpg` format, and named by using underscores to join the entry's unique_id, the trait name, the photo number, and a timestamp. The resulting files are stored in a usb-camera directory within a field-specific subdirectory of `plot_data`. An example photo filepath would be `plot_data/FIELD_NAME/usb-camera/PHOTO_FILE_NAME.jpg`.

Creation
--------

<figure align="center" class="image">
  <img src="_static/images/traits/formats/create_usb_camera.png" width="325px"> 
  <figcaption><i>USB Camera trait creation dialog</i></figcaption> 
</figure>

Collect layout
--------------

<figure align="center" class="image">
  <img src="_static/images/traits/formats/collect_usb_camera_framed.png" width="350px"> 
  <figcaption><i>USB Camera trait collection interface</i></figcaption> 
</figure>
