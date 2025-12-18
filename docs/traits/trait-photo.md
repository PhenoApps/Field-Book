<link rel="stylesheet" type="text/css" href="_styles/styles.css">

# <img class="icon-title" src="../_static/icons/formats/camera.png"> Photo Trait

## Overview

The photo trait formats are used to capture images.
Field Book allows photos to be captured from different camera sources including the device camera, <img class="icon" src="_static/icons/formats/webcam.png"> USB cameras, <img class="icon" src="_static/icons/formats/camera-gopro.png"> GoPro cameras, and <img class="icon" src="_static/icons/formats/shutter.png"> [compatible](https://developercommunity.usa.canon.com/s/article/CCAPI-Supported-Cameras) Canon cameras.

## Creation

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/joined_create_photo_format.png" width="700px"> 
  <figcaption class="screenshot-caption"><i>Photo trait creation (system camera)</i></figcaption> 
</figure>

## Collect layout

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/collect_photo_joined.png" width="700px"> 
  <figcaption class="screenshot-caption"><i>Photo trait collection interface and settings (system camera)</i></figcaption> 
</figure>

On the Collect Screen, pressing the <img class="icon" src="_static/icons/formats/shutter.png"> icon captures an image from the camera.
Pressing the <img class="icon" src="_static/icons/formats/cog.png"> icon opens a settings dialog, where the resolution, preview, and other options can be adjusted.

If crop region was set to true when creating the trait then the "Set Crop Region" option will show up in the settings.
Press it to take a photo and adjust the crop dimensions that will automatically be applied to captured photos. 

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/crop_region_joined.png" width="1100px"> 
  <figcaption class="screenshot-caption"><i>Setting a photo trait's crop region</i></figcaption> 
</figure>

Multiple photos can be captured for each entry. 

Captured photos are stored in `.jpg` format, and named by using underscores to join the entry's `unique id`, the trait name, the photo number, and a timestamp.
The resulting files are stored in a picture folder within a field-specific subfolder of `plot_data`.
An example photo filepath would be `plot_data/FIELD_NAME/picture/PHOTO_FILE_NAME.jpg`.

## External devices

Capturing photos using external cameras requires initial connection setup using the <img class="icon" src="_static/icons/formats/connection.png"> icon.
The layout and process of capturing images from external cameras is the same as with the the system camera.

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/collect_gopro_framed.png" width="350px"> 
  <figcaption class="screenshot-caption"><i>Photo trait collection interface (GoPro)</i></figcaption> 
</figure>

### GoPro Cameras

GoPros can be used to capture images via Bluetooth.
Field Book can either copy the full image to the Android device or save the name of the image stored in the GoPro memory.
If photos are copied to the Android device, they are stored in `.jpg` format, and named with entry's unique_id, the trait name, the photo number, and a timestamp.
The resulting files are stored in a gopro folder within a field-specific subfolder of `plot_data`.
An example photo filepath would be `plot_data/FIELD_NAME/gopro/PHOTO_FILE_NAME.jpg`.