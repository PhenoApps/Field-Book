package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Html;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.fieldbook.tracker.activities.ConfigActivity;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.adapters.GalleryImageAdapter;
import com.fieldbook.tracker.utilities.DialogUtils;
import com.fieldbook.tracker.utilities.PrefsConstants;
import com.fieldbook.tracker.utilities.Utils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

public class PhotoTraitLayout extends BaseTraitLayout {

    private ArrayList<Drawable> drawables;
    private Gallery photo;
    private GalleryImageAdapter photoAdapter;
    private String mCurrentPhotoPath;
    private ArrayList<String> photoLocation;
    // Creates a new thread to do importing
    private Runnable importRunnable = new Runnable() {
        public void run() {
            new PhotoTraitLayout.LoadImagesRunnableTask().execute(0);
        }
    };

    public PhotoTraitLayout(Context context) {
        super(context);
    }

    public PhotoTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PhotoTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setNaTraitsText() {
    }

    @Override
    public String type() {
        return "photo";
    }

    @Override
    public void init() {
        ImageButton capture = findViewById(R.id.capture);
        capture.setOnClickListener(new PhotoTraitOnClickListener());
        photo = findViewById(R.id.photo);
    }

    @Override
    public void loadLayout() {
        getEtCurVal().removeTextChangedListener(getCvText());
        getEtCurVal().setVisibility(EditText.GONE);
        getEtCurVal().setEnabled(false);

        // Run saving task in the background so we can showing progress dialog
        Handler mHandler = new Handler();
        mHandler.post(importRunnable);
    }

    public void loadLayoutWork() {

        String exp_id = Integer.toString(getPrefs().getInt(PrefsConstants.SELECTED_FIELD_ID, 0));

        // Always set to null as default, then fill in with trait value
        photoLocation = new ArrayList<>();
        drawables = new ArrayList<>();

        File img = new File(getPrefs().getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.PLOTDATAPATH + "/" + getPrefs().getString("FieldFile", "") + "/" + "/photos/");
        if (img.listFiles() != null) {

            //TODO causes crash
            photoLocation = ConfigActivity.dt.getPlotPhotos(exp_id, getCRange().plot_id, getCurrentTrait().getTrait());

            for (int i = 0; i < photoLocation.size(); i++) {
                drawables.add(new BitmapDrawable(displayScaledSavedPhoto(photoLocation.get(i))));
            }
        }

        if (!getNewTraits().containsKey(getCurrentTrait().getTrait())) {
            if (!img.exists()) {
                img.mkdirs();
            }
        }
    }

    @Override
    public void deleteTraitListener() {
        deletePhotoWarning(false, null);
    }

    public void brapiDelete(Map newTraits) {
        deletePhotoWarning(true, newTraits);
    }

    private Bitmap displayScaledSavedPhoto(String path) {
        if (path == null) {
            String message = getContext().getString(R.string.trait_error_photo_missing);
            Toast.makeText(getContext().getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            return null;
        }

        try {
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;

            BitmapFactory.decodeFile(path, bmOptions);
            int photoW = bmOptions.outWidth;
            int photoH = bmOptions.outHeight;

            int targetW;
            int targetH;

            if (photoW > photoH) {
                // landscape
                targetW = 800;
                targetH = 600;
            } else {
                // portrait
                targetW = 600;
                targetH = 800;
            }

            int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

            // Decode the image file into a Bitmap sized to fill the View
            bmOptions.inJustDecodeBounds = false;
            bmOptions.inSampleSize = scaleFactor;
            bmOptions.inPurgeable = true;

            Bitmap bitmap = BitmapFactory.decodeFile(path, bmOptions);
            Bitmap correctBmp = bitmap;

            try {
                File f = new File(path);
                ExifInterface exif = new ExifInterface(f.getPath());
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

                int angle = 0;

                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                    angle = 90;
                } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                    angle = 180;
                } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                    angle = 270;
                }

                Matrix mat = new Matrix();
                mat.postRotate(angle);

                correctBmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mat, true);
            } catch (IOException e) {
                Log.e(CollectActivity.TAG, "-- Error in setting image");
                return BitmapFactory.decodeResource(getResources(), R.drawable.trait_photo_missing);
            } catch (OutOfMemoryError oom) {
                Log.e(CollectActivity.TAG, "-- OOM Error in setting image");
            }

            return correctBmp;

        } catch (Exception e) {
            return BitmapFactory.decodeResource(getResources(), R.drawable.trait_photo_missing);
        }
    }

    private void displayPlotImage(String path) {
        try {
            Log.w("Display path", path);

            File f = new File(path);
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(FileProvider.getUriForFile(getContext(),
                    getContext().getApplicationContext().getPackageName() + ".fileprovider", f), "image/*");
            getContext().startActivity(intent);
        } catch (Exception ignore) {
        }
    }

    public void makeImage(TraitObject currentTrait, Map newTraits) {
        File file = new File(getPrefs().getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.PLOTDATAPATH + "/" + getPrefs().getString("FieldFile", "") + "/photos/",
                mCurrentPhotoPath);

        Utils.scanFile(getContext(), file.getAbsoluteFile());

        photoLocation.add(file.getAbsolutePath());

        drawables.add(new BitmapDrawable(displayScaledSavedPhoto(file.getAbsolutePath())));

        // Force Gallery to update
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = FileProvider.getUriForFile(getContext(), getContext().getApplicationContext().getPackageName() + ".fileprovider", file);
        mediaScanIntent.setData(contentUri);
        getContext().sendBroadcast(mediaScanIntent);

        updateTraitAllowDuplicates(currentTrait.getTrait(), "photo", file.getAbsolutePath(), null, newTraits);

        photoAdapter = new GalleryImageAdapter((Activity) getContext(), drawables);

        photo.setAdapter(photoAdapter);
        photo.setSelection(photoAdapter.getCount() - 1);
    }

    private void updateTraitAllowDuplicates(String parent, String trait, String value, String newValue, Map newTraits) {

        if (!value.equals(newValue)) {

            if (getCRange() == null || getCRange().plot_id.length() == 0) {
                return;
            }

            Log.d("Field Book", trait + " " + value);

            if (newTraits.containsKey(parent))
                newTraits.remove(parent);

            newTraits.put(parent, value);

            String exp_id = Integer.toString(getPrefs().getInt(PrefsConstants.SELECTED_FIELD_ID, 0));

            //Observation observation = ConfigActivity.dt.getObservation(getCRange().plot_id, parent);
            Observation observation = ConfigActivity.dt.getObservationByValue(exp_id, getCRange().plot_id, parent, value);

            ConfigActivity.dt.deleteTraitByValue(exp_id, getCRange().plot_id, parent, value);

            ConfigActivity.dt.insertUserTraits(getCRange().plot_id,
                    parent,
                    trait,
                    newValue == null ? value : newValue,
                    getPrefs().getString("FirstName", "") + " " + getPrefs().getString("LastName", ""),
                    getPrefs().getString("Location", ""),
                    "",
                    exp_id,
                    observation.getDbId(),
                    observation.getLastSyncedTime()); //TODO add notes and exp_id
        }
    }

    private void deletePhotoWarning(final Boolean brapiDelete, final Map newTraits) {

        String exp_id = Integer.toString(getPrefs().getInt(PrefsConstants.SELECTED_FIELD_ID, 0));

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

        builder.setTitle(getContext().getString(R.string.dialog_warning));
        builder.setMessage(getContext().getString(R.string.trait_delete_warning_photo));

        builder.setPositiveButton(getContext().getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                if (brapiDelete) {
                    Toast.makeText(getContext().getApplicationContext(), getContext().getString(R.string.brapi_delete_message), Toast.LENGTH_SHORT).show();
                    //updateTrait(parent, currentTrait.getFormat(), getString(R.string.brapi_na));
                }

                if (photo.getCount() > 0) {
                    String item = photoLocation.get(photo.getSelectedItemPosition());
                    if (!brapiDelete) {
                        photoLocation.remove(photo.getSelectedItemPosition());
                        drawables.remove(photo.getSelectedItemPosition());
                    }

                    File f = new File(item);
                    f.delete();
                    Utils.scanFile((Activity) getContext(), f);

                    // Remove individual images
                    if (brapiDelete) {
                        updateTraitAllowDuplicates(getCurrentTrait().getTrait(), "photo", item, "NA", newTraits);
                        //ConfigActivity.dt.updateTraitByValue(getCRange().plot_id, getCurrentTrait().getTrait(), item, "NA");
                        loadLayout();
                    } else {
                        ConfigActivity.dt.deleteTraitByValue(exp_id, getCRange().plot_id, getCurrentTrait().getTrait(), item);
                    }

                    // Only do a purge by trait when there are no more images left
                    if (!brapiDelete) {
                        if (photoLocation.size() == 0)
                            removeTrait(getCurrentTrait().getTrait());
                    }

                    photoAdapter = new GalleryImageAdapter((Activity) getContext(), drawables);

                    photo.setAdapter(photoAdapter);
                } else {
                    // If an NA exists, delete it
                    ConfigActivity.dt.deleteTraitByValue(exp_id, getCRange().plot_id, getCurrentTrait().getTrait(), "NA");
                    ArrayList<Drawable> emptyList = new ArrayList<>();

                    photoAdapter = new GalleryImageAdapter((Activity) getContext(), emptyList);

                    photo.setAdapter(photoAdapter);
                }
            }

        });

        builder.setNegativeButton(getContext().getString(R.string.dialog_no), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }

        });

        AlertDialog alert = builder.create();
        alert.show();
        DialogUtils.styleDialogs(alert);
    }

    private void takePicture() {
        SimpleDateFormat timeStamp = new SimpleDateFormat(
                "yyyy-MM-dd-hh-mm-ss", Locale.getDefault());

        File dir = new File(getPrefs().getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.PLOTDATAPATH + "/" + getPrefs().getString("FieldFile", "") + "/photos/");

        dir.mkdirs();

        String generatedName = getCRange().plot_id + "_" + getCurrentTrait().getTrait() + "_" + getRep() + "_" + timeStamp.format(Calendar.getInstance().getTime()) + ".jpg";
        mCurrentPhotoPath = generatedName;

        Log.w("File", getPrefs().getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.PLOTDATAPATH + "/" + getPrefs().getString("FieldFile", "") + "/photos/" + generatedName);

        // Save photo capture with timestamp as filename
        File file = new File(getPrefs().getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.PLOTDATAPATH + "/" + getPrefs().getString("FieldFile", "") + "/photos/",
                generatedName);

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getContext().getPackageManager()) != null) {
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                    FileProvider.getUriForFile(getContext(), getContext().getApplicationContext().getPackageName() + ".fileprovider", file));
            ((Activity) getContext()).startActivityForResult(takePictureIntent, 252);
        }
    }

    private String getRep() {
        int repInt = ConfigActivity.dt.getRep(getCRange().plot_id, getCurrentTrait().getTrait());
        return String.valueOf(repInt);
    }

    // Mimics the class used in the csv field importer to run the saving
    // task in a different thread from the UI thread so the app doesn't freeze up.
    private class LoadImagesRunnableTask extends AsyncTask<Integer, Integer, Integer> {

        ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = new ProgressDialog(getContext());
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html.fromHtml(getContext().getResources().getString(R.string.images_loading)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            loadLayoutWork();
            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (dialog.isShowing())
                dialog.dismiss();

            File img = new File(getPrefs().getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.PLOTDATAPATH + "/" + getPrefs().getString("FieldFile", "") + "/" + "/photos/");
            if (img.listFiles() != null) {

                photoAdapter = new GalleryImageAdapter((Activity) getContext(), drawables);
                photo.setAdapter(photoAdapter);
                photo.setSelection(photo.getCount() - 1);
                photo.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                    @Override
                    public void onItemClick(AdapterView<?> arg0,
                                            View arg1, int pos, long arg3) {
                        displayPlotImage(photoLocation.get(photo.getSelectedItemPosition()));
                    }
                });

            } else {
                photoAdapter = new GalleryImageAdapter((Activity) getContext(), drawables);
                photo.setAdapter(photoAdapter);
            }
        }
    }

    private class PhotoTraitOnClickListener implements OnClickListener {
        @Override
        public void onClick(View view) {
            try {
                int m;

                try {
                    m = Integer.parseInt(getCurrentTrait().getDetails());
                } catch (Exception n) {
                    m = 0;
                }

                // Do not take photos if limit is reached
                if (m == 0 || photoLocation.size() < m) {
                    takePicture();
                } else
                    Utils.makeToast(getContext(),getContext().getString(R.string.traits_create_photo_maximum));
            } catch (Exception e) {
                e.printStackTrace();
                Utils.makeToast(getContext(),getContext().getString(R.string.trait_error_hardware_missing));
            }
        }
    }
}