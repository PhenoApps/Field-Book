BrAPI (Breeding API)
====================
Overview
--------
BrAPI is an application programming interface for plant breeding. It allows Field Book to directly communicate with a database to import fields and traits, and export collected data. This eliminates the need to manually transfer files, and enables Field Book to offer more sophisticated features including additional field and trait metadata, and partial dataset synching.

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

In the Fields screen, press the Add icon (|add|) in the upper right-hand corner and choose BrAPI as your source.
Fieldbook will import a list of possible fields from the BrAPI Base URL set in the :doc:`settings-brapi` (|brapi|).

You may filter the list of fields by any available groupings in the upper right toolbar menu (program, trial), or by observation level.

Once you select your desired field can can preview its metadata and import it. Importing your field via BrAPI is a prerequiste to later export your collected data via BRAPI.

Import traits
-------------
Just like with fields, when importing traits in the trait screen you can chose BrAPI as your source, then select from a list of traits retrieved from your BrAPI URL

Export data
-----------
Once data has been collect for a BrAPI imported field using BrAPI imported traits it can then be exported automatically via BrAPI. 

.. |brapi| image:: /_static/icons/settings/main/server-network.png
  :width: 20

.. |add| image:: /_static/icons/fields/plus-circle.png
  :width: 20

.. |settings| image:: /_static/icons/settings/main/server-network.png
  :width: 20