<link rel="stylesheet" type="text/css" href="_styles/styles.css">

# Experimental Settings

<figure class="image">
  <img class="screenshot" src="_static/images/settings/experimental/settings_experimental_framed.png" width="350px"> 
  <figcaption class="screenshot-caption"><i>Experimental settings screen layout</i></figcaption> 
</figure>

## Stable

#### <img class="icon" src="_static/icons/settings/experimental/flask-outline.png"> Repeated Measures

Turns on repeated measures.
When turned on, a plus symbol appears next to the trait value entry box on the Collect screen.

<figure class="image">
  <img class="screenshot" src="_static/images/settings/experimental/settings_experimental_repeated_icon.png" width="325px"> 
  <figcaption class="screenshot-caption"><i>Collect screen value entry with repeated measurements enabled</i></figcaption> 
</figure>

When pressed it creates a new entry field for collecting additional phenotypes on the same entry for the same trait.

!> To export data that includes repeated measures make sure to choose the **Database** format or to use **BrAPI**.
These formats allow repeated measures to be differentiated by timestamp.
If exporting in **Table** format, only the most recent measurement will be included.

## Beta

#### <img class="icon" src="_static/icons/settings/experimental/microphone-message.png"> Enable Field Audio

Adds an icon to collect toolbar for recording audio at the field level.

#### <img class="icon" src="_static/icons/settings/experimental/qrcode-scan.png"> MLKit Library

Changes the software library used for barcode scans from `ZXing` to `MLKit`.

## Alpha

#### <img class="icon" src="_static/icons/settings/experimental/server.png"> New BrAPI Import UI

Replace old BrAPI field and trait import UIs with new streamlined version that includes search and additional step for identifer/format selection.

#### <img class="icon" src="_static/icons/settings/experimental/gamepad.png"> Enable Media Commands

Allows media remotes to control app through media commands.