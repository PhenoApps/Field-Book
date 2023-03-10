GNSS Trait (|gnss|) 
===================
Overview
--------

The GNSS trait format is used to acquire GPS coordinates from an external, bluetooth-connected device. It is created with a trait name and optional details.

On the collect screen, users should have external devices already paired through their operating system. The first step is to click the connect button, this shows a list of paired devices. The second step is to choose the device to listen to, this will automatically establish a connection and record a GPS reading.

This trait can be used in conjunction with the :doc:`geonav` (|geonav|) feature. Users may have to manually disconnect/reconnect if the socket cannot be established.

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

.. |geonav| image:: /_static/icons/settings/map-search.png
  :width: 20