GNSS Trait |gnss|
=================
Overview
--------

The GNSS trait format is used to acquire high-accuracy GPS coordinates from an external, bluetooth-connected device. While other traits only capture phenotypic or observational data, the GNSS trait is intended to be used to capture metadata about the plot itself. This metadata can be used in conjunction with the :doc:`geonav` (|geonav|) feature to later use the plot metadata to automatically navigate through the field.


Creationlayout
--------

.. figure:: /_static/images/traits/formats/create_gnss.png
   :width: 40%
   :align: center
   :alt: GNSS trait creation fields


It is created with a trait name and optional details.


Collect layout
--------------

.. figure:: /_static/images/traits/formats/collect_gnss_framed.png
   :width: 40%
   :align: center
   :alt: GNSS trait collection


When first navigating to a GNSS trait, the collect screen will show a |gnss| button.

.. figure:: /_static/images/traits/formats/collect_gnss_button.png
   :width: 60%
   :align: center
   :alt: GNSS connect button

   GNSS connect button

Pressing the |gnss| button will show a list of devices that can be accessed to provide a location for this trait.

.. figure:: /_static/images/traits/formats/collect_gnss_device_select.png
   :width: 60%
   :align: center
   :alt: GNSS device select

   GNSS device select

Once a device is selected the screen will populate with a series of values from the GNSS reciever output. This includes the high-accuracy GPS Lat and Long coordinates, as well as the time of day in Coordinated Universal Time (UCT), the Horizontal Dilution of Precision (HDOP, a measure of the suitability of satellite positioning in the sky, ideally 1 or below), the number of satellites connected to, and the altitude and accuracy.

.. figure:: /_static/images/traits/formats/collect_reciever_output.png
   :width: 60%
   :align: center
   :alt: GNSS reciever output

   GNSS reciever output

Pressing the |capture| button will record an instantaneous GPS reading. Pressing the average toggle will display options to instead record an average of incoming location data for 5s, 10s, or manually (whereby all manually collected location points are averaged to create a representative value).

.. figure:: /_static/images/traits/formats/collect_gnss_average_options.png
   :width: 60%
   :align: center
   :alt: GNSS average options

   GNSS average options

When recording data for an entry the already has coordinates collected, a warning message will be displayed to confirm that the existing coordinates should be updated.

.. figure:: /_static/images/traits/formats/collect_gnss_update_warning.png
   :width: 60%
   :align: center
   :alt: GNSS update warning

   GNSS update warning

If errors occur while collecting gnss data (e.g. socket cannot be established), users may have to manually disconnect/reconnect to the external device.

.. |gnss| image:: /_static/icons/formats/satellite-variant.png
  :width: 20

.. |geonav| image:: /_static/icons/settings/main/map-search.png
  :width: 20

.. |capture| image:: /_static/icons/formats/crosshairs.png
  :width: 20