<link rel="stylesheet" type="text/css" href="../_styles/styles.css">

# <img class="icon-title" src="../_static/icons/formats/camera.png"> Photo Trait

## Overview

The photo trait formats are used to capture images.
Field Book allows photos to be captured from different camera sources including the device camera, <img class="icon" src="_static/icons/formats/webcam.png"> USB cameras, <img class="icon" src="_static/icons/formats/camera-gopro.png"> GoPro cameras, and <img class="icon" src="_static/icons/formats/shutter.png"> [compatible](https://developercommunity.usa.canon.com/s/article/CCAPI-Supported-Cameras) Canon cameras.

## Creation

#### Parameters
- `Name` assign a value for trait name.
- `Details` text is displayed under the trait name on the Collect screen.
- `Crop` when enabled, define a crop region through the <img class="icon" src="_static/icons/formats/cog.png"> in Collect to apply to all future pictures for the trait.
- `Automatically Switch to Next Plot` toggles immediately moving to next entry when the user records an observation for an entry in the Collect screen.
- `Resource File` sets an image for the trait that will be opened by default when accessing resources from Collect.

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

If crop region was enabled when creating the trait, the "Set Crop Region" option will be displayed in the settings and can be pressed to capture a photo and adjust cropping dimensions to be applied to all captured photos. 

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

#### Canon connection

Currently, Field Book is only compatible with Canon's that have a WiFi chip.

Open the connection choice on the camera, choose smartphone connect. Some cameras will have "Advanced connection" which may work as well. Choose the "Camera access point mode" for the network, this creates a WiFi access point on the camera.

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/canon/canon_smartphone_connect.png" width="700px" alt="Put the camera into smartphone connect / camera connect mode">
  <figcaption class="screenshot-caption"><i>Put the camera into smartphone connect / camera connect mode</i></figcaption>
</figure>

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/canon/canon_select_camera_access_point_mode_network.png" width="700px" alt="Select the camera's Access Point / Remote Control mode">
  <figcaption class="screenshot-caption"><i>Select the camera's Access Point / Remote Control mode</i></figcaption>
</figure>

When using a new camera for the first time with Field Book, choose the "Add a device to connect to" option; otherwise, you can choose the "Field Book" choice to connect immediately.

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/canon/canon_add_device_to_connect.png" width="700px" alt="Option to add or pair a device (camera) on some Canon models">
  <figcaption class="screenshot-caption"><i>Option to add or pair a device (camera) on some Canon models</i></figcaption>
</figure>

Choose to create a manual connection, and follow this setup:

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/canon/canon_manual_setup.png" width="700px" alt="Manual setup screen for SSID/IP/port if automatic discovery doesn't work">
  <figcaption class="screenshot-caption"><i>Manual setup screen for camera</i></figcaption>
</figure>

You must rename the SSID to "Canon" exactly.

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/canon/canon_rename_network.png" width="700px" alt="Some workflows show renaming the camera's network SSID">
  <figcaption class="screenshot-caption"><i>Rename the SSID to "Canon"</i></figcaption>
</figure>

Set the encryption to None.

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/canon/canon_encryption_settings.png" width="700px" alt="Encryption or security settings the camera may present">
  <figcaption class="screenshot-caption"><i>Encryption or security settings the camera may present</i></figcaption>
</figure>

Choose automatic for the channel option.

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/canon/canon_auto_channel.png" width="700px" alt="Auto channel selection on certain camera models">
  <figcaption class="screenshot-caption"><i>Auto channel selection on certain camera models</i></figcaption>
</figure>

Choose automatic for the IP option.

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/canon/canon_auto_ip_setting.png" width="700px" alt="Auto IP setting screen (defaults are IP = 192.168.1.2, port = 15740)">
  <figcaption class="screenshot-caption"><i>Auto IP setting screen</i></figcaption>
</figure>

Now the camera should show that it is waiting for Field Book to connect to its network. Use Field Book by clicking the connect button on the trait page.

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/canon/canon_wait_for_network.png" width="700px" alt="Device may show a waiting for network message while the app binds the network">
  <figcaption class="screenshot-caption"><i>Device may show a "waiting for network" or similar while the app binds the network</i></figcaption>
</figure>

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/canon/canon_fieldbook_connect_button.png" width="700px" alt="Tap Connect in the Photo trait UI to begin the connection flow">
  <figcaption class="screenshot-caption"><i>Tap Connect in the Photo trait UI to begin the connection flow</i></figcaption>
</figure>

Some phones may find the network immediately, other times you may have to close this dialog and press the connect button again on Field Book. Eventually, Field Book should show this dialog with your Canon's network name: Canon. Click the Canon network option on the Field Book screen.

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/canon/canon_fieldbook_select_network.png" width="700px" alt="Field Book finds and lists the camera SSID (default: 'Canon')">
  <figcaption class="screenshot-caption"><i>Field Book finds and lists the camera SSID (default: "Canon")</i></figcaption>
</figure>

The camera should now show an IP address, and your Field Book screen will update with a live view.

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/canon/canon_post_fieldbook_accept.png" width="700px" alt="Camera shows it is connected to the smartphone/app after acceptance">
  <figcaption class="screenshot-caption"><i>Camera shows it is connected to the smartphone/app after acceptance</i></figcaption>
</figure>

If this is your first time connecting the camera to Field Book, then you will need to accept this option on the camera as well.

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/canon/canon_accept_fieldbook.png" width="700px" alt="On the camera, accept the connection from Field Book if prompted">
  <figcaption class="screenshot-caption"><i>On the camera, accept the connection from Field Book if prompted</i></figcaption>
</figure>

### GoPro Cameras

GoPros can be used to capture images via Bluetooth.
Field Book can either copy the full image to the Android device or save the name of the image stored in the GoPro memory.
If photos are copied to the Android device, they are stored in `.jpg` format, and named with entry's unique_id, the trait name, the photo number, and a timestamp.
The resulting files are stored in a gopro folder within a field-specific subfolder of `plot_data`.
An example photo filepath would be `plot_data/FIELD_NAME/gopro/PHOTO_FILE_NAME.jpg`.