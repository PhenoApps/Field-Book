package com.fieldbook.tracker.objects;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.DocumentTreeUtil;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class FieldAudioObject {
    private Context context;

    private MediaRecorder mediaRecorder;
    private Uri recordingLocation;

    private SharedPreferences ep;

    private ButtonState buttonState = ButtonState.WAITING_FOR_RECORDING;


    public FieldAudioObject(Context context) {
        this.context = context;
        ep = context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0);
    }

    public boolean isRecording() {
        if (buttonState == ButtonState.WAITING_FOR_RECORDING) return false;
        else return true;
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
        String fieldAlias = ep.getString(GeneralKeys.FIELD_FILE, "");
        try {
            mGeneratedName = "field_audio_" + ((CollectActivity) context).getCRange().plot_id + "_" + fieldAlias + " " + timeStamp.format(c.getTime());
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
        RECORDING(R.drawable.ic_tb_field_mic_on);

        private int imageId;

        ButtonState(int imageId) {
            this.imageId = imageId;
        }
    }
}