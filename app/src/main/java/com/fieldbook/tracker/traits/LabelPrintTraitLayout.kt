package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.BluetoothChooseCallback;
import com.fieldbook.tracker.utilities.BluetoothUtil;
import com.fieldbook.tracker.utilities.Constants;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;

public class LabelPrintTraitLayout extends BaseTraitLayout {

    private static final String TAG = LabelPrintTraitLayout.class.getSimpleName();

    static public String type = "zebra label print";

    private String[] options;
    private String[] labelCopiesArray;
    private String[] labelSizeArray;
    private String simpleSmall;
    private String detailedSmall;
    private String simpleLarge;
    private String detailedLarge;
    private ArrayList<String> optionsList;
    private ArrayAdapter<String> sizeArrayAdapter;
    private ArrayAdapter<String> fieldArrayAdapter;
    private ArrayAdapter<String> copiesArrayAdapter;

    private Spinner labelsize;
    private Spinner textfield1;
    private Spinner textfield2;
    private Spinner textfield3;
    private Spinner textfield4;
    private Spinner barcodefield;
    private Spinner labelcopies;

    private FloatingActionButton connectPrinter;
    private ImageView label;
    private FloatingActionButton printLabel;

    private BluetoothUtil mBluetoothUtil;

    private Activity mActivity = null;

    public void registerReceiver() {
        Log.d(TAG, "Registering mPrinterMessageReceiver");
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mPrinterMessageReceiver,
                new IntentFilter("printer_message"));
    }

    public void unregisterReceiver() {
        Log.d(TAG, "Unregistering mPrinterMessageReceiver");
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mPrinterMessageReceiver);
    }

    public LabelPrintTraitLayout(Context context) { super(context); }

    public LabelPrintTraitLayout(Context context, AttributeSet attrs) { super(context, attrs); }

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
    public int layoutId() {
        return R.layout.trait_label_print;
    }

    private boolean checkPermissions(Activity act) {

        boolean granted = false;

        if (act != null) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                boolean scan = PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(act,
                        android.Manifest.permission.BLUETOOTH_SCAN);
                boolean connect = PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(act,
                        android.Manifest.permission.BLUETOOTH_CONNECT);
                if (scan && connect) {
                    granted = true;
                } else {
                    ActivityCompat.requestPermissions(act, new String[] { android.Manifest.permission.BLUETOOTH_CONNECT,
                            android.Manifest.permission.BLUETOOTH_SCAN }, Constants.PERM_REQ);
                }
            } else {
                boolean admin = PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(act,
                        android.Manifest.permission.BLUETOOTH_ADMIN);
                boolean bluetooth = PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(act,
                        android.Manifest.permission.BLUETOOTH);
                boolean fine = PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(act,
                        android.Manifest.permission.ACCESS_FINE_LOCATION);
                boolean coarse = PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(act,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION);
                if (admin && bluetooth && fine && coarse) {
                    granted = true;
                } else {
                    ActivityCompat.requestPermissions(act, new String[] {
                            android.Manifest.permission.BLUETOOTH_ADMIN,
                            android.Manifest.permission.BLUETOOTH,
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION }, Constants.PERM_REQ);
                }
            }
        }

        return granted;
    }

    /**
     * The PrintThread class sends messages to this receiver.
     * When a message is found, it will show the message as a toast.
     * If the print was successful it will be saved to the db.
     */
    private final BroadcastReceiver mPrinterMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent != null && intent.getExtras() != null) {

                String message = intent.getExtras().getString("message");
                int numLabels = intent.getExtras().getInt("numLabels", 0);
                String plotId = intent.getExtras().getString("plotId");
                String traitId = intent.getExtras().getString("traitId");
                String traitFormat = intent.getExtras().getString("traitFormat");

                if (message != null) {

                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();

                }

                if (numLabels > 0 && plotId != null && traitId != null) {
                    String labelNumber = String.valueOf(numLabels);
                    CollectActivity activity = (CollectActivity) getContext();
                    activity.insertPrintObservation(plotId, traitId, labelNumber);
                    activity.refreshRepeatedValuesToolbarIndicator();
                }

            }
        }
    };

    @Override
    public void init(Activity act) {

        mActivity = act;

        connectPrinter = act.findViewById(R.id.connectPrinterButton);
        printLabel = act.findViewById(R.id.printLabelButton);

        mBluetoothUtil = new BluetoothUtil();
        Integer studyId = getPrefs().getInt(GeneralKeys.SELECTED_FIELD_ID, 0);
        String[] prefixTraits = getDatabase().getAllObservationUnitAttributeNames(studyId);
        optionsList = new ArrayList<>(Arrays.asList(prefixTraits));
        optionsList.add(0, getContext().getString(R.string.field_name_attribute));
        optionsList.add(getContext().getString(R.string.trait_layout_print_label_date_option));
        optionsList.add(getContext().getString(R.string.trait_layout_print_label_blank_option));
        options = new String[optionsList.size()];
        optionsList.toArray(options);

        fieldArrayAdapter = new ArrayAdapter<>(
                getContext(), R.layout.custom_spinner_layout, options);

        simpleSmall = "2\" x 1\" " + getContext().getString(R.string.trait_layout_print_label_simple_text);
        detailedSmall = "2\" x 1\" " + getContext().getString(R.string.trait_layout_print_label_detailed_text);
        simpleLarge = "3\" x 2\" " + getContext().getString(R.string.trait_layout_print_label_simple_text);
        detailedLarge = "3\" x 2\" " + getContext().getString(R.string.trait_layout_print_label_detailed_text);

        labelSizeArray = new String[]{simpleLarge, detailedLarge, simpleSmall, detailedSmall};
        sizeArrayAdapter = new ArrayAdapter<>(
                getContext(), R.layout.custom_spinner_layout, labelSizeArray);

        labelCopiesArray = new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};
        copiesArrayAdapter = new ArrayAdapter<>(
                getContext(), R.layout.custom_spinner_layout, labelCopiesArray);

        labelsize = act.findViewById(R.id.labelsize);
        textfield1 = act.findViewById(R.id.textfield);
        textfield2 = act.findViewById(R.id.textfield2);
        textfield3 = act.findViewById(R.id.textfield3);
        textfield4 = act.findViewById(R.id.textfield4);
        barcodefield = act.findViewById(R.id.barcodefield);
        labelcopies = act.findViewById(R.id.labelcopies);

        labelsize.setAdapter(sizeArrayAdapter);
        textfield1.setAdapter(fieldArrayAdapter);
        textfield2.setAdapter(fieldArrayAdapter);
        textfield3.setAdapter(fieldArrayAdapter);
        textfield4.setAdapter(fieldArrayAdapter);
        barcodefield.setAdapter(fieldArrayAdapter);
        labelcopies.setAdapter(copiesArrayAdapter);

        label = act.findViewById(R.id.labelPreview);

        label.requestFocus();
    }

    @Override
    public void loadLayout() {
        super.loadLayout();

        try {

            // Change spinner visibility, label example image for detailed label option
            labelsize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int pos, long arg3) {
                    Log.d(TAG, labelsize.getSelectedItem().toString());

                    if (labelsize.getSelectedItem().toString().equals(detailedLarge) || labelsize.getSelectedItem().toString().equals(detailedSmall)) {
                        ((View) textfield2.getParent()).setVisibility(View.VISIBLE);
                        ((View) textfield3.getParent()).setVisibility(View.VISIBLE);
                        ((View) textfield4.getParent()).setVisibility(View.VISIBLE);
                        label.setBackgroundResource(R.drawable.label_detailed);
                    } else { //else setVisibility(View.GONE) for text spinners=
                        ((View) textfield2.getParent()).setVisibility(View.GONE);
                        ((View) textfield3.getParent()).setVisibility(View.GONE);
                        ((View) textfield4.getParent()).setVisibility(View.GONE);
                        label.setBackgroundResource(R.drawable.label_simple);
                    }

                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {

                }
            });

            //region SpinnersEnabledFix
            labelsize.setSelection(sizeArrayAdapter.getPosition(getPrefs().getString("SIZE", labelSizeArray[0])));
            labelsize.setEnabled(true);

            textfield1.setSelection(fieldArrayAdapter.getPosition(getPrefs().getString("TEXT", options[1])));
            textfield1.setEnabled(true);

            textfield2.setSelection(fieldArrayAdapter.getPosition(getPrefs().getString("TEXT2", options[2])));
            textfield2.setEnabled(true);

            textfield3.setSelection(fieldArrayAdapter.getPosition(getPrefs().getString("TEXT3", getContext().getString(R.string.trait_layout_print_label_date_option))));
            textfield3.setEnabled(true);

            textfield4.setSelection(fieldArrayAdapter.getPosition(getPrefs().getString("TEXT4", getContext().getString(R.string.field_name_attribute))));
            textfield4.setEnabled(true);

            barcodefield.setSelection(fieldArrayAdapter.getPosition(getPrefs().getString("BARCODE", getPrefs().getString(GeneralKeys.UNIQUE_NAME, ""))));
            barcodefield.setEnabled(true);

            labelcopies.setSelection(copiesArrayAdapter.getPosition(getPrefs().getString("COPIES", labelCopiesArray[0])));
            labelcopies.setEnabled(true);
            //endregion

        } catch(ArrayIndexOutOfBoundsException aobe) {

            String message = aobe.getLocalizedMessage();

            if (message != null) {

                Log.d("FieldBookError", message);

            } else {

                Log.d("FieldBookError", "Spinner adapter error in print label trait.");
            }

            aobe.printStackTrace();

        } catch (NullPointerException e) {

            String message = e.getLocalizedMessage();

            if (message != null) {

                Log.d("FieldBookError", message);

            } else {

                Log.d("FieldBookError", "Null pointer exception occurred when loading spinner data.");
            }

            e.printStackTrace();

        }
        connectPrinter.setOnClickListener(view -> {
            if (checkPermissions(mActivity)) {
                mBluetoothUtil.choose(getContext(), new BluetoothChooseCallback() {
                    @Override
                    public void onDeviceChosen(String newDeviceName) {
                        saveDeviceNamePreference(newDeviceName);
                    }
                });
            } else {
                Toast.makeText(getContext(), R.string.permission_ask_bluetooth, Toast.LENGTH_SHORT).show();
            }
        });

        /*j
         * This section handles print events. TODO: Create a label prototype based class. Move most of this logic to a function/class. chaneylc 8/26/2020
         * More info on prototyping: https://refactoring.guru/design-patterns/prototype
         */
        printLabel.setOnClickListener(view -> {

            if (checkPermissions(mActivity)) {
                HashMap<String, String> labelSizes = new HashMap<>();
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

                // Save selected options for next time
                SharedPreferences.Editor ed = getPrefs().edit();
                ed.putString("SIZE", size);

                if (textfield1 != null) {
                    ed.putString("TEXT", textfield1.getSelectedItem().toString());
                }
                if (textfield2 != null && textfield2.getSelectedItem() != null) {
                    ed.putString("TEXT2", textfield2.getSelectedItem().toString());
                }
                if (textfield3 != null && textfield3.getSelectedItem() != null) {
                    ed.putString("TEXT3", textfield3.getSelectedItem().toString());
                }
                if (textfield4 != null && textfield4.getSelectedItem() != null) {
                    ed.putString("TEXT4", textfield4.getSelectedItem().toString());
                }
                if (barcodefield != null && barcodefield.getSelectedItem() != null) {
                    ed.putString("BARCODE", barcodefield.getSelectedItem().toString());
                }
                if (labelcopies != null && labelcopies.getSelectedItem() != null) {
                    ed.putString("COPIES", labelcopies.getSelectedItem().toString());
                }
                ed.apply();

                int length = barcode.length();
                int barcode_size = 6; // size for simpleSmall labels

                // Scale barcode size by label size and variable field length
                if (size.equals(detailedSmall)) {
                    barcode_size = 5 - (length / 15);
                } else if (size.equals(simpleLarge)) {
                    barcode_size = 10 - (length / 15);
                } else if (size.equals(detailedLarge)) {
                    barcode_size = 9 - (length / 15);
                }

                int dotsAvailable1;
                int dotsAvailable2;

                // Scale text based on label size and variable field length
                if (size.equals(simpleSmall) || size.equals(detailedSmall)) {
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

                if (labelData != null) {

                    labelData = labelData.replace("text1", text1);
                    labelData = labelData.replace("text2", text2);
                    labelData = labelData.replace("text3", text3);
                    labelData = labelData.replace("text4", text4);
                    labelData = labelData.replace("size1", size1);
                    labelData = labelData.replace("size2", size2);
                    labelData = labelData.replace("size3", size3);
                    labelData = labelData.replace("size4", size4);
                    if (barcode.isEmpty()) { // remove barcode if it will not encode anything
                        labelData = labelData.replace("^BQ,,sizeb^FDMA,barcode^FS", "");
                    } else {
                        labelData = labelData.replace("barcode", barcode);
                    }
                    labelData = labelData.replace("sizeb", Integer.toString(barcode_size));

                }
                //Log.d(((MainActivity) getContext()).TAG, labelData);

                if (labelcopies != null) {

                    int copiespos = labelcopies.getSelectedItemPosition();

                    ArrayList<String> labels = new ArrayList<>();
                    for (int j = 0; j <= copiespos; j++) {

                        labels.add(labelData);
                    }

                    if (checkPermissions(mActivity)) {
                        String printing = getContext().getString(R.string.trait_print_label_success);
                        triggerTts(printing);
                        /*
                         * As of v5.0.6 the app no longer requires the extra PrintConnect app.
                         * This bluetooth utility class is used to connect with a paired printer and send print commands.
                         * A local broadcast receiver is used to communicate with the print thread within this utility class.
                         */
                        String printerName = getPrefs().getString(GeneralKeys.LABEL_PRINT_DEVICE_NAME, null);
                        Log.d(TAG, "retrieved printerName is " + printerName);

                        // Retrieve plotId and trait for use in the print command
                        CollectActivity activity = (CollectActivity) getContext();
                        String plotId = activity.getRangeBox().getPlotID();
                        TraitObject trait = activity.getCurrentTrait();

                        if (printerName == null) {
                            mBluetoothUtil.choose(getContext(), new BluetoothChooseCallback() {
                                @Override
                                public void onDeviceChosen(String newDeviceName) {
                                    Log.d(TAG, "Chosen printerName is " + newDeviceName);
                                    saveDeviceNamePreference(newDeviceName);
                                    mBluetoothUtil.print(getContext(), newDeviceName, labels, plotId, trait);
                                }
                            });
                        } else {
                            mBluetoothUtil.print(getContext(), printerName, labels, plotId, trait);
                        }
                    }
                }
            } else {

                Toast.makeText(getContext(), R.string.permission_ask_bluetooth, Toast.LENGTH_SHORT).show();

            }
        });
    }

    private void saveDeviceNamePreference(String newDeviceName) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putString(GeneralKeys.LABEL_PRINT_DEVICE_NAME, newDeviceName);
        editor.apply();
    }

    @Override
    public void deleteTraitListener() {

    }

    /**
     * When the print button is clicked, this function is used to load label data from the UI.
     * @param spinner spinner view
     * @param options spinner adapter array items
     * @return String
     */
    public String getValueFromSpinner(Spinner spinner, String[] options) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar calendar = Calendar.getInstance();

        String value = null;

        String item = (String) spinner.getSelectedItem();

        if (item != null) {
            if (item.equals(getContext().getString(R.string.trait_layout_print_label_date_option))) {
                value = dateFormat.format(calendar.getTime());
            } else if (item.equals(getContext().getString(R.string.field_name_attribute))) {
                value = getPrefs().getString(GeneralKeys.FIELD_FILE, "");
            } else if (item.equals(getContext().getString(R.string.trait_layout_print_label_blank_option))) {
                value = "";
            } else {
                int pos = spinner.getSelectedItemPosition();
                if (pos < options.length) {
                    value = getDatabase().getObservationUnitPropertyValues(options[pos], getCurrentRange().uniqueId);
                }
            }
        }
         /*
        Bug fix for v4.3.3. At times, this data might be null. If its null then replace with an empty string.
        Guessing it was from ConfigActivity.dt.getDropDownRange
        TODO: add messages that detect for empty strings and notify the user chaneylc 8/26/2020
         */
        if (value == null) return "";
        return value;
    }
}