<link rel="stylesheet" type="text/css" href="../_styles/styles.css">

# <img class="icon-title" src="../_static/icons/formats/crosshairs-gps.png"> Location Trait

The location trait is used to record the GPS coordinates of the device.

### Parameters
- `Name` assign a value for trait name, alias, and assigns synonyms list to hold the value.
- `Details` text is displayed under the trait name on the Collect screen.
- `Automatically Switch to Next Plot` when enabled will immediately move to next entry when the user records an observation for an entry in the Collect screen.
- `Repeated Measures` turns on repeated measure for the trait when enabled.
- `Resource File` opens the resources folder and can be used to set a image for the trait that can be reference later in the Collect screen.

On the Collect screen, pressing the <img class="icon" src="_static/icons/formats/crosshairs-gps.png"> button will record the current latitude and longitude.
If the device is connected to an external location source, the coordinates from this source will be used instead of the device's internal GPS.

<figure class="image">
  <img class="screenshot" src="_static/images/traits/formats/joined_location_format.png" width="700px"> 
  <figcaption class="screenshot-caption"><i>Location trait creation dialog and collect format</i></figcaption> 
</figure>