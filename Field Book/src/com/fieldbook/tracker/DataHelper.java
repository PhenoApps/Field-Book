package com.fieldbook.tracker;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.fieldbook.tracker.Map.MapData;
import com.fieldbook.tracker.Search.SearchData;
import com.fieldbook.tracker.Trait.TraitObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All database related functions are here
 */
public class DataHelper {
    private static final String DATABASE_NAME = "fieldbook.db";
    public static final int DATABASE_VERSION = 5;

    public static final String RANGE = "range";
    public static final String TRAITS = "traits";

    public static final String USER_TRAITS = "user_traits";

    private Context context;
    private static SQLiteDatabase db;

    private SQLiteStatement insertTraits;
    private SQLiteStatement insertUserTraits;

    private static final String INSERTTRAITS = "insert into "
            + TRAITS
            + "(trait, format, defaultValue, minimum, maximum, details, categories, "
            + "isVisible, realPosition) values (?,?,?,?,?,?,?,?,?)";

    private static final String INSERTUSERTRAITS = "insert into " + USER_TRAITS
            + "(rid, parent, trait, userValue, timeTaken) values (?,?,?,?,?)";

    private SimpleDateFormat timeStamp;

    private OpenHelper openHelper;

    private SharedPreferences ep;

    public DataHelper(Context context) {
        try {
            this.context = context;
            openHelper = new OpenHelper(this.context);
            this.db = openHelper.getWritableDatabase();

            ep = context.getSharedPreferences("Settings", 0);

            this.insertTraits = this.db.compileStatement(INSERTTRAITS);
            this.insertUserTraits = this.db.compileStatement(INSERTUSERTRAITS);

            timeStamp = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss",
                    Locale.getDefault());

        } catch (Exception e) {
            e.printStackTrace();
            Log.w("FieldBook", "Unable to create or open database");
        }
    }

    /**
     * As the fields in the CSV file can vary, we recreate the table based on
     * the field names and column data
     */
    public void insertRange(String[] columns, String[] data) {
        try {
            String fields = "";
            String values = "";

            for (int i = 0; i < columns.length; i++) {
                if (i == (columns.length - 1)) {
                    fields += columns[i] + ")";
                } else {
                    fields += columns[i] + ",";
                }
            }

            for (int j = 0; j < data.length; j++) {
                if (j == (data.length - 1)) {
                    values += "" + DatabaseUtils.sqlEscapeString(data[j]) + ")";
                } else {
                    values += "" + DatabaseUtils.sqlEscapeString(data[j]) + ",";
                }
            }

            this.db.execSQL("insert into RANGE (" + fields + " values ("
                    + values);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Inserting traits data. The last field isVisible determines if the trait
     * is visible when using the app
     */
    public long insertTraits(String trait, String format, String defaultValue,
                             String minimum, String maximum, String details, String categories,
                             String isVisible) {
        try {
            this.insertTraits.bindString(1, trait);
            this.insertTraits.bindString(2, format);
            this.insertTraits.bindString(3, defaultValue);
            this.insertTraits.bindString(4, minimum);
            this.insertTraits.bindString(5, maximum);
            this.insertTraits.bindString(6, details);
            this.insertTraits.bindString(7, categories);
            this.insertTraits.bindString(8, isVisible);

            return this.insertTraits.executeInsert();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Helper function to change visibility of a trait. Used in the ratings
     * screen
     */
    public void updateTraitVisibility(String trait, boolean val) {
        this.db.execSQL("update " + TRAITS
                + " set isVisible = ? where trait like ?", new String[]{
                String.valueOf(val), trait});
    }

    /**
     * Helper function to insert user data. For example, the data entered for
     * numeric format, or date for date format The timestamp is updated within
     * this function as well
     * v1.6 - Amended to consider both trait and user data
     */
    public long insertUserTraits(String rid, String parent, String trait, String userValue) {

        try {
            this.insertUserTraits.bindString(1, rid);
            this.insertUserTraits.bindString(2, parent);
            this.insertUserTraits.bindString(3, trait);
            this.insertUserTraits.bindString(4, userValue);
            this.insertUserTraits.bindString(5,
                    timeStamp.format(Calendar.getInstance().getTime()));

            return this.insertUserTraits.executeInsert();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int getMaxPositionFromTraits() {

        int largest = 0;

        Cursor cursor = this.db.rawQuery("select max(realPosition) from " + TRAITS, null);

        if (cursor.moveToFirst()) {
            largest = cursor.getInt(0);
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return largest;
    }

    /**
     * Helper function to close the database
     */
    public void close() {
        try {
            this.db.close();
        } catch (Exception e) {

        }
    }

    /**
     * Helper function to open the database
     */
    public void open() {
        try {
            this.db = openHelper.getWritableDatabase();

            this.insertTraits = this.db.compileStatement(INSERTTRAITS);
            this.insertUserTraits = this.db.compileStatement(INSERTUSERTRAITS);
        } catch (Exception e) {

        }
    }

    /**
     * Helper function to delete all boolean values. Used when importing traits
     * v1.6 - Amended to consider trait
     */
    public void deleteAllBoolean(String parent) {
        try {
            this.db.delete(USER_TRAITS, "parent like ? and trait like ?",
                    new String[]{parent, "boolean"});
        } catch (Exception e) {

        }
    }

    /**
     * V2 - Convert array to String
     */
    public String arrayToString(String table, String[] s) {
        String value = "";

        for (int i = 0; i < s.length; i++) {
            if (table.length() > 0)
                value += table + "." + s[i];
            else
                value += s[i];

            if (i < s.length - 1)
                value += ",";
        }

        return value;
    }

    /**
     * Retrieves the columns needed for export using a join statement
     * v1.6 - Amended to consider both trait and format
     */
    public Cursor getExportDBData(String[] fieldList) {
        String fields = arrayToString("range", fieldList);

        Cursor cursor = this.db
                .rawQuery(
                        "select " + fields + ", traits.trait, user_traits.userValue, " +
                                "user_traits.timeTaken from user_traits, range, traits where " +
                                "user_traits.rid = range." + ep.getString("ImportUniqueName", "") +
                                " and user_traits.parent = traits.trait and " +
                                "user_traits.trait = traits.format and user_traits.userValue is not null",
                        null
                );

        return cursor;
    }

    /**
     * Retrieves the columns needed for excel style export
     */
    public Cursor getExportExcelData(String[] fieldList) {
        String fields = arrayToString("", fieldList);

        Cursor cursor = this.db
                .rawQuery(
                        "select " + fields + " from range",
                        null);

        return cursor;
    }

    /**
     * Used when exporting to Excel format by matching column and trait
     * v1.6 - Amended to consider both trait and format
     */
    public String getSingleValue(String col, String trait) {
        Cursor cursor = this.db
                .rawQuery(
                        "select user_traits.rid, traits.trait, user_traits.userValue from user_traits, " +
                                "traits where user_traits.parent = traits.trait and user_traits.trait = traits.format " +
                                "and user_traits.userValue is not null and rid = '" + col + "' " +
                                "and traits.trait = '" + trait + "'", null
                );

        String val = "";

        if (cursor.moveToFirst()) {
            val = cursor.getString(2);
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return val;
    }

    /**
     * Convert EAV database to relational //TODO add where statement for repeated values
     */

    public Cursor convertDatabaseToTable(String[] col, String[] traits) {
        String query = "";
        String[] rangeArgs = new String[col.length];
        String[] traitArgs = new String[traits.length];
        String joinArgs = "";

        for (int i = 0; i < col.length; i++) {
            rangeArgs[i] = "range." + col[i];
        }

        for (int i = 0; i < traits.length; i++) {
            traitArgs[i] = "m" + i + ".userValue as '" + traits[i] + "'";
            joinArgs = joinArgs + "LEFT JOIN user_traits m" + i + " ON range." + ep.getString("ImportUniqueName", "")
                    + " = m" + i + ".rid AND m" + i + ".parent = '" + traits[i] + "' ";
        }

        query = "SELECT " + convertToCommaDelimited(rangeArgs) + " , " + convertToCommaDelimited(traitArgs) +
                " FROM range range " + joinArgs;

        Log.e("DH", query);

        Cursor cursor = this.db.rawQuery(query, null);

        return cursor;
    }

    /**
     * Convert EAV database to relational //TODO add where statement for repeated values
     */

    public Cursor exportDatabaseFormat(String[] col, String[] traits) {
        String query = "";
        String[] rangeArgs = new String[col.length];
        String[] traitArgs = new String[traits.length];
        String joinArgs = "";

        for (int i = 0; i < col.length; i++) {
            rangeArgs[i] = "range." + col[i];
        }

        for (int i = 0; i < traits.length; i++) {
            traitArgs[i] = "m" + i + ".userValue as '" + traits[i] + "'";
            joinArgs = joinArgs + "LEFT JOIN user_traits m" + i + " ON range." + ep.getString("ImportUniqueName", "")
                    + " = m" + i + ".rid AND m" + i + ".parent = '" + traits[i] + "' ";
        }

        query = "SELECT " + convertToCommaDelimited(rangeArgs) + " , " + convertToCommaDelimited(traitArgs) +
                " FROM range range " + joinArgs;

        Log.e("DH", query);

        Cursor cursor = this.db.rawQuery(query, null);

        return cursor;
    }

    /**
     * Used by the application when moving between ranges (to uniquely identify
     * them)
     */
    public String[] getPlotID() {
        String[] data = null;

        Cursor cursor = this.db.query(RANGE,
                new String[]{ep.getString("ImportUniqueName", "")}, null, null, null, null,
                ep.getString("ImportUniqueName", ""));

        int count = 0;

        if (cursor.moveToFirst()) {
            data = new String[cursor.getCount()];

            do {
                data[count] = cursor.getString(0);

                count += 1;

            } while (cursor.moveToNext());
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Used by the application to return all traits which are visible
     */
    public String[] getVisibleTrait() {
        String[] data = null;

        Cursor cursor = this.db.query(TRAITS, new String[]{"id", "trait", "realPosition"},
                "isVisible like ?", new String[]{"true"}, null, null, "realPosition");

        int count = 0;

        if (cursor.moveToFirst()) {
            data = new String[cursor.getCount()];

            do {
                data[count] = cursor.getString(1);

                count += 1;

            } while (cursor.moveToNext());
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Used by application to loops through formats which are visible
     */
    public String[] getFormat() {
        String[] data = null;

        Cursor cursor = this.db.query(TRAITS, new String[]{"id", "format", "realPosition"},
                "isVisible like ?", new String[]{"true"}, null, null, "realPosition");

        int count = 0;

        if (cursor.moveToFirst()) {
            data = new String[cursor.getCount()];

            do {
                data[count] = cursor.getString(1);

                count += 1;

            } while (cursor.moveToNext());
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Returns all traits regardless of visibility. Used by the ratings screen
     */
    public String[] getAllTraits() {
        String[] data = null;

        Cursor cursor = this.db.query(TRAITS, new String[]{"id", "trait", "realPosition"},
                null, null, null, null, "realPosition");

        int count = 0;

        if (cursor.moveToFirst()) {
            data = new String[cursor.getCount()];

            do {
                data[count] = cursor.getString(1);

                count += 1;

            } while (cursor.moveToNext());
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Get data from specific column of trait table to reorder
     */
    public String[] getTraitColumnData(String column) {
        String[] data = null;

        Cursor cursor = this.db.query(TRAITS, new String[]{column},
                null, null, null, null, null);

        int count = 0;

        if (cursor.moveToFirst()) {
            data = new String[cursor.getCount()];

            do {
                data[count] = cursor.getString(0);

                count += 1;

            } while (cursor.moveToNext());
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Write new realPosition
     */
    public void writeNewPosition(String column, String id, String position) {
        ContentValues cv = new ContentValues();
        cv.put("realPosition", position);
        this.db.update(TRAITS, cv, column + "= ?", new String[]{id});
    }

    /**
     * V2 - Returns all traits column titles as a string array
     */
    public String[] getTraitColumns() {
        Cursor cursor = this.db.rawQuery("SELECT * from traits limit 1", null);

        String[] data = null;

        if (cursor.moveToFirst()) {
            int i = cursor.getColumnCount() - 1;

            data = new String[i];

            int k = 0;

            for (int j = 0; j < cursor.getColumnCount(); j++) {
                if (!cursor.getColumnName(j).equals("id")) {
                    data[k] = cursor.getColumnName(j);
                    k += 1;
                }
            }
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - Returns all traits column titles as a cursor
     */
    public Cursor getAllTraitsForExport() {
        Cursor cursor = this.db.query(TRAITS, getTraitColumns(),
                null, null, null, null, "id");

        return cursor;
    }

    /**
     * V2 - Get all traits in the system, in order, as TraitObjects
     */
    public ArrayList<TraitObject> getAllTraitObjects() {

        ArrayList<TraitObject> list = new ArrayList<TraitObject>();

        Cursor cursor = this.db.query(TRAITS, new String[]{"id", "trait", "format", "defaultValue",
                        "minimum", "maximum", "details", "categories", "isVisible", "realPosition"},
                null, null, null, null, "realPosition"
        );

        if (cursor.moveToFirst()) {
            do {
                TraitObject o = new TraitObject();

                o.id = cursor.getString(0);
                o.trait = cursor.getString(1);
                o.format = cursor.getString(2);
                o.defaultValue = cursor.getString(3);
                o.minimum = cursor.getString(4);
                o.maximum = cursor.getString(5);
                o.details = cursor.getString(6);
                o.categories = cursor.getString(7);
                o.realPosition = cursor.getString(9);

                list.add(o);

            } while (cursor.moveToNext());
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return list;
    }

    /**
     * Returns all traits regardless of visibility, but as a hashmap
     */
    public HashMap getTraitVisibility() {
        HashMap data = new HashMap();

        Cursor cursor = this.db.query(TRAITS, new String[]{"id", "trait",
                "isVisible", "realPosition"}, null, null, null, null, "realPosition");

        if (cursor.moveToFirst()) {
            do {
                data.put(cursor.getString(1), cursor.getString(2));

            } while (cursor.moveToNext());
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Returns a particular trait as an object
     */
    public TraitObject getDetail(String trait) {
        TraitObject data = new TraitObject();

        data.trait = "";
        data.format = "";
        data.defaultValue = "";
        data.minimum = "";
        data.maximum = "";
        data.details = "";
        data.categories = "";

        Cursor cursor = this.db.query(TRAITS, new String[]{"trait", "format", "defaultValue", "minimum",
                        "maximum", "details", "categories", "id"}, "trait like ? and isVisible like ?",
                new String[]{trait, "true"}, null, null, null
        );

        if (cursor.moveToFirst()) {
            data.trait = cursor.getString(0);
            data.format = cursor.getString(1);
            data.defaultValue = cursor.getString(2);
            data.minimum = cursor.getString(3);
            data.maximum = cursor.getString(4);
            data.details = cursor.getString(5);
            data.categories = cursor.getString(6);
            data.id = cursor.getString(7);
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Returns saved data based on plot_id
     * v1.6 - Amended to consider both trait and format
     */
    public HashMap getUserDetail(String plotId) {
        HashMap data = new HashMap();

        Cursor cursor = this.db.query(USER_TRAITS, new String[]{"parent", "trait",
                        "userValue", "rid"}, "rid like ?", new String[]{plotId},
                null, null, null
        );

        if (cursor.moveToFirst()) {
            do {
                data.put(cursor.getString(0), cursor.getString(2));
            } while (cursor.moveToNext());
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Check if a trait exists within the database
     * v1.6 - Amended to consider both trait and format
     */
    public boolean getTraitExists(int id, String parent, String trait) {
        boolean haveData = false;

        Cursor cursor = this.db
                .rawQuery(
                        "select range.id, user_traits.userValue from user_traits, range where " +
                                "user_traits.rid = range." + ep.getString("ImportUniqueName", "") +
                                " and range.id = ? and user_traits.parent like ? and user_traits.trait like ?",
                        new String[]{String.valueOf(id), parent, trait}
                );

        if (cursor.moveToFirst()) {
            if (cursor.getString(1) != null) {
                haveData = true;
            }
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return haveData;
    }

    /**
     * Returns the primary key for all ranges
     */
    public int[] getAllRangeID() {
        Cursor cursor = this.db.query(RANGE, new String[]{"id"}, null, null,
                null, null, "id");

        int[] data = null;

        if (cursor.moveToFirst()) {
            data = new int[cursor.getCount()];

            int count = 0;

            do {
                data[count] = cursor.getInt(0);

                count += 1;
            } while (cursor.moveToNext());
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - Used for mapping, returns all plot_id in order
     */
    public String[] getAllPlotID() {
        Cursor cursor = this.db.query(RANGE, new String[]{ep.getString("ImportUniqueName", "")}, null, null,
                null, null, "id");

        String[] data = null;

        if (cursor.moveToFirst()) {
            data = new String[cursor.getCount()];

            int count = 0;

            do {
                data[count] = cursor.getString(0);

                count += 1;
            } while (cursor.moveToNext());
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - Execute a custom sql query, returning the result as SearchData objects
     * Used for user search function
     */
    public SearchData[] getRangeBySql2(String sql) {

        try {
            Cursor cursor = this.db.rawQuery(sql, null);

            SearchData[] data = null;

            if (cursor.moveToFirst()) {
                data = new SearchData[cursor.getCount()];

                int count = 0;

                do {

                    SearchData sd = new SearchData();

                    sd.id = cursor.getInt(0);
                    sd.range = cursor.getString(1);
                    sd.plot = cursor.getString(2);

                    data[count] = sd;

                    count += 1;
                } while (cursor.moveToNext());
            }

            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }

            return data;
        } catch (Exception n) {
            return null;
        }
    }

    /**
     * V2 - Execute a custom sql query, returning the result as an integer array
     * Used for user search function
     */
    public int[] getRangeBySql(String sql) {

        try {
            Cursor cursor = this.db.rawQuery(sql, null);

            int[] data = null;

            if (cursor.moveToFirst()) {
                data = new int[cursor.getCount()];

                int count = 0;

                do {
                    data[count] = cursor.getInt(0);

                    count += 1;
                } while (cursor.moveToNext());
            }

            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }

            return data;
        } catch (Exception n) {
            return null;
        }
    }

    /**
     * Returns saved data based on trait, range and plot Meant for the on screen
     * drop downs
     */
    public String[] getAllRange(String trait, String range, String plot) {

        if (trait.length() == 0)
            return null;

        try {
            Cursor cursor = this.db.query(RANGE, new String[]{trait},
                    ep.getString("ImportFirstName", "") + " like ? and " +
                            ep.getString("ImportSecondName", "") + " like ?", new String[]{range, plot},
                    null, null, null
            );

            String[] myList = null;

            if (cursor.moveToFirst()) {
                myList = new String[cursor.getCount()];

                int count = 0;

                do {
                    myList[count] = cursor.getString(0);

                    count += 1;
                } while (cursor.moveToNext());
            }

            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }

            return myList;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns range data based on the primary key for range Used when moving
     * between ranges on screen
     */
    public RangeObject getRange(int id) {
        RangeObject data = new RangeObject();

        data.plot = "";
        data.plot_id = "";
        data.range = "";

        Cursor cursor = this.db.query(RANGE, new String[]{ep.getString("ImportFirstName", ""),
                        ep.getString("ImportSecondName", ""),
                        ep.getString("ImportUniqueName", ""), "id"}, "id = ?",
                new String[]{String.valueOf(id)}, null, null, null
        );

        if (cursor.moveToFirst()) {
            //data.entry = cursor.getString(0);
            data.range = cursor.getString(0);
            data.plot = cursor.getString(1);
            data.plot_id = cursor.getString(2);

        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - Returns the ids for items that match the specified range and plot
     */
    public int[] getAllRange(String range, String plot) {

        try {
            Cursor cursor = this.db.query(RANGE, new String[]{"id"},
                    ep.getString("ImportFirstName", "") + " like ? and " + ep.getString("ImportSecondName", "") + " like ?", new String[]{range, plot},
                    null, null, null);

            int[] myList = null;

            if (cursor.moveToFirst()) {
                myList = new int[cursor.getCount()];

                int count = 0;

                do {
                    myList[count] = cursor.getInt(0);

                    count += 1;
                } while (cursor.moveToNext());
            }

            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }

            return myList;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the range for items that match the specified id
     */
    public String getRangeFromId(String plot_id) {
        try {
            Cursor cursor = this.db.query(RANGE, new String[]{ep.getString("ImportFirstName", "")},
                    ep.getString("ImportUniqueName", "") + " like ? ", new String[]{plot_id},
                    null, null, null);

            String myList = null;

            if (cursor.moveToFirst()) {
                myList = cursor.getString(0);
            }

            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }

            return myList;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Helper function
     * v2.5
     */
    public void deleteTraitByValue(String rid, String parent, String value) {

        try {
            this.db.delete(USER_TRAITS, "rid like ? and parent like ? and userValue = ?",
                    new String[] { rid, parent, value });
        } catch (Exception e) {

        }
    }

    /**
     * Returns list of files associated with a specific plot
     */

    public ArrayList<String> getPlotPhotos(String plot) {
        try {
            Cursor cursor = this.db.query(USER_TRAITS, new String[]{"userValue"},"rid like ? and trait like ?", new String[]{plot,"photo"},
                    null, null, null);

            ArrayList<String> photoList = new ArrayList<String>();
            Log.d("Field",Integer.toString(cursor.getCount()));

            if(cursor.moveToFirst()) {
                do {
                    photoList.add(cursor.getString(0));
                }
                while (cursor.moveToNext());
            }

            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }

            return photoList;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns saved data based on trait, range and plot Meant for the on screen
     * drop downs
     */
    public String[] getDropDownRange(String trait, String plotId) {

        if (trait.length() == 0)
            return null;

        //String tableName = getRangeTableById(importId);

        try
        {
            Cursor cursor = this.db.query(RANGE, new String[] { trait },
                    ep.getString("ImportUniqueName", "") + " like ? ", new String[] { plotId },
                    null, null, null);

            String[] myList = null;

            if (cursor.moveToFirst()) {
                myList = new String[cursor.getCount()];

                int count = 0;

                do {
                    myList[count] = cursor.getString(0);

                    count += 1;
                } while (cursor.moveToNext());
            }

            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }

            return myList;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Returns the column names for the range table
     */
    public String[] getRangeColumnsWithoutOrganizer() {
        Cursor cursor = this.db.rawQuery("SELECT * from " + RANGE + " limit 1", null);

        String[] data = null;

        if (cursor.moveToFirst()) {
            int i = cursor.getColumnCount() - 1;

            data = new String[i];

            int k = 0;

            String first = ep.getString("ImportFirstName", "");
            String second = ep.getString("ImportSecondName", "");

            for (int j = 0; j < cursor.getColumnCount(); j++) {

                // need to hide importId and organizers
                if (!cursor.getColumnName(j).equals("id")) { // & !cursor.getColumnName(j).equals("importId") & !cursor.getColumnName(j).equals(first) & !cursor.getColumnName(j).equals(second)) {

                    data[k] = cursor.getColumnName(j).replace("//", "/");
                    k += 1;

                    //Log.w("data " + k, cursor.getColumnName(j));
                }
            }
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Returns the plot for items that match the specified id
     */
    public String getPlotFromId(String plot_id) {
        try {
            Cursor cursor = this.db.query(RANGE, new String[]{ep.getString("ImportSecondName", "")},
                    ep.getString("ImportUniqueName", "") + " like ?", new String[]{plot_id},
                    null, null, null);

            String myList = null;

            if (cursor.moveToFirst()) {
                myList = cursor.getString(0);
            }

            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }

            return myList;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Helper function
     * v1.6 - Amended to consider trait
     */
    public void deleteTrait(String rid, String parent) {
        try {
            this.db.delete(USER_TRAITS, "rid like ? and parent like ?",
                    new String[]{rid, parent});
        } catch (Exception e) {

        }
    }

    /**
     * Helper function
     * v2 - Delete trait
     */
    public void deleteTrait(String id) {
        try {
            this.db.delete(TRAITS, "id = ?",
                    new String[]{id});
        } catch (Exception e) {

        }
    }

    /**
     * Helper function to delete all data in the table
     */
    public void deleteTable(String table) {
        try {
            this.db.delete(table, null, null);
        } catch (Exception e) {

        }
    }

    /**
     * Removes the range table
     */
    public void dropRange() {
        db.execSQL("DROP TABLE IF EXISTS " + RANGE);
    }

    /**
     * Creates the range table based on column names
     */
    public void createRange(String[] data) {
        String sql = "CREATE TABLE " + RANGE + "(id INTEGER PRIMARY KEY,";

        for (int i = 0; i < data.length; i++) {
            if (i == data.length - 1) {
                sql += data[i] + " TEXT)";
            } else {
                sql += data[i] + " TEXT,";
            }
        }

        db.execSQL(sql);
    }

    /**
     * V2 - Returns titles of all range columns as a comma delimited string
     */
    public String getRangeColumnsAsString() {
        try {
            String[] s = getRangeColumns();

            String value = "";

            for (int i = 0; i < s.length; i++) {
                value += s[i];

                if (i < s.length - 1)
                    value += ",";
            }

            return value;
        } catch (Exception b) {
            return null;
        }
    }

    /**
     * V2 - Returns titles of all trait columns as a comma delimited string
     */
    public String getTraitColumnsAsString() {
        try {
            String[] s = getAllTraits();

            String value = "";

            for (int i = 0; i < s.length; i++) {
                value += s[i];

                if (i < s.length - 1)
                    value += ",";
            }

            return value;
        } catch (Exception b) {
            return null;
        }

    }

    /**
     * V2 - Returns titles of all trait columns as a comma delimited string
     * with an additional " " in between each comma
     * This function is used to assist with paragaphing
     */
    public String getTraitColumnsAsString2() {
        try {
            String[] s = getAllTraits();

            String value = "";

            for (int i = 0; i < s.length; i++) {
                value += s[i];

                if (i < s.length - 1)
                    value += ", ";
            }

            return value;
        } catch (Exception b) {
            return null;
        }

    }

    /**
     * Returns the column names for the range table
     */
    public String[] getRangeColumns() {
        Cursor cursor = this.db.rawQuery("SELECT * from range limit 1", null);

        String[] data = null;

        if (cursor.moveToFirst()) {
            int i = cursor.getColumnCount() - 1;

            data = new String[i];

            int k = 0;

            for (int j = 0; j < cursor.getColumnCount(); j++) {
                if (!cursor.getColumnName(j).equals("id")) {
                    data[k] = cursor.getColumnName(j);
                    k += 1;
                }
            }
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Returns the column names for the range table
     */
    public int findRangeColumns(String col) {
        Cursor cursor = this.db.rawQuery("SELECT * from range limit 1", null);

        int pos = -1;

        if (cursor.moveToFirst()) {
            for (int j = 0; j < cursor.getColumnCount(); j++) {
                if (!cursor.getColumnName(j).equals("id")) {
                    if (cursor.getColumnName(j).toUpperCase().equals(col.toUpperCase()))
                        pos = j;

                }
            }
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return pos;
    }

    /**
     * Returns the column names for the range table
     */
    public int findRangeColumns(String col, String[] fieldList) {

        String fields = arrayToString("", fieldList);

        Cursor cursor = this.db.rawQuery("SELECT " + fields + " from range limit 1", null);

        int pos = -1;

        if (cursor.moveToFirst()) {
            for (int j = 0; j < cursor.getColumnCount(); j++) {
                if (!cursor.getColumnName(j).equals("id")) {
                    if (cursor.getColumnName(j).toUpperCase().equals(col.toUpperCase()))
                        pos = j;

                }
            }
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return pos;
    }

    /**
     * V2 - Returns raw data for a trait, so that analysis can calculate the values based on it
     */
    public HashMap<Integer, String> analyze(String trait) {

        Cursor cursor = this.db
                .rawQuery(
                        "select range.id, user_traits.uservalue from user_traits, range, " +
                                "traits where user_traits.rid = range." + ep.getString("ImportUniqueName", "") +
                                " and user_traits.parent = traits.trait and user_traits.trait = traits.format " +
                                "and user_traits.userValue is not null and user_traits.parent like '" + trait + "'",
                        null
                );

        HashMap<Integer, String> data = null;

        if (cursor.moveToFirst()) {
            data = new HashMap<Integer, String>(cursor.getCount());

            int count = 0;

            do {
                data.put(cursor.getInt(0), cursor.getString(1));

                count += 1;
            } while (cursor.moveToNext());
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - Get the smallest value for a trait
     * Used in analysis only
     */
    public String getTraitMinimum(String trait) {
        Cursor cursor = this.db
                .rawQuery("select minimum from traits where trait = ?", new String[]{trait});

        String val = "";

        if (cursor.moveToFirst()) {
            val = cursor.getString(0);
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return val;
    }

    /**
     * Inserting traits data. The last field isVisible determines if the trait
     * is visible when using the app
     */
    public long insertTraits(String trait, String format, String defaultValue,
                             String minimum, String maximum, String details, String categories,
                             String isVisible, String realPosition) {
        try {
            this.insertTraits.bindString(1, trait);
            this.insertTraits.bindString(2, format);
            this.insertTraits.bindString(3, defaultValue);
            this.insertTraits.bindString(4, minimum);
            this.insertTraits.bindString(5, maximum);
            this.insertTraits.bindString(6, details);
            this.insertTraits.bindString(7, categories);
            this.insertTraits.bindString(8, isVisible);
            this.insertTraits.bindString(9, realPosition);

            return this.insertTraits.executeInsert();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * V2 - Update the ordering of traits
     */
    public void updateTraitPosition(String id, String realPosition) {
        try {
            ContentValues c = new ContentValues();
            c.put("realPosition", realPosition);

            this.db.update(TRAITS, c, "id = ?", new String[]{id});

        } catch (Exception e) {

        }
    }

    /**
     * V2 - Edit existing trait
     */
    public long editTraits(String id, String trait, String format, String defaultValue,
                           String minimum, String maximum, String details, String categories) {
        try {
            ContentValues c = new ContentValues();
            c.put("trait", trait);
            c.put("format", format);
            c.put("defaultValue", defaultValue);
            c.put("minimum", minimum);
            c.put("maximum", maximum);
            c.put("details", details);
            c.put("categories", categories);

            return this.db.update(TRAITS, c, "id = ?", new String[]{id});
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * V2 - Check if trait exists (non case sensitive)
     */
    public boolean hasTrait(String name) {
        boolean exist;

        Cursor cursor = this.db.rawQuery("select id from traits where " +
                "trait = ? COLLATE NOCASE", new String[]{name});

        if (cursor.moveToFirst())
            exist = true;
        else
            exist = false;

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return exist;
    }

    /**
     * V2 - Returns plots for a particular range
     * This is a logical mapping created from database data
     */
    public SearchData[] getRowForMapPlot(String rid, boolean forward) {
        String range = ep.getString("ImportFirstName", "");
        String plot = ep.getString("ImportSecondName", "");

        Cursor cursor = this.db.rawQuery("select id, " + range + " from range where " +
                range + " = '" + rid + "' order by cast(" + plot + " as integer) asc", null);

        SearchData[] data = null;

        int count = 0;

        if (forward)
            count = 0;
        else
            count = cursor.getCount() - 1;

        if (cursor.moveToFirst()) {
            data = new SearchData[cursor.getCount()];

            do {

                SearchData item = new SearchData();

                item.id = cursor.getInt(0);
                item.range = cursor.getString(1);
                item.plot = rid;

                data[count] = item;

                if (forward)
                    count += 1;
                else
                    count -= 1;

            } while (cursor.moveToNext());

        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - After generating plots by range, return the data forward (from the first plot)
     * or backwards.
     */
    public MapData[] arrangePlotByRow(boolean ascending) {
        String range = ep.getString("ImportFirstName", "");

        String sql = "select count(" + range + "), " + range + " from range group by " + range +
                " order by cast(" + range + " as integer) ";

        if (ascending)
            sql += "asc";
        else
            sql += "desc";

        Cursor cursor = this.db.rawQuery(sql, null);

        MapData[] data = null;

        int count = 0;

        if (cursor.moveToFirst()) {
            data = new MapData[cursor.getCount()];

            do {

                MapData item = new MapData();

                item.plotCount = cursor.getInt(0);
                item.plot = cursor.getString(1);

                data[count] = item;

                count += 1;

            } while (cursor.moveToNext());

        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - Check if a string has any special characters
     */
    public static boolean hasSpecialChars(String s) {
        final Pattern p = Pattern.compile("[()<>/;\\*%$]");
        final Matcher m = p.matcher(s);

        if (m.find())
            return true;
        else
            return false;
    }

    /**
     * V2 - Helper function to recreate default table
     */
    public void defaultFieldTable() {
        db.execSQL("DROP TABLE IF EXISTS " + RANGE);

        db.execSQL("CREATE TABLE "
                + RANGE
                + "(id INTEGER PRIMARY KEY, range TEXT, plot TEXT, entry TEXT, plot_id TEXT, pedigree TEXT)");
    }

    /**
     * When the version number changes, this class will recreate the entire
     * database
     * v1.6 - Amended to add new parent field. It is called parent in consideration to the enhanced search
     */
    private static class OpenHelper extends SQLiteOpenHelper {

        OpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE "
                    + RANGE
                    + "(id INTEGER PRIMARY KEY, range TEXT, plot TEXT, entry TEXT, plot_id TEXT, pedigree TEXT)");
            db.execSQL("CREATE TABLE "
                    + TRAITS
                    + "(id INTEGER PRIMARY KEY, trait TEXT, format TEXT, defaultValue TEXT, minimum TEXT, maximum TEXT, details TEXT, categories TEXT, isVisible TEXT, realPosition int)");

            db.execSQL("CREATE TABLE "
                    + USER_TRAITS
                    + "(id INTEGER PRIMARY KEY, rid TEXT, parent TEXT, trait TEXT, userValue TEXT, timeTaken TEXT)");

            try {
                db.execSQL("CREATE TABLE android_metadata (locale TEXT)");
                db.execSQL("INSERT INTO android_metadata(locale) VALUES('en_US')");
            } catch (Exception e) {
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w("FieldBook",
                    "Upgrading database, this will drop tables and recreate.");
            db.execSQL("DROP TABLE IF EXISTS " + RANGE);
            db.execSQL("DROP TABLE IF EXISTS " + TRAITS);
            db.execSQL("DROP TABLE IF EXISTS " + USER_TRAITS);

            onCreate(db);
        }
    }

    /**
     * Delete all tables
     */

    public void deleteDatabase() {
        context.deleteDatabase(DATABASE_NAME);
    }

    /**
     * Import database
     */
    public boolean importDatabase(String filename) throws IOException {
        String internalPath = "/data/data/com.fieldbook.tracker/databases/" + DATABASE_NAME;

        // Close the SQLiteOpenHelper so it will commit the created empty
        // database to internal storage.
        close();

        Log.w("File to copy", Constants.BACKUPPATH + "/" + filename);

        File newDb = new File(Constants.BACKUPPATH + "/" + filename);
        File oldDb = new File(internalPath);

        if (newDb.exists()) {
            FileUtils.copyFile(new FileInputStream(newDb), new FileOutputStream(oldDb));

            // Access the copied database so SQLiteHelper will cache it and mark
            // it as created.

            openHelper = new OpenHelper(this.context);

            open();

            return true;
        }

        throw new IOException("System database in backup directory can't be loaded. Unable to copy.");
    }


    /**
     * Export database
     */
    public boolean exportDatabase(String filename) throws IOException {
        String internalPath = "/data/data/com.fieldbook.tracker/databases/" + DATABASE_NAME;

        // Close the SQLiteOpenHelper so it will commit the created empty
        // database to internal storage.
        close();

        File newDb = new File(Constants.BACKUPPATH + "/" + filename + ".db");
        File oldDb = new File(internalPath);

        if (oldDb.exists()) {
            FileUtils.copyFile(new FileInputStream(oldDb), new FileOutputStream(newDb));

            // Access the copied database so SQLiteHelper will cache it and mark
            // it as created.

            openHelper = new OpenHelper(this.context);
            open();
            return true;
        }

        throw new IOException("Original system database in app does not exist. Unable to copy.");
    }

    /**
     * Drop specified table
     */
    public void dropTable(String table) {

        db.execSQL("DROP TABLE IF EXISTS " + table);
    }

    /**
     * V2 - Helper function to copy multiple files from asset to SDCard
     */
    public void copyFileOrDir(String fullPath, String path) {
        AssetManager assetManager = context.getAssets();
        String assets[] = null;

        try {
            assets = assetManager.list(path);

            if (assets.length == 0) {
                copyFile(fullPath, path);
            } else {
                File dir = new File(fullPath);

                if (!dir.exists())
                    dir.mkdir();

                for (int i = 0; i < assets.length; ++i) {
                    copyFileOrDir(fullPath, path + "/" + assets[i]);
                }
            }
        } catch (IOException ex) {
            Log.e("Sample Data", "I/O Exception", ex);
        }
    }

    /**
     * V2 - Helper function to copy files from asset to SDCard
     */
    public void copyFile(String fullPath, String filename) {
        AssetManager assetManager = context.getAssets();

        InputStream in = null;
        OutputStream out = null;

        try {
            in = assetManager.open(filename);
            String newFileName = filename;
            out = new FileOutputStream(fullPath + "/" + newFileName);

            byte[] buffer = new byte[1024];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch (Exception e) {
            Log.e("Sample Data", e.getMessage());
        }

    }

    /**
     * Helper function to convert array to csv format
     */
    public static String convertToCommaDelimited(String[] list) {
        StringBuffer ret = new StringBuffer("");
        for (int i = 0; list != null && i < list.length; i++) {
            ret.append(list[i]);
            if (i < list.length - 1) {
                ret.append(',');
            }
        }
        return ret.toString();
    }

}
