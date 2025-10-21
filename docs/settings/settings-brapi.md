<link rel="stylesheet" type="text/css" href="_styles/styles.css">

# BrAPI Settings

<figure class="image">
  <img class="screenshot" src="_static/images/settings/brapi/settings_brapi_1_framed.png" width="350px"> 
  <figcaption class="screenshot-caption"><i>BrAPI settings screen layout</i></figcaption> 
</figure>

#### <img class="icon" src="_static/icons/settings/brapi/server-network.png"> Enable BrAPI

Enable/Disable BrAPI throughout Field Book app.
Disabling BrAPI hides all other BrAPI settings, and removes BrAPI as an option from imports and exports.

## Server

#### <img class="icon" src="_static/icons/settings/brapi/link-plus.png"> BrAPI Base URL

Set the server URL when importing via BrAPI.

<figure class="image">
  <img class="screenshot" src="_static/images/settings/brapi/settings_brapi_base_url.png" width="325px"> 
  <figcaption class="screenshot-caption"><i>BrAPI Base URL setting</i></figcaption> 
</figure>

#### <img class="icon" src="_static/icons/settings/brapi/rename-outline.png"> BrAPI Display Name

Set the display name Field Book should use when referring to the BrAPI server.

<figure class="image">
  <img class="screenshot" src="_static/images/settings/brapi/settings_brapi_display_name.png" width="325px"> 
  <figcaption class="screenshot-caption"><i>BrAPI Display Name setting</i></figcaption> 
</figure>

## Authorization

#### <img class="icon" src="_static/icons/settings/brapi/alpha-v-box-outline.png"> OIDC Flow

BrAPI server authentication version.
Defaults to OAuth2 Implicit Grant.

<figure class="image">
  <img class="screenshot" src="_static/images/settings/brapi/settings_brapi_oidc_flow.png" width="325px"> 
  <figcaption class="screenshot-caption"><i>BrAPI OIDC flow setting</i></figcaption> 
</figure>

#### <img class="icon" src="_static/icons/settings/brapi/link-plus.png"> OIDC Discovery URL

The location of the OIDC discovery JSON document.

<figure class="image">
  <img class="screenshot" src="_static/images/settings/brapi/settings_brapi_oidc_url.png" width="325px"> 
  <figcaption class="screenshot-caption"><i>BrAPI OIDC url setting</i></figcaption> 
</figure>

#### <img class="icon" src="_static/icons/settings/brapi/rename-outline.png"> OIDC Client ID and OIDC Scope

Optional settings for when OIDC Client ID and Scope need to be specified.

## Advanced

<figure class="image">
  <img class="screenshot" src="_static/images/settings/brapi/settings_brapi_2_framed.png" width="350px"> 
  <figcaption class="screenshot-caption"><i>BrAPI Advanced Settings</i></figcaption> 
</figure>

#### <img class="icon" src="_static/icons/settings/brapi/alpha-v-box-outline.png"> BrAPI Version

Set the version of the BrAPI specification that will be used to communicate with the server.
Defaults to V2.

<figure class="image">
  <img class="screenshot" src="_static/images/settings/brapi/settings_brapi_version.png" width="325px"> 
  <figcaption class="screenshot-caption"><i>BrAPI version setting</i></figcaption> 
</figure>

#### <img class="icon" src="_static/icons/settings/brapi/layers-triple.png"> Page Size

Set the page size for BrAPI server responses.
Defaults to 1000.

<figure class="image">
  <img class="screenshot" src="_static/images/settings/brapi/settings_brapi_page_size.png" width="325px"> 
  <figcaption class="screenshot-caption"><i>BrAPI page size setting</i></figcaption> 
</figure>

#### <img class="icon" src="_static/icons/settings/brapi/transfer.png"> Chunk Size

Set the chunk size.
Defaults to 500.

<figure class="image">
  <img class="screenshot" src="_static/images/settings/brapi/settings_brapi_chunk_size.png" width="325px"> 
  <figcaption class="screenshot-caption"><i>BrAPI chunk size setting</i></figcaption> 
</figure>

#### <img class="icon" src="_static/icons/settings/brapi/timer-outline.png"> Server Timeout

Set the time limit in seconds to wait for a response from the server before timing out.
Defaults to 2 minutes.

<figure class="image">
  <img class="screenshot" src="_static/images/settings/brapi/settings_brapi_server_timeout.png" width="325px"> 
  <figcaption class="screenshot-caption"><i>BrAPI server timeout setting</i></figcaption> 
</figure>

#### <img class="icon" src="_static/icons/settings/brapi/history.png"> Cache Invalidation

Set the interval for Field Book to invalidate/refresh cached BrAPI data.
Cache refreshes are important for field and trait imports to be able to capture new data from the BrAPI server.

<figure class="image">
  <img class="screenshot" src="_static/images/settings/brapi/settings_brapi_cache_invalidation.png" width="325px"> 
  <figcaption class="screenshot-caption"><i>BrAPI cache invalidation</i></figcaption> 
</figure>

## Preferences

#### <img class="icon" src="_static/icons/settings/brapi/view-list-outline.png"> Value vs Label Display

Sets desired field for display when using a categorical trait the comes from a BrAPI Variable.

<figure class="image">
  <img class="screenshot" src="_static/images/settings/brapi/settings_brapi_value_label.png" width="325px"> 
  <figcaption class="screenshot-caption"><i>BrAPI Value vs Label setting</i></figcaption> 
</figure>