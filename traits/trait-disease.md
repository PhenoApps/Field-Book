<link rel="stylesheet" type="text/css" href="../_styles/styles.css">

# <img class="icon-title" src="../_static/icons/formats/bug.png"> Disease Rating Trait

The disease rating trait format is used for collecting ratings of both disease incidence and severity.

### Parameters
- `Name` assign a value for trait name.
- `Details` text is displayed under the trait name on the Collect screen.
- `Repeated Measures` toggles repeated measure for the trait.
- `Resource File` sets an image for the trait that will be opened by default when accessing resources from Collect.

The incidence scale can be adjusted by editing the `disease_severity.txt` file in the `traits` folder.

On the collect screen, buttons showing numbers between **0-100** in 5 digit increments are used to record incidence, and **R**, **M**, and **S** are used to record severity.
For each measurement, only a single incidence can be recorded while severity types can be combined.
The `/` button allows varying incidence rates to be recorded (e.g., heterogenous response within a plot).

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/joined_disease_rating_format.png" width="700px"> 
  <figcaption class="screenshot-caption"><i>Disease trait creation dialog and collect format</i></figcaption> 
</figure>