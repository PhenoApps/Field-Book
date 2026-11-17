<link rel="stylesheet" type="text/css" href="_styles/styles.css">

# BrAPI Settings

#### <img class="icon" src="_static/icons/settings/brapi/server-network.png"> Enable BrAPI

Enable/Disable BrAPI throughout Field Book app.
Disabling BrAPI hides all other BrAPI settings, and removes BrAPI as an option from imports and exports.

Once enabled, three options are available:

- <img class="icon" src="_static/icons/settings/brapi/key.png"> [**Add Account**](#accounts) to add and manage BrAPI accounts.
- <img class="icon" src="_static/icons/settings/brapi/share-variant.png"> **Shared Servers** to reuse a BrAPI account already added in another PhenoApps app on the same device.
- <img class="icon" src="_static/icons/settings/brapi/cogs.png"> [**Transmission Settings**](#transmission-settings) to configure how Field Book communicates with BrAPI servers.

<figure class="image">
  <img class="screenshot" src="_static/images/brapi/settings/brapi_default.png" width="350px"> 
  <figcaption class="screenshot-caption"><i>BrAPI settings screen layout</i></figcaption> 
</figure>

## Accounts

### Adding an Account

Press <img class="icon" src="_static/icons/settings/brapi/key.png"> **Add Account** to open a 3-step setup wizard:

1. Choose **Manual Input** or **Scan Configuration Code** to scan a configuration QR code shared from another device (see [Sharing an Account](#sharing-an-account) below).
2. Enter the server's Base URL, or press the <img class="icon" src="_static/icons/settings/brapi/barcode-scan.png"> icon to scan a URL.
3. Review and edit the account's Display Name, BrAPI Version (defaults to `V2`), OIDC Flow (defaults to `OAuth2 Implicit Grant`), OIDC Discovery URL, OIDC Client ID (defaults to `fieldbook`), and optional OIDC Scope.
Press **Authorize** to save the account and log in.

<figure class="image" style="text-align: center">
    <p>
      <img src="_static/images/brapi/settings/brapi_account_add_1.png" width="230px"> 
      <img src="_static/images/brapi/settings/brapi_account_add_2.png" width="230px"> 
      <img src="_static/images/brapi/settings/brapi_account_add_3.png" width="230px"> 
    </p>

  <figcaption class="screenshot-caption"><i>Adding an account: input method, Base URL, and account details</i></figcaption> 
</figure>

### Managing Accounts

Saved accounts are listed as cards under **Active Server** (the account currently in use) and **Available Servers** (all others).
Each card shows a lock icon if server authentication was successful.
Cards can be expanded to reveal all available actions:

- <img class="icon" src="_static/icons/settings/brapi/list-status.png"> **Compatibility** checks the supported BrAPI calls for that account's server.
- <img class="icon" src="_static/icons/settings/brapi/share-variant.png"> **Share** displays a QR code of the account's settings so it can be added on another device.
See [Sharing an Account](#sharing-an-account) below.
- <img class="icon" src="_static/icons/settings/brapi/square-edit-outline.png"> **Edit** reopens the account's connection details for editing.
- <img class="icon" src="_static/icons/settings/brapi/logout.png"> **Log out** clears the account's saved login, with the option to remove the account entirely.

On inactive servers, a **Switch Server** action switches Field Book to use that account.

<figure class="image">
  <img class="screenshot" src="_static/images/brapi/settings/brapi_account_list.png" width="350px"> 
  <figcaption class="screenshot-caption"><i>Active and Available Server accounts</i></figcaption> 
</figure>

#### Check Compatibility

The top card displays the information for a server including its server name, organization, and description.
The second card shows how many of the BrAPI calls used by Field Book are supported by the server.
The remaining cards show module-wise comparison of all calls in the BrAPI specification that have been implemented by the server and/or Field Book.

<figure class="image">
  <img class="screenshot" src="_static/images/brapi/settings/brapi_compatibility.png" width="350px"> 
  <figcaption class="screenshot-caption"><i>BrAPI Compatibility for an account</i></figcaption> 
</figure>

#### Sharing an Account

Sharing a server packages the account settings (URL, display name, BrAPI version, OIDC settings, and current Transmission Settings) into a QR code that another device can scan.
On the other device, choose **Add Account** > **Scan Configuration Code** to import it.

## Transmission Settings

Press <img class="icon" src="_static/icons/settings/brapi/cogs.png"> **Transmission Settings** to configure how Field Book communicates with BrAPI servers:

- <img class="icon" src="_static/icons/settings/brapi/layers-triple.png"> **Page Size** controls how many records Field Book asks for at a time when downloading data from a server.
Defaults to `50`.
- <img class="icon" src="_static/icons/settings/brapi/transfer.png"> **Chunk Size** controls how many records Field Book sends at a time when uploading data to a server.
Defaults to `500`.
- <img class="icon" src="_static/icons/settings/brapi/timer-outline.png"> **Server Timeout** is how long Field Book waits for a server to respond before giving up.
Defaults to `120 seconds`.
- <img class="icon" src="_static/icons/settings/brapi/image-multiple-outline.png"> **Max Concurrent Image Export** is how many images Field Book uploads to a server at the same time.
Defaults to `5`.
- <img class="icon" src="_static/icons/settings/brapi/transfer-down.png"> **Max Concurrent Observation Transfer** is how many observations Field Book sends or receives at the same time.
Defaults to `5`.
- <img class="icon" src="_static/icons/settings/brapi/history.png"> **Cache Invalidation** controls how often Field Book automatically clears its saved copy of server data so it can pick up anything new.
This matters for field and trait imports, which need a fresh cache to see new data added on the server.

<figure class="image">
  <img class="screenshot" src="_static/images/brapi/settings/brapi_transmission_settings.png" width="350px"> 
  <figcaption class="screenshot-caption"><i>Transmission Settings</i></figcaption> 
</figure>
