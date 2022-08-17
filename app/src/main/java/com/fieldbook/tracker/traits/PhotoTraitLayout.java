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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Html;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.activities.ConfigActivity;
import com.fieldbook.tracker.adapters.GalleryImageAdapter;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.DialogUtils;
import com.fieldbook.tracker.utilities.DocumentTreeUtil;
import com.fieldbook.tracker.utilities.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PhotoTraitLayout extends BaseTraitLayout {

    private ArrayList<Drawable> drawables;
    private Gallery photo;
    private GalleryImageAdapter photoAdapter;
    private Uri currentPhotoPath = null;
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

        // Always set to null as default, then fill in with trait value
        drawables = new ArrayList<>();

        DocumentFile photosDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "photos");

        if (photosDir != null) {

            String plot = getCRange().plot_id;

            List<DocumentFile> locations = DocumentTreeUtil.Companion.getPlotMedia(photosDir, plot, ".jpg");

            if (!locations.isEmpty()) {

                for (DocumentFile imageFile : locations) {

                    if (imageFile != null && imageFile.exists()) {

                        String name = imageFile.getName();

                        if (name != null) {

                            if (name.contains(plot)) {

                                drawables.add(new BitmapDrawable(displayScaledSavedPhoto(imageFile.getUri())));

                            }
                        }
                    }
                }
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

    private Bitmap displayScaledSavedPhoto(Uri path) {
        if (path == null) {
            String message = getContext().getString(R.string.trait_error_photo_missing);
            Toast.makeText(getContext().getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            return null;
        }

        try {
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;

            Bitmap bmp = MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), path);

            int photoW = bmp.getWidth();
            int photoH = bmp.getHeight();

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

            Bitmap correctBmp = null;

            try {

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                File f = new File(getContext().getExternalMediaDirs()[0], "temp.jpg");
                FileOutputStream fis = new FileOutputStream(f);
                bos.writeTo(fis);
                bos.close();
                fis.close();

                //TODO check how to save EXIF to the media store, right now it is undefined
                Matrix mat = new Matrix();
                mat.postRotate(90);

                correctBmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mat, true);

                f.delete();

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

    private void displayPlotImage(Uri path) {

        try {

            Log.w("Display path", path.toString());

            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(path, "image/*");
            getContext().startActivity(intent);

        } catch (Exception e) {

            e.printStackTrace();

        }
    }

    public void makeImage(TraitObject currentTrait, Map<String, String> newTraits, Boolean success) {

        DocumentFile photosDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "photos");

        if (photosDir != null && currentPhotoPath != null) {

            DocumentFile file = DocumentFile.fromSingleUri(getContext(), currentPhotoPath);

            if (file != null) {

                if (success) {

                    List<DocumentFile> p = DocumentTreeUtil.Companion.getPlotMedia(photosDir, getCRange().plot_id, ".jpg");

                    Utils.scanFile(getContext(), file.getUri().toString(), "image/*");

                    drawables.add(new BitmapDrawable(displayScaledSavedPhoto(file.getUri())));

                    updateTraitAllowDuplicates(currentTrait.getTrait(), "photo", currentPhotoPath.toString(), null, newTraits);

                    photoAdapter = new GalleryImageAdapter((Activity) getContext(), drawables);

                    photo.setAdapter(photoAdapter);
                    photo.setSelection(photoAdapter.getCount() - 1);
                    photo.setOnItemClickListener((arg0, arg1, pos, arg3) -> {
                        if (p.size() > pos) {
                            displayPlotImage(p.get(pos).getUri());
                        }
                    });

                } else {

                    file.delete();

                }
            }
        }
    }

    private void updateTraitAllowDuplicates(String parent, String trait, String value, String newValue, Map newTraits) {

        if (!value.equals(newValue)) {

            if (getCRange() == null || getCRange().plot_id.length() == 0) {
                return;
            }

            Log.d("Field Book", trait + " " + value);

            newTraits.remove(parent);

            newTraits.put(parent, value);

            String exp_id = Integer.toString(getPrefs().getInt(GeneralKeys.SELECTED_FIELD_ID, 0));

            Observation observation = ConfigActivity.dt.getObservationByValue(exp_id, getCRange().plot_id, parent, value);

            ConfigActivity.dt.deleteTraitByValue(exp_id, getCRange().plot_id, parent, value);

            ConfigActivity.dt.insertUserTraits(getCRange().plot_id,
                    parent,
                    trait,
                    newValue == null ? value : newValue,
                    getPrefs().getString(GeneralKeys.FIRST_NAME, "") + " " + getPrefs().getString(GeneralKeys.LAST_NAME, ""),
                    getPrefs().getString(GeneralKeys.LOCATION, ""),
                    "",
                    exp_id,
                    observation.getDbId(),
                    observation.getLastSyncedTime()); //TODO add notes and exp_id
        }
    }

    private void deletePhotoWarning(final Boolean brapiDelete, final Map newTraits) {

        String exp_id = Integer.toString(getPrefs().getInt(GeneralKeys.SELECTED_FIELD_ID, 0));

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

                    DocumentFile photosDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "photos");
                    List<DocumentFile> photosList = DocumentTreeUtil.Companion.getPlotMedia(photosDir, getCRange().plot_id, ".jpg");

                    int index = photo.getSelectedItemPosition();
                    DocumentFile selected = photosList.get(index);
                    Uri item = selected.getUri();
                    if (!brapiDelete) {
                        selected.delete();
                        photosList.remove(index);
                        drawables.remove(photo.getSelectedItemPosition());
                    }

                    DocumentFile file = DocumentFile.fromSingleUri(getContext(), item);
                    if (file != null && file.exists()) {
                        file.delete();
                    }

                    // Remove individual images
                    if (brapiDelete) {
                        updateTraitAllowDuplicates(getCurrentTrait().getTrait(), "photo", item.toString(), "NA", newTraits);
                        loadLayout();
                    } else {
                        ConfigActivity.dt.deleteTraitByValue(exp_id, getCRange().plot_id, getCurrentTrait().getTrait(), item.toString());
                    }

                    // Only do a purge by trait when there are no more images left
                    if (!brapiDelete) {
                        if (photosList.size() == 0)
                            removeTrait(getCurrentTrait().getTrait());
                    }

                    photoAdapter = new GalleryImageAdapter((Activity) getContext(), drawables);

                } else {
                    // If an NA exists, delete it
                    ConfigActivity.dt.deleteTraitByValue(exp_id, getCRange().plot_id, getCurrentTrait().getTrait(), "NA");
                    ArrayList<Drawable> emptyList = new ArrayList<>();

                    photoAdapter = new GalleryImageAdapter((Activity) getContext(), emptyList);

                }

                photo.setAdapter(photoAdapter);
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

        DocumentFile dir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "photos");

        if (dir != null) {

            String generatedName = getCRange().plot_id + "_" + getCurrentTrait().getTrait() + "_" + getRep() + "_" + timeStamp.format(Calendar.getInstance().getTime()) + ".jpg";

            Log.w("File", dir.getUri() + generatedName);

            DocumentFile file = dir.createFile("image/jpg", generatedName);

            if (file != null) {

                currentPhotoPath = file.getUri();

                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                // Ensure that there's a camera activity to handle the intent
                if (takePictureIntent.resolveActivity(getContext().getPackageManager()) != null) {

                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, file.getUri());

                    ((Activity) getContext()).startActivityForResult(takePictureIntent, 252);
                }
            }
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

            DocumentFile photosDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "photos");

            if (photosDir != null) {

                List<DocumentFile> photos = DocumentTreeUtil.Companion.getPlotMedia(photosDir, getCRange().plot_id, ".jpg");

                if (!photos.isEmpty()) {
                    photoAdapter = new GalleryImageAdapter((Activity) getContext(), drawables);
                    photo.setAdapter(photoAdapter);
                    photo.setSelection(photo.getCount() - 1);
                    photo.setOnItemClickListener((arg0, arg1, pos, arg3) -> {
                        if (photos.size() > pos) {
                            displayPlotImage(photos.get(pos).getUri());
                        }
                    });
                } else {
                    photoAdapter = new GalleryImageAdapter((Activity) getContext(), drawables);
                    photo.setAdapter(photoAdapter);
                }
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

                DocumentFile photosDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "photos");

                String plot = getCRange().plot_id;

                List<DocumentFile> locations = DocumentTreeUtil.Companion.getPlotMedia(photosDir, plot, ".jpg");

                if (photosDir != null) {
                    // Do not take photos if limit is reached
                    if (m == 0 || locations.size() < m) {
                        takePicture();
                    } else
                        Utils.makeToast(getContext(),getContext().getString(R.string.traits_create_photo_maximum));
                }

            } catch (Exception e) {
                e.printStackTrace();
                Utils.makeToast(getContext(),getContext().getString(R.string.trait_error_hardware_missing));
            }
        }
    }
}