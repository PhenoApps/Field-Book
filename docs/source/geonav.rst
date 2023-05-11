GeoNav
======
Overview
--------

The GeoNav feature is used to automatically navigate between entries in the field based on the location of a high precision GNSS device that provides corrections using the . This feature requires field entries to have high-accuracy location data either collected via the GNSS trait (#todo link to GNSS trait) or included in the imported field file (#todo include details).

To use GeoNav, navigate to the :doc:`settings-geonav` (|geonav|) and enable GeoNav. Turn on bluetooth, and pair your external reciever. Navigate to the Collect screen and look for a confirmation message that says 'Rover communications estabished'

.. figure:: /_static/images/geonav/connect_cropped.png
   :width: 60%
   :align: center
   :alt: GeoNav Connection

   The collect screen GeoNav confirmation message

Once the rover is connected, Field Book will automatically display the nearest plot in the bottom toolbar. Press the arrow symbol on the right to jump to the plot. To enable automatic navigation, toggle the compass in the top toolbar. (#todo include toolbar icons and meanings)

.. figure:: /_static/images/geonav/goto_cropped.png
   :width: 60%
   :align: center
   :alt: GeoNav Interface

   The collect screen bottom toolbar with GeoNav on

Recommendations
---------------
This section provides specific hardware recommendations, as well as instructions for deploying the suggested devices to use the GeoNav feature in the field.

Supplies
~~~~~~~~
* Survey tripod
* Survey post (optional depending on rover case)
* Tablet
* RTK Hardware (Base):
  * Emlid RS2
* RTK Hardware (Rover)
  * Emlid RS2 OR
  * Emlid M2 + Antenna + cable + LoRa radio + microUSB cable + battery

Instructions (field use)
~~~~~~~~~~~~~~~~~~~~~~~~

1. Turn on BASE and ROVER, wait for both to broadcast WIFI hotspots
2. In TABLET settings, join ROVER WIFI.
3. In Reach3 app, navigate to ROVER WIFI settings. Connect ROVER to BASE WIFI.
4. Go back to TABLET settings, now connect TABLET to BASE WIFI
5. In Reach3 app, connect to BASE. Navigate to Base Settings. Wait until the BASE collects enough data to report a “single” position in the left hand corner and click apply.
6. In Reach3 app, connect to ROVER. Navigate to BT settings and turn on ROVER BT. Turn off and on again if you’ve forgotten the BT password (currently it is 1111).
7. Go back to TABLET BT settings. Turn on TABLET BT. Select ROVER from list of available devices and input password when prompted.
8. Finally, navigate to FIELDBOOK. Start collecting data. When prompted for GNSS trait, select ROVER.

Cases
~~~~~
3D Cases for M2 hardware are available for purchase online.

https://cults3d.com/en/3d-model/tool/emlid-reach-m2-case-estuche-gnss-gps-rtk


.. |geonav| image:: /_static/icons/settings/main/map-search.png
  :width: 20

