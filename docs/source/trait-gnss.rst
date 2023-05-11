GNSS Trait |gnss|
=================
Overview
--------

The GNSS trait format is used to acquire GPS coordinates from an external, bluetooth-connected device. It is created with a trait name and optional details. To use this trait, an external GNSS device must first be paired with the Android phone or tablet.

Pressing the connect button will show a list of devices that can be accessed to provide a location for this trait. Selecting a device will establish a connection. Pressing the capture button will record a GPS reading. Incoming location data can be averaged for 5s, 10s, or manually- whereby all manually collected location points are averaged to ascertain a representative value.

While other traits only capture phenotypic or observational data, the GNSS trait is intended to be used to capture metadata about the plot itself. This metadata can be used in conjunction with the :doc:`geonav` (|geonav|) feature. Users may have to manually disconnect/reconnect if the socket cannot be established.

Creation
--------

.. figure:: /_static/images/traits/formats/create_gnss.png
   :width: 40%
   :align: center
   :alt: GNSS trait creation fields

Collect layout
--------------

.. figure:: /_static/images/traits/formats/collect_gnss_framed.png
   :width: 40%
   :align: center
   :alt: GNSS trait collection

.. |gnss| image:: /_static/icons/formats/satellite-variant.png
  :width: 20

.. |geonav| image:: /_static/icons/settings/main/map-search.png
  :width: 20