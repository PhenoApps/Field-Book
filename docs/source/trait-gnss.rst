GNSS Trait
==========
Creation
--------

.. figure:: /_static/images/traits/formats/create_gnss.png
   :width: 40%
   :align: center
   :alt: GNSS trait creation fields

Layout
------

.. figure:: /_static/images/traits/formats/collect_gnss_framed.png
   :width: 40%
   :align: center
   :alt: GNSS trait layout

Description
-----------

A trait for acquiring GPS coordinates from an external, bluetooth-connected device. Users should have external devices already paired through their operating system.

First step is to click the connect button, this shows a list of paired devices. Second step is to choose the device to listen to, this will automatically establish a connection and begin listening for NMEA messages.

This trait can be used in conjunction with GeoNav (external only). User may have to manually disconnect/reconnect if the socket cannot be established.