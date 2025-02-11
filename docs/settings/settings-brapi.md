BrAPI Settings
==============

<figure align="center" class="image">
  <img src="_static/images/settings/brapi/settings_brapi_1_framed.png" width="350px"> 
  <figcaption><i>BrAPI settings screen layout</i></figcaption> 
</figure>

#### <img ref="url" style="vertical-align: middle;" src="_static/icons/settings/brapi/server-network.png" width="20px"> Enable BrAPI

Enable/Disable BrAPI throughout Field Book app. Disabling BrAPI hides all other BrAPI settings, and removes BrAPI as an option from imports and exports.

Server
------

#### <img ref="url" style="vertical-align: middle;" src="_static/icons/settings/brapi/link-plus.png" width="20px"> BrAPI Base URL

Set the server URL when importing via BrAPI.

<figure align="center" class="image">
  <img src="_static/images/settings/brapi/settings_brapi_base_url.png" width="325px"> 
  <figcaption><i>BrAPI Base URL setting</i></figcaption> 
</figure>

#### <img ref="url" style="vertical-align: middle;" src="_static/icons/settings/brapi/rename-outline.png" width="20px"> BrAPI Base URL

Set the display name Field Book should use when referring to the BrAPI server.

<figure align="center" class="image">
  <img src="_static/images/settings/brapi/settings_brapi_display_name.png" width="325px"> 
  <figcaption><i>BrAPI Display Name setting</i></figcaption> 
</figure>

Authorization
-------------

#### <img ref="version" style="vertical-align: middle;" src="_static/icons/settings/brapi/alpha-v-box-outline.png" width="20px"> OIDC Flow

BrAPI server authentication version. Defaults to OAuth2 Implicit Grant.

<figure align="center" class="image">
  <img src="_static/images/settings/brapi/settings_brapi_oidc_flow.png" width="325px"> 
  <figcaption><i>BrAPI OIDC flow setting</i></figcaption> 
</figure>

#### <img ref="url" style="vertical-align: middle;" src="_static/icons/settings/brapi/link-plus.png" width="20px"> OIDC Discovery URL

The location of the OIDC discovery JSON document.

<figure align="center" class="image">
  <img src="_static/images/settings/brapi/settings_brapi_oidc_url.png" width="325px"> 
  <figcaption><i>BrAPI OIDC url setting</i></figcaption> 
</figure>

#### <img ref="url" style="vertical-align: middle;" src="_static/icons/settings/brapi/rename-outline.png" width="20px"> OIDC Client ID and OIDC Scope

Optional settings for when OIDC Client ID and Scope need to be specified.

Advanced
--------

<figure align="center" class="image">
  <img src="_static/images/settings/brapi/settings_brapi_2_framed.png" width="350px"> 
  <figcaption><i>BrAPI Advanced Settings</i></figcaption> 
</figure>

#### <img ref="version" style="vertical-align: middle;" src="_static/icons/settings/brapi/alpha-v-box-outline.png" width="20px"> BrAPI Version

Set the version of the BrAPI specification that will be used to
communicate with the server. Defaults to V2.

<figure align="center" class="image">
  <img src="_static/images/settings/brapi/settings_brapi_version.png" width="325px"> 
  <figcaption><i>BrAPI version setting</i></figcaption> 
</figure>

#### <img ref="page" style="vertical-align: middle;" src="_static/icons/settings/brapi/layers-triple.png" width="20px"> Page Size

Set the page size for BrAPI server responses. Defaults to 1000.

<figure align="center" class="image">
  <img src="_static/images/settings/brapi/settings_brapi_page_size.png" width="325px"> 
  <figcaption><i>BrAPI page size setting</i></figcaption> 
</figure>

#### <img ref="chunk" style="vertical-align: middle;" src="_static/icons/settings/brapi/transfer.png" width="20px"> Chunk Size

Set the chunk size. Defaults to 500.

<figure align="center" class="image">
  <img src="_static/images/settings/brapi/settings_brapi_chunk_size.png" width="325px"> 
  <figcaption><i>BrAPI chunk size setting</i></figcaption> 
</figure>

#### <img ref="timeout" style="vertical-align: middle;" src="_static/icons/settings/brapi/timer-outline.png" width="20px"> Server Timeout

Set the time limit in seconds to wait for a response from the server
before timing out. Defaults to 2 minutes.

<figure align="center" class="image">
  <img src="_static/images/settings/brapi/settings_brapi_server_timeout.png" width="325px"> 
  <figcaption><i>BrAPI server timeout setting</i></figcaption> 
</figure>

#### <img ref="timeout" style="vertical-align: middle;" src="_static/icons/settings/brapi/history.png" width="20px"> Cache Invalidation

Set the interval for Field Book to invalidate/refresh cached BrAPI data. Cache refreshes are important for field and trait imports to be able to capture new data from the BrAPI server.

<figure align="center" class="image">
  <img src="_static/images/settings/brapi/settings_brapi_cache_invalidation.png" width="325px"> 
  <figcaption><i>BrAPI cache invalidation</i></figcaption> 
</figure>

Preferences
-----------

#### <img ref="display" style="vertical-align: middle;" src="_static/icons/settings/brapi/view-list-outline.png" width="20px"> Value vs Label Display

Sets desired field for display when using a categorical trait the comes
from a BrAPI Variable.

<figure align="center" class="image">
  <img src="_static/images/settings/brapi/settings_brapi_value_label.png" width="325px"> 
  <figcaption><i>BrAPI Value vs Label setting</i></figcaption> 
</figure>
