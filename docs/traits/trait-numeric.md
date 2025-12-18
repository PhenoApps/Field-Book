<link rel="stylesheet" type="text/css" href="../_styles/styles.css">

# <img class="icon-title" src="../_static/icons/formats/numeric.png"> Numeric Trait

The numeric trait format is used for quantitative traits like height.

### Parameters
- `Name` assign a value for trait name, alias, and assigns synonyms list to hold the value.
- `Default` automatically assigns a specific value for the observation when visiting an entry having no observation recorded previously.
- `minimum` and `maximum` thresholds can be set during trait creation to help reduce errors during data collection.
- `Decimal Places` adds additional layer of data validation to be performed during collection.
`No restriction` will allow all kinds of possible values including mathematical symbols (if enabled), 
`Integers only` will only accept integer values, 
`Allow decimal input` will allow user specified (between 1-10) characters after the decimal point.
- `Mathematical Symbols` hides the buttons with mathematical symbols ( **;** , **+** , **-** and **\*** ) when disabled.
- `Allow Invalid Value` when enabled and a value that voilates any of the set restrictions is entered, will prompt the user if they want to save the data.
- `Details` text is displayed under the trait name on the Collect screen.
- `Unit` text can be set to denote the unit for the observation.
- `Repeated Measures` turns on repeated measure for the trait when enabled.
- `Resource File` opens the resources folder and can be used to set a image for the trait that can be reference later in the Collect screen.

Setting either `Integers only` or `Allow decimal input` in `Decimal Places` cannot be done while `Mathematical Symbols` are enabled to avoid invalid inputs.

<figure class="image">
  <img class="screenshot" src="_static/images/traits/formats/joined_numeric_format.png" width="700px"> 
  <figcaption class="screenshot-caption"><i>Numeric trait creation dialog and collect format</i></figcaption> 
</figure>

<figure class="image">
  <img class="screenshot" src="_static/images/traits/formats/joined_numeric_invalid.png" width="700px"> 
  <figcaption class="screenshot-caption"><i>Invalid value prompt for integer only restriction</i></figcaption> 
</figure>