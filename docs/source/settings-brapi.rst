BrAPI Settings
==============
Configuration
-------------

.. figure:: /_static/images/settings/brapi/settings_brapi_1_framed.png
   :width: 40%
   :align: center
   :alt: Brapi settings screen layout part 1

   BrAPI Configuration Settings

|url| BrAPI Base URL
~~~~~~~~~~~~~~~~~~~~
Set the server URL when importing via BrAPI.

.. figure:: /_static/images/settings/brapi/settings_brapi_base_url.png
   :width: 40%
   :align: center
   :alt: Brapi settings base url

   BrAPI Base URL setting

|authorize| Authorize BrAPI
~~~~~~~~~~~~~~~~~~~~~~~~~~~
Open the authorization page to login to the BrAPI server and allow Field Book to communicate with it.

|version| BrAPI Version
~~~~~~~~~~~~~~~~~~~~~~~
Set the version of the BrAPI specification that will be used to communicate with the server. Defaults to V2.

.. figure:: /_static/images/settings/brapi/settings_brapi_version.png
   :width: 40%
   :align: center
   :alt: Brapi settings version

   BrAPI version setting

|page| Page Size
~~~~~~~~~~~~~~~~
Set the page size for BrAPI server responses. Defaults to 1000.

.. figure:: /_static/images/settings/brapi/settings_brapi_page_size.png
   :width: 40%
   :align: center
   :alt: Brapi settings page size

   BrAPI page size setting

|chunk| Chunk Size
~~~~~~~~~~~~~~~~~~
Set the chunk size. Defaults to 500.

.. figure:: /_static/images/settings/brapi/settings_brapi_chunk_size.png
   :width: 40%
   :align: center
   :alt: Brapi settings chunk size

   BrAPI chunk size setting

|timeout| Server Timeout
~~~~~~~~~~~~~~~~~~~~~~~~
Set the time limit in seconds to wait for a repsonse from the server before timing out. Defaults to 2 minutes.

.. figure:: /_static/images/settings/brapi/settings_brapi_server_timeout.png
   :width: 40%
   :align: center
   :alt: Brapi settings server timeout

   BrAPI server timeout setting

Advanced Auth Settings
----------------------

.. figure:: /_static/images/settings/brapi/settings_brapi_2_framed.png
   :width: 40%
   :align: center
   :alt: Brapi settings screen layout part 2

   BrAPI Advanced Auth Settings

|version| OIDC Flow
~~~~~~~~~~~~~~~~~~~
BrAPI server authentication version. Defaults to OAuth2 Implicit Grant.

.. figure:: /_static/images/settings/brapi/settings_brapi_oidc_flow.png
   :width: 40%
   :align: center
   :alt: Brapi OIDC flow setting

   BrAPI OIDC flow setting

|url| OIDC Discovery URL
~~~~~~~~~~~~~~~~~~~~~~~~
The location of the OIDC discovery JSON document.

.. figure:: /_static/images/settings/brapi/settings_brapi_oidc_url.png
   :width: 40%
   :align: center
   :alt: Brapi OIDC url setting

   BrAPI OIDC url setting

BrAPI Variables
---------------
|display| Value vs Label Display
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Sets desired field for display when using a categorical trait the comes from a BrAPI Variable.

.. figure:: /_static/images/settings/brapi/settings_brapi_value_label.png
   :width: 40%
   :align: center
   :alt: Brapi value label setting

   BrAPI Value vs Label setting

Community Servers
-----------------
|barcode| Scan a server barcode
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Add a BrAPI server by scanning a URL barcode.

Advanced
~~~~~~~~
Access servers from the BrAPI community.

.. figure:: /_static/images/settings/brapi/settings_brapi_database_whitelist.png
   :width: 40%
   :align: center
   :alt: Brapi whitelisted databases

   BrAPI database whitelist

.. |url| image:: /_static/icons/settings/brapi/link-plus.png
  :width: 20

.. |authorize| image:: /_static/icons/settings/brapi/open-in-new.png
  :width: 20

.. |version| image:: /_static/icons/settings/brapi/alpha-v-box-outline.png
  :width: 20

.. |page| image:: /_static/icons/settings/brapi/layers-triple.png
  :width: 20

.. |chunk| image:: /_static/icons/settings/brapi/transfer.png
  :width: 20

.. |timeout| image:: /_static/icons/settings/brapi/timer-outline.png
  :width: 20

.. |display| image:: /_static/icons/settings/brapi/view-list-outline.png
  :width: 20

.. |barcode| image:: /_static/icons/settings/brapi/barcode-scan.png
  :width: 20
