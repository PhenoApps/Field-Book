<img ref="gopro" style="vertical-align: middle;" src="_static/icons/formats/camera-gopro.png" width="40px"> GoPro Trait
===========================================================================

Overview
--------

The GoPro trait format is used to capture images with an external,
Bluetooth-connected GoPro camera. It is created with a trait name and
optional details.

On the collect screen, once a GoPro is connected via Bluetooth, it can
be opened using the
<img ref="connect" style="vertical-align: middle;" src="_static/icons/formats/connection.png" width="20px"> button. Once connected, press the
<img ref="gopro" style="vertical-align: middle;" src="_static/icons/formats/camera-gopro.png" width="20px"> icon to access it and capture images. Multiple photos can be captured for each
entry.

Captured photos are stored in `.jpg` format, and named by using underscores to join the entry's unique_id, the trait name, the photo number, and a timestamp. The resulting files are stored in a gopro folder within a field-specific subfolder of `plot_data`. An example photo filepath would be `plot_data/FIELD_NAME/gopro/PHOTO_FILE_NAME.jpg`.

Creation
--------

<figure align="center" class="image">
  <img src="_static/images/traits/formats/create_gopro.png" width="325px"> 
  <figcaption><i>Gopro trait creation dialog</i></figcaption> 
</figure>

Collect layout
--------------

<figure align="center" class="image">
  <img src="_static/images/traits/formats/collect_gopro_framed.png" width="350px"> 
  <figcaption><i>Gopro trait collection interface</i></figcaption> 
</figure>
