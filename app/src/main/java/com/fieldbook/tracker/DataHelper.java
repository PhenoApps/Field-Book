package com.fieldbook.tracker;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.fields.FieldObject;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.search.SearchData;
import com.fieldbook.tracker.traits.TraitObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All database related functions are here
 */
public class DataHelper {
    private static final String DATABASE_NAME = "fieldbook.db";
    static final int DATABASE_VERSION = 7;

    private static String TAG = "Field Book";

    public static final String RANGE = "range";
    public static final String TRAITS = "traits";
    private static final String USER_TRAITS = "user_traits";
    private static final String EXP_INDEX = "exp_id";
    private static final String PLOTS = "plots";
    private static final String PLOT_ATTRIBUTES = "plot_attributes";
    private static final String PLOT_VALUES = "plot_values";

    private static String TICK = "`";

    private Context context;
    public static SQLiteDatabase db;

    private SQLiteStatement insertTraits;
    private SQLiteStatement insertUserTraits;

    private static final String INSERTTRAITS = "insert into "
            + TRAITS
            + "(trait, format, defaultValue, minimum, maximum, details, categories, "
            + "isVisible, realPosition) values (?,?,?,?,?,?,?,?,?)";

    private static final String INSERTUSERTRAITS = "insert into " + USER_TRAITS
            + "(rid, parent, trait, userValue, timeTaken, person, location, rep, notes, exp_id) values (?,?,?,?,?,?,?,?,?,?)";

    private SimpleDateFormat timeStamp;

    private OpenHelper openHelper;

    private SharedPreferences ep;

    public DataHelper(Context context) {
        try {
            this.context = context;
            openHelper = new OpenHelper(this.context);
            db = openHelper.getWritableDatabase();
            ep = context.getSharedPreferences("Settings", 0);

            this.insertTraits = db.compileStatement(INSERTTRAITS);
            this.insertUserTraits = db.compileStatement(INSERTUSERTRAITS);

            timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ",
                    Locale.getDefault());

        } catch (Exception e) {
            e.printStackTrace();
            Log.w("FieldBook", "Unable to create or open database");
        }
    }

    /**
     * Helper function to change visibility of a trait. Used in the ratings
     * screen
     */
    public void updateTraitVisibility(String trait, boolean val) {
        db.execSQL("update " + TRAITS
                + " set isVisible = ? where trait like ?", new String[]{
                String.valueOf(val), trait});
    }

    /**
     * Helper function to insert user data. For example, the data entered for
     * numeric format, or date for date format The timestamp is updated within
     * this function as well
     * v1.6 - Amended to consider both trait and user data
     */
    public long insertUserTraits(String rid, String parent, String trait, String userValue, String person, String location, String notes, String exp_id) {

        Cursor cursor = db.rawQuery("SELECT * from user_traits WHERE user_traits.rid = ? and user_traits.parent = ?", new String[]{rid, parent});
        int rep = cursor.getCount() + 1;

        try {
            this.insertUserTraits.bindString(1, rid);
            this.insertUserTraits.bindString(2, parent);
            this.insertUserTraits.bindString(3, trait);
            this.insertUserTraits.bindString(4, userValue);
            this.insertUserTraits.bindString(5, timeStamp.format(Calendar.getInstance().getTime()));
            this.insertUserTraits.bindString(6, person);
            this.insertUserTraits.bindString(7, location);
            this.insertUserTraits.bindString(8, Integer.toString(rep));
            this.insertUserTraits.bindString(9, notes);
            this.insertUserTraits.bindString(10, exp_id);

            return this.insertUserTraits.executeInsert();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Get rep of current plot/trait combination
     */
    public int getRep(String plot, String trait) {
        Cursor cursor = db.rawQuery("SELECT * from user_traits WHERE user_traits.rid = ? and user_traits.parent = ?", new String[]{plot, trait});
        return cursor.getCount() + 1;
    }

    public int getMaxPositionFromTraits() {

        int largest = 0;

        Cursor cursor = db.rawQuery("select max(realPosition) from " + TRAITS, null);

        if (cursor.moveToFirst()) {
            largest = cursor.getInt(0);
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return largest;
    }

    /**
     * Helper function to close the database
     */
    public void close() {
        try {
            db.close();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
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
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * V2 - Convert array to String
     */
    private String arrayToString(String table, String[] s) {
        String value = "";

        for (int i = 0; i < s.length; i++) {
            if (table.length() > 0)
                value += table + "." + TICK + s[i] + TICK;
            else
                value += s[i];

            if (i < s.length - 1)
                value += ",";
        }

        return value;
    }

    /**
     * Retrieves the columns needed for export using a join statement
     */
    public Cursor getExportDBData(String[] fieldList, String[] traits) {
        String fields = arrayToString("range", fieldList);
        String activeTraits = arrayToLikeString(traits);

        String query = "select " + fields + ", traits.trait, user_traits.userValue, " +
                "user_traits.timeTaken, user_traits.person, user_traits.location, user_traits.rep" +
                " from user_traits, range, traits where " +
                "user_traits.rid = range." + TICK + ep.getString("ImportUniqueName", "") + TICK +
                " and user_traits.parent = traits.trait and " +
                "user_traits.trait = traits.format and user_traits.userValue is not null and " + activeTraits;

        Log.i("Field Book", query);

        return db.rawQuery(query,null);
    }

    private String arrayToLikeString(String[] visibleTrait) {
        String value = "(";

        for (int i = 0; i < visibleTrait.length; i++) {
            //TODO replace apostrophes with ticks
            value += "user_traits.parent like '" + visibleTrait[i] + "'";
            if (i != visibleTrait.length - 1) {
                value += " or ";
            }
        }

        value += ")";
        return value;
    }


    /**
     * Convert EAV database to relational
     * TODO add where statement for repeated values
     */
    public Cursor convertDatabaseToTable(String[] col, String[] traits) {
        String query;
        String[] rangeArgs = new String[col.length];
        String[] traitArgs = new String[traits.length];
        String joinArgs = "";

        for (int i = 0; i < col.length; i++) {
            rangeArgs[i] = "range." + TICK + col[i] + TICK;
        }

        for (int i = 0; i < traits.length; i++) {
            traitArgs[i] = "m" + i + ".userValue as '" + traits[i] + "'";
            joinArgs = joinArgs + "LEFT JOIN user_traits m" + i + " ON range." + TICK + ep.getString("ImportUniqueName", "")
                    + TICK + " = m" + i + ".rid AND m" + i + ".parent = '" + traits[i] + "' ";
        }

        query = "SELECT " + convertToCommaDelimited(rangeArgs) + " , " + convertToCommaDelimited(traitArgs) +
                " FROM range range " + joinArgs + "GROUP BY range." + TICK + ep.getString("ImportUniqueName", "") + TICK + "ORDER BY range.id";

        Log.i("DH", query);

        return db.rawQuery(query, null);
    }


    /**
     * Used by the application to return all traits which are visible
     */
    public String[] getVisibleTrait() {
        String[] data = null;

        Cursor cursor = db.query(TRAITS, new String[]{"id", "trait", "realPosition"},
                "isVisible like ?", new String[]{"true"}, null, null, "realPosition");

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
     * Used by application to loops through formats which are visible
     */
    public String[] getFormat() {
        String[] data = null;

        Cursor cursor = db.query(TRAITS, new String[]{"id", "format", "realPosition"},
                "isVisible like ?", new String[]{"true"}, null, null, "realPosition");

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
     * Returns all traits regardless of visibility. Used by the ratings screen
     */
    public String[] getAllTraits() {
        String[] data = null;

        Cursor cursor = db.query(TRAITS, new String[]{"id", "trait", "realPosition"},
                null, null, null, null, "realPosition");

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
     * Get data from specific column of trait table to reorder
     */
    public String[] getTraitColumnData(String column) {
        String[] data = null;

        Cursor cursor = db.query(TRAITS, new String[]{column},
                null, null, null, null, null);

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
     * Write new realPosition
     */
    public void writeNewPosition(String column, String id, String position) {
        ContentValues cv = new ContentValues();
        cv.put("realPosition", position);
        db.update(TRAITS, cv, column + "= ?", new String[]{id});
    }

    /**
     * V2 - Returns all traits column titles as a string array
     */
    public String[] getTraitColumns() {
        Cursor cursor = db.rawQuery("SELECT * from traits limit 1", null);

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

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - Returns all traits column titles as a cursor
     */
    public Cursor getAllTraitsForExport() {
        Cursor cursor = db.query(TRAITS, getTraitColumns(),
                null, null, null, null, "id");

        return cursor;
    }

    /**
     * V4 - Get all traits in the system, in order, as TraitObjects
     */
    public ArrayList<FieldObject> getAllFieldObjects() {

        ArrayList<FieldObject> list = new ArrayList<>();

        Cursor cursor = db.query(EXP_INDEX, new String[]{"exp_id", "exp_name", "unique_id", "primary_id",
                        "secondary_id", "date_import", "date_edit", "date_export", "count"},
                null, null, null, null, "exp_id"
        );

        if (cursor.moveToFirst()) {
            do {
                FieldObject o = new FieldObject();
                o.setExp_id(cursor.getInt(0));
                o.setExp_name(cursor.getString(1));
                o.setUnique_id(cursor.getString(2));
                o.setPrimary_id(cursor.getString(3));
                o.setSecondary_id(cursor.getString(4));
                o.setDate_import(cursor.getString(5));
                o.setDate_edit(cursor.getString(6));
                o.setDate_export(cursor.getString(7));
                o.setCount(cursor.getString(8));
                list.add(o);
            } while (cursor.moveToNext());
        }

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return list;
    }

    /**
     * V2 - Get all traits in the system, in order, as TraitObjects
     */
    public ArrayList<TraitObject> getAllTraitObjects() {

        ArrayList<TraitObject> list = new ArrayList<>();

        Cursor cursor = db.query(TRAITS, new String[]{"id", "trait", "format", "defaultValue",
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

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return list;
    }

    /**
     * Returns all traits regardless of visibility, but as a hashmap
     */
    public HashMap getTraitVisibility() {
        HashMap data = new HashMap();

        Cursor cursor = db.query(TRAITS, new String[]{"id", "trait",
                "isVisible", "realPosition"}, null, null, null, null, "realPosition");

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
    public TraitObject getDetail(String trait) {
        TraitObject data = new TraitObject();

        data.trait = "";
        data.format = "";
        data.defaultValue = "";
        data.minimum = "";
        data.maximum = "";
        data.details = "";
        data.categories = "";

        Cursor cursor = db.query(TRAITS, new String[]{"trait", "format", "defaultValue", "minimum",
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

        if (!cursor.isClosed()) {
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

        Cursor cursor = db.query(USER_TRAITS, new String[]{"parent", "trait",
                        "userValue", "rid"}, "rid like ?", new String[]{plotId},
                null, null, null
        );

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

    /**
     * Check if a trait exists within the database
     * v1.6 - Amended to consider both trait and format
     */
    public boolean getTraitExists(int id, String parent, String trait) {
        boolean haveData = false;

        Cursor cursor = db
                .rawQuery(
                        "select range.id, user_traits.userValue from user_traits, range where " +
                                "user_traits.rid = range." + TICK +ep.getString("ImportUniqueName", "") +TICK +
                                " and range.id = ? and user_traits.parent like ? and user_traits.trait like ?",
                        new String[]{String.valueOf(id), parent, trait}
                );

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
     * Returns the primary key for all ranges
     */
    public int[] getAllRangeID() {
        //TODO check for range table, if not exist create
        Cursor cursor = db.query(RANGE, new String[]{"id"}, null, null,
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

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * V2 - Execute a custom sql query, returning the result as SearchData objects
     * Used for user search function
     */
    public SearchData[] getRangeBySql(String sql) {

        try {
            Cursor cursor = db.rawQuery(sql, null);

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

            if (!cursor.isClosed()) {
                cursor.close();
            }

            return data;
        } catch (Exception n) {
            return null;
        }
    }

    /**
     * Returns range data based on the primary key for range Used when moving
     * between ranges on screen
     * //TODO add catch here for sqlite error
     */
    public RangeObject getRange(int id) {
        RangeObject data = new RangeObject();
        Cursor cursor;

        data.plot = "";
        data.plot_id = "";
        data.range = "";

        try {
            cursor = db.query(RANGE, new String[]{TICK + ep.getString("ImportFirstName", "") + TICK,
                            TICK + ep.getString("ImportSecondName", "") + TICK,
                            TICK + ep.getString("ImportUniqueName", "") + TICK, "id"}, "id = ?",
                    new String[]{String.valueOf(id)}, null, null, null
            );

            if (cursor.moveToFirst()) {
                //data.entry = cursor.getString(0);
                data.range = cursor.getString(0);
                data.plot = cursor.getString(1);
                data.plot_id = cursor.getString(2);

            }

            if (!cursor.isClosed()) {
                cursor.close();
            }
        } catch(SQLiteException e) {
            switchField(-1);
            return null;
        }

        return data;
    }

    /**
     * Returns the range for items that match the specified id
     */
    public String getRangeFromId(String plot_id) {
        try {
            Cursor cursor = db.query(RANGE, new String[]{TICK +ep.getString("ImportFirstName", "")+TICK},
                    TICK +ep.getString("ImportUniqueName", "")+TICK + " like ? ", new String[]{plot_id},
                    null, null, null);

            String myList = null;

            if (cursor.moveToFirst()) {
                myList = cursor.getString(0);
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
     * v2.5
     */
    public void deleteTraitByValue(String rid, String parent, String value) {

        try {
            db.delete(USER_TRAITS, "rid like ? and parent like ? and userValue = ?",
                    new String[]{rid, parent, value});
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * Returns list of files associated with a specific plot
     */

    public ArrayList<String> getPlotPhotos(String plot, String trait) {
        try {
            //Cursor cursor = db.query(USER_TRAITS, new String[]{"userValue"}, "rid like ? and trait like ?", new String[]{plot, trait},
             //       null, null, null);

            Cursor cursor = db.rawQuery(
                            "select userValue FROM user_traits where user_traits.rid = ? and user_traits.parent like ?",
                            new String[]{plot, trait}
                    );

            ArrayList<String> photoList = new ArrayList<>();
            Log.d("Field", Integer.toString(cursor.getCount()));

            if (cursor.moveToFirst()) {
                do {
                    photoList.add(cursor.getString(0));
                }
                while (cursor.moveToNext());
            }

            if (!cursor.isClosed()) {
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

        try {
            Cursor cursor = db.query(RANGE, new String[]{TICK +trait+TICK},
                    TICK +ep.getString("ImportUniqueName", "")+TICK + " like ? ", new String[]{plotId},
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
     * Returns the column names for the range table
     */
    public String[] getRangeColumnNames() {
        if(db==null || !db.isOpen()) db =  openHelper.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * from " + RANGE + " limit 1", null);
        //Cursor cursor = db.rawQuery("SELECT * from range limit 1", null);
        String[] data = null;

        if (cursor.moveToFirst()) {
            int i = cursor.getColumnCount() - 1;

            data = new String[i];

            int k = 0;

            for (int j = 0; j < cursor.getColumnCount(); j++) {

                if (!cursor.getColumnName(j).equals("id")) {
                    data[k] = cursor.getColumnName(j).replace("//", "/");
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
     * Returns the plot for items that match the specified id
     */
    public String getPlotFromId(String plot_id) {
        try {
            Cursor cursor = db.query(RANGE, new String[]{TICK +ep.getString("ImportSecondName", "")+TICK},
                    TICK +ep.getString("ImportUniqueName", "")+ TICK + " like ?", new String[]{plot_id},
                    null, null, null);

            String myList = null;

            if (cursor.moveToFirst()) {
                myList = cursor.getString(0);
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
    public void deleteTrait(String rid, String parent) {
        try {
            db.delete(USER_TRAITS, "rid like ? and parent like ?",
                    new String[]{rid, parent});
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * Helper function
     * v2 - Delete trait
     */
    public void deleteTrait(String id) {
        try {
            db.delete(TRAITS, "id = ?",
                    new String[]{id});
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * Helper function to delete all data in the table
     */
    public void deleteTable(String table) {
        try {
            db.delete(table, null, null);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
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
                sql += TICK +data[i] +TICK + " TEXT)";
            } else {
                sql += TICK +data[i] +TICK + " TEXT,";
            }
        }

        db.execSQL(sql);
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
     * Returns the column names for the range table
     */
    public String[] getRangeColumns() {
        Cursor cursor = db.rawQuery("SELECT * from range limit 1", null);

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

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return data;
    }

    /**
     * Inserting traits data. The last field isVisible determines if the trait
     * is visible when using the app
     */
    public long insertTraits(TraitObject t) {
            /*String trait, String format, String defaultValue,
                             String minimum, String maximum, String details, String categories,
                             String isVisible, String realPosition) {*/

        if (hasTrait(t.trait)) {
            return -1;
        }

        try {
            this.insertTraits.bindString(1, t.trait);
            this.insertTraits.bindString(2, t.format);
            this.insertTraits.bindString(3, t.defaultValue);
            this.insertTraits.bindString(4, t.minimum);
            this.insertTraits.bindString(5, t.maximum);
            this.insertTraits.bindString(6, t.details);
            this.insertTraits.bindString(7, t.categories);
            this.insertTraits.bindString(8, String.valueOf(t.visible));
            //Probably wrong with this one, because the type of realPosition is int
            this.insertTraits.bindString(9, t.realPosition);

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
            Log.e(TAG, e.getMessage());
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
    public boolean hasTrait(String name) {
        boolean exist;

        Cursor cursor = db.rawQuery("select id from traits where " +
                "trait = ? COLLATE NOCASE", new String[]{name});

        exist = cursor.moveToFirst();

        if (!cursor.isClosed()) {
            cursor.close();
        }

        return exist;
    }

    /**
     * V2 - Check if a string has any special characters
     */
    public static boolean hasSpecialChars(String s) {
        final Pattern p = Pattern.compile("[()<>/;\\*%$]");
        final Matcher m = p.matcher(s);

        return m.find();
    }

    /**
     * When the version number changes, this class will recreate the entire
     * database
     * v1.6 - Amended to add new parent field. It is called parent in consideration to the enhanced search
     */
    private static class OpenHelper extends SQLiteOpenHelper {
        SharedPreferences ep2;

        OpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            ep2 = context.getSharedPreferences("Settings", 0);
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
                    + "(id INTEGER PRIMARY KEY, rid TEXT, parent TEXT, trait TEXT, userValue TEXT, timeTaken TEXT, person TEXT, location TEXT, rep TEXT, notes TEXT, exp_id TEXT)");
            db.execSQL("CREATE TABLE "
                    + PLOTS
                    + "(plot_id INTEGER PRIMARY KEY AUTOINCREMENT, exp_id INTEGER, unique_id VARCHAR, primary_id VARCHAR, secondary_id VARCHAR, coordinates VARCHAR)");
            db.execSQL("CREATE TABLE "
                    + PLOT_ATTRIBUTES
                    + "(attribute_id INTEGER PRIMARY KEY AUTOINCREMENT, attribute_name VARCHAR, exp_id INTEGER)");
            db.execSQL("CREATE TABLE "
                    + PLOT_VALUES
                    + "(attribute_value_id INTEGER PRIMARY KEY AUTOINCREMENT, attribute_id INTEGER, attribute_value VARCHAR, plot_id INTEGER, exp_id INTEGER)");
            db.execSQL("CREATE TABLE "
                    + EXP_INDEX
                    + "(exp_id INTEGER PRIMARY KEY AUTOINCREMENT, exp_name VARCHAR, exp_alias VARCHAR, unique_id VARCHAR, primary_id VARCHAR, secondary_id VARCHAR, exp_layout VARCHAR, exp_species VARCHAR, exp_sort VARCHAR, date_import VARCHAR, date_edit VARCHAR, date_export VARCHAR, count INTEGER)");

            //Do not know why the unique constraint does not work
            //db.execSQL("CREATE UNIQUE INDEX expname ON " + EXP_INDEX +"(exp_name);");

            try {
                db.execSQL("CREATE TABLE android_metadata (locale TEXT)");
                db.execSQL("INSERT INTO android_metadata(locale) VALUES('en_US')");
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w("Field Book", "Upgrading database.");

            if (oldVersion < 5) {
                db.execSQL("DROP TABLE IF EXISTS " + RANGE);
                db.execSQL("DROP TABLE IF EXISTS " + TRAITS);
                db.execSQL("DROP TABLE IF EXISTS " + USER_TRAITS);
            }

            if (oldVersion <= 5 & newVersion >= 5) {
                // add columns to tables
                db.execSQL("ALTER TABLE user_traits ADD COLUMN person TEXT");
                db.execSQL("ALTER TABLE user_traits ADD COLUMN location TEXT");
                db.execSQL("ALTER TABLE user_traits ADD COLUMN rep TEXT");
                db.execSQL("ALTER TABLE user_traits ADD COLUMN notes TEXT");
                db.execSQL("ALTER TABLE user_traits ADD COLUMN exp_id TEXT");
            }

            if (oldVersion <= 6 & newVersion >= 6) {
                // create new tables: plots, plotAttributes, plotAttributeValues, exp_index
                db.execSQL("CREATE TABLE "
                        + PLOTS
                        + "(plot_id INTEGER PRIMARY KEY AUTOINCREMENT, exp_id INTEGER, unique_id VARCHAR, primary_id VARCHAR, secondary_id VARCHAR, coordinates VARCHAR)");

                db.execSQL("CREATE TABLE "
                        + PLOT_ATTRIBUTES
                        + "(attribute_id INTEGER PRIMARY KEY AUTOINCREMENT, attribute_name VARCHAR, exp_id INTEGER)");
                db.execSQL("CREATE TABLE "
                        + PLOT_VALUES
                        + "(attribute_value_id INTEGER PRIMARY KEY AUTOINCREMENT, attribute_id INTEGER, attribute_value VARCHAR, plot_id INTEGER, exp_id INTEGER)");

                db.execSQL("CREATE TABLE "
                        + EXP_INDEX
                        + "(exp_id INTEGER PRIMARY KEY AUTOINCREMENT, exp_name VARCHAR, exp_alias VARCHAR, unique_id VARCHAR, primary_id VARCHAR, secondary_id VARCHAR, exp_layout VARCHAR, exp_species VARCHAR, exp_sort VARCHAR, date_import VARCHAR, date_edit VARCHAR, date_export VARCHAR, count INTEGER)");

                // add current range info to exp_index
                db.execSQL("insert into " + EXP_INDEX + "(exp_name, exp_alias, unique_id, primary_id, secondary_id) values (?,?,?,?,?)",
                        new String[]{ep2.getString("FieldFile", ""), ep2.getString("FieldFile", ""), ep2.getString("ImportUniqueName", ""), ep2.getString("ImportFirstName", ""), ep2.getString("ImportSecondName", "")});

                // convert current range table to plots
                Cursor cursor = db.rawQuery("SELECT * from range", null);

                // columns into attributes
                String[] columnNames = cursor.getColumnNames();
                List<String> list = new ArrayList<>(Arrays.asList(columnNames));
                list.remove("id");
                columnNames = list.toArray(new String[0]);

                for (String columnName1 : columnNames) {
                    ContentValues insertValues = new ContentValues();
                    insertValues.put("attribute_name", columnName1);
                    insertValues.put("exp_id", 1);
                    db.insert(PLOT_ATTRIBUTES, null, insertValues);
                }

                // plots into plots
                String cur2 = "SELECT " + TICK + ep2.getString("ImportUniqueName", "") + TICK + ", " + TICK + ep2.getString("ImportFirstName", "") + TICK + ", " + TICK + ep2.getString("ImportSecondName", "") + TICK + " from range";
                Cursor cursor2 = db.rawQuery(cur2, null);

                if (cursor2.moveToFirst()) {
                    do {
                        ContentValues insertValues = new ContentValues();
                        insertValues.put("unique_id", cursor2.getString(0));
                        insertValues.put("primary_id", cursor2.getString(1));
                        insertValues.put("secondary_id", cursor2.getString(2));
                        insertValues.put("exp_id", 1);
                        db.insert(PLOTS, null, insertValues);
                    } while (cursor2.moveToNext());
                }

                // plot values into plot values
                for (String columnName : columnNames) {
                    String att_id = "select plot_attributes.attribute_id from plot_attributes where plot_attributes.attribute_name = " + "'" + columnName + "'" + " and plot_attributes.exp_id = ";
                    Cursor attribute_id = db.rawQuery(att_id + 1, null);
                    Integer attId = 0;

                    if (attribute_id.moveToFirst()) {
                        attId = attribute_id.getInt(0);
                    }

                    String att_val = "select range." + "'" + columnName + "'" + ", plots.plot_id from range inner join plots on range." + "'" + ep2.getString("ImportUniqueName", "") + "'" + "=plots.unique_id";
                    Cursor attribute_val = db.rawQuery(att_val, null);

                    if (attribute_val.moveToFirst()) {
                        do {
                            ContentValues insertValues = new ContentValues();
                            insertValues.put("attribute_id", attId);
                            insertValues.put("attribute_value", attribute_val.getString(0));
                            insertValues.put("plot_id", attribute_val.getInt(1));
                            insertValues.put("exp_id", 1);
                            db.insert(PLOT_VALUES, null, insertValues);
                        } while (attribute_val.moveToNext());
                    }
                }
            }
        }
    }

    public boolean checkUnique(HashMap<String, String> values) {
        Cursor cursor = db.rawQuery("SELECT unique_id from " + PLOTS, null);

        if (cursor.moveToFirst()) {
            do {
                if(values.containsKey(cursor.getString(0))) {
                    return false;
                }
            } while (cursor.moveToNext());
        }

        cursor.close();

        return true;
    }

    public void updateExpTable(Boolean imp, Boolean ed, Boolean ex, int exp_id) {
        ConfigActivity.dt.open();
        Cursor cursor = db.rawQuery("SELECT * from " + EXP_INDEX, null);
        cursor.moveToFirst();

        if(imp) {
            // get import date and count of plots
            Cursor cursor2 = db.rawQuery("SELECT * from plots where exp_id = " + exp_id, null);
            int count = cursor2.getCount();

            ContentValues cv = new ContentValues();
            cv.put("count",count);
            cv.put("date_import",timeStamp.format(Calendar.getInstance().getTime()));
            db.update(EXP_INDEX, cv, "exp_id="+exp_id, null);
        }

        if(ed) {
            // get and save edit date
            for (int i = 0; i < cursor.getCount(); i++) {
                int experimental_id = cursor.getInt(0);
                String expIdString = Integer.toString(experimental_id);
                Cursor cursor3 = db.rawQuery("SELECT timeTaken from user_traits WHERE user_traits.exp_id = " + expIdString + " ORDER BY datetime(substr(timeTaken,1,19)) DESC", null);

                if(cursor3.moveToFirst()) {
                    String date_edited = cursor3.getString(0);
                    ContentValues cv = new ContentValues();
                    cv.put("date_edit",date_edited);
                    db.update(EXP_INDEX, cv, "exp_id="+experimental_id, null);
                    Log.d("date_edit",date_edited);
                }
                Log.d("date_edit2",Integer.toString(cursor3.getCount()));

                cursor3.close();
                cursor.moveToNext();
            }
        }

        if(ex) {
            // get export date
            ContentValues cv = new ContentValues();
            cv.put("date_export",timeStamp.format(Calendar.getInstance().getTime()));
            db.update(EXP_INDEX, cv, "exp_id="+exp_id, null);
        }

        cursor.close();
    }

    public void deleteField(int exp_id) {
        db.execSQL("DELETE FROM " + EXP_INDEX + " WHERE exp_id = " + exp_id);
        db.execSQL("DELETE FROM " + PLOTS + " WHERE exp_id = " + exp_id);
        db.execSQL("DELETE FROM " + PLOT_ATTRIBUTES + " WHERE exp_id = " + exp_id);
        db.execSQL("DELETE FROM " + PLOT_VALUES + " WHERE exp_id = " + exp_id);
        db.execSQL("DELETE FROM " + USER_TRAITS + " WHERE exp_id = " + exp_id);
    }

    public void switchField(int exp_id) {
        Cursor cursor;

        // get array of plot attributes
        if(exp_id == -1) {
            cursor = db.rawQuery("SELECT plot_attributes.attribute_name FROM plot_attributes limit 1", null);
            cursor.moveToFirst();
        } else {
            cursor = db.rawQuery("SELECT plot_attributes.attribute_name FROM plot_attributes WHERE plot_attributes.exp_id = " + exp_id, null);
            cursor.moveToFirst();
        }

        String[] plotAttr = new String[cursor.getCount()];

        for (int i = 0; i < cursor.getCount(); i++) {
            plotAttr[i] = cursor.getString(0);
            cursor.moveToNext();
        }

        cursor.close();

        // create query to get data for range
        String args = "";

        for (String aPlotAttr : plotAttr) {
            args = args + ", MAX(CASE WHEN plot_attributes.attribute_name = '" + aPlotAttr + "' THEN plot_values.attribute_value ELSE NULL END) AS \"" + aPlotAttr + "\"";
        }

        String query = "CREATE TABLE "+ RANGE +" AS SELECT plots.plot_id as id" + args +
                " FROM plots " +
                "LEFT JOIN plot_values USING (plot_id) " +
                "LEFT JOIN plot_attributes USING (attribute_id) " +
                "WHERE plots.exp_id = '" + exp_id +
                "' GROUP BY plots.plot_id";

        // drop range table and import new query into range table
        dropRange();
        db.execSQL(query);

        //String index = "CREATE INDEX range_unique_index ON " + RANGE + "(" + ep.getString("ImportUniqueName",null) + ")";
        //db.execSQL(index);
    }

    public int checkFieldName(String name) {
        Cursor c = db.rawQuery("SELECT exp_id FROM " + EXP_INDEX + " WHERE exp_name=?", new String[] {name});

        if (c.moveToFirst()) {
            return c.getInt(0);
        }

        return -1;
    }

    public int createField(FieldObject e,  List<String> columns) {
        // String exp_name, String exp_alias, String unique_id, String primary_id, String secondary_id, String[] columns){

        long exp_id = checkFieldName(e.getExp_name());
        if (exp_id != -1) {
            return (int)exp_id;
        }

        // add to exp_index
        ContentValues insertExp = new ContentValues();
        insertExp.put("exp_name", e.getExp_name());
        insertExp.put("exp_alias", e.getExp_alias());
        insertExp.put("unique_id", e.getUnique_id());
        insertExp.put("primary_id", e.getPrimary_id());
        insertExp.put("secondary_id", e.getSecondary_id());
        insertExp.put("exp_layout", e.getExp_layout());
        insertExp.put("exp_species", e.getExp_species());
        insertExp.put("exp_sort", e.getExp_sort());
        insertExp.put("count", e.getCount());
        insertExp.put("date_import", timeStamp.format(Calendar.getInstance().getTime()));

        exp_id = db.insert(EXP_INDEX, null, insertExp);

        /* columns to plot_attributes
        String[] columnNames = columns;
        List<String> list = new ArrayList<>(Arrays.asList(columnNames));
        list.remove("id");
        columnNames = list.toArray(new String[0]);*/

        for (String columnName : columns) {
            ContentValues insertAttr = new ContentValues();
            insertAttr.put("attribute_name", columnName);
            insertAttr.put("exp_id", (int) exp_id);
            db.insert(PLOT_ATTRIBUTES, null, insertAttr);
        }

        return (int) exp_id;
    }

    public void createFieldData(int exp_id, List<String> columns, List<String> data) {
        // get unique_id, primary_id, secondary_id names from exp_id
        Cursor cursor = db.rawQuery("SELECT exp_id.unique_id, exp_id.primary_id, exp_id.secondary_id from exp_id where exp_id.exp_id = " + exp_id, null);
        cursor.moveToFirst();

        // extract unique_id, primary_id, secondary_id indices
        int[] plotIndices = new int[3];
        //plotIndices[0] = Arrays.asList(columns).indexOf(cursor.getString(0));
        //plotIndices[1] = Arrays.asList(columns).indexOf(cursor.getString(1));
        //plotIndices[2] = Arrays.asList(columns).indexOf(cursor.getString(2));

        plotIndices[0] = columns.indexOf(cursor.getString(0));
        plotIndices[1] = columns.indexOf(cursor.getString(1));
        plotIndices[2] = columns.indexOf(cursor.getString(2));

        // add plot to plots table
        ContentValues insertValues = new ContentValues();
        insertValues.put("exp_id", exp_id);
        insertValues.put("unique_id", data.get(plotIndices[0]));    //data[plotIndices[0]]);
        insertValues.put("primary_id", data.get(plotIndices[1]));   //data[plotIndices[1]]);
        insertValues.put("secondary_id", data.get(plotIndices[2])); //data[plotIndices[2]]);
        long plot_id = db.insert(PLOTS, null, insertValues);

        // add plot data plot_values table
        for (int i = 0; i < columns.size(); i++) {
            Cursor attribute_id = db.rawQuery("select plot_attributes.attribute_id from plot_attributes where plot_attributes.attribute_name = " + "'" + columns.get(i) + "'" + " and plot_attributes.exp_id = " + exp_id, null);
            Integer attId = 0;

            if(attribute_id.moveToFirst()) {
                attId = attribute_id.getInt(0);
            }

            ContentValues plotValuesInsert = new ContentValues();
            plotValuesInsert.put("attribute_id",attId);
            plotValuesInsert.put("attribute_value", data.get(i));
            plotValuesInsert.put("plot_id", (int) plot_id);
            plotValuesInsert.put("exp_id", exp_id);
            db.insert(PLOT_VALUES, null, plotValuesInsert);

            attribute_id.close();
        }

        cursor.close();
    }

    /**
     * Delete all tables
     */

    void deleteDatabase() {
        context.deleteDatabase(DATABASE_NAME);
    }

    /**
     * Import database
     */

    void importDatabase(String filename) throws IOException {
        String internalDbPath = getDatabasePath(this.context);
        String internalSpPath = "/data/data/com.fieldbook.tracker/shared_prefs/Settings.xml";

        close();

        Log.w("File to copy", Constants.BACKUPPATH + "/" + filename);

        File newDb = new File(Constants.BACKUPPATH + "/" + filename);
        File oldDb = new File(internalDbPath);

        File newSp = new File(Constants.BACKUPPATH + "/" + filename + "_sharedpref.xml");
        File oldSp = new File(internalSpPath);

        try {
            copyFile(newDb, oldDb);
            copyFile(newSp, oldSp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Export database
     */
    void exportDatabase(String filename) throws IOException {
        String internalDbPath = getDatabasePath(this.context);
        String internalSpPath = "/data/data/com.fieldbook.tracker/shared_prefs/Settings.xml";
        close();

        try {
            File newDb = new File(Constants.BACKUPPATH + "/" + filename + ".db");
            File oldDb = new File(internalDbPath);

            File newSp = new File(Constants.BACKUPPATH + "/" + filename + ".db_sharedpref.xml");
            File oldSp = new File(internalSpPath);

            copyFile(oldDb, newDb);
            copyFile(oldSp, newSp);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * Copy old file to new file
     */
    private void copyFile(File oldFile, File newFile) throws IOException {
        if (oldFile.exists()) {
            try {
                copyFileCall(new FileInputStream(oldFile), new FileOutputStream(newFile));
                openHelper = new OpenHelper(this.context);
                open();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * V2 - Helper function to copy multiple files from asset to SDCard
     */
    public void copyFileOrDir(String fullPath, String path) {
        AssetManager assetManager = context.getAssets();
        String assets[];

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
    private void copyFile(String fullPath, String filename) {
        AssetManager assetManager = context.getAssets();

        InputStream in;
        OutputStream out;

        try {
            in = assetManager.open(filename);
            out = new FileOutputStream(fullPath + "/" + filename);

            byte[] buffer = new byte[1024];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            in.close();
            out.flush();
            out.close();
        } catch (Exception e) {
            Log.e("Sample Data", e.getMessage());
        }

    }

    /**
     * Helper function to convert array to csv format
     */
    private static String convertToCommaDelimited(String[] list) {
        StringBuilder ret = new StringBuilder("");
        for (int i = 0; list != null && i < list.length; i++) {
            ret.append(list[i]);
            if (i < list.length - 1) {
                ret.append(',');
            }
        }
        return ret.toString();
    }

    /**
     * Helper function to copy database
     */
    private static void copyFileCall(FileInputStream fromFile, FileOutputStream toFile) throws IOException {
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

    public boolean isTableExists(String tableName) {

        Cursor cursor = db.rawQuery("select DISTINCT tbl_name from sqlite_master where tbl_name = '" + tableName + "'", null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.close();
                return true;
            }
            cursor.close();
        }
        return false;
    }

    public boolean isTableEmpty(String tableName) {
        boolean empty = true;

        if(!isTableExists(tableName)) {
            return empty;
        }

        Cursor cur = db.rawQuery("SELECT COUNT(*) FROM " + tableName, null);
        if (cur != null) {
            if (cur != null && cur.moveToFirst()) {
                empty = (cur.getInt(0) == 0);
            }
            cur.close();
        }
        return empty;
    }
}