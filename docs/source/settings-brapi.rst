BrAPI Settings
==============
Configuration
-------------

.. figure:: /_static/images/settings/settings_brapi_1_framed.png
   :width: 40%
   :align: center
   :alt: Brapi settings screen layout part 1

   BrAPI Configuration Settings

BrAPI Base URL (|url|)
~~~~~~~~~~~~~~~~~~~~~~
Set the server URL when importing via BrAPI.

Authorize BrAPI (|authorize|)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Open the authorization page to login to the BrAPI server and allow Field Book to communicate with it.

BrAPI Version (|version|)
~~~~~~~~~~~~~~~~~~~~~~~~~
Set the version of the BrAPI specification that will be used to communicate with the server. Defaults to V2.

Page Size (|page|)
~~~~~~~~~~~~~~~~~~
Set the page size for BrAPI server responses. Defaults to 1000.

Chunk Size (|chunk|)
~~~~~~~~~~~~~~~~~~~~
Set the chunk size. Defaults to 500.

Server Timeout (|timeout|)
~~~~~~~~~~~~~~~~~~~~~~~~~~
Set the time limit in seconds to wait for a repsonse from the server before timing out. Defaults to 2 minutes.

Advanced Auth Settings
----------------------

.. figure:: /_static/images/settings/settings_brapi_2_framed.png
   :width: 40%
   :align: center
   :alt: Brapi settings screen layout part 2

   BrAPI Advanced Auth Settings

ODIC Flow (|version|)
~~~~~~~~~~~~~~~~~~~~~
BrAPI server authentication version. Defaults to OAuth2 Implicit Grant.

ODIC Discovery URL (|url|)
~~~~~~~~~~~~~~~~~~~~~~~~~~
The location of the ODIC discovery JSON document.

BrAPI Variables
---------------
Value vs Label Display (|display|)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Sets desired field for display when using a categorical trait the comes from a BrAPI Variable.

Community Servers
-----------------
Scan a server barcode (|barcode|)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
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
