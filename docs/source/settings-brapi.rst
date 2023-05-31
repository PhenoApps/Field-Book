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

|authorize| Authorize BrAPI
~~~~~~~~~~~~~~~~~~~~~~~~~~~
Open the authorization page to login to the BrAPI server and allow Field Book to communicate with it.

|version| BrAPI Version
~~~~~~~~~~~~~~~~~~~~~~~
Set the version of the BrAPI specification that will be used to communicate with the server. Defaults to V2.

|page| Page Size
~~~~~~~~~~~~~~~~
Set the page size for BrAPI server responses. Defaults to 1000.

|chunk| Chunk Size
~~~~~~~~~~~~~~~~~~
Set the chunk size. Defaults to 500.

|timeout| Server Timeout
~~~~~~~~~~~~~~~~~~~~~~~~
Set the time limit in seconds to wait for a repsonse from the server before timing out. Defaults to 2 minutes.

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

|url| OIDC Discovery URL
~~~~~~~~~~~~~~~~~~~~~~~~
The location of the OIDC discovery JSON document.

BrAPI Variables
---------------
|display| Value vs Label Display
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Sets desired field for display when using a categorical trait the comes from a BrAPI Variable.

Community Servers
-----------------
|barcode| Scan a server barcode
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Add a BrAPI server by scanning a barcode of it's URL.

Advanced
~~~~~~~~
Access whitelisted servers from the BrAPI community.


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
