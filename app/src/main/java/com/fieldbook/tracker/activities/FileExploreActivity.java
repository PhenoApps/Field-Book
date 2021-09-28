package com.fieldbook.tracker.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.view.MenuItem;
import android.widget.Toast;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.utilities.Utils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class FileExploreActivity extends AppCompatActivity {

    public ListView mainListView;
    // Stores names of traversed directories
    ArrayList<String> str = new ArrayList<>();
    Comparator comp = new Comparator() {
        public int compare(Object o1, Object o2) {
            File f1 = (File) o1;
            File f2 = (File) o2;
            if (f1.isDirectory() && !f2.isDirectory()) {
                // Directory before non-directory
                return -1;
            } else if (!f1.isDirectory() && f2.isDirectory()) {
                // Non-directory after directory
                return 1;
            } else {
                // Alphabetic order otherwise
                return f1.toString().compareToIgnoreCase(f2.toString());
            }
        }
    };
    // Check if the first level of the directory structure is the one showing
    private Boolean firstLvl = true;
    private Item[] fileList;
    private File path;
    private String[] include = new String[0];
    private String[] exclude = new String[0];
    private String chosenFile;
    private ListAdapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        if (getIntent() != null && getIntent().getExtras() != null) {

            String data = getIntent().getExtras().getString("path");
            include = getIntent().getExtras().getStringArray("include");
            exclude = getIntent().getExtras().getStringArray("exclude");
            String title = getIntent().getExtras().getString("title");
            path = new File(data);

            super.onCreate(savedInstanceState);

            mainListView = new ListView(this);

            android.view.WindowManager.LayoutParams params = this.getWindow().getAttributes();
            params.width = LinearLayout.LayoutParams.MATCH_PARENT;
            params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            this.getWindow().setAttributes(params);

            setContentView(mainListView);

            try {

                loadFileList();

            } catch (NullPointerException e) {

                setResult(RESULT_CANCELED);

                finish();
            }

            mainListView.setAdapter(adapter);

            if (title != null && title.length() > 0) {
                this.setTitle(title);
            }

            mainListView.setOnItemClickListener(new OnItemClickListener() {

                public void onItemClick(AdapterView<?> arg0, View arg1, int which, long arg3) {
                    chosenFile = fileList[which].file;

                    File sel = new File(path + "/" + chosenFile);

                    if (sel.isDirectory()) {
                        firstLvl = false;

                        // Adds chosen directory to list
                        str.add(chosenFile);
                        fileList = null;
                        path = new File(sel + "");

                        loadFileList();

                        mainListView.setAdapter(adapter);
                    }

                    // Checks if 'up' was clicked
                    else if (chosenFile.equalsIgnoreCase("up") && !sel.exists()) {

                        // present directory removed from list
                        String s = str.remove(str.size() - 1);

                        // path modified to exclude present directory
                        path = new File(path.toString().substring(0,
                                path.toString().lastIndexOf(s)));
                        fileList = null;

                        // if there are no more directories in the list, then
                        // its the first level
                        if (str.isEmpty()) {
                            firstLvl = true;
                        }
                        loadFileList();
                        mainListView.setAdapter(adapter);
                    }
                    // File picked
                    else {
                        try {
                            Intent returnIntent = new Intent();
                            returnIntent.putExtra("result", sel.toString());
                            setResult(RESULT_OK, returnIntent);
                            finish();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

        } else {

            setResult(RESULT_CANCELED);

            finish();
        }
    }

    private void loadFileList() {
        try {
            path.mkdirs();
        } catch (SecurityException ignored) {
        }

        // Checks whether path exists
        if (path.exists()) {
            // collect files in the specific directory
            //           and to satisfy the requirement
            File[] filesList = path.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String filename) {
                    File sel = new File(dir, filename);
                    if (exclude != null && exclude.length > 0) {
                        if (Arrays.asList(exclude).contains(getFileExtension(sel))) {
                            return false;
                        }
                    }

                    if (include != null && include.length > 0) {
                        return Arrays.asList(include).contains(getFileExtension(sel)) || sel.isDirectory();
                    }

                    return !(sel.toString().contains(".fieldbook") || sel.toString().contains("severity.txt") || sel.toString().contains("sharedpref.xml"));

                }
            });

            Arrays.sort(filesList, comp);

            // collect file names
            String[] fList = new String[filesList.length];

            for (int i = 0; i < filesList.length; ++i) {
                fList[i] = filesList[i].getName();
            }

            // create file list for
            fileList = new Item[fList.length];

            for (int i = 0; i < fList.length; i++) {
                fileList[i] = new Item(fList[i], R.drawable.ic_file_generic);

                // Convert into file path
                File sel = new File(path, fList[i]);

                // Set drawables
                if (sel.isDirectory()) {
                    fileList[i].icon = R.drawable.ic_file_directory;
                    Log.d("DIRECTORY", fileList[i].file);
                }
                if (sel.toString().toLowerCase().contains(".csv")) {
                    fileList[i].icon = R.drawable.ic_file_csv;
                }

                if (sel.toString().toLowerCase().contains(".xls")) {
                    fileList[i].icon = R.drawable.ic_file_xls;
                } else {
                    Log.d("FILE", fileList[i].file);
                }
            }

            if (!firstLvl) {
                Item[] temp = new Item[fileList.length + 1];
                for (int i = 0; i < fileList.length; i++) {
                    temp[i + 1] = fileList[i];
                }
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

    private String getFileExtension(File file) {
        String extension = "";
        String fileName = file.toString();

        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1);
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
    private class Item {
        public String file;
        public int icon;

        Item(String file, Integer icon) {
            this.file = file;
            this.icon = icon;
        }

        @Override
        public String toString() {
            return file;
        }
    }
}