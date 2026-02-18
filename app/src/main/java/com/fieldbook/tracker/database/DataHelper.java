package com.fieldbook.tracker.database;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.model.FieldBookImage;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.database.dao.GroupDao;
import com.fieldbook.tracker.database.dao.ObservationDao;
import com.fieldbook.tracker.database.dao.ObservationUnitAttributeDao;
import com.fieldbook.tracker.database.dao.ObservationUnitDao;
import com.fieldbook.tracker.database.dao.ObservationUnitPropertyDao;
import com.fieldbook.tracker.database.dao.ObservationVariableDao;
import com.fieldbook.tracker.database.dao.spectral.DeviceDao;
import com.fieldbook.tracker.database.dao.spectral.ProtocolDao;
import com.fieldbook.tracker.database.dao.spectral.SpectralDao;
import com.fieldbook.tracker.database.dao.StudyDao;
import com.fieldbook.tracker.database.dao.spectral.UriDao;
import com.fieldbook.tracker.database.migrators.ObservationMediaMigratorVersion21;
import com.fieldbook.tracker.database.views.ObservationVariableAttributeDetailViewCreator;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.database.models.ObservationUnitModel;
import com.fieldbook.tracker.database.models.ObservationVariableModel;
import com.fieldbook.tracker.database.models.GroupModel;
import com.fieldbook.tracker.database.models.StudyModel;
import com.fieldbook.tracker.database.repository.SpectralRepository;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.objects.SearchData;
import com.fieldbook.tracker.objects.SearchDialogDataModel;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.GeoJsonUtil;
import com.fieldbook.tracker.utilities.ZipUtil;
import com.fieldbook.tracker.utilities.export.SpectralFileProcessor;
import com.fieldbook.tracker.utilities.export.ValueProcessorFormatAdapter;

import org.phenoapps.utils.BaseDocumentTreeUtil;
import org.threeten.bp.OffsetDateTime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * All database related functions are here
 */
public class DataHelper {

    public static final int DATABASE_VERSION = ObservationMediaMigratorVersion21.VERSION;
    private static final String DATABASE_NAME = "fieldbook.db";
    public static SQLiteDatabase db;
    private static final String TAG = "Field Book";
    private static final String TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss.SSSZZZZZ";
    private Context context;
    private SimpleDateFormat timeStamp;

    private OpenHelper openHelper;

    private SharedPreferences preferences;

    private Bitmap missingPhoto;

    private final SpectralDao spectralDao = new SpectralDao(this);
    private final ProtocolDao protocolDao = new ProtocolDao(this);
    private final UriDao uriDao = new UriDao(this);
    private final DeviceDao deviceDao = new DeviceDao(this);
    private final SpectralRepository proto = new SpectralRepository(spectralDao, protocolDao, deviceDao, uriDao);
    private final SpectralFileProcessor spectralFileProcessor = new SpectralFileProcessor(this, proto);

    private SearchQueryBuilder queryBuilder;

    private final GroupDao studyGroupDao = new GroupDao(GroupsTable.Type.STUDY);

    @Inject
    public DataHelper(@ApplicationContext Context context) {
        try {
            this.context = context;

            preferences = PreferenceManager.getDefaultSharedPreferences(context);

            queryBuilder = new SearchQueryBuilder(preferences, this);

            openHelper = new OpenHelper(this);
            db = openHelper.getWritableDatabase();

            timeStamp = new SimpleDateFormat(TIME_FORMAT_PATTERN,
                    Locale.getDefault());

            missingPhoto = BitmapFactory.decodeResource(context.getResources(), R.drawable.trait_photo_missing);

        } catch (Exception e) {
            e.printStackTrace();
            Log.w("FieldBook", "Unable to create or open database");
        }
    }

    public SQLiteDatabase getDb() {
        open();
        return openHelper.getWritableDatabase();
    }

    /**
     * V9 special character delete function.
     * TODO: If we want to accept headers with special characters we need to rethink the dynamic range table.
     * @param s, the column to sanitize
     * @return output, a new string without special characters
     */
    public static String replaceSpecialChars(String s) {

        final Pattern p = Pattern.compile("[\\[\\]`\"']");

        int lastIndex = 0;

        StringBuilder output = new StringBuilder();

        Matcher matcher = p.matcher(s);

        while (matcher.find()) {

            output.append(s, lastIndex, matcher.start());

            lastIndex = matcher.end();

        }

        if (lastIndex < s.length()) {

            output.append(s, lastIndex, s.length());

        }

        return output.toString();
    }

    /**
     * Used to sanitize traits in the selection clause of raw queries.
     * Android SDK (SQLiteDatabase classes specifically) doesn't allow sanitization of columns in select clauses.
     * Because FB accepts any trait/observation unit property name and eventually pivots these into columns names in
     * DataHelper.switchField this function is necessary to manually sanitize.
     * @param s string to sanitize
     * @return string with escaped apostrophes
     */
    public static String replaceIdentifiers(String s) {

        return s.replaceAll("'", "''").replaceAll("\"", "\"\"");
    }

    /**
     * V2 - Check if a string has any special characters
     */
    public static boolean hasSpecialChars(String s) {

        final Pattern p = Pattern.compile("[\\[\\]`\"']");

        final Matcher m = p.matcher(s);

        return m.find();
    }

    /**
     * Issue 753, lat/lngs are saved in the incorrect order and need to be swapped
     */
    public void fixGeoCoordinates(SQLiteDatabase db) {

        db.beginTransaction();

        try {

            GeoJsonUtil.Companion.fixGeoCoordinates(this, db);

            db.setTransactionSuccessful();

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            db.endTransaction();
        }
    }

    /**
     * Populates import format based on study_source values
     */
    public void populateImportFormat(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            String updateImportFormatSQL =
                    "UPDATE studies " +
                            "SET import_format = CASE " +
                            "WHEN study_source IS NULL OR study_source = 'csv' OR study_source LIKE '%.csv' THEN 'csv' " +
                            "WHEN study_source = 'excel' OR study_source LIKE '%.xls' THEN 'xls'" +
                            "WHEN study_source LIKE '%.xlsx' THEN 'xlsx'" +
                            "ELSE 'brapi' " +
                            "END";

            db.execSQL(updateImportFormatSQL);

            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }


    /**
     * Fixes issue where BrAPI study_db_ids are saved in study_alias
     */
    public void fixStudyAliases(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            // Update `study_db_id` and `study_alias` for studies imported via 'brapi'
            String updateAliasesSQL =
                    "UPDATE studies " +
                            "SET study_db_id = study_alias, " +
                            "study_alias = study_name " +
                            "WHERE import_format = 'brapi'";

            db.execSQL(updateAliasesSQL);

            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }


    /**
     * Helper function to change visibility of a trait. Used in the ratings
     * screen
     */
    public void updateTraitVisibility(String traitDbId, boolean val) {

        open();

        ObservationVariableDao.Companion.updateTraitVisibility(traitDbId, String.valueOf(val));
    }

    public void updateObservationUnit(ObservationUnitModel model, String geoCoordinates) {

        open();

        ObservationUnitDao.Companion.updateObservationUnit(model, geoCoordinates);

    }

    public ObservationUnitModel[] getAllObservationUnits() {

        open();

        return ObservationUnitDao.Companion.getAll();
    }

    public ObservationUnitModel[] getAllObservationUnits(SQLiteDatabase db) {

        return ObservationUnitDao.Companion.getAll(db);
    }

    public ObservationUnitModel[] getAllObservationUnits(int studyId) {

        open();

        return ObservationUnitDao.Companion.getAll(studyId);
    }

    @Nullable
    public StudyModel getStudyById(String id) {

        open();

        return StudyDao.Companion.getById(id);
    }

    @Nullable
    public StudyModel getStudyByDbId(String id) {

        open();

        return StudyDao.Companion.getStudyByDbId(id);
    }

    @Nullable
    public ObservationModel getObservationById(String id) {

        open();

        return ObservationDao.Companion.getById(id);
    }

    @Nullable
    public ObservationUnitModel getObservationUnitById(String id) {

        open();

        return ObservationUnitDao.Companion.getById(id);
    }

    @Nullable
    public ObservationVariableModel getObservationVariableById(String id) {

        open();

        return ObservationVariableDao.Companion.getById(id);
    }

    public String getSearchQuery(List<SearchDialogDataModel> dataSet) {
        return queryBuilder.buildSearchQuery(dataSet);
    }

    /**
     * Helper function to insert user data. For example, the data entered for
     * numeric format, or date for date format The timestamp is updated within
     * this function as well
     * v1.6 - Amended to consider both trait and user data
     */
    public long insertObservation(String plotId, String traitDbId, String value, String person, String location, String notes, String studyId, String observationDbId, OffsetDateTime timestamp, OffsetDateTime lastSyncedTime, String rep) {

        open();

        return ObservationDao.Companion.insertObservation(plotId, traitDbId, value, person, location, notes, studyId, observationDbId, timestamp, lastSyncedTime, rep, null, null, null);
    }

    // New overload to accept optional media URIs. Kept for Java callers that may supply media URIs.
    public long insertObservation(String plotId, String traitDbId, String value, String person, String location, String notes, String studyId, @Nullable String observationDbId, @Nullable OffsetDateTime timestamp, @Nullable OffsetDateTime lastSyncedTime, @Nullable String rep, @Nullable String audioUri, @Nullable String videoUri, @Nullable String photoUri) {
        open();
        return ObservationDao.Companion.insertObservation(plotId, traitDbId, value, person, location, notes, studyId, observationDbId, timestamp, lastSyncedTime, rep, audioUri, videoUri, photoUri);
    }

    /**
     * Get rep of current plot/trait combination
     */
    public int getRep(String studyId, String plot, String traitDbId) {

        open();

        return ObservationDao.Companion.getRep(studyId, plot, traitDbId) + 1;
    }

    @NonNull
    public String getNextRep(String studyId, String unit, String traitDbId) {

        open();

        return String.valueOf(ObservationDao.Companion.getNextRepeatedValue(studyId, unit, traitDbId));

    }

    @NonNull
    public String getDefaultRep(String studyId, String unit, String traitDbId) {

        open();

        return ObservationDao.Companion.getDefaultRepeatedValue(studyId, unit, traitDbId);

    }

    public int getMaxPositionFromTraits() {

        open();

        return ObservationVariableDao.Companion.getMaxPosition();
    }

    public Boolean isBrapiSynced(String studyId, String plotId, String traitDbId, String rep) {

        open();

        return ObservationDao.Companion.isBrapiSynced(studyId, plotId, traitDbId, rep);
    }

    /**
     * Fetch all locally created observations (excluding images) and convert them to BrAPI observations
     */
    public List<Observation> getLocalObservations(int fieldId) {

        open();

        String studyId = Integer.toString(fieldId);

        return ObservationDao.Companion.getLocalObservations(studyId);
    }

    /**
     * Get user created trait observations for currently selected study
     */
    public List<FieldBookImage> getUserTraitImageObservations(Context ctx, int fieldId) {

        open();

        String studyId = Integer.toString(fieldId);

        return ObservationDao.Companion.getUserTraitImageObservations(ctx, studyId, missingPhoto);
    }

    public List<Observation> getWrongSourceObservations(String hostUrl) {

        open();

        return ObservationDao.Companion.getWrongSourceObservations(hostUrl);
    }

    public List<FieldBookImage> getWrongSourceImageObservations(Context ctx, String hostUrl) {

        open();

        return ObservationDao.Companion.getWrongSourceImageObservations(ctx, hostUrl, missingPhoto);
    }

    /**
     * Get the data for brapi export to external system
     */
    public List<Observation> getBrapiObservations(int fieldId, String hostUrl) {

        open();

        return ObservationDao.Companion.getBrapiObservations(fieldId, hostUrl);
    }

    /**
     * Get all BrAPI export data categorized by type and status
     * @param fieldId The field ID to get data for
     * @param hostUrl The BrAPI host URL
     * @return Map containing categorized observations
     */
    public Map<String, List<Observation>> getBrAPIExportData(int fieldId, String hostUrl) {
        return ObservationDao.Companion.getBrAPIExportData(Integer.toString(fieldId), hostUrl);
    }

    /**
     * Convert observations to FieldBookImage objects
     * @param ctx Context for file operations
     * @param observations List of observations to convert
     * @return List of FieldBookImage objects
     */
    public List<FieldBookImage> getImageDetails(Context ctx, List<Observation> observations) {
        return ObservationDao.Companion.getImageDetails(ctx, observations, missingPhoto);
    }

    /**
     * Get the image observations for brapi export to external system
     */
    public List<FieldBookImage> getImageObservations(Context ctx, String hostUrl) {

        open();

        return ObservationDao.Companion.getHostImageObservations(ctx, hostUrl, missingPhoto);
    }

    public void updateObservationUnitModels(SQLiteDatabase db, List<ObservationUnitModel> models) {

        ObservationUnitDao.Companion.updateObservationUnitModels(db, models);
    }

    public void updateObservationModels(SQLiteDatabase db, List<ObservationModel> observations) {

        ObservationDao.Companion.updateObservationModels(db, observations);

    }

    public void updateObservationMediaUris(ObservationModel observation) {

        open();

        ObservationDao.Companion.updateObservationMediaUris(observation);
    }

    /**
     * Sync with observationdbids BrAPI
     */
    public void updateObservationsByBrapiId(List<Observation> observations) {

        open();

        ObservationDao.Companion.updateObservationsByBrapiId(observations);
    }

    public void updateObservationsByFieldBookId(List<Observation> observations) {

        open();

        ObservationDao.Companion.updateObservationsByFieldBookId(observations);
    }

    public List<String> getPossibleUniqueAttributes(int studyId) {
        open();
        return StudyDao.Companion.getPossibleUniqueAttributes(studyId);
    }

    public void updateSearchAttribute(int studyId, String newSearchAttribute) {
        open();
        StudyDao.Companion.updateSearchAttribute(studyId, newSearchAttribute);
    }
    
    public int updateSearchAttributeForAllFields(String newSearchAttribute) {
        open();
        return StudyDao.Companion.updateSearchAttributeForAllFields(newSearchAttribute);
    }

    public ObservationUnitModel[] getObservationUnitsBySearchAttribute(int studyId, String searchValue) {
        open();
        return ObservationUnitDao.Companion.getBySearchAttribute(studyId, searchValue);
    }

    public void updateImage(FieldBookImage image) {

        open();

        ObservationDao.Companion.updateImage(image);
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
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public ValueProcessorFormatAdapter getValueFormatter() {
        return new ValueProcessorFormatAdapter(context, spectralFileProcessor);
    }

    /**
     * Retrieves the columns needed for database export with all imported attributes
     */
    public Cursor getExportDBData(String[] fieldList, ArrayList<TraitObject> traits, int fieldId) {

        open();

        ValueProcessorFormatAdapter processor = getValueFormatter();

        return ObservationUnitPropertyDao.Companion.getExportDbData(
                context, fieldId, fieldList, traits, processor);

    }

    /**
     * Retrieves the columns needed for database export with only unique identifier
     */
    public Cursor getExportDBDataShort(String[] fieldList, String uniqueId, ArrayList<TraitObject> traits, int fieldId) {

        open();

        ValueProcessorFormatAdapter processor = getValueFormatter();

        return ObservationUnitPropertyDao.Companion.getExportDbDataShort(
                context, fieldId, fieldList, uniqueId, traits, processor);

    }

    /**
     * Same as convertDatabaseToTable but filters by obs unit
     */
    public Cursor convertDatabaseToTable(String[] col, ArrayList<TraitObject> traits, String obsUnit) {

        open();

        return ObservationUnitPropertyDao.Companion.convertDatabaseToTable(
                preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, -1),
                preferences.getString(GeneralKeys.UNIQUE_NAME, ""),
                obsUnit,
                col,
                traits.toArray(new TraitObject[]{}));

    }

    /**
     * Convert EAV database to relational
     */
    public Cursor getExportTableDataShort(int fieldId,  String uniqueId, ArrayList<TraitObject> traits) {

        open();

        ValueProcessorFormatAdapter processor = new ValueProcessorFormatAdapter(context, spectralFileProcessor);

        return ObservationUnitPropertyDao.Companion.getExportTableDataShort(context, fieldId, uniqueId, traits, processor);

    }

    public Cursor getExportTableData(int fieldId, ArrayList<TraitObject> traits) {

        open();

        ValueProcessorFormatAdapter processor = new ValueProcessorFormatAdapter(context, spectralFileProcessor);

        return ObservationUnitPropertyDao.Companion.getExportTableData(context, fieldId, traits, processor);

    }

    /**
     * Used by the application to return all traits which are visible
     */
    public ArrayList<TraitObject> getVisibleTraits() {

        open();

        String sortColumn = preferences.getString(GeneralKeys.TRAITS_LIST_SORT_ORDER, "position");

        return ObservationVariableDao.Companion.getAllVisibleTraitObjects(sortColumn);

    }

    /**
     * V2 - Returns all traits column titles as a cursor
     */
    public Cursor getAllTraitsForExport() {

        open();

        return ObservationVariableDao.Companion.getAllTraitsForExport();

    }

    public Cursor getAllTraitObjectsForExport() {

        open();

        return ObservationVariableDao.Companion.getAllTraitObjectsForExport();
    }

    /**
     * V4 - Get all traits in the system, in order, as TraitObjects
     */
    public ArrayList<FieldObject> getAllFieldObjects() {

        open();

        return StudyDao.Companion.getAllFieldObjects(
                preferences.getString(GeneralKeys.FIELDS_LIST_SORT_ORDER, "date_import")
        );
    }

    public FieldObject getFieldObject(Integer studyId) {

        open();

        List<FieldObject.TraitDetail> traitDetails = getTraitDetailsForStudy(studyId);

        return StudyDao.Companion.getFieldObject(
                studyId,
                traitDetails
        );
    }

    public List<FieldObject.TraitDetail> getTraitDetailsForStudy(Integer studyId) {

        open();

        String sortOrder = preferences.getString(GeneralKeys.TRAITS_LIST_SORT_ORDER, "position");

        return StudyDao.Companion.getTraitDetailsForStudy(studyId, sortOrder);
    }

    /**
     * V2 - Get all traits in the system, in order, as TraitObjects
     */
    public ArrayList<TraitObject> getAllTraitObjects() {

        open();

        return ObservationVariableDao.Companion.getAllTraitObjects(
                preferences.getString(GeneralKeys.TRAITS_LIST_SORT_ORDER, "position")
        );
    }

    /**
     * Returns all traits regardless of visibility, but as a hashmap
     */
    public HashMap<String, String> getTraitVisibility() {

        open();

        return ObservationVariableDao.Companion.getTraitVisibility();
    }

    /**
     * Returns saved data based on plot_id
     * v1.6 - Amended to consider both trait and format
     */
    public HashMap<String, String> getUserDetail(String plotId) {

        open();

        String studyId = Integer.toString(preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));

        return ObservationDao.Companion.getUserDetail(studyId, plotId);

    }

    /**
     * Get observation data that needs to be saved on edits
     */
    public Observation getObservation(String studyId, String plotId, String traitDbId, String rep) {

        open();

        return ObservationDao.Companion.getObservation(studyId, plotId, traitDbId, rep);
    }

    public void updateObservationValue(int id, String value) {

        open();

        ObservationDao.Companion.updateObservationValue(id, value);
    }

    /**
     * Check if a trait exists within the database
     * v1.6 - Amended to consider both trait and format
     */
    public boolean getTraitExists(int plotId, String traitDbId) {

        open();

        return ObservationVariableDao.Companion.getTraitExists(preferences.getString(GeneralKeys.UNIQUE_NAME, ""), plotId, traitDbId);
    }

    /**
     * Returns the primary key for all ranges
     */
    public int[] getAllRangeID(int studyId) {

        open();

        if (!isTableExists("ObservationUnitProperty")) {

            ArrayList<FieldObject> fields = StudyDao.Companion.getAllFieldObjects(
                    preferences.getString(GeneralKeys.FIELDS_LIST_SORT_ORDER, "date_import")
            );

            if (!fields.isEmpty()) {

                StudyDao.Companion.switchField(fields.get(0).getStudyId());

            }
        }

        Integer[] result = ObservationUnitPropertyDao.Companion.getAllRangeId(context, studyId);

        int[] data = new int[result.length];

        int count = 0;

        for (Integer i : result) {
            data[count++] = i;
        }

        return data;
    }

    /**
     * V2 - Execute a custom sql query, returning the result as SearchData objects
     * Used for user search function
     */
    public SearchData[] getRangeBySql(String sql) {

        open();

        try {
            Cursor cursor = db.rawQuery(sql, null);

            SearchData[] data = null;

            if (cursor.moveToFirst()) {
                data = new SearchData[cursor.getCount()];

                int count = 0;

                do {

                    SearchData sd = new SearchData();

                    sd.id = cursor.getInt(0);
                    sd.unique = cursor.getString(1);
                    sd.range = cursor.getString(2);
                    sd.plot = cursor.getString(3);

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
    public RangeObject getRange(String first, String second, String unique, int id) {

        open();

        return ObservationUnitPropertyDao.Companion.getRangeFromId(
                first, second, unique,
                id);
    }

    /**
     * Helper function
     * v2.5
     */
    public void deleteTraitByValue(String studyId, String plotId, String traitDbId, String value) {

        open();

        ObservationDao.Companion.deleteTraitByValue(studyId, plotId, traitDbId, value);
    }

    /**
     * Returns the string value for a given obs. unit / column pair in the obs unit property table
     *
     * @param column the column name to select such as "plot_id" or "row"
     * @param plotId the obs unit id to filter by such as 13RPN0001
     * @return the value for the given column/plotId pair
     */
    @NonNull
    public String getObservationUnitPropertyValues(String column, String plotId) {

        open();

        String uniqueName = preferences.getString(GeneralKeys.UNIQUE_NAME, "");

        return ObservationUnitPropertyDao.Companion.getObservationUnitPropertyValues(uniqueName, column, plotId);
    }

    /**
     * Helper function
     * v1.6 - Amended to consider trait
     */
    public void deleteTrait(String studyId, String plotId, String traitDbId, String rep) {

        open();

        ObservationDao.Companion.deleteTrait(studyId, plotId, traitDbId, rep);
    }

    /**
     * Helper function
     * v2 - Delete trait
     */
    public void deleteTrait(String id) {

        open();

        ObservationVariableDao.Companion.deleteTrait(id);
    }

    /**
     * The above deleteTable function is only used to delete Traits table.
     */
    public void deleteTraitsTable() {

        open();

        try {
            ObservationVariableDao.Companion.deleteTraits();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * Inserting traits data. The last field isVisible determines if the trait
     * is visible when using the app
     */
    public long insertTraits(TraitObject t) {

        open();

        return ObservationVariableDao.Companion.insertTraits(t);
    }

    /**
     * V2 - Update the ordering of traits
     */
    public void updateTraitPosition(String id, int realPosition) {

        open();

        ObservationVariableDao.Companion.updateTraitPosition(id, realPosition);
    }

    /**
     * V2 - Edit existing trait
     */
    public long editTraits(String traitDbId, String trait, String traitAlias,String format, String defaultValue,
                           String minimum, String maximum, String details, String categories,
                           Boolean closeKeyboardOnOpen,
                           Boolean saveImage,
                           Boolean cropImage, Boolean useDayOfYear, Boolean categoryDisplayValue, String resourceFile,
                           List<String> synonyms, String decimalPlacesRequired, Boolean mathSymbolsEnabled,
                           Boolean allowMulticat, Boolean repeatMeasure, Boolean autoSwitchPlot, String unit,
                           Boolean invalidValues,
                           Boolean multiMediaPhoto,
                           Boolean multiMediaAudio,
                           Boolean multiMediaVideo) {

        open();

        return ObservationVariableDao.Companion.editTraits(traitDbId, trait, traitAlias, format, defaultValue,
                minimum, maximum, details, categories, closeKeyboardOnOpen, cropImage,
                saveImage, useDayOfYear, categoryDisplayValue, resourceFile, synonyms, decimalPlacesRequired,
                mathSymbolsEnabled, allowMulticat, repeatMeasure, autoSwitchPlot, unit, invalidValues,
                multiMediaPhoto, multiMediaAudio, multiMediaVideo);
    }

    /**
     * Warning: This method should be used cautiously when trait names are mutable.
     * It is currently safe for checking trait name uniqueness.
     * It is also used in SearchDialog, where renaming the trait would invalidate the search query.
     * In general, avoid using this method in features where the trait name might change between invocations.
     * @param name The name of the trait object (e.g., "Height").
     * @return The trait object fetched from the database.
     */
    public TraitObject getTraitByName(String name) {

        open();

        return ObservationVariableDao.Companion.getTraitByName(name);
    }

    public TraitObject getTraitByAlias(String name) {

        open();

        return ObservationVariableDao.Companion.getTraitByAlias(name);
    }

    public TraitObject getTraitById(String id) {

        open();

        return ObservationVariableDao.Companion.getTraitById(id);
    }

    public TraitObject getTraitByExternalDbId(String externalDbId, String traitDataSource) {

        open();

        return ObservationVariableDao.Companion.getTraitByExternalDbId(externalDbId, traitDataSource);
    }

    public long updateTrait(TraitObject trait) {

        open();

        return ObservationVariableDao.Companion.editTraits(trait.getId(), trait.getName(), trait.getAlias(),
                trait.getFormat(), trait.getDefaultValue(), trait.getMinimum(), trait.getMaximum(),
                trait.getDetails(), trait.getCategories(), trait.getCloseKeyboardOnOpen(), trait.getCropImage(),
                trait.getSaveImage(),
                trait.getUseDayOfYear(), trait.getCategoryDisplayValue(), trait.getResourceFile(), trait.getSynonyms(),
                trait.getMaxDecimalPlaces(), trait.getMathSymbolsEnabled(), trait.getAllowMulticat(),
                trait.getRepeatedMeasures(), trait.getAutoSwitchPlot(), trait.getUnit(), trait.getInvalidValues(),
                trait.getAttachPhoto(), trait.getAttachVideo(), trait.getAttachAudio());
    }

    public boolean checkUnique(HashMap<String, String> values) {

        open();

        return ObservationUnitDao.Companion.checkUnique(values);
    }

    public void updateImportDate(int studyId) {
        StudyDao.Companion.updateImportDate(studyId);
    }

    public void updateEditDate(int studyId) {
        StudyDao.Companion.updateEditDate(studyId);
    }

    public void updateExportDate(int studyId) {
        StudyDao.Companion.updateExportDate(studyId);
    }

    public void updateSyncDate(int studyId) {
        StudyDao.Companion.updateSyncDate(studyId);
    }

    public void updateStudyAlias(int studyId, String newName) {
        open();
        StudyDao.Companion.updateStudyAlias(studyId, newName);
        preferences.edit().putString(GeneralKeys.FIELD_ALIAS, newName).apply();
        close();
    }

    /**
     * Get all study group names
     */
    public List<GroupModel> getAllStudyGroups() {
        return studyGroupDao.getAllGroups();
    }

    /**
     * Delete the unassigned study groups
     */
    public void deleteUnusedStudyGroups() {
        studyGroupDao.deleteUnusedGroups();
    }

    /**
     * Create a study group
     */
    public Integer createOrGetStudyGroup(String groupName) {
        return studyGroupDao.createOrGetGroup(groupName);
    }

    public String getStudyGroupNameById(Integer groupId) {
        return studyGroupDao.getGroupNameById(groupId);
    }

    public Integer getStudyGroupIdByName(String groupName) {
        return studyGroupDao.getGroupIdByName(groupName);
    }

    /**
     * Update the group_id for a study
     */
    public void updateStudyGroup(int studyId, Integer groupId) {
        StudyDao.Companion.updateStudyGroup(studyId, groupId);
    }

    /**
     * Update the is_archived flag for a study
     */
    public void setIsArchived(int studyId, boolean isArchived) {
        StudyDao.Companion.setIsArchived(studyId, isArchived);
    }

    public boolean getStudyGroupIsExpanded(int studyId) {
        return studyGroupDao.getIsExpanded(studyId);
    }

    public void updateStudyGroupIsExpanded(int studyId, boolean value) {
        studyGroupDao.updateGroupIsExpanded(studyId, value);
    }

    public void deleteField(int studyId) {

        open();

        if (studyId >= 0) {
            StudyDao.Companion.deleteField(studyId);
            resetSummaryLabels(studyId);
            deleteFieldSortOrder(studyId);
        }
    }

    private void resetSummaryLabels(int studyId) {
        try {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit().remove(GeneralKeys.SUMMARY_FILTER_ATTRIBUTES + "." + studyId)
                    .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void deleteFieldSortOrder(int studyId) {
        try {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit().remove(GeneralKeys.SORT_ORDER + "." + studyId)
                    .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void switchField(int studyId) {

        open();

        //TODO lastplot is effectively erased when fields are switched, change this to persist and save each field's last plot.
        //potentially use preference map or db column

        preferences.edit().remove(GeneralKeys.LAST_PLOT).apply();
        //ep.edit().putString("lastplot", null).apply();

        //delete the old table
        db.execSQL("DROP TABLE IF EXISTS ObservationUnitProperty");

        StudyDao.Companion.switchField(studyId);
    }

    public int checkBrapiStudyUnique(String observationLevel, String brapiId) {

        open();

        return StudyDao.Companion.checkBrapiStudyUnique(observationLevel, brapiId);
    }

    public int createField(FieldObject e, List<String> columns, Boolean fromBrapi) {

        open();

        return StudyDao.Companion.createField(e, timeStamp.format(Calendar.getInstance().getTime()), columns, fromBrapi);
    }

    public void createFieldData(int studyId, List<String> columns, List<String> data) {

        open();

        StudyDao.Companion.createFieldData(studyId, columns, data);
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

    public void importDatabase(DocumentFile file) {
        String internalDbPath = getDatabasePath(this.context);

        close();

        String fileName = file.getName();

        if (fileName != null) {

            Log.w("File to copy", file.getName());

            File oldDb = new File(internalDbPath);

            //first check if the file to import is just a .db file
            if (fileName.endsWith(".db")) { //if it is import it old-style
                try {
                    BaseDocumentTreeUtil.Companion.copy(context, file, DocumentFile.fromFile(oldDb));

                    open();
                } catch (Exception e) {

                    Log.d("Database", e.toString());

                }
            } else if (fileName.endsWith(".zip")){ // for zip file, call the unzip function
                try (InputStream input = context.getContentResolver().openInputStream(file.getUri())) {

                    try (OutputStream output = new FileOutputStream(internalDbPath)) {
                        boolean isSampleDb = fileName.equals("sample_db.zip");
                        ZipUtil.Companion.unzip(context, input, output, isSampleDb);

                        open();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new Exception();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (!isTableExists(Migrator.Study.tableName)) {

                Migrator.Companion.migrateSchema(db, getAllTraitObjects());

            }
        }
    }

    /**
     * Export database
     * TODO add documentation
     */
    public void exportDatabase(Context ctx, String filename) throws IOException {
        String internalDbPath = getDatabasePath(this.context);

        close();

        try {

            File oldDb = new File(internalDbPath);

            DocumentFile databaseDir = BaseDocumentTreeUtil.Companion.getDirectory(ctx, R.string.dir_database);

            if (databaseDir != null) {

                String dbFileName = "fieldbook.db";
                String prefFileName = filename + "_db_sharedpref";
                String zipFileName = filename + ".zip";

                DocumentFile dbDoc = databaseDir.findFile(dbFileName);
                DocumentFile prefDoc = databaseDir.findFile(prefFileName);
                if (dbDoc != null && dbDoc.exists()) {
                    dbDoc.delete();
                }

                if (prefDoc != null && prefDoc.exists()) {
                    prefDoc.delete();
                }

                DocumentFile backupDatabaseFile = databaseDir.createFile("*/*", dbFileName);
                DocumentFile backupPreferenceFile = databaseDir.createFile("*/*", prefFileName);


                DocumentFile zipFile = databaseDir.findFile(zipFileName);
                if (zipFile == null){
                    zipFile = databaseDir.createFile("*/*", zipFileName);
                }

                // copy the preferences in the backupPreferenceFile
                OutputStream tempStream = BaseDocumentTreeUtil.Companion.getFileOutputStream(context, R.string.dir_database, prefFileName);
                ObjectOutputStream objectStream = new ObjectOutputStream(tempStream);

                objectStream.writeObject(preferences.getAll());

                objectStream.close();

                if (tempStream != null) {
                    tempStream.close();
                }

                // add the .db file and preferences file to the zip file
                OutputStream outputStream = context.getContentResolver().openOutputStream(zipFile.getUri());
                if (backupDatabaseFile != null && backupPreferenceFile != null) {

                    BaseDocumentTreeUtil.Companion.copy(context, DocumentFile.fromFile(oldDb), backupDatabaseFile);

                    if (outputStream != null){
                        ZipUtil.Companion.zip(context, new DocumentFile[] { backupDatabaseFile, backupPreferenceFile }, outputStream);

                    }

                    // delete .db file and preferences file
                    if (backupDatabaseFile.exists()){
                        backupDatabaseFile.delete();
                    }

                    if (backupPreferenceFile.exists()){
                        backupPreferenceFile.delete();
                    }
                }
            }

        } catch (Exception e) {

            Log.e(TAG, e.getMessage());

        } finally {

            open();

        }

        open();
    }

    public static String getDatabasePath(Context context) {
        return context.getDatabasePath(DATABASE_NAME).getPath();
    }

    public boolean isTableExists(String tableName) {

        open();

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

    public void updateStudySort(String sortString, int studyId) {

        open();

        StudyDao.Companion.updateStudySort(sortString, studyId);
    }

    public String[] getAllObservationUnitAttributeNames(int studyId) {

        open();

        return ObservationUnitAttributeDao.Companion.getAllNames(studyId);
    }

    public void beginTransaction() {
        openHelper.getWritableDatabase().beginTransaction();
    }

    public void endTransaction() {
        openHelper.getWritableDatabase().endTransaction();
    }

    public void setTransactionSuccessfull() {
        openHelper.getWritableDatabase().setTransactionSuccessful();
    }

    public ObservationModel[] getAllObservations() {

        open();

        return ObservationDao.Companion.getAll();
    }

    public ObservationModel[] getAllObservationsOfVariable(String traitDbId) {

        open();

        return ObservationDao.Companion.getAllOfTrait(traitDbId);
    }

    /**
     * Get the count of missing observations for a trait
     * @param traitId the trait ID
     * @return the count of missing observations
     */
    public int getMissingObservationsCount(String traitId) {
        return ObservationDao.Companion.getMissingObservationsCount(traitId);
    }

    public ObservationModel[] getAllObservations(SQLiteDatabase db) {

        return ObservationDao.Companion.getAll(db);
    }

    public ObservationModel[] getAllObservations(String studyId) {

        open();

        return ObservationDao.Companion.getAll(studyId);
    }

    public ObservationModel[] getAllObservations(String studyId, String unit) {

        open();

        return ObservationDao.Companion.getAll(studyId, unit);
    }

    public ObservationModel[] getAllObservations(String studyId, String plotId, String traitDbId) {

        open();

        return ObservationDao.Companion.getAll(studyId, plotId, traitDbId);
    }

    public ObservationModel[] getAllObservationsFromAYear(String year) {

        open();

        return ObservationDao.Companion.getAllFromAYear(year);
    }

    public void deleteSpectralFact(String factId) {

        open();

        spectralDao.deleteSpectralFactById(factId);
    }

    public ObservationModel[] getRepeatedValues(String studyId, String plotId, String traitDbId) {

        open();

        return ObservationDao.Companion.getAllRepeatedValues(studyId, plotId, traitDbId);
    }

    public String getObservationUnitPropertyByPlotId(String uniqueName, String column, String uniqueId) {

        open();

        return ObservationUnitPropertyDao.Companion.getObservationUnitPropertyByUniqueId(uniqueName, column, uniqueId);
    }

    public void deleteObservation(String id) {

        open();

        ObservationDao.Companion.delete(id);
    }

    /**
     * When the version number changes, this class will recreate the entire
     * database
     * v1.6 - Amended to add new parent field. It is called parent in consideration to the enhanced search
     */
    private static class OpenHelper extends SQLiteOpenHelper {

        SharedPreferences preferences;
        DataHelper helper;

        String RANGE = "`range`";
        String TRAITS = "traits";
        String USER_TRAITS = "user_traits";
        String EXP_INDEX = "exp_id";
        String PLOTS = "plots";
        String PLOT_ATTRIBUTES = "plot_attributes";
        String PLOT_VALUES = "plot_values";
        String TICK = "`";

        ObservationVariableAttributeDetailViewCreator observationVariableAttributeViewCreator
                = new ObservationVariableAttributeDetailViewCreator();

        OpenHelper(DataHelper helper) {
            super(helper.context, DATABASE_NAME, null, DATABASE_VERSION);
            preferences = PreferenceManager.getDefaultSharedPreferences(helper.context);
            this.helper = helper;
        }

        @Override
        public void onOpen(SQLiteDatabase db) {

            db.disableWriteAheadLogging();

            //enables foreign keys for cascade deletes
            db.rawQuery("PRAGMA foreign_keys=ON;", null).close();

            observationVariableAttributeViewCreator.createViews(db);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            //enables foreign keys for cascade deletes
            db.rawQuery("PRAGMA foreign_keys=ON;", null).close();

            db.execSQL("CREATE TABLE "
                    + RANGE
                    + "(id INTEGER PRIMARY KEY, `range` TEXT, plot TEXT, entry TEXT, plot_id TEXT, pedigree TEXT)");
            db.execSQL("CREATE TABLE "
                    + TRAITS
                    + "(id INTEGER PRIMARY KEY, external_db_id TEXT, trait_data_source TEXT, trait TEXT, format TEXT, defaultValue TEXT, minimum TEXT, maximum TEXT, details TEXT, categories TEXT, isVisible TEXT, realPosition int)");
            db.execSQL("CREATE TABLE "
                    + USER_TRAITS
                    + "(id INTEGER PRIMARY KEY, rid TEXT, parent TEXT, trait TEXT, userValue TEXT, timeTaken TEXT, person TEXT, location TEXT, rep TEXT, notes TEXT, exp_id TEXT, observation_db_id TEXT, last_synced_time TEXT)");
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
                    + "(exp_id INTEGER PRIMARY KEY AUTOINCREMENT, exp_name VARCHAR, exp_alias VARCHAR, unique_id VARCHAR, primary_id VARCHAR, secondary_id VARCHAR, exp_layout VARCHAR, exp_species VARCHAR, exp_sort VARCHAR, date_import VARCHAR, date_edit VARCHAR, date_export VARCHAR, count INTEGER, exp_source VARCHAR)");

            //Do not know why the unique constraint does not work
            //db.execSQL("CREATE UNIQUE INDEX expname ON " + EXP_INDEX +"(exp_name);");

            try {
                db.execSQL("CREATE TABLE android_metadata (locale TEXT)");
                db.execSQL("INSERT INTO android_metadata(locale) VALUES('en_US')");
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }

            //migrate handles database upgrade from 8 -> 9
            Migrator.Companion.createTables(db, getAllOldTraitObjects(db));

            //this will force new databases to have full updates, otherwise sqliteopenhelper will not upgrade
            onUpgrade(db, 9, DATABASE_VERSION);
        }

        /**
         * Copy of getAllTraitObjects in DataHelper to migrate to version 9.
         */
        public ArrayList<TraitObject> getAllOldTraitObjects(SQLiteDatabase db) {

            ArrayList<TraitObject> list = new ArrayList<>();

            Cursor cursor = db.query(TRAITS, new String[]{"id", "trait", "format", "defaultValue",
                            "minimum", "maximum", "details", "categories", "isVisible", "realPosition"},
                    null, null, null, null, "realPosition"
            );

            if (cursor.moveToFirst()) {
                do {
                    TraitObject o = new TraitObject();

                    String traitName = cursor.getString(1);
                    String format = cursor.getString(2);

                    //v5.1.0 bugfix branch update, Android getString can return null.
                    if (traitName == null || format == null) continue;

                    o.setId(cursor.getString(0));
                    o.setName(traitName);
                    o.setFormat(format);
                    o.setDefaultValue(cursor.getString(3));
                    o.setMinimum(cursor.getString(4));
                    o.setMaximum(cursor.getString(5));
                    o.setDetails(cursor.getString(6));
                    o.setCategories(cursor.getString(7));
                    o.setRealPosition(cursor.getInt(9));

                    list.add(o);

                } while (cursor.moveToNext());
            }

            if (!cursor.isClosed()) {
                cursor.close();
            }

            return list;
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

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
                        new String[]{preferences.getString(GeneralKeys.FIELD_FILE, ""), preferences.getString(GeneralKeys.FIELD_FILE, ""), preferences.getString(GeneralKeys.UNIQUE_NAME, ""), preferences.getString(GeneralKeys.PRIMARY_NAME, ""), preferences.getString(GeneralKeys.SECONDARY_NAME, "")});

                // convert current range table to plots
                Cursor cursor = db.rawQuery("SELECT * from `range`", null);

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
                String cur2 = "SELECT " + TICK + preferences.getString(GeneralKeys.UNIQUE_NAME, "")
                        + TICK + ", " + TICK
                        + preferences.getString(GeneralKeys.PRIMARY_NAME, "")
                        + TICK + ", " + TICK
                        + preferences.getString(GeneralKeys.SECONDARY_NAME, "")
                        + TICK + " from range";

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

                    String att_val = "select range." + "'" + columnName + "'" + ", plots.plot_id from range inner join plots on range." + "'" + preferences.getString(GeneralKeys.UNIQUE_NAME, "") + "'" + "=plots.unique_id";
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

            if (oldVersion <= 7 & newVersion >= 8) {

                // add columns to tables for brapi integration

                db.execSQL("ALTER TABLE traits ADD COLUMN external_db_id VARCHAR");
                db.execSQL("ALTER TABLE traits ADD COLUMN trait_data_source VARCHAR");
                db.execSQL("ALTER TABLE exp_id ADD COLUMN exp_source VARCHAR");
                db.execSQL("ALTER TABLE user_traits ADD COLUMN observation_db_id TEXT");
                db.execSQL("ALTER TABLE user_traits ADD COLUMN last_synced_time TEXT");

            }

            if (oldVersion < 9 & newVersion >= 9) {

                // Backup database
                try {
                    helper.open();
                    helper.exportDatabase(helper.context, "backup_v8");
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, e.getMessage());
                }

                Migrator.Companion.migrateSchema(db, getAllOldTraitObjects(db));

                preferences.edit().putInt(GeneralKeys.SELECTED_FIELD_ID, -1).apply();
            }

            if (oldVersion <= 9 && newVersion >= 10) {

                helper.fixGeoCoordinates(db);

                preferences.edit().putInt(GeneralKeys.SELECTED_FIELD_ID, -1).apply();

            }

            if (oldVersion <= 10 && newVersion >= 11) {

                // modify studies table for better handling of brapi study attributes
                db.execSQL("ALTER TABLE studies ADD COLUMN import_format TEXT");
                db.execSQL("ALTER TABLE studies ADD COLUMN date_sync TEXT");
                helper.populateImportFormat(db);
                helper.fixStudyAliases(db);

            }

            if (oldVersion <= 11 && newVersion >= 12) {
                // Add observation_unit_search_attribute column to studies table, use study_unique_id_name as default value
                db.execSQL("ALTER TABLE studies ADD COLUMN observation_unit_search_attribute TEXT");
                db.execSQL("UPDATE studies SET observation_unit_search_attribute = study_unique_id_name");
            }

            if (oldVersion <= 12 && newVersion >= 13) {
                // migrate to version 13 for minor refactoring
                Migrator.Companion.migrateToVersion13(db);
            }

            if (oldVersion <= 13 && newVersion >= 14) {
                //groups table migration
                Migrator.Companion.migrateToVersion14(db);
            }

            //skipped version 15

            if (oldVersion <= 15 && newVersion >= 16) {
                //spectral data migration
                Migrator.Companion.migrateToVersion16(db);
            }

            if (oldVersion <= 16 && newVersion >= 17) {
                // add field creator configuration columns to studies table (start corner, walking directiop/pattern)
                Migrator.Companion.migrateToVersion17(db);
            }

            if (oldVersion <= 17 && newVersion >= 18) {
                // add trait alias and synonyms column migration
                Migrator.Companion.migrateToVersion18(db);
            }

            if (oldVersion <= 18 && newVersion >= 19) {

                Migrator.Companion.migrateToVersion19(db);
            }

            if (oldVersion <= 19 && newVersion >= 20) {

                Migrator.Companion.migrateToVersion20(db);
            }

            if (oldVersion <= 20 && newVersion >= 21) {

                Migrator.Companion.migrateToVersion21(db);
            }
        }
    }
}