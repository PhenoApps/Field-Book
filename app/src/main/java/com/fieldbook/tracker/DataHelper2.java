package com.fieldbook.tracker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.fieldbook.tracker.Trait.TraitObject;

/**
 * All database related functions are here
 */
public class DataHelper2 {
    private static final String DATABASE_NAME = "fieldbook.db";
    private static final int DATABASE_VERSION = 7;

    public static final String IMPORT = "import";

    public static final String TRAITS = "traits";
    public static final String MAPDATA = "mapdata";

    public static final String USER_TRAITS = "user_traits";

    private Context context;
    private static SQLiteDatabase db;

    private SQLiteStatement insertTraits;
    private SQLiteStatement insertUserTraits;
    private SQLiteStatement insertImport;

    private static final String INSERTTRAITS = "insert into "
            + TRAITS
            + "(importId, trait, format, defaultValue, minimum, maximum, details, categories, "
            + "isVisible, realPosition) values (?,?,?,?,?,?,?,?,?,?)";

    private static final String INSERTUSERTRAITS = "insert into " + USER_TRAITS
            + "(importid, rid, parent, trait, userValue, timeTaken) values (?,?,?,?,?,?)";

    private static final String INSERTIMPORT = "insert into " + IMPORT
            + "(filename, rangeTable, importDate) values (?,?,?)";

    private static final String INSERTMAPDATA = "insert into " + MAPDATA
            + "(plotCount, plot) values (?,?)";

    private SimpleDateFormat timeStamp;

    private OpenHelper openHelper;

    private SharedPreferences ep;

    public DataHelper2(Context context) {
        try {
            this.context = context;
            openHelper = new OpenHelper(this.context);
            db = openHelper.getWritableDatabase();

            ep = context.getSharedPreferences("Settings", 0);

            this.insertTraits = db.compileStatement(INSERTTRAITS);
            this.insertUserTraits = db.compileStatement(INSERTUSERTRAITS);
            this.insertImport = db.compileStatement(INSERTIMPORT);

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
    public void insertRange(String importId, String rangeTable, String[] columns, String[] data) {

        if (importId == null)
            return;

        try {
            String fields = "importId,";
            String values = importId + ",";

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

            DataHelper2.db.execSQL("insert into " + rangeTable + "(" + fields + " values ("
                    + values);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Inserting traits data. The last field isVisible determines if the trait
     * is visible when using the app
     */
    public long insertTraits(String importId, String trait, String format, String defaultValue,
                             String minimum, String maximum, String details, String categories,
                             String isVisible) {

        if (importId == null)
            return -1;

        try {
            this.insertTraits.bindString(1, importId);
            this.insertTraits.bindString(2, trait);
            this.insertTraits.bindString(3, format);
            this.insertTraits.bindString(4, defaultValue);
            this.insertTraits.bindString(5, minimum);
            this.insertTraits.bindString(6, maximum);
            this.insertTraits.bindString(7, details);
            this.insertTraits.bindString(8, categories);
            this.insertTraits.bindString(9, isVisible);

            return this.insertTraits.executeInsert();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Inserting import set data.
     */
    public long insertImport(String fileName, String rangeTable) {

        try {
            this.insertImport.bindString(1, fileName);
            this.insertImport.bindString(2, rangeTable);
            this.insertImport.bindString(3, timeStamp.format(Calendar.getInstance().getTime()));

            return this.insertImport.executeInsert();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }


    /**
     * Helper function to change visibility of a trait. Used in the ratings
     * screen
     */
    public void updateTraitVisibility(String importId, String trait, boolean val) {

        if (importId == null)
            return;

        db.execSQL("update " + TRAITS
                + " set isVisible = ? where trait like ? and importId = ?", new String[]{
                String.valueOf(val), trait, importId});
    }

    /**
     * Helper function to insert user data. For example, the data entered for
     * numeric format, or date for date format The timestamp is updated within
     * this function as well
     * v1.6 - Amended to consider both trait and user data
     */
    public long insertUserTraits(String importId, String rid, String parent, String trait, String userValue) {

        if (importId == null)
            return -1;

        try {
            this.insertUserTraits.bindString(1, importId);
            this.insertUserTraits.bindString(2, rid);
            this.insertUserTraits.bindString(3, parent);
            this.insertUserTraits.bindString(4, trait);
            this.insertUserTraits.bindString(5, userValue);
            this.insertUserTraits.bindString(6,
                    timeStamp.format(Calendar.getInstance().getTime()));

            return this.insertUserTraits.executeInsert();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int getMaxPositionFromTraits(String importId) {

        int largest = 0;

        if (importId == null)
            return largest;

        Cursor cursor = db.rawQuery("select max(realPosition) from " + TRAITS + " where importId = ?", new String[]{importId});

        if (cursor.moveToFirst()) {
            largest = cursor.getInt(0);
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return largest;
    }

    public String[] getDataSet() {
        String[] data = null;

        Cursor cursor = db.query(IMPORT,
                new String[]{"rangeTable"}, null, null, null, null,
                "rangeTable asc");

        int count = 0;

        if (cursor.moveToFirst()) {
            data = new String[cursor.getCount()];

            do {
                data[count] = cursor.getString(0);

                count += 1;

            } while (cursor.moveToNext());
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    public String getDataSetByTable(String table) {
        String data = null;

        Cursor cursor = db.query(IMPORT,
                new String[]{"id", "rangeTable"}, "rangeTable = ?", new String[]{table}, null, null,
                "rangeTable asc");

        int count = 0;

        if (cursor.moveToFirst()) {
            data = cursor.getString(0);
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Helper function to close the database
     */
    public void close() {
        try {
            db.close();
        } catch (Exception e) {
            Log.w("Field Book Db", e.getMessage());
        }
    }

    /**
     * Helper function to open the database
     */
    public void open() {
        try {
            db = openHelper.getWritableDatabase();

            this.insertTraits = db.compileStatement(INSERTTRAITS);
            this.insertUserTraits = db.compileStatement(INSERTUSERTRAITS);
            this.insertImport = db.compileStatement(INSERTIMPORT);

        } catch (Exception e) {
            Log.w("Field Book Db", e.getMessage());

        }
    }

    /**
     * Helper function to delete all boolean values. Used when importing traits
     * v1.6 - Amended to consider trait
     */
    public void deleteAllBoolean(String importId, String parent) {

        if (importId == null)
            return;

        try {
            db.delete(USER_TRAITS, "parent like ? and trait like ? and importId = ?",
                    new String[]{parent, "boolean", importId});
        } catch (Exception e) {
            Log.w("Field Book Db", e.getMessage());

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

    public String arrayToDelimitedString(String[] s) {
        String value = "";

        for (int i = 0; i < s.length; i++) {
            if (i > 0)
                value += ",'" + s[i] + "'";
            else
                value += "'" + s[i] + "'";
        }

        return value;
    }

    public String arrayToSimpleString(String[] s) {
        String value = "";

        for (int i = 0; i < s.length; i++) {
            if (i > 0)
                value += "," + s[i];
            else
                value += s[i];
        }

        return value;
    }

    public String getImportDate(String importId) {
        String date = "";

        if (importId == null)
            return date;

        Cursor cursor = db
                .rawQuery(
                        "select importDate from " + IMPORT + " where id = ?",
                        new String[]{importId});


        if (cursor.moveToFirst()) {
            date = cursor.getString(0);
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return date;

    }

    /**
     * Retrieves the columns needed for export using a join statement
     * v1.6 - Amended to consider both trait and format
     */
    public Cursor getExportDBData(String importId, String[] fieldList, String[] traitList) {

        if (importId == null)
            return null;

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return null;

        String fields = arrayToString(tableName, fieldList);
        String traits = arrayToDelimitedString(traitList);

        return db
                .rawQuery(
                        "select " + fields + ", traits.trait, user_traits.userValue, " +
                                "user_traits.timeTaken from user_traits, " + tableName + ", traits where " +
                                "user_traits.rid = " + tableName + "." + ep.getString("ImportUniqueName", "") +
                                " and user_traits.parent = traits.trait and " +
                                "user_traits.trait = traits.format and user_traits.userValue is not null and " + tableName + ".importId = ? and traits.trait in (" + traits + ")",
                        new String[]{importId});
    }

    /**
     * Retrieves the columns needed for excel style export
     */
    public Cursor getExportExcelData(String importId, String[] fieldList) {

        if (importId == null)
            return null;

        String fields = arrayToString("", fieldList);

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return null;

        Cursor cursor = db
                .rawQuery(
                        "select " + fields + " from " + tableName + " where importId = ?",
                        new String[]{importId});

        return cursor;
    }

    /**
     * Used when exporting to Excel format by matching column and trait
     * v1.6 - Amended to consider both trait and format
     */
    public String getSingleValue(String importId, String col, String trait) {
        Cursor cursor = db
                .rawQuery(
                        "select user_traits.rid, traits.trait, user_traits.userValue from user_traits, " +
                                "traits where user_traits.parent = traits.trait and user_traits.trait = traits.format " +
                                "and user_traits.userValue is not null and rid = '" + col + "' " +
                                "and traits.trait = '" + trait + "' and traits.importId = " + importId, null);

        String val = "";

        if (cursor.moveToFirst()) {
            val = cursor.getString(2);
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return val;
    }

    /**
     * Used when exporting to Excel format by matching column and trait
     * v2.5 - Added to return array of values
     */
    public String getSingleValueArray(String importId, String col, String trait) {
        String actualValues = null;

        if (importId == null)
            return actualValues;

        Cursor cursor2 = db.rawQuery("select " + trait + " from " + TRAITS, null);

        String[] dataCol = new String[cursor2.getCount()];

        int i = 0;

        if (cursor2.moveToFirst()) {

            do {
                dataCol[i] = cursor2.getString(0);

                i += 1;

            } while (cursor2.moveToNext());
        }

        if (!cursor2.isClosed()) {
            cursor2.close();
        }

        String sql = "";

        if (trait.toUpperCase().equals("TRAIT")) {
            // Actual trait column uses sql

            sql = "select user_traits.rid, traits.trait, user_traits.userValue from user_traits, " +
                    "traits where user_traits.parent = traits." + trait + " and user_traits.trait = traits.format " +
                    "and user_traits.userValue is not null and rid = '" + col + "' " +
                    "and traits.trait in (" + arrayToDelimitedString(dataCol) + ") and traits.importId = " + importId;
        } else {
            // All non key columns are to use sql2 (no null value validation)

            sql = "select user_traits.rid, traits." + trait + " from user_traits, " +
                    "traits where user_traits.parent = traits.trait " +
                    "and user_traits.userValue is not null and rid = '" + col + "' " +
                    "and traits.importId = " + importId;
        }

        //Log.w("getSingleValueArray", sql);

        Cursor cursor = db
                .rawQuery(
                        sql, null);


        // skip loop, first value only
        if (cursor.moveToFirst()) {

            actualValues = cursor.getString(1);

            //Log.w("value", actualValues);

        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return actualValues;
    }

    /**
     * Used by the application when moving between ranges (to uniquely identify
     * them)
     */
    public String[] getPlotID(String importId) {

        String[] data = null;

        if (importId == null)
            return data;

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return null;

        Cursor cursor = db.query(tableName,
                new String[]{ep.getString("ImportUniqueName", "")}, "importId = ?", new String[]{importId}, null, null,
                ep.getString("ImportUniqueName", ""));

        int count = 0;

        if (cursor.moveToFirst()) {
            data = new String[cursor.getCount()];

            do {
                data[count] = cursor.getString(0);

                count += 1;

            } while (cursor.moveToNext());
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    public int getTraitPosition(String importId, String trait) {
        Cursor cursor = db.query(TRAITS, new String[]{"realPosition"},
                "trait like ? and importId like ?", new String[]{trait, importId}, null, null, "realPosition");

        int position = -1;

        if (cursor.moveToFirst()) {
            position = cursor.getInt(0);
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return position;
    }

    /**
     * Used by the application to return all traits which are visible
     */
    public String[] getVisibleTrait(String importId) {
        ArrayList<String> data = null;
        ArrayList<Integer> realPosition = null;

        if (importId == null)
            return null;

        Cursor cursor = db.query(TRAITS, new String[]{"id", "trait", "realPosition"},
                "isVisible like ? and importId like ?", new String[]{"true", importId}, null, null, "realPosition");

        int count = 0;

        boolean hasQuickTrait = false;

        if (cursor.moveToFirst()) {
            data = new ArrayList<String>();
            realPosition = new ArrayList<Integer>();

            do {
                data.add(cursor.getString(1));
                realPosition.add(cursor.getInt(2));

                count += 1;

                if (ep.getString("QuickTrait", "").length() > 0 && cursor.getString(1).equals(ep.getString("QuickTrait", ""))) {
                    hasQuickTrait = true;
                }
            } while (cursor.moveToNext());
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        //if (!hasQuickTrait & ep.getString("QuickTrait", "").length() > 0)
        //{
        //	int position = getTraitPosition(importId, ep.getString("QuickTrait", ""));
        //
        //	for (int m = data.size()-1; m >= 0; m--)
        //	{
        //		if (position > realPosition.get(m))
        //		{
        //			if (m == data.size()-1)
        //				data.add(ep.getString("QuickTrait", ""));
        //			else
        //				data.add(m, ep.getString("QuickTrait", ""));
        //		}
        //	}
        //
        //}

        if (data == null || data.size() == 0)
            return null;
        else
            return data.toArray(new String[data.size()]);
    }

    /**
     * Used by application to loops through formats which are visible
     */
    public String[] getFormat(String importId) {
        String[] data = null;

        if (importId == null)
            return data;

        Cursor cursor = db.query(TRAITS, new String[]{"id", "format", "realPosition"},
                "isVisible like ? and importId like ?", new String[]{"true", importId}, null, null, "realPosition");

        int count = 0;

        if (cursor.moveToFirst()) {
            data = new String[cursor.getCount()];

            do {
                data[count] = cursor.getString(1);

                count += 1;

            } while (cursor.moveToNext());
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    public int getTraitCount(String importId) {

        int count = 0;

        if (importId == null)
            return count;

        try {
            String sql = "select count(*) from traits where importId = ?";

            Cursor cursor = db.rawQuery(sql, new String[]{importId});

            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }

            cursor.close();
        } catch (Exception e) {
            // Force zero count on non existent import data
            count = 0;
        }

        return count;
    }

    public int getRangeCount(String importId) {

        int count = 0;

        if (importId == null)
            return count;

        String tableName = getRangeTableById(importId);

        try {
            String sql = "select count(*) from " + tableName + " where importId = ?";

            Cursor cursor = db.rawQuery(sql, new String[]{importId});

            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }

            cursor.close();
        } catch (Exception e) {
            // Force zero count on non existent import data
            count = 0;
        }

        return count;
    }

    public int getRangeColumnCount(String importId, String column) {

        int count = 0;

        if (importId == null)
            return count;

        String tableName = getRangeTableById(importId);

        try {
            String sql = "select count(" + column + ") from " + tableName + " where importId = ? and " + column + " is not null";

            Cursor cursor = db.rawQuery(sql, new String[]{importId});

            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }

            cursor.close();
        } catch (Exception e) {
            // Force zero count on non existent import data
            count = 0;
        }

        return count;
    }

    // Only suppresses duplicates, does not affect overall count
    public int getDistinctRangeColumnCount(String importId, String column) {

        int count = 0;

        if (importId == null)
            return count;

        String tableName = getRangeTableById(importId);

        try {
            String sql = "select distinct count(" + column + ") from " + tableName + " where importId = ? and " + column + " is not null";

            Cursor cursor = db.rawQuery(sql, new String[]{importId});

            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }

            cursor.close();
        } catch (Exception e) {
            // Force zero count on non existent import data
            count = 0;
        }

        return count;
    }

    // All rows, regardless of whether column has empty values
    public int geTotalRangeColumnCount(String importId, String column) {

        int count = 0;

        if (importId == null)
            return count;

        String tableName = getRangeTableById(importId);

        try {
            String sql = "select count(" + column + ") from " + tableName + " where importId = ? ";

            Cursor cursor = db.rawQuery(sql, new String[]{importId});

            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }

            cursor.close();
        } catch (Exception e) {
            // Force zero count on non existent import data
            count = 0;
        }

        return count;
    }

    /**
     * Returns all traits regardless of visibility. Used by the ratings screen
     */
    public String[] getAllTraits(String importId) {
        String[] data = null;

        if (importId == null)
            return data;

        Cursor cursor = db.query(TRAITS, new String[]{"id", "trait", "realPosition"},
                "importId = ?", new String[]{importId}, null, null, "realPosition");

        int count = 0;

        if (cursor.moveToFirst()) {
            data = new String[cursor.getCount()];

            do {
                data[count] = cursor.getString(1);

                count += 1;

            } while (cursor.moveToNext());
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - Returns all traits column titles as a string array
     */
    public String[] getTraitColumns(String importId) {

        String[] data = null;

        if (importId == null)
            return data;

        Cursor cursor = db.rawQuery("SELECT * from traits where importId = " + importId + " limit 1", null);

        if (cursor.moveToFirst()) {
            int i = cursor.getColumnCount() - 2;

            data = new String[i];

            int k = 0;

            for (int j = 0; j < cursor.getColumnCount(); j++) {
                if (!cursor.getColumnName(j).equals("id") & !cursor.getColumnName(j).equals("importId")) {
                    data[k] = cursor.getColumnName(j);
                    k += 1;
                }
            }
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - Returns all traits column titles as a cursor
     */
    public Cursor getAllTraitsForExport(String importId) {

        if (importId == null)
            return null;

        Cursor cursor = db.query(TRAITS, getTraitColumns(importId),
                "importId = ?", new String[]{importId}, null, null, "id");

        return cursor;
    }

    /**
     * V2 - Get all traits in the system, in order, as TraitObjects
     */
    public ArrayList<TraitObject> getAllTraitObjects(String importId) {

        ArrayList<TraitObject> list = new ArrayList<TraitObject>();

        if (importId == null)
            return list;

        Cursor cursor = db.query(TRAITS, new String[]{"id", "trait", "format", "defaultValue",
                        "minimum", "maximum", "details", "categories", "isVisible", "realPosition"},
                "importId = ?", new String[]{importId}, null, null, "realPosition");

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
    public HashMap getTraitVisibility(String importId) {
        HashMap data = new HashMap();

        if (importId == null)
            return data;

        Cursor cursor = db.query(TRAITS, new String[]{"id", "trait",
                "isVisible", "realPosition"}, "importId = ?", new String[]{importId}, null, null, "realPosition");

        if (cursor.moveToFirst()) {
            do {
                data.put(cursor.getString(1), cursor.getString(2));

            } while (cursor.moveToNext());
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Returns a particular trait as an object
     */
    public TraitObject getDetail(String importId, String trait) {
        TraitObject data = new TraitObject();

        data.trait = "";
        data.format = "";
        data.defaultValue = "";
        data.minimum = "";
        data.maximum = "";
        data.details = "";
        data.categories = "";

        if (importId == null)
            return data;

        Cursor cursor = null;

        //if (ep.getString("QuickTrait", "").length() > 0)
        //{
        //	cursor = this.db.query(TRAITS, new String[] { "trait", "format", "defaultValue", "minimum",
        //	        "maximum", "details", "categories", "id" }, "trait like ? and importId = ?",
        //	        new String[] { trait, importId }, null, null, null);
        //}
        //else
        //{
        cursor = db.query(TRAITS, new String[]{"trait", "format", "defaultValue", "minimum",
                        "maximum", "details", "categories", "id"}, "trait like ? and isVisible like ? and importId = ?",
                new String[]{trait, "true", importId}, null, null, null);
        //}

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

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Returns saved data based on plot_id
     * v1.6 - Amended to consider both trait and format
     */
    public HashMap getUserDetail(String importId, String plotId) {
        HashMap data = new HashMap();

        if (importId == null)
            return data;

        Cursor cursor = db.query(USER_TRAITS, new String[]{"parent", "trait",
                        "userValue", "rid"}, "rid like ? and importId = ?", new String[]{plotId, importId},
                null, null, null);

        if (cursor.moveToFirst()) {
            do {
                data.put(cursor.getString(0), cursor.getString(2));
            } while (cursor.moveToNext());
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    public boolean getTraitExists(String importId, String trait) {
        boolean exists = false;

        Cursor cursor = db.rawQuery("select id from " + TRAITS + " where trait = ? collate nocase", new String[]{trait});

        if (cursor.moveToFirst()) {
            exists = true;
        }

        cursor.close();

        return exists;
    }

    /**
     * Check if a trait exists within the database
     * v1.6 - Amended to consider both trait and format
     */
    public boolean getTraitExists(int id, String importId, String parent, String trait) {
        boolean haveData = false;

        if (importId == null)
            return haveData;

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return false;

        Cursor cursor = db
                .rawQuery(
                        "select " + tableName + ".id, user_traits.userValue from user_traits, " + tableName + " where " +
                                "user_traits.rid = " + tableName + "." + ep.getString("ImportUniqueName", "") +
                                " and " + tableName + ".id = ? and user_traits.parent like ? and user_traits.trait like ? and " + tableName + ".importId = ?",
                        new String[]{String.valueOf(id), parent, trait, importId});

        if (cursor.moveToFirst()) {
            if (cursor.getString(1) != null) {
                haveData = true;
            }
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return haveData;
    }

    /**
     * Get range table name by id
     */
    public String getRangeTableById(String importId) {

        String tableName = "";

        if (importId == null)
            return tableName;

        try {
            Cursor cursor = db.query(IMPORT, new String[]{"rangetable"}, "id = ?", new String[]{importId},
                    null, null, "id");

            if (cursor.moveToFirst()) {
                tableName = cursor.getString(0);
            }

            if (!cursor.isClosed()) {
                cursor.close();
            }
        } catch (Exception e) {
            Log.w("Missing table", "" + importId);
            tableName = "";
        }

        return tableName;
    }

    /**
     * Get range table name by id
     */
    public String getImportTableByName(String name) {

        String tableId = "";

        if (name == null)
            return "";

        try {
            Cursor cursor = db.query(IMPORT, new String[]{"id"}, "rangetable = ?", new String[]{name},
                    null, null, "id");

            if (cursor.moveToFirst()) {
                tableId = cursor.getString(0);
            }

            if (!cursor.isClosed()) {
                cursor.close();
            }
        } catch (Exception e) {
            Log.w("Missing table", name);
            tableId = "";
        }

        return tableId;
    }

    /**
     * Returns the primary key for all ranges
     */
    public int[] getAllRangeID(String importId) {

        int[] data = null;

        if (importId == null)
            return data;

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return data;

        Cursor cursor = db.query(tableName, new String[]{"id"}, "importId = ?", new String[]{importId},
                null, null, "id");

        int count = 0;

        if (cursor.moveToFirst()) {
            data = new int[cursor.getCount()];

            do {
                data[count] = cursor.getInt(0);

                count += 1;
            } while (cursor.moveToNext());
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Returns the primary key for all ranges
     */
    public String[] getAllRangeSecondOrganizer(String importId) {

        String[] data = null;

        if (importId == null)
            return data;

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return data;

        Cursor cursor = db.query(tableName, new String[]{ep.getString("ImportSecondName", "")}, "importId = ?", new String[]{importId},
                null, null, "id");

        int count = 0;

        if (cursor.moveToFirst()) {
            data = new String[cursor.getCount()];

            do {
                data[count] = cursor.getString(0);

                count += 1;
            } while (cursor.moveToNext());
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - Used for mapping, returns all plot_id in order
     */
    public String[] getAllPlotID(String importId) {

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return null;

        Cursor cursor = db.query(tableName, new String[]{ep.getString("ImportUniqueName", "")}, "importId = ?", new String[]{importId},
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

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    public String[] getAllPlotRangeDataForTrait(String importId, String trait) {

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return null;

        String[] data = getAllRangeSecondOrganizer(importId);

        String sql = "select " + ep.getString("ImportFirstName", "")
                + ", " + ep.getString("ImportSecondName", "")
                + ", userValue from ("
                + "select f." + ep.getString("ImportFirstName", "")
                + ", f." + ep.getString("ImportSecondName", "")
                + ", ut.userValue, ut.parent from " + tableName + " f "
                + "left join user_traits ut on f." + ep.getString("ImportUniqueName", "") + " = ut.rid) "
                + "left join traits t on parent = t.trait "
                + "and t.format = '"
                + trait + "' order by CAST(" + ep.getString("ImportFirstName", "")
                + " as integer), CAST(" + ep.getString("ImportSecondName", "")
                + " as integer)";

        Log.w("OneRowGrid", sql);

        Cursor cursor = db.rawQuery(sql, null);

        if (cursor.moveToFirst()) {

            int count = 0;

            do {
                // loop through full data set, set matches to corresponding value
                if (!cursor.isAfterLast()) {
                    if (cursor.getString(1).equals(data[count])) {
                        data[count] = cursor.getString(2);
                        cursor.moveToNext();
                    } else {
                        data[count] = "";
                    }
                } else {
                    data[count] = "";
                }

                count += 1;
            }
            while (count < data.length);
        } else {
            // Empty out grid in cases where there are no matching trait values
            for (int i = 0; i < data.length; i++) {
                data[i] = "";
            }
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }


    /**
     * Returns saved data based on trait, range and plot Meant for the on screen
     * drop downs
     */
    public String[] getDropDownRange(String importId, String trait, String plotId) {

        if (trait.length() == 0)
            return null;

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return null;

        try {
            Cursor cursor = db.query(tableName, new String[]{trait},
                    ep.getString("ImportUniqueName", "") + " like ? and importId = ?", new String[]{plotId, importId},
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

            if (!cursor.isClosed()) {
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
    public RangeObject getRange(int id, String importId) {
        RangeObject data = new RangeObject();

        data.plot = "";
        data.plot_id = "";
        data.range = "";

        String tableName = getRangeTableById(importId);

        if (tableName.equals("") | ep.getString("ImportFirstName", "").length() == 0 | ep.getString("ImportSecondName", "").length() == 0 | ep.getString("ImportUniqueName", "").length() == 0)
            return data;

        Cursor cursor = db.query(tableName, new String[]{ep.getString("ImportFirstName", ""),
                        ep.getString("ImportSecondName", ""),
                        ep.getString("ImportUniqueName", ""), "id"}, "id = ? and importId = ?",
                new String[]{String.valueOf(id), importId}, null, null, null);

        if (cursor.moveToFirst()) {
            //data.entry = cursor.getString(0);
            data.range = cursor.getString(0);
            data.plot = cursor.getString(1);
            data.plot_id = cursor.getString(2);
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - Returns the ids for items that match the specified range and plot
     */
    public int[] getAllRange(String importId, String range, String plot) {

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return null;

        try {
            Cursor cursor = db.query(tableName, new String[]{"id"},
                    ep.getString("ImportFirstName", "") + " like ? and " + ep.getString("ImportSecondName", "") + " like ? and importId = ?", new String[]{range, plot, importId},
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

            if (!cursor.isClosed()) {
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
    public void deleteTrait(String importId, String rid, String parent) {

        if (importId == null)
            return;

        try {
            db.delete(USER_TRAITS, "rid like ? and parent like ? and importId = ?",
                    new String[]{rid, parent, importId});
        } catch (Exception e) {

        }
    }

    public boolean getImportFieldExists(String field) {
        boolean exists = false;

        Cursor cursor = db.rawQuery("select id from " + IMPORT + " where rangeTable = ? collate nocase", new String[]{field});

        if (cursor.moveToFirst()) {
            exists = true;
        }

        cursor.close();

        return exists;
    }

    /**
     * Helper function
     * v2.5
     */
    public void deleteTraitByValue(String importId, String rid, String parent, String value) {

        if (importId == null)
            return;

        try {
            db.delete(USER_TRAITS, "rid like ? and parent like ? and importId = ? and userValue = ?",
                    new String[]{rid, parent, importId, value});
        } catch (Exception e) {

        }
    }

    /**
     * Helper function
     * v2 - Delete trait
     */
    public void deleteTrait(String importId, String id) {

        if (importId == null)
            return;

        try {
            db.delete(TRAITS, "id = ? and importId = ?",
                    new String[]{id, importId});
        } catch (Exception e) {

        }
    }

    /**
     * Helper function to delete all data in the table
     */
    public void deleteTable(String table) {
        try {
            db.delete(table, null, null);
        } catch (Exception e) {

        }
    }

    public void purgeDataset(String importId) {
        String tableName = getRangeTableById(importId);

        Log.w("Purge", tableName);

        deleteTableByImportId(TRAITS, importId);

        Log.w("Purge", "traits");

        deleteTableByImportId(USER_TRAITS, importId);

        Log.w("Purge", "user traits");

        dropTable(tableName);

        Log.w("Drop table", tableName);

        deleteImport(importId);

        Log.w("Parent Import", "deleted");
    }

    /**
     * Helper function to delete all data in the table
     */
    public void deleteTableByImportId(String table, String importId) {
        try {
            db.delete(table, "importId = ?", new String[]{importId});
        } catch (Exception e) {

        }
    }

    public void deleteImport(String importId) {
        try {
            db.delete(IMPORT, "id = ?", new String[]{importId});
        } catch (Exception e) {

        }
    }

    /**
     * Removes the range table
     */
    public void dropTable(String rangeTable) {

        db.execSQL("DROP TABLE IF EXISTS " + rangeTable);
    }

    /**
     * Creates the range table based on column names
     */
    public void createRange(String rangeTable, String[] data) {

        String sql = "CREATE TABLE " + rangeTable + "(id INTEGER PRIMARY KEY, importId INTEGER, ";

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
    public String getRangeColumnsAsString(String importId) {
        if (importId == null)
            return null;

        try {
            String[] s = getRangeColumns(importId);

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
    public String getTraitColumnsAsString(String importId) {
        if (importId == null)
            return null;

        try {
            String[] s = getAllTraits(importId);

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
    public String getTraitColumnsAsString2(String importId) {
        if (importId == null)
            return null;

        try {
            String[] s = getAllTraits(importId);

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
    public String[] getRangeColumns(String importId) {

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return null;

        Cursor cursor = db.rawQuery("SELECT * from " + tableName + " where importId = " + importId + " limit 1", null);

        String[] data = null;

        if (cursor.moveToFirst()) {
            int i = cursor.getColumnCount() - 2;

            data = new String[i];

            int k = 0;

            for (int j = 0; j < cursor.getColumnCount(); j++) {
                // need to hide importId
                if (!cursor.getColumnName(j).equals("id") & !cursor.getColumnName(j).equals("importId")) {

                    data[k] = cursor.getColumnName(j);
                    k += 1;
                }
            }
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Returns the column names for the range table
     */
    public String[] getRangeColumnsWithoutOrganizer(String importId) {

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return null;

        Cursor cursor = db.rawQuery("SELECT * from " + tableName + " where importId = " + importId + " limit 1", null);

        String[] data = null;

        if (cursor.moveToFirst()) {
            int i = cursor.getColumnCount() - 4;

            data = new String[i];

            int k = 0;

            String first = ep.getString("ImportFirstName", "");
            String second = ep.getString("ImportSecondName", "");

            for (int j = 0; j < cursor.getColumnCount(); j++) {

                // need to hide importId and organizers
                if (!cursor.getColumnName(j).equals("id") & !cursor.getColumnName(j).equals("importId") & !cursor.getColumnName(j).equals(first) & !cursor.getColumnName(j).equals(second)) {

                    data[k] = cursor.getColumnName(j).replace("//", "/");
                    k += 1;

                    //Log.w("data " + k, cursor.getColumnName(j));
                }
            }
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Returns column data for the range table
     * V2.5
     */
    public ArrayList<ColumnData> getRangeColumnDataWithoutOrganizer(String importId) {

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return null;

        Cursor cursor = db.rawQuery("SELECT * from " + tableName + " where importId = " + importId + " limit 1", null);

        ArrayList<ColumnData> info = new ArrayList<ColumnData>();

        if (cursor.moveToFirst()) {
            int i = cursor.getColumnCount() - 4;

            String first = ep.getString("ImportFirstName", "");
            String second = ep.getString("ImportSecondName", "");

            for (int j = 0; j < cursor.getColumnCount(); j++) {

                // need to hide importId and organizers
                if (!cursor.getColumnName(j).equals("id") & !cursor.getColumnName(j).equals("importId") & !cursor.getColumnName(j).equals(first) & !cursor.getColumnName(j).equals(second)) {

                    ColumnData c = new ColumnData();

                    c.header = cursor.getColumnName(j).replace("//", "/");
                    c.value = cursor.getString(j).replace("//", "/");

                    info.add(c);

                    //Log.w("data " + k, cursor.getColumnName(j));
                }
            }
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return info;
    }

    /**
     * Returns column data for the range table
     * V2.5
     */
    public ArrayList<ColumnData> getTraitColumnDataWithoutOrganizer(String importId) {

        Cursor cursor = db.rawQuery("SELECT * from traits where importId = " + importId + " limit 1", null);

        ArrayList<ColumnData> info = new ArrayList<ColumnData>();

        if (cursor.moveToFirst()) {
            int i = cursor.getColumnCount() - 2;

            for (int j = 0; j < cursor.getColumnCount(); j++) {

                // need to hide importId and organizers
                if (!cursor.getColumnName(j).equals("id") & !cursor.getColumnName(j).equals("importId")) {

                    ColumnData c = new ColumnData();

                    c.header = cursor.getColumnName(j).replace("//", "/");
                    c.value = cursor.getString(j).replace("//", "/");

                    info.add(c);

                    //Log.w("data " + k, cursor.getColumnName(j));
                }
            }
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return info;
    }

    /**
     * Returns the column names for the range table
     */
    public int findRangeColumns(String importId, String col) {

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return -1;


        Cursor cursor = db.rawQuery("SELECT * from " + tableName + " where importId = " + importId + " limit 1", null);

        int pos = -1;

        if (cursor.moveToFirst()) {
            for (int j = 0; j < cursor.getColumnCount(); j++) {
                if (!cursor.getColumnName(j).equals("id") & !cursor.getColumnName(j).equals("importId")) {
                    if (cursor.getColumnName(j).toUpperCase().equals(col.toUpperCase()))
                        pos = j;

                }
            }
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return pos;
    }

    /**
     * Returns the column names for the range table
     */
    public int findRangeColumns(String importId, String col, String[] fieldList) {

        String fields = arrayToString("", fieldList);

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return -1;

        String sql = "SELECT " + fields + " from " + tableName + " where importId = " + importId + " limit 1";

        Cursor cursor = db.rawQuery(sql, null);

        int pos = -1;

        if (cursor.moveToFirst()) {
            for (int j = 0; j < cursor.getColumnCount(); j++) {
                if (!cursor.getColumnName(j).equals("id") & !cursor.getColumnName(j).equals("importId")) {
                    if (cursor.getColumnName(j).toUpperCase().equals(col.toUpperCase()))
                        pos = j;

                }
            }
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return pos;
    }

    /**
     * V2 - Returns raw data for a trait, so that analysis can calculate the values based on it
     */
    public HashMap<Integer, String> analyze(String importId, String trait) {

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return null;


        Cursor cursor = db
                .rawQuery(
                        "select " + tableName + ".id, user_traits.uservalue from user_traits, " + tableName + ", " +
                                "traits where user_traits.rid = " + tableName + "." + ep.getString("ImportUniqueName", "") +
                                " and user_traits.parent = traits.trait and user_traits.trait = traits.format " +
                                "and user_traits.userValue is not null and user_traits.parent like '" + trait + "' and " + tableName + ".importId = " + importId,
                        null);

        HashMap<Integer, String> data = null;

        if (cursor.moveToFirst()) {
            data = new HashMap<Integer, String>(cursor.getCount());

            int count = 0;

            do {
                data.put(cursor.getInt(0), cursor.getString(1));

                count += 1;
            } while (cursor.moveToNext());
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - Get the smallest value for a trait
     * Used in analysis only
     */
    public String getTraitMinimum(String importId, String trait) {
        if (importId == null)
            return "";

        Cursor cursor = db
                .rawQuery("select minimum from traits where trait = ? and importId = ?", new String[]{trait, importId});

        String val = "";

        if (cursor.moveToFirst()) {
            val = cursor.getString(0);
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return val;
    }

    /**
     * Inserting traits data. The last field isVisible determines if the trait
     * is visible when using the app
     */
    public long insertTraits(String importId, String trait, String format, String defaultValue,
                             String minimum, String maximum, String details, String categories,
                             String isVisible, String realPosition) {

        if (importId == null)
            return -1;

        try {
            this.insertTraits.bindString(1, importId);
            this.insertTraits.bindString(2, trait);
            this.insertTraits.bindString(3, format);
            this.insertTraits.bindString(4, defaultValue);
            this.insertTraits.bindString(5, minimum);
            this.insertTraits.bindString(6, maximum);
            this.insertTraits.bindString(7, details);
            this.insertTraits.bindString(8, categories);
            this.insertTraits.bindString(9, isVisible);
            this.insertTraits.bindString(10, realPosition);

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

            db.update(TRAITS, c, "id = ?", new String[]{id});

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

            return db.update(TRAITS, c, "id = ?", new String[]{id});
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * V2 - Check if trait exists (non case sensitive)
     */
    public boolean hasTrait(String importId, String name) {
        boolean exist;

        if (importId == null)
            return false;

        Cursor cursor = db.rawQuery("select id from traits where " +
                "trait = ? and importId = ? COLLATE NOCASE", new String[]{name, importId});

        if (cursor.moveToFirst())
            exist = true;
        else
            exist = false;

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return exist;
    }

    // Summary Screen function
    public int getUniqueRangeCount(String importId) {

        int count = 0;

        if (importId == null)
            return count;

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return count;

        Cursor cursor = db.rawQuery("SELECT count( distinct " + ep.getString("ImportFirstName", "") + ") from " + tableName + "", null);

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return count;
    }

    // Summary Screen function
    public int getUniquePlotCount(String importId) {

        int count = 0;

        if (importId == null)
            return count;

        String tableName = getRangeTableById(importId);

        if (tableName.equals(""))
            return count;

        Cursor cursor = db.rawQuery("SELECT count(" + ep.getString("ImportSecondName", "") + ") from " + tableName + "", null);

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return count;
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
    public void defaultFieldTable(String tableName) {
        db.execSQL("DROP TABLE IF EXISTS " + tableName);

        db.execSQL("CREATE TABLE "
                + tableName
                + "(id INTEGER PRIMARY KEY, importId INTEGER, range TEXT, plot TEXT, entry TEXT, plot_id TEXT, pedigree TEXT)");
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
                    + IMPORT
                    + "(id INTEGER PRIMARY KEY, filename TEXT, rangeTable TEXT, importDate TEXT, exportCount TEXT)");

            //db.execSQL("CREATE TABLE "
            //		+ RANGE
            //		+ "(id INTEGER PRIMARY KEY, importId INTEGER, range TEXT, plot TEXT, entry TEXT, plot_id TEXT, pedigree TEXT)");

            db.execSQL("CREATE TABLE "
                    + TRAITS
                    + "(id INTEGER PRIMARY KEY, importId INTEGER, trait TEXT, format TEXT, defaultValue TEXT, minimum TEXT, maximum TEXT, details TEXT, categories TEXT, isVisible TEXT, realPosition int)");

            db.execSQL("CREATE TABLE "
                    + USER_TRAITS
                    + "(id INTEGER PRIMARY KEY, importId INTEGER, rid TEXT, parent TEXT, trait TEXT, userValue TEXT, timeTaken TEXT)");

            db.execSQL("CREATE TABLE "
                    + MAPDATA
                    + "(id INTEGER PRIMARY KEY, plotCount INTEGER, plot TEXT)");

            try {
                db.execSQL("CREATE TABLE android_metadata (locale TEXT)");
                db.execSQL("INSERT INTO android_metadata(locale) VALUES('en_US')");
            } catch (Exception e) {
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            //Log.w("FieldBook",
            //		"Upgrading database, this will drop tables and recreate.");

            //db.execSQL("DROP TABLE IF EXISTS " + RANGE);
            //db.execSQL("DROP TABLE IF EXISTS " + TRAITS);
            //db.execSQL("DROP TABLE IF EXISTS " + USER_TRAITS);

            onCreate(db);
        }
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

                for (String asset : assets) {
                    copyFileOrDir(fullPath, path + "/" + asset);
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

    public void beginTransaction() {
        db.beginTransaction();
    }

    public void endTransaction() {
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public void rollback() {
        db.endTransaction();
    }

    public boolean copyTables(String dbPath) {
        //SQLiteDatabase externalDb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.NO_LOCALIZED_COLLATORS);

        try {
            db.execSQL("attach database " + dbPath + " as db2");

            Cursor c = db.rawQuery("SELECT name FROM db2.sqlite_master WHERE type='table';", null);

            if (c != null) {
                if (c.moveToFirst()) {
                    do {
                        String sql = "insert into " + c.getString(0) + " if exists select * from db2." + c.getString(0);

                        Log.w("Table Sql", sql);
                        Log.w("Table Copy", c.getString(0));

                        db.execSQL(sql);

                    } while (c.moveToNext());
                }
            }

            db.execSQL("dettach database db2");

            c.close();

            return true;
        } catch (Exception e) {
            e.printStackTrace();

            return false;
        }
    }

    /**
     * Copy old file to new file
     */
    private Boolean copyFile(File oldFile, File newFile) throws IOException {
        if (oldFile.exists()) {
            try {
                copyFileCall(new FileInputStream(oldFile), new FileOutputStream(newFile));
                openHelper = new OpenHelper(this.context);
                open();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Helper function to copy database
     */
    public static void copyFileCall(FileInputStream fromFile, FileOutputStream toFile) throws IOException {
        FileChannel fromChannel = null;
        FileChannel toChannel = null;

        try {
            fromChannel = fromFile.getChannel();
            toChannel = toFile.getChannel();
            fromChannel.transferTo(0, fromChannel.size(), toChannel);
        } finally {
            try {
                if (fromChannel != null) {
                    fromChannel.close();
                }
            } finally {
                if (toChannel != null) {
                    toChannel.close();
                }
            }
        }
    }

    private String getDatabasePath(Context context) {
        return context.getDatabasePath(DATABASE_NAME).getPath();
    }


    private boolean saveSharedPreferencesToFile(String filename) {
        boolean res = false;
        ObjectOutputStream output = null;

        try {
            File newFile = new File(Constants.BACKUPPATH + "/" + filename);

            output = new ObjectOutputStream(new FileOutputStream(newFile));
            SharedPreferences pref = context.getSharedPreferences("Settings", 0);

            output.writeObject(pref.getAll());

            res = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (output != null) {
                    output.flush();
                    output.close();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        return res;
    }

    @SuppressWarnings({"unchecked"})
    private boolean loadSharedPreferencesFromFile(String filename) {
        boolean res = false;
        ObjectInputStream input = null;

        try {
            File newFile = new File(Constants.BACKUPPATH + "/" + filename);

            Log.w("ImportPrefPath", filename);

            input = new ObjectInputStream(new FileInputStream(newFile));

            Editor prefEdit = context.getSharedPreferences("Settings", 0).edit();

            //prefEdit.clear();

            Map<String, ?> entries = (Map<String, ?>) input.readObject();

            for (Entry<String, ?> entry : entries.entrySet()) {

                if (entry.getKey().equals("ImportUniquePosition") | entry.getKey().equals("ImportFirstPosition") | entry.getKey().equals("ImportSecondPosition") | entry.getKey().equals("ImportExtraPosition") | entry.getKey().equals("ImportUniqueName") | entry.getKey().equals("ImportFirstName") | entry.getKey().equals("ImportSecondName")) {
                    // Only keep the entries we need from initial import
                    Object v = entry.getValue();
                    String key = entry.getKey();

                    if (v instanceof Boolean)
                        prefEdit.putBoolean(key, ((Boolean) v).booleanValue());
                    else if (v instanceof Float)
                        prefEdit.putFloat(key, ((Float) v).floatValue());
                    else if (v instanceof Integer)
                        prefEdit.putInt(key, ((Integer) v).intValue());
                    else if (v instanceof Long)
                        prefEdit.putLong(key, ((Long) v).longValue());
                    else if (v instanceof String)
                        prefEdit.putString(key, ((String) v));

                }
            }

            prefEdit.commit();

            res = true;

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        return res;
    }
}
