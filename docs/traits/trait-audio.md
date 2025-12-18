<link rel="stylesheet" type="text/css" href="_styles/styles.css">

# <img class="icon-title" src="../_static/icons/formats/microphone.png"> Audio Trait

The audio trait format is used to record audio.
It is created with a `name` and optional `details`.
Details text is displayed under the trait name on the Collect screen.

On the Collect screen, press the <img class="icon" src="_static/icons/formats/microphone.png"> button to begin recording audio.
Press the <img class="icon" src="_static/icons/formats/stop.png"> button to stop recording.
Press the <img class="icon" src="_static/icons/formats/play.png"> button to play back the audio that has been recorded.

Recorded audio files are stored in `.mp3` format, and named with the entry's `unique id` and a
timestamp.
The resulting files are stored in an audio folder within a field-specific subfolder of `plot_data`.
An example audio filepath would be `plot_data/FIELD_NAME/audio/AUDIO_FILE_NAME.mp3`.

<figure class="image">
  <img class="screenshot" src="../_static/images/traits/formats/joined_audio_format.png" width="700px"> 
  <figcaption class="screenshot-caption"><i>Audio trait creation dialog and collect format</i></figcaption> 
</figure>