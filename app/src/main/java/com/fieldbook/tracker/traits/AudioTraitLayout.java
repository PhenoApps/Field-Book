package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.documentfile.provider.DocumentFile;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.utilities.FieldAudioHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AudioTraitLayout extends BaseTraitLayout {

    static public String type = "audio";
    private MediaPlayer mediaPlayer;
    private Uri recordingLocation;
    private FloatingActionButton controlButton;
    private ButtonState buttonState;

    private CardView audioInfoCard;
    private LinearLayout fileMetadataLayout;
    private TextView fileNameText;
    private TextView fileTimestamp;
    private TextView fileDuration;
    private TextView fileSize;

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
        // delete previously saved audio if it exists
        deleteRecording();
        recordingLocation = null;
        mediaPlayer = null;
        refreshButtonState();

        setNAAudioInfoCard();
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
        audioInfoCard = act.findViewById(R.id.audio_info_card);
        fileMetadataLayout = act.findViewById(R.id.file_metadata_layout);
        fileNameText = act.findViewById(R.id.file_name_text);
        fileTimestamp = act.findViewById(R.id.file_timestamp);
        fileDuration = act.findViewById(R.id.file_duration);
        fileSize = act.findViewById(R.id.file_size);

        buttonState = ButtonState.WAITING_FOR_RECORDING;
        controlButton = act.findViewById(R.id.record);
        controlButton.setOnClickListener(new AudioTraitOnClickListener());
        controlButton.requestFocus();

        audioInfoCard.setOnLongClickListener( view -> {
            ((CollectActivity) getContext()).showObservationMetadataDialog();
            // handle the long click when some audio was saved
//            if(fileNameText.getText().toString().equals(getContext().getString(R.string.trait_layout_data_stored))){
//                ((CollectActivity) getContext()).showObservationMetadataDialog();
//            }
            return true;
        });
    }

    @Override
    public void afterLoadExists(CollectActivity act, String value) {
        super.afterLoadExists(act, value);
        if (value != null && value.equals("NA")) {
            buttonState = ButtonState.WAITING_FOR_RECORDING;
            controlButton.setImageResource(buttonState.getImageId());
            setNAAudioInfoCard();
        } else {
            DocumentFile file = DocumentFile.fromSingleUri(getContext(), Uri.parse(value));
            if (file != null && file.exists()) {
                this.recordingLocation = file.getUri();
                buttonState = ButtonState.WAITING_FOR_PLAYBACK;
                controlButton.setImageResource(buttonState.getImageId());
                setAudioInfoCard(file);
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
        hideAudioInfoCard();
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
        if (model != null && !model.getValue().isEmpty() && !model.getValue().equals("NA")) {
            buttonState = ButtonState.WAITING_FOR_PLAYBACK;
            controlButton.setImageResource(buttonState.getImageId());
        } else { // for NA, change the button state to WAITING_FOR_RECORDING
            buttonState = ButtonState.WAITING_FOR_RECORDING;
            controlButton.setImageResource(buttonState.getImageId());
            fileNameText.setText("");
            getCollectInputView().setText("");
        }
    }

    @Override
    public void deleteTraitListener() {
        deleteRecording();
        removeTrait(getCurrentTrait());
        super.deleteTraitListener();
        recordingLocation = null;
        mediaPlayer = null;
        refreshButtonState();
        hideAudioInfoCard();
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

    private void setNAAudioInfoCard() {
        audioInfoCard.setVisibility(View.VISIBLE);
        fileMetadataLayout.setVisibility(View.GONE);

        fileNameText.setText("NA");
        fileNameText.setGravity(Gravity.CENTER);
    }

    private void setAudioInfoCard(DocumentFile file) {
        audioInfoCard.setVisibility(View.VISIBLE);
        fileMetadataLayout.setVisibility(View.VISIBLE);

        fileNameText.setGravity(Gravity.START);
        fileNameText.setText(getContext().getString(R.string.trait_audio_placeholder_filename));

        fileTimestamp.setText(formatDateTime(file.lastModified()));
        fileSize.setText(getFileSize(file.length()));
        fileDuration.setText(getAudioDuration(file.getUri()));
    }

    private void hideAudioInfoCard() {
        audioInfoCard.setVisibility(View.GONE);
    }

    private String getAudioDuration(Uri uri) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(getContext(), uri);
            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

            if (time == null) {
                Log.w("AudioTraitLayout", "Could not extract file duration");
                return "00:00";
            }


            long timeInMillis = Long.parseLong(time);

            // ceil any fraction of a second to the next second
            long totalSeconds = (long) Math.ceil(timeInMillis / 1000.0);
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;

            return String.format("%02d:%02d", minutes, seconds);
        } catch (Exception e) {
            Log.e("AudioTraitLayout", "Error getting file duration", e);
            return "00:00";
        }
    }

    private String getFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exponent = (int) (Math.log(bytes) / Math.log(1024));
        String prefix = "KMGTPE".charAt(exponent-1) + "";
        // bytes / 1024^(exponent)
        return String.format("%.2f %sB", bytes / Math.pow(1024, exponent), prefix);
    }

    private String formatDateTime(long timestamp) {
        return new SimpleDateFormat("MMM d, yyyy | h:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    public boolean isAudioRecording(){
        return buttonState == ButtonState.RECORDING;
    }

    public boolean isAudioPlaybackPlaying(){
        return buttonState == ButtonState.PLAYING;
    }

    private enum ButtonState {
        WAITING_FOR_RECORDING(R.drawable.trait_audio),
        RECORDING(R.drawable.trait_audio_stop),
        WAITING_FOR_PLAYBACK(R.drawable.trait_audio_play),
        PLAYING(R.drawable.trait_audio_stop);

        private final int imageId;

        ButtonState(int imageId) {
            this.imageId = imageId;
        }

        public int getImageId() {
            return imageId;
        }
    }

    public class AudioTraitOnClickListener implements OnClickListener {

        // use FieldAudioHelper mainly to start and stop recording
        // eliminates repeated methods
        FieldAudioHelper fieldAudioHelper = new FieldAudioHelper((Context) controller);

        @Override
        public void onClick(View view) {

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
                    // check if field audio is already recording
                    if (controller.isFieldAudioRecording()) {
                        Toast.makeText((Context) controller, R.string.field_audio_recording_warning, Toast.LENGTH_SHORT).show();
                    } else {
                        startPlayback();
                        buttonState = ButtonState.PLAYING;
                        enableNavigation = false;
                    }
                    break;
                case WAITING_FOR_RECORDING:
                    // check if field audio is already recording
                    if (controller.isFieldAudioRecording()) {
                        Toast.makeText((Context) controller, R.string.field_audio_recording_warning, Toast.LENGTH_SHORT).show();
                    } else {
                        startRecording();
                        buttonState = ButtonState.RECORDING;
                        enableNavigation = false;
                    }
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
                // if getRecordingLocation returns null, default back to recordingLocation

                // when audio is just recorded and played, uri comes from getRecordingLocation
                // when user changes the plot or exits collect screen, uri comes from recordingLocation in afterLoadExists
                if (fieldAudioHelper.getRecordingLocation() != null) {
                    mediaPlayer = MediaPlayer.create(getContext(), fieldAudioHelper.getRecordingLocation());
                } else {
                    mediaPlayer = MediaPlayer.create(getContext(), recordingLocation);
                }
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
                removeTrait(getCurrentTrait());
                hideAudioInfoCard();
                fieldAudioHelper.startRecording(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void stopRecording() {
            try {
                fieldAudioHelper.stopRecording();
                Uri audioUri = fieldAudioHelper.getRecordingLocation();
                if (audioUri != null) {
                    updateObservation(getCurrentTrait(), audioUri.toString());
                    getCollectInputView().setText(audioUri.toString());

                    // update recordingLocation
                    DocumentFile file = DocumentFile.fromSingleUri(getContext(), audioUri);
                    if (file != null && file.exists()) {
                        AudioTraitLayout.this.recordingLocation = file.getUri();
                        setAudioInfoCard(file);
                    }
                }
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
    }
}