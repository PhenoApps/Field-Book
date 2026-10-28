<link rel="stylesheet" type="text/css" href="_styles/styles.css">

# BrAPI Settings

#### <img class="icon" src="_static/icons/settings/brapi/server-network.png"> Enable BrAPI

Enable/Disable BrAPI throughout Field Book app.
Disabling BrAPI hides all other BrAPI settings, and removes BrAPI as an option from imports and exports.

Once enabled, four options are available:

- <img class="icon" src="_static/icons/settings/brapi/key.png"> [**Add Account**](#accounts) to add and manage BrAPI accounts.
- <img class="icon" src="_static/icons/settings/brapi/share-variant.png"> **Shared Servers** to reuse a BrAPI account already added in another PhenoApps app on the same device.
- <img class="icon" src="_static/icons/settings/brapi/cogs.png"> [**Transmission Settings**](#transmission-settings) to configure how Field Book communicates with BrAPI servers.
- <img class="icon" src="_static/icons/fields/sync.png"> [**Sync Settings**](#sync-settings) to sync fields automatically and get reminders about unsynced observations.

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
The next two cards show how many of the BrAPI calls used by Field Book are supported by the server.
**Import to Field Book** covers the calls used to import fields and traits and to download observations.
**Upload from Field Book** covers the calls used to send observations and images to the server.
Each method of a call is its own row, so a server that lets Field Book read observations but not create or update them shows `GET observations` as supported and `POST observations` and `PUT observations` as not supported.
The remaining cards, under **All Server Calls**, compare every call the server lists, grouped by BrAPI module, with the calls Field Book uses.

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

## Sync Settings

Press <img class="icon" src="_static/icons/fields/sync.png"> **Sync Settings** to have Field Book sync BrAPI fields without opening the sync screen.
Automatic syncs download new observations from the server and then upload new and edited observations, including images.
They only include fields imported from the active server that are not archived.

Automatic syncs never make a choice on your behalf.
If an observation was edited both on the device and on the server, both values are left as they are and the local edit is not uploaded.
If a new observation appears to already be on the server, it is not uploaded.
The field's Sync card shows how many observations need review, and they can be resolved in the [Needs Review](../brapi.md#needs-review) card on the sync screen.
Observations edited only on the device are uploaded, and observations changed only on the server are downloaded.

- <img class="icon" src="_static/icons/settings/system/refresh.png"> **Sync When Opening a Field** syncs a BrAPI field each time its details are opened.
Defaults to off.
- <img class="icon" src="_static/icons/settings/brapi/timer-outline.png"> **Background Sync** syncs every BrAPI field on a schedule, from every 15 minutes to daily, whether or not Field Book is open.
Background syncs wait for a network connection, and Android may delay them to save battery.
Defaults to `Off`.
- **Automatic Upload** chooses what automatic syncs upload.
**New and edited observations** uploads everything.
**New observations only** uploads observations and images collected on this device, and leaves changes to data already on the server to the sync screen, where they are checked first.
Defaults to `New and edited observations`.
- **Notify on Reconnect** shows a notification when the device gets a network connection back and BrAPI fields have observations that are not synced.
Tapping the notification opens the sync screen, or the field list when more than one field needs syncing.
Going back from the sync screen returns to that field's details.
The notification's **Sync** button syncs those fields in the background without opening Field Book, and **Dismiss** closes it.
If anything is still not synced afterwards, for example observations that need review, the notification is shown again.
Field Book asks for permission to post notifications when this is turned on.
Defaults to off.
