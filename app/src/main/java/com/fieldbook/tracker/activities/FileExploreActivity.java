package com.fieldbook.tracker.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.fieldbook.tracker.R;

import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FileExploreActivity extends ActivityDialog {

    public static final String EXTRA_RESULT_KEY = "com.fieldbook.tracker.activities.FieldEditorActivity.extras.RESULT";

    public ListView mainListView;
    // Stores names of traversed directories
    private final ArrayList<String> str = new ArrayList<>();
    private final Comparator<DocumentFile> mComparator = (f1, f2) -> {
        if (f1.isDirectory() && !f2.isDirectory()) {
            // Directory before non-directory
            return -1;
        } else if (!f1.isDirectory() && f2.isDirectory()) {
            // Non-directory after directory
            return 1;
        } else {
            // Alphabetic order otherwise
            String f1Name = BaseDocumentTreeUtil.Companion.getStem(f1.getUri(), this);
            String f2Name = BaseDocumentTreeUtil.Companion.getStem(f2.getUri(), this);
            return f1Name.compareToIgnoreCase(f2Name);
        }
    };
    // Check if the first level of the directory structure is the one showing
    private Boolean firstLvl = true;
    private Item[] fileList;
    private String chosenFile;
    private ListAdapter adapter;

    //updates whenever we traverse a directory, loads the current level's files
    private DocumentFile path;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent() != null && getIntent().getExtras() != null) {

            final String data = getIntent().getExtras().getString("path");
            final String[] include = getIntent().getExtras().getStringArray("include");
            final String[] exclude = getIntent().getExtras().getStringArray("exclude");
            final String title = getIntent().getExtras().getString("title");

            path = DocumentFile.fromTreeUri(this, Uri.parse(data));

            if (path != null) {

                mainListView = new ListView(this);

                android.view.WindowManager.LayoutParams params = this.getWindow().getAttributes();
                params.width = LinearLayout.LayoutParams.MATCH_PARENT;
                params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
                this.getWindow().setAttributes(params);

                setContentView(mainListView);

                try {

                    loadFileList(path, exclude, include);

                } catch (NullPointerException e) {

                    setResult(RESULT_CANCELED);

                    finish();
                }

                mainListView.setAdapter(adapter);

                if (title != null && title.length() > 0) {
                    this.setTitle(title);
                }

                mainListView.setOnItemClickListener((arg0, arg1, which, arg3) -> {

                    chosenFile = fileList[which].file;

                    //File sel = new File(path + "/" + chosenFile);
                    DocumentFile file = path.findFile(chosenFile);

                    if (file != null && file.exists() && file.isDirectory()) {
                        firstLvl = false;

                        // Adds chosen directory to list
                        str.add(chosenFile);
                        fileList = null;
                        path = file;

                        loadFileList(file, exclude, include);

                        mainListView.setAdapter(adapter);
                    }

                    // Checks if 'up' was clicked
                    // basically this is a 'fake' file in the list
                    // check if the file is null or file doesn't exist
                    else if (chosenFile.equalsIgnoreCase("up")
                            && (file == null || !file.exists())) {

                        // present directory removed from list
                        String s = str.remove(str.size() - 1);

                        // path modified to exclude present directory
                        path = path.getParentFile();

                        fileList = null;

                        // if there are no more directories in the list, then
                        // its the first level
                        if (str.isEmpty()) {
                            firstLvl = true;
                        }
                        loadFileList(path, exclude, include);
                        mainListView.setAdapter(adapter);
                    }
                    // File picked
                    else {
                        try {
                            if (file != null && file.exists()) {
                                Intent returnIntent = new Intent();
                                returnIntent.putExtra(EXTRA_RESULT_KEY, file.getUri().toString());
                                setResult(RESULT_OK, returnIntent);
                                finish();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

        } else {

            setResult(RESULT_CANCELED);

            finish();
        }
    }

    private void loadFileList(DocumentFile path, String[] exclude, String[] include) {

        // Checks whether path exists
        if (path != null && path.exists()) {

            //list all files in the passed directory
            DocumentFile[] files = path.listFiles();

            //created an 'accepted' list of the files that do not have the excluded (param) extension
            //and do contain the included extensions
            ArrayList<DocumentFile> accepted = new ArrayList<>();

            //populate exclude/include lists before iteration intent extra is optional and can be null
            List<String> excludedExtensions;
            if (exclude == null) excludedExtensions = new ArrayList<>();
            else excludedExtensions = Arrays.asList(exclude);

            List<String> includedExtensions;
            if (include == null) includedExtensions = new ArrayList<>();
            else includedExtensions = Arrays.asList(include);

            for (DocumentFile file : files) {
                String name = file.getName();
                if (name != null) {
//                    // Skip asset files
//                    if (name.contains(".fieldbook") || name.contains("severity.txt") || name.contains("sharedpref.xml")) {
//                        continue;
//                    }

                    if (excludedExtensions.contains(checkExtension(name))) continue;

                    if (includedExtensions.contains(checkExtension(name)) || file.isDirectory()) {
                        accepted.add(file);
                        continue;
                    }
                }
            }

            Arrays.sort(files, mComparator);

            // collect file names
            String[] fileNameList = new String[accepted.size()];
            for (DocumentFile file : accepted) {
                fileNameList[accepted.indexOf(file)] = file.getName();
            }

            // create file list for
            fileList = new Item[fileNameList.length];

            for (int i = 0; i < fileNameList.length; i++) {
                fileList[i] = new Item(fileNameList[i], R.drawable.ic_file_generic);

                DocumentFile file = path.findFile(fileNameList[i]);

                if (file != null && file.exists()) {

                    String name = file.getName();

                    if (name != null) {

                        // Set drawables
                        if (file.isDirectory()) {
                            fileList[i].icon = R.drawable.ic_file_directory;
                            Log.d("DIRECTORY", fileList[i].file);
                        }
                        if (name.toLowerCase().contains(".csv")) {
                            fileList[i].icon = R.drawable.ic_file_csv;
                        }

                        if (name.contains(".xls")) {
                            fileList[i].icon = R.drawable.ic_file_xls;
                        } else {
                            Log.d("FILE", fileList[i].file);
                        }
                    }
                }
            }

            if (!firstLvl) {
                Item[] temp = new Item[fileList.length + 1];
                System.arraycopy(fileList, 0, temp, 1, fileList.length);
                temp[0] = new Item("Up", R.drawable.ic_file_up_dir);
                fileList = temp;
            }
        }

        adapter = new ArrayAdapter<Item>(this,
                R.layout.custom_dialog_item_select, android.R.id.text1,
                fileList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                // creates view
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);

                // put the image on the text view
                textView.setCompoundDrawablesWithIntrinsicBounds(
                        fileList[position].icon, 0, 0, 0);

                // add margin between image and text (support various screen
                // densities)
                int dp5 = (int) (5 * getResources().getDisplayMetrics().density + 0.5f);
                textView.setCompoundDrawablePadding(dp5);

                return view;
            }
        };
    }

    private String checkExtension(String name) {
        String extension = "";

        int i = name.lastIndexOf('.');
        if (i > 0) {
            extension = name.substring(i + 1);
        }
        return extension.toLowerCase();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    // Wrapper class to hold file data
    private static class Item {
        public String file;
        public int icon;

        Item(String file, Integer icon) {
            this.file = file;
            this.icon = icon;
        }

        @NonNull
        @Override
        public String toString() {
            return file;
        }
    }
}