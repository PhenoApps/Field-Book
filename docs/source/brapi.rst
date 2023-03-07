BrAPI (Breeding API)
====================

Setup
-----

To use BrAPI, first set a valid base URL and authorize it. This, and lots of optional configuration can be done in :doc:`settings-brapi` (|settings|). 

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
Once you select your desired field can can preview its metadata and import it.
Importing your field via BrAPI is a prerequiste to later export your collected data via BRAPI.

Import traits
-------------

Just like with fields, when importing traits in the trait screen you can chose BrAPI, then select from a list of traits retrieved from your BrAPI URL

Export data
-----------

Once data has been collect for a BrAPI imported field using BrAPI imported traits it can be exported automatically via BrAPI. 

.. |brapi| image:: /_static/icons/settings/main/server-network.png
  :width: 20
