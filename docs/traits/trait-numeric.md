<link rel="stylesheet" type="text/css" href="../_styles/styles.css">

# <img class="icon-title" src="../_static/icons/formats/numeric.png"> Numeric Trait

The numeric trait format is used for quantitative traits like height.

### Parameters
- `Name` assign a value for trait name.
- `Default` automatically assigns a value when visiting an entry that has no observations.
- `Minimum` and `Maximum` can be set to create lower and upper boundaries for observation values.
- `Decimal Places` adds additional layer of data validation during collection:
`No restriction` will allow all values (including mathematical symbols if enabled), 
`Integers only` will only allow integer values, 
`Allow decimal input` will limit input to a user-defined number of values after the decimal (between 1-10).
- `Mathematical Symbols` toggles visibility of the mathematical symbol buttons ( **;** , **+** , **-** and **\*** ) in the trait layout.
- `Allow Invalid Value` will prompt users with an option to save data that violate any set restrictions when collecting data.
- `Details` text is displayed under the trait name on the Collect screen.
- `Unit` text can be set to denote the unit for the observation.
- `Repeated Measures` toggles repeated measure for the trait.
- `Resource File` sets an image for the trait that will be opened by default when accessing resources from Collect.

Setting either `Integers only` or `Allow decimal input` in `Decimal Places` cannot be done while `Mathematical Symbols` are enabled to avoid invalid inputs.

<figure class="image">
  <img class="screenshot" src="_static/images/traits/formats/joined_numeric_format.png" width="700px"> 
  <figcaption class="screenshot-caption"><i>Numeric trait creation dialog and collect format</i></figcaption> 
</figure>

<figure class="image">
  <img class="screenshot" src="_static/images/traits/formats/joined_numeric_invalid.png" width="700px"> 
  <figcaption class="screenshot-caption"><i>Invalid value prompt for integer only restriction</i></figcaption> 
</figure>