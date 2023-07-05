package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.documentfile.provider.DocumentFile;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.utilities.DocumentTreeUtil;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AudioTraitLayout extends BaseTraitLayout {

    static public String type = "audio";

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private Uri recordingLocation;
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
        audioRecordingText.setText("NA");
    }

    @Override
    public String type() {
        return "audio";
    }

    @Override
    public int layoutId() {
        return R.layout.trait_audio;
    }

    @Override
    public void init(Activity act) {
        audioRecordingText = act.findViewById(R.id.audioRecordingText);
        buttonState = ButtonState.WAITING_FOR_RECORDING;
        controlButton = act.findViewById(R.id.record);
        controlButton.setOnClickListener(new AudioTraitOnClickListener());
        controlButton.requestFocus();
    }

    @Override
    public void afterLoadExists(CollectActivity act, String value) {
        super.afterLoadExists(act, value);
        if (value != null && value.equals("NA")) {
            buttonState = ButtonState.WAITING_FOR_RECORDING;
            controlButton.setImageResource(buttonState.getImageId());
            audioRecordingText.setText("NA");
        } else {
            DocumentFile file = DocumentFile.fromSingleUri(getContext(), Uri.parse(value));
            if (file != null && file.exists()) {
                this.recordingLocation = file.getUri();
                buttonState = ButtonState.WAITING_FOR_PLAYBACK;
                controlButton.setImageResource(buttonState.getImageId());
                audioRecordingText.setText(getContext().getString(R.string.trait_layout_data_stored));
            } else {
                deleteTraitListener();
            }
        }
    }

    @Override
    public void afterLoadNotExists(CollectActivity act) {
        super.afterLoadNotExists(act);
        buttonState = ButtonState.WAITING_FOR_RECORDING;
        controlButton.setImageResource(buttonState.getImageId());
        audioRecordingText.setText("");
    }

    @Override
    public void refreshLock() {
        super.refreshLock();
        ((CollectActivity) getContext()).traitLockData();
        try {
            loadLayout();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void refreshLayout(Boolean onNew) {
        super.refreshLayout(onNew);
        refreshButtonState();
    }

    private void refreshButtonState() {
        ObservationModel model = getCurrentObservation();
        if (model != null && !model.getValue().isEmpty()) {
            buttonState = ButtonState.WAITING_FOR_PLAYBACK;
            controlButton.setImageResource(buttonState.getImageId());
        } else {
            buttonState = ButtonState.WAITING_FOR_RECORDING;
            controlButton.setImageResource(buttonState.getImageId());
            audioRecordingText.setText("");
            getCollectInputView().setText("");
        }
    }

    @Override
    public void deleteTraitListener() {
        deleteRecording();
        removeTrait(getCurrentTrait().getTrait());
        super.deleteTraitListener();
        recordingLocation = null;
        mediaPlayer = null;
        mediaRecorder = null;
        refreshButtonState();
    }

    // Delete recording
    private void deleteRecording() {
        if (recordingLocation != null) {
            DocumentFile file = DocumentFile.fromSingleUri(getContext(), recordingLocation);

            if (file != null && file.exists()) {
                file.delete();
            }
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

    public class AudioTraitOnClickListener implements OnClickListener {

        @Override
        public void onClick(View view) {
            ((CollectActivity) getContext()).setNewTraits(getDatabase().getUserDetail(getCurrentRange().plot_id));

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
            toggleNavigationButtons(enableNavigation);
        }

        private void startPlayback() {
            try {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.reset();
                    mediaPlayer.release();
                }
                mediaPlayer = MediaPlayer.create(getContext(),recordingLocation);
                mediaPlayer.setOnCompletionListener(mp -> {
                    stopPlayback();
                    buttonState = ButtonState.WAITING_FOR_PLAYBACK;
                    controlButton.setImageResource(buttonState.getImageId());
                    toggleNavigationButtons(true);
                });
                mediaPlayer.setOnPreparedListener(MediaPlayer::start);
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
                releaseRecorder();
                updateObservation(getCurrentTrait().getTrait(), "audio", recordingLocation.toString());
                audioRecordingText.setText(getContext().getString(R.string.trait_layout_data_stored));
                getCollectInputView().setText(recordingLocation.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void toggleNavigationButtons(boolean enabled) {
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
            DocumentFile audioDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "audio");
            if (audioDir != null && audioDir.exists()) {
                DocumentFile audioFile = audioDir.createFile("*/mp4", recordingName + ".mp4");
                if (audioFile != null) {
                    recordingLocation = audioFile.getUri();
                }
            }
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
                mGeneratedName = getCurrentRange().plot_id + " " + timeStamp.format(c.getTime());
            } catch (Exception e) {
                mGeneratedName = "error " + timeStamp.format(c.getTime());
            }

            setRecordingLocation(mGeneratedName);
            try {
                FileDescriptor fd = getContext().getContentResolver().openFileDescriptor(recordingLocation, "rw").getFileDescriptor();
                mediaRecorder.setOutputFile(fd);
            } catch (FileNotFoundException | IllegalStateException e) {
                e.printStackTrace();
            }

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