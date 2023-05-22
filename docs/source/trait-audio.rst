Audio Trait |audio|
===================
Overview
--------

The audio trait format is used to record speech. It is created with a trait name and optional details.

On the collect page, the microphone button begins recording audio which can be played back using the play button.

Recorded audio files are named with the entry's unique_id and a timestamp. They are stored as .mp4 files within Field Book's **plot_data** directory and a field-specific subdirectory (plot_data/FIELD_NAME/audio/AUDIO_FILE_NAME.mp4).

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
