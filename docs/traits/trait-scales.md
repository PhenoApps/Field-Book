<link rel="stylesheet" type="text/css" href="../_styles/styles.css">

# <img width="64" height="64" src="../_static/icons/formats/scale.png"> Scale Trait

## Overview

The scale trait allows the user to connect to Bluetooth scales and collect weight data.

## Creation

#### Parameters
- `Name` assign a value for trait name, alias, and assigns synonyms list to hold the value.
- `Default` automatically assigns a specific value for the observation when visiting an entry having no observation recorded previously.
- `Details` text is displayed under the trait name on the Collect screen.
- `Automatically Switch to Next Plot` when enabled will immediately move to next entry when the user records an observation for an entry in the Collect screen.
- `Repeated Measures` turns on repeated measure for the trait when enabled.
- `Resource File` opens the resources folder and can be used to set a image for the trait that can be reference later in the Collect screen.

<figure class="image" style="text-align: center">
    <p>
      <img src="../_static/images/traits/formats/scale/create_scale.png" width="250" />
    </p>
</figure>

## Collect

Press the connect button to scan for compatible devices.
The desired device can be selected from the dialog that is displayed.

Once a device is connected, data is read from the scale and displayed in the UI.
Supported devices send data configured on the device, sometimes including unit of measure, such as pounds or kilograms.
Some scales also indicate weight stability which is displayed as an asterisk above the weight in the UI.

<figure class="image" style="text-align: center">
    <p>
      <img src="../_static/images/traits/formats/scale/collect_scale_unconnected.png" width="256"  alt=""/>
      <img src="../_static/images/traits/formats/scale/collect_scale_framed.png" width="256"  alt=""/>
    </p>
</figure>

## Compatible devices

### Crane Scale OCS-L

Low-cost Crane hanging scales with Bluetooth are compatible with this trait with no modifications necessary.

### A&D Check Weighing Bench Scales with Bluetooth

Deivces such as the SJ-6000WP-BT can be used with this trait but the settings must be adjusted to output data in `Stream` mode.

### Other devices

Other devices may be compatible with this trait but have not been tested. If you have a specific scale model you'd like to see supported, please contact the developers with more information.