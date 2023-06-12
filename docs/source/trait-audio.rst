|audio| Audio Trait
===================
Overview
--------

The audio trait format is used to record audio. It is created with a trait name and optional details.

On the collect page, press the |audio| button to begin recording audio. Press the |stop| button to stop recording. Press the |play| button to play back the audio that has been recorded.

Recorded audio files are named with the entry's unique_id and a timestamp. They are stored as ``.mp3`` files in a field-specific subdirectory (``plot_data/FIELD_NAME/audio/AUDIO_FILE_NAME.mp3``).

Creation
--------

.. figure:: /_static/images/traits/formats/create_audio.png
   :width: 40%
   :align: center
   :alt: Audio trait creation fields

Collect layout
--------------

.. figure:: /_static/images/traits/formats/collect_audio_framed.png
   :width: 40%
   :align: center
   :alt: Audio trait collection

.. |audio| image:: /_static/icons/formats/microphone.png
  :width: 20

.. |stop| image:: /_static/icons/formats/stop.png
  :width: 20

.. |play| image:: /_static/icons/formats/play.png
  :width: 20