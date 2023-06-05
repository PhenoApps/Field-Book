BrAPI (Breeding API)
====================
Overview
--------
BrAPI is an application programming interface for plant breeding. It allows Field Book to directly communicate with a database to import fields and traits, and export collected data. This eliminates the need to manually transfer files, and enables Field Book to offer more sophisticated features including additional field and trait metadata, and partial dataset syncing.

Setup
-----
To use BrAPI, first set the base URL to the URL of a valid BrAPI server, then authorize it. These setup steps, as well as many more optional settings are handled in |brapi| :doc:`settings-brapi`. 

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

In the Fields screen, press |add| icon in the top toolbar and choose BrAPI as your source. Then press the 'Load Fields' button

Field Book will import a list of possible fields (known as `studies` in the BrAPI standard) from the BrAPI Base URL set in the |brapi| :doc:`settings-brapi`.

The possible fields can be filtered by program and trial using the menu options in the top toolbar. The list can be filtered by ``Observation Level`` using the dropdown below the server URL.

The 'Next' button advances the page if the fields list has been paginated. Once a field has been selected, the field structure can be previewed by pressing the 'Preview Field' button 

Previewed fields are imported by pressing the 'Save' button. Only fields that have been imported via BrAPI can be exported to BrAPI servers.

Import traits
-------------
Depending on the BrAPI server, fields may have linked traits that are automatically imported with the field.

However, additional traits can be imported via BrAPI by selecting import from the Traits menu, then selecting BrAPI.

.. figure:: /_static/images/brapi/brapi_trait_import_joined.png
   :width: 80%
   :align: center
   :alt: BrAPI trait import screen

   The BrAPI trait import process.

Field Book will import a list of possible traits (known as `observationVariables` in the BrAPI standard) from the BrAPI Base URL set in the |brapi| :doc:`settings-brapi`.

One or more of the possible traits can be selected by pressing it's corresponding checkbox. The 'Next' button advances the page if the traits list has been paginated. Once the desired traits have been selected, press the 'Save Traits' button to import them.

Fieldbook will report *Selected traits saved successfully*, and return to the traits screen with the imported traits selected.

Export data
-----------
Once data has been collected it can then be exported via BrAPI by going to Export in the main menu, and selecting BrAPI from the Export Data options.

Before the export is finalized, Field Book will display a summary of BrAPI Export statistics. These include a breakdown of the number of new vs synched(imported) vs edited observations, as well as skipped observations. And a section showing the same stats for images. 

.. figure:: /_static/images/brapi/brapi_export_process_joined.png
   :width: 80%
   :align: center
   :alt: BrAPI export process

   The BrAPI data export process.

After reviewing the export stats, press the 'Export' button to save the observations to the external BrAPI database.

.. |brapi| image:: /_static/icons/settings/main/server-network.png
  :width: 20

.. |add| image:: /_static/icons/fields/plus-circle.png
  :width: 20