package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.ResultReceiver;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.fieldbook.tracker.activities.ConfigActivity;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.R;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;

public class LabelPrintTraitLayout extends BaseTraitLayout {

    private String[] options;
    private String[] labelCopiesArray;
    private String[] labelSizeArray;
    private ArrayList<String> optionsList;
    private ArrayAdapter<String> sizeArrayAdapter;
    private ArrayAdapter<String> fieldArrayAdapter;
    private ArrayAdapter<String> copiesArrayAdapter;
    private ImageView exampleLabel;

    private Spinner labelsize;
    private Spinner textfield1;
    private Spinner textfield2;
    private Spinner textfield3;
    private Spinner textfield4;
    private Spinner barcodefield;
    private Spinner labelcopies;

    public LabelPrintTraitLayout(Context context) {
        super(context);
    }

    public LabelPrintTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LabelPrintTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setNaTraitsText() {
    }

    @Override
    public String type() {
        return "zebra label print";
    }

    @Override
    public void init() {
        String[] prefixTraits = ConfigActivity.dt.getRangeColumnNames();
        optionsList = new ArrayList<>(Arrays.asList(prefixTraits));
        optionsList.add("date");
        optionsList.add("trial_name");
        optionsList.add("blank");
        options = new String[optionsList.size()];
        optionsList.toArray(options);

        fieldArrayAdapter = new ArrayAdapter<>(
                getContext(), R.layout.custom_spinnerlayout, options);

        labelSizeArray = new String[]{"3\" x 2\" simple", "3\" x 2\" detailed", "2\" x 1\" simple", "2\" x 1\" detailed"};
        sizeArrayAdapter = new ArrayAdapter<>(
                getContext(), R.layout.custom_spinnerlayout, labelSizeArray);

        labelCopiesArray = new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};
        copiesArrayAdapter = new ArrayAdapter<>(
                getContext(), R.layout.custom_spinnerlayout, labelCopiesArray);

        labelsize = findViewById(R.id.labelsize);
        textfield1 = findViewById(R.id.textfield);
        textfield2 = findViewById(R.id.textfield2);
        textfield3 = findViewById(R.id.textfield3);
        textfield4 = findViewById(R.id.textfield4);
        barcodefield = findViewById(R.id.barcodefield);
        labelcopies = findViewById(R.id.labelcopies);

        labelsize.setAdapter(sizeArrayAdapter);
        textfield1.setAdapter(fieldArrayAdapter);
        textfield2.setAdapter(fieldArrayAdapter);
        textfield3.setAdapter(fieldArrayAdapter);
        textfield4.setAdapter(fieldArrayAdapter);
        barcodefield.setAdapter(fieldArrayAdapter);
        labelcopies.setAdapter(copiesArrayAdapter);
    }

    @Override
    public void loadLayout() {

        getEtCurVal().setVisibility(EditText.GONE);

        PackageManager pm = getContext().getPackageManager();
        try {
            pm.getPackageInfo("com.zebra.printconnect", PackageManager.GET_ACTIVITIES);

            final ImageView exampleLabel = findViewById(R.id.labelPreview);


            if (!labelsize.equals(null)) {
                int spinnerPosition = sizeArrayAdapter.getPosition(getPrefs().getString("SIZE", labelSizeArray[0]));
                labelsize.setSelection(spinnerPosition);

                if (labelsize.getSelectedItem().toString().equals("3\" x 2\" detailed") || labelsize.getSelectedItem().toString().equals("2\" x 1\" detailed")) { //if extra text, make extra text spinners visible
                    ((View) textfield2.getParent()).setVisibility(View.VISIBLE);
                    ((View) textfield3.getParent()).setVisibility(View.VISIBLE);
                    ((View) textfield4.getParent()).setVisibility(View.VISIBLE);
                    exampleLabel.setBackgroundResource(R.drawable.label_detailed);
                } else { //else setVisibility(View.GONE) for text spinners=
                    ((View) textfield2.getParent()).setVisibility(View.GONE);
                    ((View) textfield3.getParent()).setVisibility(View.GONE);
                    ((View) textfield4.getParent()).setVisibility(View.GONE);
                    exampleLabel.setBackgroundResource(R.drawable.label_simple);
                }
            }
            if (!textfield1.equals(null)) {
                int spinnerPosition = fieldArrayAdapter.getPosition(getPrefs().getString("TEXT", options[0]));
                textfield1.setSelection(spinnerPosition);
            }
            if (!textfield2.equals(null)) {
                int spinnerPosition = fieldArrayAdapter.getPosition(getPrefs().getString("TEXT2", options[0]));
                textfield2.setSelection(spinnerPosition);
            }
            if (!textfield3.equals(null)) {
                int spinnerPosition = fieldArrayAdapter.getPosition(getPrefs().getString("TEXT3", options[0]));
                textfield3.setSelection(spinnerPosition);
            }
            if (!textfield4.equals(null)) {
                int spinnerPosition = fieldArrayAdapter.getPosition(getPrefs().getString("TEXT4", options[0]));
                textfield4.setSelection(spinnerPosition);
            }
            if (!barcodefield.equals(null)) {
                int spinnerPosition = fieldArrayAdapter.getPosition(getPrefs().getString("BARCODE", options[0]));
                barcodefield.setSelection(spinnerPosition);
            }
            if (!labelcopies.equals(null)) {
                int spinnerPosition = copiesArrayAdapter.getPosition(getPrefs().getString("COPIES", labelCopiesArray[0]));
                labelcopies.setSelection(spinnerPosition);
            }

            // Change spinner visibility, label example image for detailed label option
            labelsize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int pos, long arg3) {
                    Log.d(((CollectActivity) getContext()).TAG, labelsize.getSelectedItem().toString());

                    if (labelsize.getSelectedItem().toString().equals("3\" x 2\" detailed") || labelsize.getSelectedItem().toString().equals("2\" x 1\" detailed")) {
                        ((View) textfield2.getParent()).setVisibility(View.VISIBLE);
                        ((View) textfield3.getParent()).setVisibility(View.VISIBLE);
                        ((View) textfield4.getParent()).setVisibility(View.VISIBLE);
                        exampleLabel.setBackgroundResource(R.drawable.label_detailed);
                    } else { //else setVisibility(View.GONE) for text spinners=
                        ((View) textfield2.getParent()).setVisibility(View.GONE);
                        ((View) textfield3.getParent()).setVisibility(View.GONE);
                        ((View) textfield4.getParent()).setVisibility(View.GONE);
                        exampleLabel.setBackgroundResource(R.drawable.label_simple);
                    }

                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {

                }
            });

            //Get and display printer status
            final TextView printStatus = (TextView) findViewById(R.id.printStatus);
            Intent statusIntent = new Intent();
            statusIntent.setComponent(new ComponentName("com.zebra.printconnect",
                    "com.zebra.printconnect.print.GetPrinterStatusService"));

            ResultReceiver buildIPCSafeReceiver2 = new ResultReceiver(null) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    if (resultCode == 0) { // Result code 0 indicates success
                        // Handle successful printer status retrieval
                        HashMap<String, String> printerStatusMap = (HashMap<String, String>)
                                resultData.getSerializable("PrinterStatusMap");
                        final String successMessage = printerStatusMap.get("friendlyName") + " is connected.";
                        Log.d(((CollectActivity) getContext()).TAG, successMessage);
                        ((Activity) getContext()).runOnUiThread(new Runnable() {
                            public void run() {
                                printStatus.setText(successMessage);
                            }
                        });
                    } else {
                        // Handle unsuccessful printer status retrieval
                        final String errorMessage = resultData.getString("com.zebra.printconnect.PrintService.ERROR_MESSAGE");
                        Log.e(((CollectActivity) getContext()).TAG, errorMessage);
                        ((Activity) getContext()).runOnUiThread(new Runnable() {
                            public void run() {
                                printStatus.setText(errorMessage);
                            }
                        });
                    }

                }
            };

            statusIntent.putExtra("com.zebra.printconnect.PrintService.RESULT_RECEIVER", receiverForSending(buildIPCSafeReceiver2));
            getContext().startService(statusIntent);

            ImageButton printLabel = findViewById(R.id.printLabelButton);
            printLabel.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    HashMap<String, String> labelSizes = new HashMap<String, String>();
                    labelSizes.put(labelSizeArray[0], "^XA^POI^PW609^LL0406^FO0,25^FB599,2,0,C,0^A0,size1,^FDtext1^FS^FO180,120^BQ,,sizeb^FDMA,barcode^FS^XZ");
                    labelSizes.put(labelSizeArray[1], "^XA^POI^PW609^LL0406^FO0,25^FB599,2,0,C,0^A0,size1,^FDtext1^FS^FO30,120^BQ,,sizeb^FDMA,barcode^FS^FO260,140^FB349,2,0,C,0^A0,size2,^FDtext2^FS^FO260,270^FB349,2,0,C,0^A0,size3,^FDtext3^FS^FO260,320^FB349,2,0,C,0^A0,size4,^FDtext4^FS^XZ");
                    labelSizes.put(labelSizeArray[2], "^XA^POI^PW406^LL0203^FO0,10^FB399,2,0,C,0^A0,size1,^FDtext1^FS^FO125,50^BQ,,sizeb^FDMA,barcode^FS^XZ");
                    labelSizes.put(labelSizeArray[3], "^XA^POI^PW406^LL0203^FO15,50^BQ,,sizeb^FDMA,barcode^FS^FO0,10^FB406,1,0,C,0^A0,size1,^FDtext1^FS^FO155,60^FB250,1,0,C,0^A0,size2,^FDtext2^FS^FO155,130^FB250,1,0,C,0^A0,size3,^FDtext3^FS^FO155,155^FB250,1,0,C,0^A0,size4,^FDtext4^FS^XZ");

                    //get and handle selected items from dropdowns
                    String size = labelsize.getSelectedItem().toString();
                    String text1 = getValueFromSpinner(textfield1, options);
                    String text2 = getValueFromSpinner(textfield2, options);
                    String text3 = getValueFromSpinner(textfield3, options);
                    String text4 = getValueFromSpinner(textfield4, options);
                    String barcode = getValueFromSpinner(barcodefield, options);

                    int copiespos = labelcopies.getSelectedItemPosition();
                    String copies = labelcopies.getSelectedItem().toString();

                    // Save selected options for next time
                    SharedPreferences.Editor ed = getPrefs().edit();
                    ed.putString("SIZE", size);
                    ed.putString("TEXT", textfield1.getSelectedItem().toString());
                    ed.putString("TEXT2", textfield2.getSelectedItem().toString());
                    ed.putString("TEXT3", textfield3.getSelectedItem().toString());
                    ed.putString("TEXT4", textfield4.getSelectedItem().toString());
                    ed.putString("BARCODE", barcodefield.getSelectedItem().toString());
                    ed.putString("COPIES", copies);
                    ed.apply();

                    int length = barcode.length();
                    int barcode_size = 6;

                    // Scale barcode based on label size and variable field length
                    switch (size) {
                        case "3\" x 2\" simple":
                            barcode_size = 10 - (length / 15);
                            break;
                        case "3\" x 2\" detailed":
                            barcode_size = 9 - (length / 15);
                            break;
                        case "2\" x 1\" simple":
                        case "2\" x 1\" detailed":
                            barcode_size = 5 - (length / 15);
                            break;
                        default:
                            //Log.d(((MainActivity) getContext()).TAG, "Matched no sizes");
                            break;
                    }

                    int dotsAvailable1;
                    int dotsAvailable2;

                    // Scale text based on label size and variable field length
                    if (size.equals("2\" x 1\" simple") || size.equals("2\" x 1\" detailed")) {
                        dotsAvailable1 = 399;
                        dotsAvailable2 = 250;

                    } else {
                        dotsAvailable1 = 599;
                        dotsAvailable2 = 349;
                    }

                    String size1 = Integer.toString(dotsAvailable1 * 3 / (text1.length() + 13));
                    String size2 = Integer.toString(dotsAvailable2 * 2 / (text2.length() + 5));
                    String size3 = Integer.toString(dotsAvailable2 * 2 / (text3.length() + 5));
                    String size4 = Integer.toString(dotsAvailable2 * 2 / (text4.length() + 5));

                    // Replace placeholders in zpl code
                    String labelData = labelSizes.get(size);
                    labelData = labelData.replace("text1", text1);
                    labelData = labelData.replace("text2", text2);
                    labelData = labelData.replace("text3", text3);
                    labelData = labelData.replace("text4", text4);
                    labelData = labelData.replace("size1", size1);
                    labelData = labelData.replace("size2", size2);
                    labelData = labelData.replace("size3", size3);
                    labelData = labelData.replace("size4", size4);
                    labelData = labelData.replace("barcode", barcode);
                    labelData = labelData.replace("sizeb", Integer.toString(barcode_size));

                    //Log.d(((MainActivity) getContext()).TAG, labelData);

                    String passthroughData = "";
                    for (int j = 0; j <= copiespos; j++) {
                        passthroughData += labelData;
                    }

                    byte[] passthroughBytes = null;

                    try {
                        passthroughBytes = passthroughData.getBytes("UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        // Handle exception
                    }

                    Intent printIntent = new Intent();
                    printIntent.setComponent(new ComponentName("com.zebra.printconnect", "com.zebra.printconnect.print.PassthroughService"));
                    printIntent.putExtra("com.zebra.printconnect.PrintService.PASSTHROUGH_DATA", passthroughBytes);

                    ResultReceiver buildIPCSafeReceiver = new ResultReceiver(null) {
                        @Override
                        protected void onReceiveResult(int resultCode, Bundle resultData) {
                            if (resultCode == 0) {
                                // Handle successful print
                                ((Activity) getContext()).runOnUiThread(new Runnable() {
                                    public void run() {
                                        printStatus.setText("Label printed!");
                                    }
                                });
                            } else {
                                // Error message (null on successful print)
                                // Handle unsuccessful print
                                String errorMessage = resultData.getString("com.zebra.printconnect.PrintService.ERROR_MESSAGE");
                                //Log.e(((MainActivity) getContext()).TAG, "Unable to print label. Make sure the PrintConnect app is installed and connected to your Zebra printer.");
                                ((Activity) getContext()).runOnUiThread(new Runnable() {
                                    public void run() {
                                        printStatus.setText("Unable to print label. Make sure the PrintConnect app is installed and connected to your Zebra printer.");
                                    }
                                });
                            }
                        }
                    };

                    printIntent.setExtrasClassLoader(getContext().getClassLoader());
                    printIntent.putExtra("com.zebra.printconnect.PrintService.RESULT_RECEIVER", receiverForSending(buildIPCSafeReceiver));
                    getContext().startService(printIntent);
                }
            });

        } catch (PackageManager.NameNotFoundException e) {
            //Log.d(((MainActivity) getContext()).TAG, "Print Connect package not found");
            showDownloadDialog();
        }
    }

    @Override
    public void deleteTraitListener() {

    }

    private AlertDialog showDownloadDialog() {
        //Log.d(((MainActivity) getContext()).TAG, "Building Download dialog");
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AppAlertDialog);

        builder.setTitle("Install PrintConnect?")
                .setMessage("This trait requires PrintConnect. Would you like to install it?");

        builder.setPositiveButton("Install", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

                Uri uri = Uri.parse("market://details?id=com.zebra.printconnect");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                try {
                    getContext().startActivity(intent);
                } catch (ActivityNotFoundException ignored) {

                }
            }
        });
        builder.setNegativeButton("Cancel", null);

        return builder.show();
    }

    public ResultReceiver receiverForSending(ResultReceiver actualReceiver) {
        Parcel parcel = Parcel.obtain();
        actualReceiver.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ResultReceiver receiverForSending = ResultReceiver.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return receiverForSending;
    }

    public String getValueFromSpinner(Spinner spinner, String[] options) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar calendar = Calendar.getInstance();
        String value = "";
        if (spinner.getSelectedItem().toString().equals("date")) {
            value = dateFormat.format(calendar.getTime());
        } else if (spinner.getSelectedItem().toString().equals("trial_name")) {
            value = getPrefs().getString("FieldFile", "");
        } else if (spinner.getSelectedItem().toString().equals("blank")) {
            value = "";
        } else {
            int pos = spinner.getSelectedItemPosition();
            value = ConfigActivity.dt.getDropDownRange(options[pos], getCRange().plot_id)[0];
        }
        return value;
    }
}