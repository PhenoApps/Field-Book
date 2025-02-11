<img ref="GNSS" style="vertical-align: middle;" src="_static/icons/formats/satellite-variant.png" width="40px"> GNSS Trait
==============================================================================

Overview
--------

The GNSS trait format is used to acquire high-accuracy GPS coordinates
from an external, Bluetooth-connected device. While other traits only
capture phenotypic or observational data, the GNSS trait is intended to
be used to capture metadata about the plot itself. This metadata can be
used in conjunction with the
<a href="geonav.md"><img style="vertical-align: middle;" src="_static/icons/settings/main/map-search.png" width="20px"></a> [Geonav](geonav.md) feature to automatically navigate
through the field.

<figure align="center" class="image">
  <img src="_static/images/traits/formats/gnss_format_joined.png" width="700px"> 
  <figcaption><i>GNSS trait creation dialog and collect format</i></figcaption> 
</figure>

Details
-------

When first navigating to a GNSS trait, the collect screen will show a
<img ref="gnss" style="vertical-align: middle;" src="_static/icons/formats/satellite-variant.png" width="20px">
button that will show a list of devices that can be accessed to provide
a location for this trait.

<figure align="center" class="image">
  <img src="_static/images/traits/formats/collect_gnss_select_device.png" width="325px"> 
  <figcaption><i>GNSS device select dialog</i></figcaption> 
</figure>

Once a device is selected the screen will populate with a series of
values from the GNSS receiver output including high-accuracy latitude
and longitude coordinates, current Coordinated Universal Time (UCT), the
Horizontal Dilution of Precision (HDOP, a measure of the suitability of
satellite positioning in the sky, ideally 1 or below), the number of
available satellites, the altitude, and accuracy.

<figure align="center" class="image">
  <img src="_static/images/traits/formats/collect_gnss_receiver_output.png" width="325px"> 
  <figcaption><i>GNSS receiver output</i></figcaption> 
</figure>

Pressing
<img ref="capture" style="vertical-align: middle;" src="_static/icons/formats/crosshairs-gps.png" width="20px"> will record an instantaneous GPS reading. Toggling the average option will
record an average of incoming location data for 5s, 10s, or manually
(whereby all manually collected location points are averaged to create a
representative value).

<figure align="center" class="image">
  <img src="_static/images/traits/formats/collect_gnss_average_options.png" width="325px"> 
  <figcaption><i>GNSS average options</i></figcaption> 
</figure>

When recording data for an entry with existing coordinates, a warning
message will be displayed to confirm that the existing coordinates will
be updated.

<figure align="center" class="image">
  <img src="_static/images/traits/formats/collect_gnss_update_warning.png" width="325px"> 
  <figcaption><i>GNSS update warning</i></figcaption> 
</figure>

If errors occur while collecting gnss data (e.g., socket cannot be
established), users may have to manually disconnect/reconnect to the
external device.
