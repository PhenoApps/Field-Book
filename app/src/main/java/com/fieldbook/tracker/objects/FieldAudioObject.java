package com.fieldbook.tracker.objects;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.view.MenuItem;

import androidx.documentfile.provider.DocumentFile;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.utilities.DocumentTreeUtil;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class FieldAudioObject {

    static public String type = "audio";

    private Context context;

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private Uri recordingLocation;
    private MenuItem controlButton;

    private ButtonState buttonState = ButtonState.WAITING_FOR_RECORDING;


    public FieldAudioObject(Context context) {
        this.context = context;
    }

    public String getButtonState() {
        if (buttonState == ButtonState.WAITING_FOR_RECORDING) return "mic_off";
        else return "mic_on";
    }

    public void startRecording() {
        try {
//                removeTrait(getCurrentTrait().getTrait());
//                audioRecordingText.setText("");
            prepareRecorder();
            if (mediaRecorder != null) {
                mediaRecorder.start();
                buttonState = ButtonState.RECORDING;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRecording() {
        try {
            mediaRecorder.stop();
            buttonState = ButtonState.WAITING_FOR_RECORDING;
            releaseRecorder();
//            updateObservation(getCurrentTrait().getTrait(), "audio", recordingLocation.toString());
//                audioRecordingText.setText(getContext().getString(R.string.trait_layout_data_stored));
//            getCollectInputView().setText(recordingLocation.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setRecordingLocation(String recordingName) {
        DocumentFile audioDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(context, "field_audio");
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
        context.sendBroadcast(i);
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
            mGeneratedName = "field_audio_" + ((CollectActivity) context).getCRange().plot_id + " " + timeStamp.format(c.getTime());
        } catch (Exception e) {
            mGeneratedName = "error " + timeStamp.format(c.getTime());
        }

        setRecordingLocation(mGeneratedName);
        try {
            FileDescriptor fd = context.getContentResolver().openFileDescriptor(recordingLocation, "rw").getFileDescriptor();
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

    private enum ButtonState {
        WAITING_FOR_RECORDING(R.drawable.ic_tb_field_mic_off),
        RECORDING(R.drawable.ic_tb_field_mic_on),

        WAITING_FOR_PLAYBACK(R.drawable.ic_tb_help);

        private int imageId;

        ButtonState(int imageId) {
            this.imageId = imageId;
        }

        public int getImageId() {
            return imageId;
        }
    }
}