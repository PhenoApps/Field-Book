BrAPI (Breeding API)
====================
Overview
--------
BrAPI is an application programming interface for plant breeding. It allows Field Book to directly communicate with a database to import fields and traits, and export collected data. This eliminates the need to manually transfer files, and enables Field Book to offer more sophisticated features including additional field and trait metadata, and partial dataset syncing.

Setup
-----
To use BrAPI, first set the base URL to that of a valid BrAPI server, then authorize it. This setup plus a whole lot more optional configuration can be done in :doc:`settings-brapi` (|settings|). 

.. figure:: /_static/images/brapi/brapi_url_joined.png
   :width: 60%
   :align: center
   :alt: Brapi url authorization

   Example Brapi url authorization.

Import fields
-------------

.. figure:: /_static/images/brapi/brapi_field_import_joined.png
   :width: 100%
   :align: center
   :alt: BrAPI field import screen

   The BrAPI field import process.

In the Fields screen, press Add (|add|) on the top toolbar and choose BrAPI as your source.
Fieldbook will import a list of possible fields from the BrAPI Base URL set in the :doc:`settings-brapi` (|brapi|).

The field list can be filtered by the program dn trial using the menu options in the top toolbar. The list can be filtered by ``Observation Level`` using the dropdown below the server URL.

Once a field has been selected, the field structure can be previewed and imported. Only fields that have been imported via BrAPI can be exported to BrAPI servers.

Import traits
-------------
Depending on the BrAPI server, fields may have linked traits that are automatically imported with the field. However, additional traits can be imported via BrAPI by selecting import from the Traits menu and selecting specific traits to import.

Export data
-----------
Once data has been collected it can then be exported via BrAPI be selecting BrAPI from the 

.. |brapi| image:: /_static/icons/settings/main/server-network.png
  :width: 20

.. |add| image:: /_static/icons/fields/plus-circle.png
  :width: 20

.. |settings| image:: /_static/icons/settings/main/server-network.png
  :width: 20