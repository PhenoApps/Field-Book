<img ref="photo" style="vertical-align: middle;" src="_static/icons/formats/camera.png" width="40px"> Photo Trait
=====================================================================

Overview
--------

The photo trait formats are used to capture images. The creation process starts by picking a device-specific format. The simplest option is to use the system camera, but external devices (<img ref="connect" style="vertical-align: middle;" src="_static/icons/formats/camera-gopro.png" width="20px"> GoPro, <img ref="connect" style="vertical-align: middle;" src="_static/icons/formats/webcam.png" width="20px"> USB, or <img ref="connect" style="vertical-align: middle;" src="_static/icons/formats/shutter.png" width="20px"> Canon) are also supported.

Creation
--------

<figure align="center" class="image">
  <img src="_static/images/traits/formats/create_photo_joined.png" width="700px"> 
  <figcaption><i>Photo trait creation (system camera)</i></figcaption> 
</figure>

On the collect page, pressing the <img ref="connect" style="vertical-align: middle;" src="_static/icons/formats/shutter.png" width="20px"> icon captures an image from the camera. Pressing the <img ref="connect" style="vertical-align: middle;" src="_static/icons/formats/cog.png" width="20px"> icon opens a settings dialog, where the resolution, preview, and capture options can be adjusted. Multiple photos can be captured for each entry. 

Collect layout
--------------

<figure align="center" class="image">
  <img src="_static/images/traits/formats/collect_photo_joined.png" width="700px"> 
  <figcaption><i>Photo trait collection interface and settings (system camera)</i></figcaption> 
</figure>

Captured photos are stored in `.jpg` format, and named by using underscores to join the entry's unique_id, the trait name, the photo number, and a timestamp. The resulting files are stored in a picture folder within a field-specific subfolder of `plot_data`. An example photo filepath would be `plot_data/FIELD_NAME/picture/PHOTO_FILE_NAME.jpg`.

External devices
----------------

The photo trait formats for capturing images from external devices work the same way as with the system camera, the only difference is the initial setup to connect to the device. Connect by pressing the <img ref="connect" style="vertical-align: middle;" src="_static/icons/formats/connection.png" width="20px"> icon when you first access the trait on the collect screen

<figure align="center" class="image">
  <img src="_static/images/traits/formats/collect_gopro_framed.png" width="350px"> 
  <figcaption><i>Photo trait collection interface (GoPro)</i></figcaption> 
</figure>