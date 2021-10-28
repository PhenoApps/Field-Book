package com.fieldbook.tracker.traits;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.activities.ConfigActivity;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.utilities.Utils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AudioTraitLayout extends BaseTraitLayout {

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private File recordingLocation;
    private ImageButton controlButton;
    private ButtonState buttonState;
    private TextView audioRecordingText;

    public AudioTraitLayout(Context context) {
        super(context);
    }

    public AudioTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AudioTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setNaTraitsText() {
    }

    @Override
    public String type() {
        return "audio";
    }

    @Override
    public void init() {
        audioRecordingText = findViewById(R.id.audioRecordingText);
        buttonState = ButtonState.WAITING_FOR_RECORDING;
        controlButton = findViewById(R.id.record);
        controlButton.setOnClickListener(new AudioTraitOnCLickListener());
    }

    @Override
    public void loadLayout() {
        if (!getNewTraits().containsKey(getCurrentTrait().getTrait())) {
            buttonState = ButtonState.WAITING_FOR_RECORDING;
            controlButton.setImageResource(buttonState.getImageId());
            audioRecordingText.setText("");
        } else if (getNewTraits().containsKey(getCurrentTrait().getTrait())
                && getNewTraits().get(getCurrentTrait().getTrait()).toString().equals("NA")) {
            buttonState = ButtonState.WAITING_FOR_RECORDING;
            controlButton.setImageResource(buttonState.getImageId());
            audioRecordingText.setText("NA");
        } else {
            Map<String, String> observations = getNewTraits();
            File recordingLocation = new File(getNewTraits().get(getCurrentTrait().getTrait()).toString());
            if (recordingLocation.exists()) {
                this.recordingLocation = recordingLocation;
                buttonState = ButtonState.WAITING_FOR_PLAYBACK;
                controlButton.setImageResource(buttonState.getImageId());
                audioRecordingText.setText(getContext().getString(R.string.trait_layout_data_stored));
            } else {
                deleteTraitListener();
            }
        }
    }

    @Override
    public void deleteTraitListener() {
        deleteRecording();
        removeTrait(getCurrentTrait().getTrait());
        recordingLocation = null;
        mediaPlayer = null;
        mediaRecorder = null;
        buttonState = ButtonState.WAITING_FOR_RECORDING;
        controlButton.setImageResource(buttonState.getImageId());
        audioRecordingText.setText("");
    }

    // Delete recording
    private void deleteRecording() {
        if (recordingLocation != null && recordingLocation.exists()) {
            recordingLocation.delete();
        }
    }

    private enum ButtonState {
        WAITING_FOR_RECORDING(R.drawable.trait_audio),
        RECORDING(R.drawable.trait_audio_stop),
        WAITING_FOR_PLAYBACK(R.drawable.trait_audio_play),
        PLAYING(R.drawable.trait_audio_stop);

        private int imageId;

        ButtonState(int imageId) {
            this.imageId = imageId;
        }

        public int getImageId() {
            return imageId;
        }
    }

    public class AudioTraitOnCLickListener implements OnClickListener {

        @Override
        public void onClick(View view) {
            ((CollectActivity) getContext()).setNewTraits(ConfigActivity.dt.getUserDetail(getCRange().plot_id));

            boolean enableNavigation = true;
            switch (buttonState) {
                case PLAYING:
                    stopPlayback();
                    buttonState = ButtonState.WAITING_FOR_PLAYBACK;
                    break;
                case RECORDING:
                    stopRecording();
                    buttonState = ButtonState.WAITING_FOR_PLAYBACK;
                    break;
                case WAITING_FOR_PLAYBACK:
                    startPlayback();
                    buttonState = ButtonState.PLAYING;
                    enableNavigation = false;
                    break;
                case WAITING_FOR_RECORDING:
                    startRecording();
                    buttonState = ButtonState.RECORDING;
                    enableNavigation = false;
                    break;
            }

            controlButton.setImageResource(buttonState.getImageId());
            toggleNavigationButtoms(enableNavigation);
        }

        private void startPlayback() {
            try {
                mediaPlayer = MediaPlayer.create(getContext(), Uri.parse(recordingLocation.getAbsolutePath()));
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    public void onCompletion(MediaPlayer mp) {
                        stopPlayback();
                        buttonState = ButtonState.WAITING_FOR_PLAYBACK;
                        controlButton.setImageResource(buttonState.getImageId());
                        toggleNavigationButtoms(true);
                    }
                });
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        mediaPlayer.start();
                    }
                });
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void stopPlayback() {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }
        }

        private void startRecording() {
            try {
                removeTrait(getCurrentTrait().getTrait());
                audioRecordingText.setText("");
                prepareRecorder();
                if (mediaRecorder != null) {
                    mediaRecorder.start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void stopRecording() {
            try {
                mediaRecorder.stop();
                File storedAudio = new File(recordingLocation.getAbsolutePath());
                Utils.scanFile(getContext(), storedAudio);
                releaseRecorder();
                updateTrait(getCurrentTrait().getTrait(), "audio", recordingLocation.getAbsolutePath());
                audioRecordingText.setText(getContext().getString(R.string.trait_layout_data_stored));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void toggleNavigationButtoms(boolean enabled) {
            ImageButton deleteValue = ((CollectActivity) getContext()).getDeleteValue();
            ImageView traitLeft = ((CollectActivity) getContext()).getTraitLeft();
            ImageView traitRight = ((CollectActivity) getContext()).getTraitRight();
            ImageView rangeLeft = ((CollectActivity) getContext()).getRangeLeft();
            ImageView rangeRight = ((CollectActivity) getContext()).getRangeRight();

            rangeLeft.setEnabled(enabled);
            rangeRight.setEnabled(enabled);
            traitLeft.setEnabled(enabled);
            traitRight.setEnabled(enabled);
            deleteValue.setEnabled(enabled);
        }

        // For audio trait type
        private void setRecordingLocation(String recordingName) {
            String dirPath = getPrefs().getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.PLOTDATAPATH + "/" + getPrefs().getString("FieldFile", "") + "/audio/";
            File dir = new File(dirPath);
            dir.mkdirs();
            recordingLocation = new File(dirPath, recordingName + ".mp4");
        }

        // Make sure we're not recording music playing in the background; ask the
        // MediaPlaybackService to pause playback
        private void stopAllAudioForPlayback() {
            Intent i = new Intent("com.android.music.musicservicecommand");
            i.putExtra("command", "pause");
            getContext().sendBroadcast(i);
        }

        // Reset the recorder to default state so it can begin recording
        private void prepareRecorder() {

            stopAllAudioForPlayback();

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);

            SimpleDateFormat timeStamp = new SimpleDateFormat(
                    "yyyy-MM-dd-hh-mm-ss", Locale.getDefault());

            Calendar c = Calendar.getInstance();

            String mGeneratedName;
            try {
                mGeneratedName = getCRange().plot_id + " " + timeStamp.format(c.getTime());
            } catch (Exception e) {
                mGeneratedName = "error " + timeStamp.format(c.getTime());
            }

            setRecordingLocation(mGeneratedName);
            mediaRecorder.setOutputFile(recordingLocation.getAbsolutePath());

            try {
                mediaRecorder.prepare();
            } catch (IllegalStateException | IOException e) {
                e.printStackTrace();
            }
        }

        // Remove the recorder resource
        private void releaseRecorder() {
            if (mediaRecorder != null) {
                mediaRecorder.release();
            }
        }
    }
}