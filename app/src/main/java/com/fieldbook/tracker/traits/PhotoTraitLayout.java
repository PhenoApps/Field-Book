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
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.CancellationSignal;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Html;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.BitmapCompat;
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

import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PhotoTraitLayout extends BaseTraitLayout {

    public static final int PICTURE_REQUEST_CODE = 252;

    private ArrayList<Bitmap> drawables;
    private Gallery photo;
    private GalleryImageAdapter photoAdapter;
    private String mCurrentPhotoPath;

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

        loadLayoutWork();
    }

    public void loadLayoutWork() {

        // Always set to null as default, then fill in with trait value
        drawables = new ArrayList<>();

        DocumentFile photosDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "thumbnails");

        //back down to the photos directory if thumbnails don't exist
        if (photosDir == null || photosDir.listFiles().length == 0) {
            generateThumbnails();
        }

        if (photosDir != null) {

            String plot = getCRange().plot_id;

            List<DocumentFile> locations = DocumentTreeUtil.Companion.getPlotMedia(photosDir, plot, ".jpg");

            if (!locations.isEmpty()) {

                for (DocumentFile imageFile : locations) {

                    if (imageFile != null && imageFile.exists()) {

                        String name = imageFile.getName();

                        if (name != null) {

                            if (name.contains(plot)) {

                                Bitmap bmp = decodeBitmap(imageFile.getUri());

                                if (bmp != null) {

                                    drawables.add(bmp);

                                }
                            }
                        }
                    }
                }
            }

            loadGallery();
        }
    }

    private void loadGallery() {

        DocumentFile photosDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "photos");

        if (photosDir != null) {

            List<DocumentFile> photos = DocumentTreeUtil.Companion.getPlotMedia(photosDir, getCRange().plot_id, ".jpg");

            if (!photos.isEmpty()) {
                photoAdapter = new GalleryImageAdapter((Activity) getContext(), drawables);
                photo.setAdapter(photoAdapter);
                photo.setSelection(photo.getCount() - 1);
                photo.setOnItemClickListener((arg0, arg1, pos, arg3) ->
                        displayPlotImage(photos.get(pos).getUri()));
            } else {
                photoAdapter = new GalleryImageAdapter((Activity) getContext(), drawables);
                photo.setAdapter(photoAdapter);
            }
        }
    }

    private Bitmap decodeBitmap(Uri uri) {
        try {
            InputStream input = getContext().getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(input);
            input.close();
            return bmp;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void generateThumbnails() {

        DocumentFile photosDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "photos");

        if (photosDir != null) {

           DocumentFile[] files = photosDir.listFiles();
           for (DocumentFile doc : files) {

               createThumbnail(doc.getUri());
           }
        }
    }

    private void createThumbnail(Uri uri) {

        //create thumbnail
        try {

            DocumentFile thumbsDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "thumbnails");

            String name = BaseDocumentTreeUtil.Companion.getStem(uri, getContext());

            if (thumbsDir != null) {

                Bitmap bmp = MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), uri);

                bmp = Bitmap.createScaledBitmap(bmp, 256, 256, true);

                DocumentFile thumbnail = thumbsDir.createFile("image/*", name + ".jpg");

                if (thumbnail != null) {

                    OutputStream output = getContext().getContentResolver().openOutputStream(thumbnail.getUri());
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, output);
                    output.close();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteTraitListener() {
        deletePhotoWarning(false, null);
    }

    public void brapiDelete(Map newTraits) {
        deletePhotoWarning(true, newTraits);
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

    public void makeImage(TraitObject currentTrait, Map newTraits) {

        DocumentFile photosDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "photos");
        DocumentFile thumbsDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "thumbnails");

        if (photosDir != null && thumbsDir != null) {

            DocumentFile file = photosDir.findFile(mCurrentPhotoPath);

            if (file != null) {

                try {

                    Utils.scanFile(getContext(), file.getUri().toString(), "image/*");

                    createThumbnail(file.getUri());

                    updateTraitAllowDuplicates(currentTrait.getTrait(), "photo", mCurrentPhotoPath, null, newTraits);

                    loadLayoutWork();

                } catch (Exception e) {
                    e.printStackTrace();
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
                    DocumentFile thumbsDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(getContext(), "thumbnails");

                    List<DocumentFile> photosList = DocumentTreeUtil.Companion.getPlotMedia(photosDir, getCRange().plot_id, ".jpg");
                    List<DocumentFile> thumbsList = DocumentTreeUtil.Companion.getPlotMedia(thumbsDir, getCRange().plot_id, ".jpg");

                    int index = photo.getSelectedItemPosition();
                    DocumentFile selected = photosList.get(index);
                    DocumentFile thumbSelected = thumbsList.get(index);
                    Uri item = selected.getUri();
                    if (!brapiDelete) {
                        selected.delete();
                        thumbSelected.delete();
                        photosList.remove(index);
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

                } else {

                    // If an NA exists, delete it
                    ConfigActivity.dt.deleteTraitByValue(exp_id, getCRange().plot_id, getCurrentTrait().getTrait(), "NA");
                }

                loadLayoutWork();
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

            mCurrentPhotoPath = generatedName;

            Log.w("File", dir.getUri() + generatedName);

            DocumentFile file = dir.createFile("image/jpg", generatedName);

            if (file != null) {

                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                // Ensure that there's a camera activity to handle the intent
                if (takePictureIntent.resolveActivity(getContext().getPackageManager()) != null) {

                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, file.getUri());

                    ((Activity) getContext()).startActivityForResult(takePictureIntent, PICTURE_REQUEST_CODE);
                }
            }
        }
    }

    private String getRep() {
        int repInt = ConfigActivity.dt.getRep(getCRange().plot_id, getCurrentTrait().getTrait());
        return String.valueOf(repInt);
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