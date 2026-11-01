<link rel="stylesheet" type="text/css" href="../_styles/styles.css">

# <img class="icon-title" src="../_static/icons/formats/qrcode.png"> Code Display Trait

The code display trait format shows a barcode or QR code for each entry, drawn as large as the trait area allows.
It is useful for scanning entry data into another device or app directly from the Collect screen.
This trait only displays a code and does not record an observation.

### Parameters
- `Name` assign a value for trait name.
- `Details` text is displayed under the trait name on the Collect screen.

### Settings
On the Collect screen, press the <img class="icon" src="../_static/icons/formats/cog.png"> settings button to choose how the code is generated.
Settings are shared by all code display traits.

- `Code format` selects the symbology.
Supported formats are QR Code, Data Matrix, Aztec, PDF417, Code 128, Code 39, Code 93, Codabar, ITF, EAN-13, EAN-8, UPC-A, and UPC-E.
- `Code data` selects the entry attribute or trait value to encode, using the same chooser as the InfoBars.
By default the field's unique identifier is encoded.

The encoded value is shown below the code.
Some formats only accept certain data, such as EAN-13, which requires 12 or 13 digits.
If the entry's value can't be encoded in the chosen format, a message is shown instead of the code.
