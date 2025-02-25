<img ref="audio" style="vertical-align: middle;" src="_static/icons/formats/microphone.png" width="40px"> Audio Trait
=========================================================================

The audio trait format is used to record audio. It is created with a
trait name and optional details.

On the collect page, press the
<img ref="audio" style="vertical-align: middle;" src="_static/icons/formats/microphone.png" width="20px"> button to
begin recording audio. Press the
<img ref="stop" style="vertical-align: middle;" src="_static/icons/formats/stop.png" width="20px"> button to stop
recording. Press the
<img ref="play" style="vertical-align: middle;" src="_static/icons/formats/play.png" width="20px"> button to play
back the audio that has been recorded.

Recorded audio files are stored in `.mp3` format, and named with the entry's unique_id and a
timestamp. The resulting files are stored in an audio folder within a field-specific subfolder of `plot_data`. An example audio filepath would be `plot_data/FIELD_NAME/audio/AUDIO_FILE_NAME.mp3`.

<figure align="center" class="image">
  <img src="_static/images/traits/formats/audio_format_joined.png" width="700px"> 
  <figcaption><i>Audio trait creation dialog and collect format</i></figcaption> 
</figure>

