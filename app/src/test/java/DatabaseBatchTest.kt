import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.os.Build
import androidx.core.database.getStringOrNull
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.brapi.Image
import com.fieldbook.tracker.brapi.Observation
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.models.*
import com.fieldbook.tracker.database.models.ObservationModel.Companion.getObservation
import com.fieldbook.tracker.database.models.ObservationModel.Companion.getObservationByValue
import com.fieldbook.tracker.database.models.ObservationModel.Companion.getPlotPhotos
import com.fieldbook.tracker.database.models.StudyModel.Companion.checkFieldName
import com.fieldbook.tracker.database.models.StudyModel.Companion.createField
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.TraitObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * This file is for batched database testing. Specifically, this leverages a 'fake' test function
 * batchStart, which will iterate through all databases in assets/database/samples and run all given
 * functions in setupDatabase. The file DatabaseTest can be used to test individual databases.
 *
 * The purpose of this file is to ensure backend functionality for the new schema update.
 * Each test operates on a function used in the backend (DataHelper.java), which is compared with
 * the new version of the function for the new schema.
 *
 * New and old functions share parameter and output types, excluding SharedPreference variables.
 *
 * Furthermore, each test should examine the runtime for respective functions.
 *
 * TODO: use cascade deletes for Studies but backup db.
 * TODO: Merge with develop and create pull request
 *
 * author: Chaney
 */
@Config(sdk = [Build.VERSION_CODES.O_MR1])
@RunWith(RobolectricTestRunner::class)
@ExperimentalTime
open class DatabaseBatchTest {

    private lateinit var mDataHelper: DataHelper

    /**
     * These variables are initialized in the setup function.
     * They are used as values that would be in a shared preferences instance.
     * S.a firstname: row, second: col, unique: plot
     *
     * These are also repopulated in switchField.
     *
     * TODO: check if this is now redundant, might be better to query the study table each time we need these
     */
    private lateinit var firstName: String
    private lateinit var secondName: String
    private lateinit var uniqueName: String

    @Test
    fun batchStart() {
        //driver test function, run this to start the batch testing.
    }

    /**
     * Iterates through all databases in assets/database/samples and runs functions in setupDatabase.
     */
    @Before
    open fun setUp() {

        ApplicationProvider.getApplicationContext<Context>()
            .assets.list("database/samples")?.forEach {

                try {
                    setupDatabase(it)
                    println("$it passes.")
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("$it failed.")
                }
            }
    }

    /**
     * Handles database loading, schema migration and test runs.
     */
    private fun setupDatabase(path: String) {

        loadingSqlFile(path)

        mDataHelper = DataHelper(ApplicationProvider.getApplicationContext())

        mDataHelper.open()

        //at this point the new schema has been created through DataHelper
        //next we need to query the study table for unique/primary/secondary ids to build the other queries
        val study = withDatabase { db -> db.query(StudyModel.tableName).toFirst() } ?: emptyMap()

        if (study.isNotEmpty()) {

            firstName = study["study_primary_id_name"].toString()
            secondName = study["study_secondary_id_name"].toString()
            uniqueName = study["study_unique_id_name"].toString()

            val ep = ApplicationProvider.getApplicationContext<Context>()
                    .getSharedPreferences("Settings", MODE_PRIVATE)

            ep.edit().apply {
                putString("ImportFirstName", firstName)
                putString("ImportSecondName", secondName)
                putString("ImportUniqueName", uniqueName)
            }.commit()

            mDataHelper.switchField(1)

            //create views
            withDatabase { db ->

                db.execSQL("DROP VIEW IF EXISTS $sImageObservationViewName")

                db.execSQL(sImageObservationView)
            }

            println("Database path: $path")

            /**
             * This is where the test functions are run, the result is also timed.
             */
            println("Test runtime: ${measureTimeMillis { 
                
                checkRangeTests()
                checkAllObservationTests()
                
                //the only observation unit test
                checkCheckUnique()
                
                checkAllVariableTests()
                checkAllStudyTests()

            }} ms")

            mDataHelper.close()
        }
    }

    @Test
    fun checkRangeTests() {

        println("Range tests.")

        //runs multiple tests on all imported fields
        checkSwitchField()

        //backend fails on some databases, looks like range table is not created
        //checkGetExportDbData()

        //backend fails again on some dbs, also issue in old backend with columns with blob/numeric affinity
        //checkConvertDatabaseToTable()

    }

    @Test
    fun checkAllObservationTests() {
        println("Observation tests.")

        //pass
        checkDeleteTrait()

        //fail TODO: implement brapi client test
//        checkDeleteTraitByValue()
//        checkGetObservation()
//        checkGetObservationByValue()
//        checkGetPlotPhotos()
//        checkUpdateObservations()
//        checkGetImageObservations()
//        checkUpdateImage()
    }

    @Test
    fun checkAllVariableTests() {
        println("Variable tests.")
        checkGetUserDetail()
        checkGetDetail()
        checkHasTrait()
        checkGetTraitColumns()
        checkGetAllTraitsForExport()
        checkGetTraitColumnData()
        checkGetAllTraitObjects()
        checkGetTraitVisibility()
        checkGetDetails()
        checkWriteNewPosition()
        checkGetAllTraits()
        checkInsertTraits()
        checkUpdateTraitPosition()
        checkEditTraits()
        checkGetFormat()
        checkUpdateTraitVisibility()
        checkGetVisibleTrait()
        checkGetTraitExists()
    }

    @Test
    fun checkAllStudyTests() {
        println("Study tests.")
        checkGetFieldObject()
        checkGetFieldCounts()
        checkGetAllFieldObjects()
        checkAllPlots()
        checkCreateField()
        checkCreateFieldData()
        checkUpdateStudyTable()
        checkCheckFieldName()
        checkCreateFieldObjects()
    }

    @After
    open fun after() {

        //mDataHelper.close()

    }

    /**
     * Reads the first 16 bytes from the sample.db asset,
     * It should read SQLite format 3\u0000 for a valid sqlite3 file.
     */
    @Test
    fun isValidSqlDatabase() {

        val context = ApplicationProvider.getApplicationContext<Context>()

        context.assets.open("database/sample.db").use { input ->

            val buffer = ByteArray(16)
            input.read(buffer, 0, 16)

            assert(String(buffer) == "SQLite format 3\u0000")
        }
    }

    /**
     * Copies the sample.db in the assets folder to the app database file.
     */
    @Test
    fun exportSqlFile() {

        val context = ApplicationProvider.getApplicationContext<Context>()

        FileInputStream(context.getDatabasePath("fieldbook.db")).use { input ->

            val size = input.available()

            val buffer = ByteArray(size)

//            println("Exporting db filesize: $size B")

//            println(context.packageName)
//            println(File(context.filesDir, "export.db").path)
            FileOutputStream(File(context.filesDir, "export.db")).use {

                input.read(buffer)

                it.write(buffer)
            }
        }
    }

    /**
     * Copies the sample.db in the assets folder to the app database file.
     */
    private fun loadingSqlFile(path: String) {

        val context = ApplicationProvider.getApplicationContext<Context>()

//        context.assets.open("database/sample.db").use { input ->
        context.assets.open("database/samples/$path").use { input ->

            val size = input.available()

            val buffer = ByteArray(size)

//            println("Loading db filesize: $size B")

            FileOutputStream(context.getDatabasePath("fieldbook.db")).use {

                input.read(buffer)

                it.write(buffer)
            }

        }
    }

    //region Other Tests

    @Test
    fun checkGetTraitExists() {

        val traits = mDataHelper.allTraitObjects
        val variables = ObservationVariableModel.getAllTraitObjects()

        check(traits.size == variables.size) {
            "Old and new database trait row count does not match."
        }

        traits.forEachIndexed { index, trait ->

            //getTraitExist parameters
            //id -> range table id
            //parent -> observation variable name
            //trait -> observation variable format
            val oldExists = mDataHelper.getTraitExists(trait.id.toInt(), trait.trait, trait.format)

            val newExists = ObservationVariableModel.getTraitExists(uniqueName, trait.id.toInt(), trait.trait, trait.format)

            assert(oldExists == newExists) {
                "$oldExists != $newExists"
            }
        }

    }

    @Test
    fun checkGetAllRangeId() {

        val oldRangeId = try {
            mDataHelper.allRangeID
        } catch (e: Exception) {
            null
        }

        if (oldRangeId != null) {

            val newRangeId = getAllRangeId()

            assert(oldRangeId!!.size == newRangeId.size) {
                "Range table sizes mismatch. ${oldRangeId.size} != ${newRangeId.size}"
            }

            oldRangeId.forEachIndexed { index, range ->

                val newRange = newRangeId[index]

                //print("$range == $newRange...")

                assert(range.toString() == newRange) {
                    "$range != $newRange"
                }

                //print("verified")

                //println()

            }
        } else {

            println("\tOld backend failed in checkGetAllRangeId")

        }
    }

    private fun getPlotUniqueNames(eid: Int) = withDatabase { db ->

        var cursor: Cursor

        // get array of plot attributes
        if (eid == -1) {
            cursor = db.rawQuery("SELECT unique_id FROM plots", null)
            cursor.moveToFirst()
        } else {
            cursor = db.rawQuery("SELECT unique_id FROM plots WHERE plots.exp_id = ?", arrayOf(eid.toString()))
            cursor.moveToFirst()
        }

        val numAttributes = cursor.count

        val plotAttr = arrayOfNulls<String>(numAttributes)

        for (i in 0 until numAttributes) {
            plotAttr[i] = cursor.getString(0)
            cursor.moveToNext()
        }

        plotAttr

    } ?: emptyArray()

    private fun getPlotAttributes(eid: Int) = withDatabase { db ->

        var cursor: Cursor

        // get array of plot attributes
        if (eid == -1) {
            cursor = db.rawQuery("SELECT plot_attributes.attribute_name FROM plot_attributes limit 1", null)
            cursor.moveToFirst()
        } else {
            cursor = db.rawQuery("SELECT plot_attributes.attribute_name FROM plot_attributes WHERE plot_attributes.exp_id = $eid", null)
            cursor.moveToFirst()
        }

        val numAttributes = cursor.count

        val plotAttr = arrayOfNulls<String>(numAttributes)

        for (i in 0 until numAttributes) {
            plotAttr[i] = cursor.getString(0)
            cursor.moveToNext()
        }

        plotAttr

    } ?: emptyArray()

    @Test
    fun checkGetDropDownRange() {

        for (field in mDataHelper.allFieldObjects) {

            val fieldNames = StudyModel.getNames(field.exp_id)

            val plotAttributes = getPlotAttributes(field.exp_id)
            val unitAttributes = ObservationUnitAttributeModel.getAllNames(field.exp_id)

            val plots = mDataHelper.allPlotIds
            val units = ObservationUnitModel.getAll()

            check(plotAttributes.size == unitAttributes.size) {
                "PlotAttribute/UnitAttribute table size mismatch: ${plotAttributes.size} != ${unitAttributes.size}"
            }
            check(plots.size == units.size)

            plots.sliceArray(0 until 10).forEachIndexed { index, plot ->

                plotAttributes.forEachIndexed { attrIndex, trait ->

                    val unitAttribute = unitAttributes[attrIndex]
                    val unit = units[index]

                    check(unitAttribute == trait)
                    check(unit.internal_id_observation_unit.toString() == plot)
    //
    //                println(mDataHelper
    //                        .getDropDownRange(trait, unit.observation_unit_db_id)!!
    //                        .joinToString(","))
    //
    //                println(getDropDownRange(uniqueName, unitAttribute, unit.observation_unit_db_id)
    //                        .joinToString(","))

                    //trait is actually plot attribute names
                    mDataHelper.getDropDownRange(trait, unit.observation_unit_db_id)?.let { it ->
                        val newValues = getDropDownRange(fieldNames!!.unique, unitAttribute, unit.observation_unit_db_id)

                        //print("Checking ${oldValues.joinToString(",")} == ${newValues.joinToString(",")}...")
                        it.forEachIndexed { index, value ->
                            assert(value == newValues[index])
                        }
                    }


                    //println("verified.")
                }
            }
        }
    }

    @Test
    fun checkGetRangeColumnNames() {

        //println(mDataHelper.rangeColumnNames.joinToString(","))
        //println(getRangeColumnNames().joinToString(","))

        val newNames = getRangeColumnNames()
        mDataHelper.rangeColumnNames.forEachIndexed { index, column ->
            //old query doesn't return id
//            println(mDataHelper.rangeColumnNames.joinToString(","))
//            println(getRangeColumnNames().joinToString(","))
            assert(column in newNames) {
                "Range/UnitProperty column name mismatch: $column != ${getRangeColumnNames()[index]}"
            }
        }
    }

    @Test
    fun checkGetExportDbData() {

        insertRandomObservations()

        for (field in mDataHelper.allFieldObjects) {

            mDataHelper.switchField(field.exp_id)

            val fieldNames = StudyModel.getNames(field.exp_id)!!

            checkGetRangeColumns()
            val fieldList = mDataHelper.rangeColumns //getRangeColumns()
            val newRangeCols = getRangeColumns()

            val traits = ObservationVariableModel.getAllTraits()

//            println(fieldList.joinToString(","))

            val newCursor = try {
                getExportDbData(fieldNames.unique, fieldList, traits)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            if (newCursor != null) {

                val oldCursor = try {
                    mDataHelper.getExportDBData(fieldList, traits)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                if (oldCursor != null) {
                    //println(oldCursor.columnNames.joinToString(","))

                    //println(newCursor?.columnNames?.joinToString(","))

                    oldCursor.assertExists(newCursor)
//                    newCursor.assertExists(oldCursor)
                } else {
                    println("Old backend failed in checkGetExportDbData")
                }

            } else assert(false)
        }

    }

    /**
     * TODO: Old backend seems to have issues querying for BLOB/Ints e.g Photos url is missed and random integer columns are missed.
     */
    @Test
    fun checkConvertDatabaseToTable() {

        check(mDataHelper.allFieldObjects.size == StudyModel.getAllFieldObjects().size)

        for (field in mDataHelper.allFieldObjects) {

            mDataHelper.switchField(field.exp_id)

            /**
             * In field book, convertDatabaseToTable is used twice.
             * Once where the first parameter only contains the user unique id string e.g "plot_id"
             * Secondly where getRangeColumns() is used to populate the first parameter.
             */
            val cols = getRangeColumns()

            val oldCols = mDataHelper.rangeColumns

            check(oldCols.size == cols.size) {
                "${cols.joinToString(",")} != ${oldCols.joinToString(",")}"
            }

            oldCols.forEachIndexed { index, trait ->
                check(trait == cols[index])
            }

            var traits = ObservationVariableModel.getAllTraits().filter { "notes" !in it.toLowerCase() && "location" !in it.toLowerCase() }.toTypedArray()
            val oldTraits = mDataHelper.allTraits.filter { "notes" !in it.toLowerCase() && "location" !in it.toLowerCase() }.toTypedArray()

            check(traits.size == oldTraits.size)
            oldTraits.forEachIndexed { index, trait ->
                check(trait == traits[index]) {
                    "${oldTraits.joinToString(",")} != ${traits.joinToString(",")}"
                }
            }

            val cursor = try {
                mDataHelper.convertDatabaseToTable(cols, traits)
//                println("${field.exp_id} old convertDatabaseToTable: ${measureTimedValue {
//                    cursor = mDataHelper.convertDatabaseToTable(cols, traits)
//                }.duration.inSeconds} seconds")
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            if (cursor != null) {
                val newCursor: Cursor? = convertDatabaseToTable(field.unique_id, cols, traits)
//                println("${field.exp_id} new convertDatabaseToTable: ${measureTimedValue {
//                    newCursor = convertDatabaseToTable(field.unique_id, cols, traits)
//                }.duration.inSeconds} seconds")

                if (newCursor != null) {
                    //original backend parsing:             plotIdData[i] = databaseData.getString(databaseData.getColumnIndex(databaseData.getColumnName(0)));

                    var oldTable = cursor.toTable()
                    oldTable = oldTable.slice(0 until if (oldTable.size < 10) oldTable.size else 10)

                    //with missedCount track the number of old backend values that were missed
                    var missedCount = 0
                    var mismatchCount = 0
                    var matchCount = 0
                    newCursor!!.toTable().slice(0 until if (oldTable.size < 10) oldTable.size else 10).forEachIndexed { index, row ->
                        val oldRow = oldTable[index]
                        row.forEach { (k, v) ->
                            if (oldRow[k] == null && v != null) {
                                missedCount++
                            }  else if (oldRow[k] != v) {
                                mismatchCount++
//                                //TODO the .toTable function on the old cursors doesnt populate integer/blob columns
//                                println("missing $k ${oldRow[k]} != $v")
                            } else {
                                matchCount++
                            }
                        }
                    }

                    if (missedCount > 0) {
                        println("Old backend missed values in convertDatabaseToTable: $missedCount")
                    }

                    if (mismatchCount > 0) {
                        println("Backends have different values in convertDatabaseToTable: $mismatchCount")
                    }

                    if (matchCount > 0) {
                        println("Values matching in convertDatabaseToTable: $matchCount")
                    }

                } else {
                    println("new backend failed for convertDatabaseToTable")
                }
                //newCursor!!.assertEqual(cursor, 10)
                //cursor.assertEqual(newCursor!!)

            } else {
                println("Old backend failed on convertDatabaseToTable")
            }
        }
    }

    @Test
    fun checkSwitchField() {

        val fields = mDataHelper.allFieldObjects
        val studies = StudyModel.getAllFieldObjects()

        check(fields.size == studies.size)

        fields.forEachIndexed { index, fieldObject ->

            val study = studies[index]

            firstName = study.study_primary_id_name!!
            secondName = study.study_secondary_id_name!!
            uniqueName = study.study_unique_id_name!!

            val ep = ApplicationProvider.getApplicationContext<Context>()
                    .getSharedPreferences("Settings", MODE_PRIVATE)

            ep.edit().apply {
                putString("ImportFirstName", firstName)
                putString("ImportSecondName", secondName)
                putString("ImportUniqueName", uniqueName)
            }.commit()

//            println("$firstName $secondName $uniqueName")
            mDataHelper.switchField(fieldObject.exp_id)

            println("Checking field: ${fieldObject.exp_id}")
//            checkAllPlots()
//            checkGetAllTraits()
//            checkGetAllRangeId()
//            checkGetTraitExists()
//            checkGetRangeColumnNames()
//            checkGetRangeColumns()
//            checkGetDropDownRange()
//            println("passed")
        }
    }

    //TODO: Trevor, check what data should be deleted via cascade
    //Deleting a study row will cascade delete to foreign keys.
    @Test
    fun deleteField() {

        val oldFields = mDataHelper.allFieldObjects
        val newStudies = StudyModel.getAllFieldObjects()

        checkCreateFieldObjects()

        check(oldFields.size == newStudies.size) {
            "Study/Field table size mismatch. ${oldFields.size} != ${newStudies.size}"
        }

        oldFields.forEachIndexed { index, fieldObject ->

            val study = newStudies[index]

            assert(try {

                //println("Delete old: ${fieldObject.exp_id} new: ${study.internal_id_study}")

                mDataHelper.deleteField(fieldObject.exp_id)
                deleteField(study.internal_id_study)!!

                checkGetAllFieldObjects()

                true

            } catch (npe: NullPointerException) {
                println("New deleteField function failed.")
                npe.printStackTrace()
                false
            } catch (sql: SQLiteException) {
                sql.printStackTrace()
                false
            }) {
                "Function deleteField did not preserve DB state."
            }
        }

    }

    @Test
    fun checkGetFormat() {

        val oldFormats by lazy { mDataHelper.format }
        val newFormats by lazy { getFormat() }

        //println("Checking old getFormat query time: ${measureTimedValue { oldFormats.size }}")
        //println("Checking new getFormat query time: ${measureTimedValue { newFormats?.size }}")

        assert(oldFormats.size == newFormats?.size) {
            "Queries must return the same sized result array."
        }

        oldFormats.forEachIndexed { index, format ->
//            println("Checking $format == ${newFormats?.get(index)}")
            val newFormat = newFormats?.get(index)
            assert(format == newFormat) {
                "Formats must be equal: $format / $newFormat"
            }
        }
    }

    @Test
    fun checkGetVisibleTrait() {

        val oldTraits by lazy { mDataHelper.visibleTrait }
        val newTraits by lazy { getVisibleTrait() }

        //println("Checking old getVisibleTrait query time: ${measureTimedValue { oldTraits.size }}")
        //println("Checking new getVisibleTrait query time: ${measureTimedValue { newTraits.size }}")

        //oldTraits.forEach { println(it) }

        //newTraits.forEach { println("new $it") }

        assert(oldTraits.size == newTraits.size) {
            "Visible Trait/Variable table size mismatch. ${oldTraits.size} != ${newTraits.size}"
        }

        oldTraits.forEachIndexed { index, trait ->

            val newTrait = newTraits[index].observation_variable_name

//            println("Checking $trait == $newTrait...")

            assert(trait == newTrait)

            //print("verified")

            //println()
        }

        //println("Ordering verified")
    }

    @Test
    fun checkGetAllTraits() {

        val traits by lazy {
            mDataHelper.allTraitObjects
        }

        val variables by lazy {
            ObservationVariableModel.getAllTraitObjects()
        }

//        println("Checking original query getAllTraits ${
//            measureTimedValue {
//                traits
//            }.value.size
//        }")
//
//        println("Checking new query getAllTraits ${
//            measureTimedValue {
//                variables
//            }.value.size
//        }")

        traits.forEachIndexed { index, trait ->

            val variable = variables[index]

            assert(trait.visible == variable.visible)
            assert(trait.defaultValue == variable.defaultValue)
            assert(trait.trait == variable.trait)
            assert(trait.format == variable.format)
            assert(trait.realPosition == variable.realPosition)
            assert(trait.externalDbId ?: "" == variable.externalDbId) {
                "Trait/Variable external id mismatch: ${trait.externalDbId} != ${variable.externalDbId}"
            }
            assert(trait.traitDataSource == variable.traitDataSource)

        }
    }

    @Test
    fun checkCreateFieldObjects() {

        println("Creating field objects using data helper ${
            measureTimedValue {
                for (i in 1 until 1000) {
                    val name = UUID.randomUUID().toString()
                    val firstField = FieldObject().apply {
                        exp_name = name
                        unique_id = "plot"
                        primary_id = "row"
                        secondary_id = "col"
                        date_import = "hello"
                        date_export = "world"
                        exp_source = "?"
                    }
                    val rowid = mDataHelper.createField(firstField, listOf("A"))
                    StudyModel.createField(FieldObject().apply {
                        exp_id = rowid
                        exp_name = name
                        unique_id = "plot"
                        primary_id = "row"
                        secondary_id = "col"
                        date_import = firstField.date_import
                        date_export = "world"
                        exp_source = "?"
                    }, listOf("A"))
                }
            }
        }")

        checkGetFieldObject()
    }

    @Test
    fun checkGetRangeColumns() {

        val oldCols by lazy { mDataHelper.rangeColumns }

//        println(oldCols.joinToString(","))

        val newCols by lazy { getRangeColumns() }

//        println("Checking old range column query time: ${measureTimedValue { oldCols.size }}")
//        println("Checking new range column query time: ${measureTimedValue { newCols.size }}")

//        println(newCols.joinToString(","))
        assert(oldCols.size == newCols.size) {
            "Range col size mismatch: ${oldCols.size} != ${newCols.size}"
        }

        oldCols.forEachIndexed { index, oldCol ->

            val newCol = newCols[index]

            assert(newCol == oldCol)
        }
    }
    //endregion

    private fun checkFieldEquality(field: FieldObject?, field2: FieldObject?) {
//        assert(field?.exp_id == field2?.exp_id)
        assert(field?.exp_name == field2?.exp_name)
        assert(field?.exp_alias == field2?.exp_alias)
        assert(field?.exp_source == field2?.exp_source)
//        assert(field?.date_export == field2?.date_export)
//        println("${field?.date_import} ${field2?.date_import}")
//        assert(field?.date_import == field2?.date_import)
        assert(field?.exp_sort == field2?.exp_sort)
        assert(field?.exp_species == field2?.exp_species)
        assert(field?.primary_id == field2?.primary_id)
        assert(field?.unique_id == field2?.unique_id)
        assert(field?.secondary_id == field2?.secondary_id)
        assert(field?.exp_layout == field2?.exp_layout)
//        assert(field?.date_edit == field2?.date_edit)

//        println("${field?.exp_name} == ${field2?.exp_name} verified")
    }

//    fun checkMemory() {
//
//        val mi = ActivityManager.MemoryInfo()
//
//        (ApplicationProvider.getApplicationContext<Context>()
//                .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
//                .getMemoryInfo(mi)
//
//        val availableMegs: Long = mi.availMem / 0x100000L
//        val percentAvail: Double = mi.availMem / mi.totalMem * 100.0
//
//        println(percentAvail)
//    }

    //region ObservationModel Tests
    @Test
    fun checkGetPlotPhotos() {

        insertRandomObservations()

        var oldSum = 0.0
        var newSum = 0.0

        mDataHelper.allPlotIds.sliceArray(0..9).forEachIndexed { index, plotId ->

            mDataHelper.allTraitObjects.forEachIndexed { index, traitObject ->

                val photos by lazy { mDataHelper.getPlotPhotos(plotId, traitObject.trait) }

                val newPhotos by lazy { getPlotPhotos(plotId, traitObject.trait) }

                oldSum += measureTimedValue { photos }.duration.inMilliseconds
                newSum += measureTimedValue { newPhotos }.duration.inMilliseconds

                check(photos.size == newPhotos.size) {
                    "Photo size mismatch: ${photos.size} != ${newPhotos.size}"
                }

                if (photos.size > 0) {

                    photos.forEachIndexed { index, photo ->

                        val newPhoto = newPhotos[index]

//                        print("Checking $photo = $newPhoto...")

                        assert(photo == newPhoto)

//                        println("verified.")
                    }
                }
            }
        }

//        println("Old getPhotos average query time: ${oldSum / 10 * mDataHelper.allPlotIds.size}")
//        println("New getPhotos average query time: ${newSum / 10 * mDataHelper.allPlotIds.size}")

    }

    /**
     * Inserts ten random observations per plot/trait pairs.
     */
    private fun insertRandomObservations() {

        ObservationUnitModel.getAll().sliceArray(0 until 10).forEachIndexed { index, unit ->

             mDataHelper.allTraitObjects.forEachIndexed { index, traitObject ->

                for (i in 0..1) {

                    val obs = randomObservation(
                            unit.internal_id_observation_unit.toString(),
                            unit.observation_unit_db_id,
                            traitObject.format,
                            traitObject.trait,
                            traitObject.id)

                    //println("${obs.observation_unit_id} and ${obs.value}")
                    val newRowid = ObservationModel.insertObservation(obs)
                    val rowid = mDataHelper.insertUserTraits(
                            unit.observation_unit_db_id,
                            traitObject.trait,
                            traitObject.format,
                            obs.value,
                            obs.collector,
                            "",
                            "",
                            obs.study_db_id,
                            obs.observation_unit_id,
                            OffsetDateTime.from(brapiFormatter.parse(obs.last_synced_time))
                    )

//                    println("Inserting observation ${obs.observation_unit_id} $newRowid $rowid at ${obs.last_synced_time}")
                }
            }
        }
    }

    /**
     * Query only selects plot unique name and last time synced
     */
    @Test
    fun checkGetObservation() {

        checkAllPlots()

        insertRandomObservations()

        val plots = mDataHelper.allPlotIds
        val traitObjects = mDataHelper.allTraitObjects

        var oldTimingSum = .0
        var newTimingSum = .0

        ObservationUnitModel.getAll().sliceArray(0 until 10).forEachIndexed { index, unit ->

            traitObjects.forEachIndexed { traitIndex, trait ->

                /**
                 * getObservation takes a plotId (not PK, actually observation unit db id aka rid)
                 *                        trait, which is the trait/variable name
                 */
                val oldObs by lazy { mDataHelper.getObservation(
                        unit.observation_unit_db_id,
                        trait.trait) }

                val newObs by lazy { getObservation(
                        unit.observation_unit_db_id,
                        trait.trait) }

                oldTimingSum += measureTimedValue { oldObs }.duration.inMilliseconds
                newTimingSum += measureTimedValue { newObs }.duration.inMilliseconds

                check((oldObs.dbId ?: "-1") == newObs?.dbId) {
                    "Observation object id mismatch. ${oldObs.dbId} and ${newObs?.dbId}"
                }

//                newObs?.lastSyncedTime?.let { println("${oldObs.lastSyncedTime} and $it") }

                assert((oldObs.lastSyncedTime) == newObs?.lastSyncedTime) {
                    "Observation values mismatch. ${oldObs.lastSyncedTime} and ${newObs?.lastSyncedTime}"
                }
            }
        }

//        println("Old getObservation query average: $oldTimingSum")
//        println("New getObservation query average: $newTimingSum")
    }

    @Test
    fun checkGetObservationByValue() {

        val idMap = HashMap<String, String?>()

        mDataHelper.allTraitObjects.forEachIndexed { index, traitObject ->

            ObservationUnitModel.getAll().sliceArray(0 until 5).forEachIndexed { index, unit ->

                for (i in 0..5) {

                    val obs = randomObservation(
                            unit.internal_id_observation_unit.toString(),
                            unit.observation_unit_db_id,
                            traitObject.format,
                            traitObject.trait,
                            traitObject.id)

                    val rowid = ObservationModel.insertObservation(obs)
                    val oldRowid = mDataHelper.insertUserTraits(
                            unit.internal_id_observation_unit.toString(),
                            traitObject.trait,
                            traitObject.format,
                            obs.value,
                            obs.collector,
                            "",
                            "",
                            obs.study_db_id,
                            obs.observation_unit_id,
                            OffsetDateTime.parse(obs.last_synced_time, formatter)
                    ).toInt()

                    assert(rowid == oldRowid)

                    idMap[obs.value!!] = obs.observation_unit_id

                }
            }
        }

        checkGetObservation()

        mDataHelper.allTraitObjects.forEachIndexed { index, traitObject ->

            ObservationUnitModel.getAll().sliceArray(0 until 5).forEachIndexed { index, unit ->

                idMap.forEach { (obsValue, obsId) ->

                    var oldId = mDataHelper.getObservationByValue(unit.observation_unit_db_id, traitObject.trait, obsValue).dbId
                    var newId = getObservationByValue(unit.observation_unit_db_id, traitObject.trait, obsValue)!!.dbId

                    if (oldId == "null") oldId = null
                    if (newId == "null") newId = null

                    if (oldId != null && newId != null) {
//                        println("Checking $oldId == $newId")
                    }

                    assert((oldId == null && newId == null) || oldId == newId) {
                        "$obsValue/$obsId UserValue/Observation observation_db_id mismatch: $oldId != $newId"
                    }
                }
            }
        }
    }

    @Test
    fun checkDeleteTrait() {

        val traits = mDataHelper.allTraitObjects
        val variables = ObservationVariableModel.getAllTraitObjects()

        check(traits.size == variables.size)

        traits.forEachIndexed { index, trait ->

            mDataHelper.deleteTrait(trait.id)

            ObservationVariableModel.deleteTrait(trait.id)

            checkGetAllTraits()
        }

    }

    private fun getAllUserValues() = withDatabase { db ->

        db.query("user_traits").toTable()

    }

    //TODO: Test is almost done, needs assert statements
    //used for photos, each one has a unique name
    //this actually deletes observations/uservalues by trait/variable values
    @Test
    fun checkDeleteTraitByValue() {

//        checkGetAllFieldObjects()

        checkGetAllTraitObjects()

        insertRandomObservations()

        ObservationModel.getAll()?.sliceArray(0 until 5)?.asSequence()?.forEach {

            //ensure that we have equal number of observation/user_traits in each db
            val size = ObservationModel.getAll()?.size ?: 0
            val size2 = getAllUserValues()?.size ?: 0

            check(size == size2) {
                "$size != $size2"
            }

            //delete the same trait by its value
            val result = ObservationModel.deleteTraitByValue(it.observation_unit_id,
                    it.observation_variable_name!!,
                    it.value!!)

            val result2 = mDataHelper.deleteTraitByValue(
                    it.observation_unit_id,
                    it.observation_variable_name,
                    it.value)

            result?.let {

//                if (size > ObservationModel.getAll()?.size ?: 0) {
//                    println("Deleted.")
//                }
//
//                if (size2 > getAllUserValues()?.size ?: 0) {
//                    println("Original deleted.")
//                }

                //ensure that an observation was deleted
                assert(size >= ObservationModel.getAll()?.size ?: 0) {
                    "$size <= ${ObservationModel.getAll()?.size ?: 0}"
                }

                assert(size2 >= getAllUserValues()?.size ?: -1) {
                    "$size2 <= ${getAllUserValues()?.size ?: -1}"
                }

                //finally ensure that the observation sizes match
                check((ObservationModel.getAll()?.size ?: 0) == (getAllUserValues()?.size ?: 0)) {
                    "${ObservationModel.getAll()?.size ?: 0} != ${getAllUserValues()?.size ?: 0}"
                }

                checkGetObservation()
            }
        }
    }

    @Test
    fun checkUpdateObservations() {

        insertRandomObservations()

        val obs = ArrayList<Observation>()

        //loop through all pairs, only add observation if id and lastSyncedTime are not null.
        ObservationUnitModel.getAll().sliceArray(0 until 10).forEachIndexed { index, unit ->

            mDataHelper.allTraitObjects.forEach { trait ->

                val o = mDataHelper.getObservation(unit.observation_unit_db_id, trait.trait)

                o.dbId?.let { id ->

                    o.lastSyncedTime?.let { time ->

                        o.fieldBookDbId = Random().nextInt().toString()
                        o.variableName = trait.trait
                        o.setStudyId(unit.study_db_id.toString())

                        obs.add(o)

                    }
                }
            }
        }

        check(obs.size > 0)

//        println("Updating ${obs.size} observations.")

        mDataHelper.updateObservations(obs)

        ObservationModel.updateObservations(obs)

        checkGetObservation()

    }

    //observationDbId is an imported column when using brapi
    @Test
    fun checkGetImageObservations() {

        mDataHelper.getImageObservations("test")
        assert(false)
    }

    /**
     * Helper function that creates a random blank image.
     */
    private fun randomImage(): Image = Image(
            File(ApplicationProvider
                    .getApplicationContext<Context>()!!
                    .filesDir, UUID.randomUUID().toString()).path,
            Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888))

    /**
     * Helper function that inserts a batch of random images.
     */
    private fun insertRandomImages(number: Int) {

        for (i in 0..number) {

            mDataHelper.updateImage(randomImage(), true)

        }
    }

    @Test
    fun checkUpdateImage() {

        //mDataHelper.updateImage(Image(), true)
        assert(false)
    }

    //endregion

    //region ObservationVariableModel tests

    @Test
    fun checkGetUserDetail() {

        mDataHelper.allPlotIds.forEach {

            val oldDetail by lazy {
                mDataHelper.getUserDetail(it)
            }

            val newDetail by lazy {
                ObservationModel.getUserDetail(it)
            }

//            println("old getUserDetail $it ${measureTimedValue { oldDetail }}")
//            println("new getUserDetail $it ${measureTimedValue { newDetail }}")

            oldDetail.forEach { t, u ->
                assert(t in newDetail.keys
                        && newDetail[t] == oldDetail[t])
            }
        }
    }

    //TODO: new view for joining variable columns should improve performance here
    @Test
    fun checkGetDetail() {

        var oldSum = 0.0
        var newSum = 0.0

        val traits = mDataHelper.allTraits!!
        val variables = ObservationVariableModel.getAllTraits()

        check(traits.size == variables.size) {
            "Trait/Variable table size mismatch ${traits.size} != ${variables.size}"
        }

        for (i in 0..10) {
            traits.forEachIndexed { index, trait ->

                val detail by lazy {
                    mDataHelper.getDetail(trait).trait
                }

                val newDetail by lazy {
                    ObservationVariableModel.getDetail(trait)?.trait
                }

                oldSum += measureTimedValue { detail }.duration.inMilliseconds
                newSum += measureTimedValue { newDetail }.duration.inMilliseconds

//                println("Checking $trait == $newDetail...")

                assert(newDetail != null)

                newDetail?.let { assert(it == detail) }

            }
        }

//        println("Old query average time: ${oldSum}")
//        println("New query average time: ${newSum}")

    }

    @Test
    fun checkHasTrait() {

        mDataHelper.allTraitObjects.forEachIndexed { index, traitObject ->

//            println("Checking ${traitObject.trait}")

            val oldResult by lazy {
                mDataHelper.hasTrait(traitObject.trait)
            }

            val newResult by lazy {
                ObservationVariableModel.hasTrait(traitObject.trait)
            }

//            println("${measureTimedValue { oldResult }}")
//
//            println("${measureTimedValue { newResult }}")

            assert(oldResult == newResult)
        }
    }

    @Test
    fun checkGetTraitColumns() {

        val oldCols by lazy {
            mDataHelper.traitColumns
        }

        val newCols by lazy {
            ObservationVariableModel.getTraitPropertyColumns()
        }

//        println(oldCols.joinToString {"$it" })
//        println(newCols.joinToString { "$it" })

        assert((oldCols.toSet() - newCols.toSet()).isEmpty())
    }

    @Test
    fun checkGetAllTraitsForExport() {

        val oldCursor by lazy {
            mDataHelper.allTraitsForExport
        }

        val newCursor by lazy {
            ObservationVariableModel.getAllTraitsForExport()
        }

//        println(ObservationVariableAttributeModel.getAll()?.map {
//            it["observation_variable_attribute_name"]
//        })
//
//        println(oldCursor.columnNames.joinToString(","))
//        println(newCursor?.columnNames!!.joinToString(","))
        oldCursor.assertEqual(newCursor)
    }

    private fun Cursor.assertExists(other: Cursor?) {

        //TODO figure out solutions for these columns
        //rep number of obs collected for a given variable/unit pair
        //location is a shared pref
        val todoKeys = arrayOf("timeTaken", "rep", "location")

        other?.toTable()?.let { table ->

            this.toTable().let { table ->

                //check that each row exists in the other table
                table.forEach { row ->

                    assert(table.find { otherRow ->

                        val requiredFields = columnNames.toSet() - todoKeys.toSet()
                        (requiredFields).all { col ->
                            otherRow[col] == row[col]
                        }

                    } != null) {
                        "$row"
                    }
                }
            }
        }
    }

    /**
     * Walks through both cursors and checks for column equality.
     * rowIterations: number of rows to check before exitting the assertion.
     */
    private fun Cursor.assertEqual(other: Cursor?, rowIterations: Int = -1) {

        val first = moveToFirst()
        val otherFirst = other?.moveToFirst()

        assert(first == otherFirst)

        var iterations = 0

        if (first && otherFirst == true) {

            do {

//                println(columnNames.joinToString(","))
//                println(other?.columnNames?.joinToString(","))
//                assert((columnNames.toSet() - other.columnNames.toSet()).isEmpty()) {
//                    "Column mismatch: ${columnNames.joinToString(",")} != ${other.columnNames.joinToString(",")}"
//                }
//
//                assert((other.columnNames.toSet() - columnNames.toSet()).isEmpty()) {
//                    "Column mismatch: ${columnNames.joinToString(",")} != ${other.columnNames.joinToString(",")}"
//                }

                println("Expected number of column exported: ${columnNames.size}")

                columnNames.sliceArray(0 until 5).forEach { col ->

                    val index = getColumnIndex(col)
                    val newIndex = other.getColumnIndex(col)

                    if (index > -1 && newIndex > -1) {

                        val old = getStringOrNull(index)
                        val new = other.getStringOrNull(newIndex)

                        if (old?.isNotEmpty() == true && new?.isNotEmpty() == true
                                && col != "timeTaken") {
//                        print("Checking $col $old == $new...")
                            assert(old == new)
//                        println("verified.")
                        }
                    }
                }

                iterations++

                println(iterations)

            } while (moveToNext() && other.moveToNext() && ((rowIterations == -1) || iterations < rowIterations))

        } else assert(false) {
            "Cursor mismatch: ${this.columnNames.joinToString(",")} != ${other?.columnNames?.joinToString(",")}"
        }
    }

    /*
    Typically column is trait/isVisible/sort
     */
    @Test
    fun checkGetTraitColumnData() {

        var iterations = 10

        var oldSum = 0.0
        var newSum = 0.0

        for (i in 0..iterations) {

            listOf("trait", "isVisible", "realPosition").forEach {

                val oldColumnData by lazy { mDataHelper.getTraitColumnData(it) }
                val newColumnData by lazy {
                    ObservationVariableModel.migratePattern[it]?.let { key ->
                        ObservationVariableModel
                                .getTraitColumnData(key)
                    }
                }

                oldSum += measureTimedValue { oldColumnData }.duration.inMilliseconds
                newSum += measureTimedValue { newColumnData }.duration.inMilliseconds

                assert(newColumnData != null)

                newColumnData?.let { data ->

                    oldColumnData.forEachIndexed { index, col ->

                        val newCol = data[index].toString()

                        assert(newCol == col) {
                            "$newCol != $col"
                        }
                    }
                }
            }
        }

//        println("Old query average time: ${oldSum / (3.0 * (iterations - 1))}")
//        println("New query average time: ${newSum / (3.0 * (iterations - 1))}")

    }

    @Test
    fun checkGetAllTraitObjects() {

        val traits by lazy { mDataHelper.allTraitObjects }
        val variables by lazy { ObservationVariableModel.getAllTraitObjects() }

//        println("Old getAllTraitObjects query time: ${measureTimedValue { traits }}")
//        println("New getAllTraitObjects query time: ${measureTimedValue { variables }}")

        check(traits.size == variables.size) {
            "Trait/Variable table size mismatch. ${traits.size} != ${variables.size}"
        }

        traits.forEachIndexed { index, traitObject ->

            val variable = variables[index]

            assert(traitObject != null)

            assert(traitObject.visible ?: "true" == variable.visible ?: "true") {
                "${traitObject.visible} != ${variable.visible}"
            }

            assert(traitObject.trait ?: "" == variable.trait ?: "") {
                "Trait/VariableName mismatch. ${traitObject.trait} != ${variable.trait}"
            }
            assert(traitObject.id == variable.id) {
                "Trait/Variable id mismatch: ${traitObject.id} != ${variable.id}"
            }
            assert(traitObject.format == variable.format)
            assert(traitObject.defaultValue == variable.defaultValue)
            assert(traitObject.maximum == variable.maximum) {
                "Trait/Variable maximum value mismatch: ${traitObject.maximum} != ${variable.maximum}"
            }
            assert(traitObject.minimum == variable.minimum) {
                "Trait/Variable minimum value mismatch: ${traitObject.minimum} != ${variable.minimum}"
            }
            assert(traitObject.details == variable.details) {
                "Trait/Variable details value mismatch: ${traitObject.details} != ${variable.details}"
            }
            assert(traitObject.categories == variable.categories) {
                "Trait/Variable categories value mismatch: ${traitObject.categories} != ${variable.categories}"
            }
            assert(traitObject.realPosition == variable.realPosition) {
                "Trait/Variable position value mismatch: ${traitObject.realPosition} != ${variable.realPosition}"
            }
        }

    }

    @Test
    fun checkGetTraitVisibility() {

        val variables by lazy { getVisibleTrait() }
        val traits by lazy { mDataHelper.visibleTrait }

//        println("Checking old getTraitVisibilityQueryTime ${measureTimedValue { traits }}")
//        println("Checking new getTraitVisibilityQueryTime ${measureTimedValue { variables }}")

        check(variables.size == traits.size) {
            "Trait/Variable table size mismatch. ${traits.size} != ${variables.size}"
        }

        variables.forEachIndexed { index, variable ->

            val trait = traits[index]

//            println("Checking $trait == ${variable.observation_variable_name}...")

            assert(trait == variable.observation_variable_name)

//            println("verified")
        }

    }

    @Test
    fun checkGetDetails() {

        val iterations = 10

        val traits = mDataHelper.visibleTrait
        val variables = getVisibleTrait()

        check(traits.size == variables.size) {
            "Trait/Variable table size mismatch ${traits.size} != ${variables.size}"
        }

        var oldTimeAvg = 0.0
        var newTimeAvg = 0.0

        for (i in 0..iterations) {

            traits.forEach {

                val newDetails by lazy { ObservationVariableModel.getDetail(it) }
                val oldDetails by lazy { mDataHelper.getDetail(it) }

                oldTimeAvg += measureTimedValue { newDetails }.duration.inMilliseconds
                newTimeAvg += measureTimedValue { oldDetails }.duration.inMilliseconds

                newDetails?.let { variable ->

                    assert(oldDetails.visible == variable.visible)
                    assert(oldDetails.categories == variable.categories) {
                        "Trait/Variable category mismatch: ${oldDetails.categories} != ${variable.categories}"
                    }
                    assert(oldDetails.details == variable.details) {
                        "Trait/Variable details mismatch: ${oldDetails.details} != ${variable.details}"
                    }
                    assert(oldDetails.minimum == variable.minimum)
                    assert(oldDetails.maximum == variable.maximum)
                    assert(oldDetails.defaultValue == variable.defaultValue)
                    assert(oldDetails.trait == variable.trait)
                    assert(oldDetails.traitDataSource == variable.traitDataSource)
//                assert(oldDetails.externalDbId == variable.externalDbId)
                    assert(oldDetails.realPosition == variable.realPosition)
                    assert(oldDetails.format == variable.format)
                    assert(oldDetails.id == variable.id)
                }
            }
        }

//        println("Checking old getDetails average query time: ${oldTimeAvg / (iterations * traits.size)}}")
//        println("Checking new getDetails average query time: ${newTimeAvg / (iterations * traits.size)}}")

    }

    @Test
    fun checkInsertTraits() {

        val traits = mDataHelper.allTraits

        val iterations = traits.size + 1 + 10

        for (i in (traits.size+1)..iterations) {

            with(randomTraitObject(i)) {
                mDataHelper.insertTraits(this)
                ObservationVariableModel.insertTraits(this)
            }

            checkGetAllTraitObjects()
        }
    }

    /**
     * Unit -> plot/unit internal id
     * variable name -> trait/variable readable name
     * variable -> trait/variable db id
     * format -> trait/variable observation_field_book_format
     */
    private fun randomObservation(unit: String, uniqueName: String, format: String, variableName: String, variable: String): ObservationModel = ObservationModel(mapOf(
            "observation_variable_name" to variableName,
            "observation_variable_field_book_format" to format,
            "value" to UUID.randomUUID().toString(),
            "observation_time_stamp" to "2019-10-15 12:14:59.040+0000",
            "collector" to UUID.randomUUID().toString(),
            "geo_coordinates" to UUID.randomUUID().toString(),
            "last_synced_time" to brapiFormatter.format(OffsetDateTime.now()), //"2019-10-15 12:14:59.040+0000",
            "additional_info" to UUID.randomUUID().toString(),
            StudyModel.FK to UUID.randomUUID().toString(),
            ObservationUnitModel.FK to uniqueName,
            ObservationVariableModel.FK to variable.toInt()))

    var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZZZZZ")
    var brapiFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ");

    private fun randomTraitObject(position: Int): TraitObject = TraitObject().apply {
        trait = UUID.randomUUID().toString()
        format = UUID.randomUUID().toString()
        defaultValue = UUID.randomUUID().toString()
        details = UUID.randomUUID().toString()
        externalDbId = UUID.randomUUID().toString()
        minimum = "0"
        maximum = "100"
        categories = UUID.randomUUID().toString()
        visible = true
        realPosition = position.toString()
    }

    @Test
    fun checkUpdateTraitPosition() {

        val traits = mDataHelper.allTraitObjects
        val variables = ObservationVariableModel.getAllTraitObjects()

        //randomly shuffle and update positions
        val positions = traits.map { it.realPosition }.shuffled()

        traits.forEachIndexed { index, trait ->

            val variable = variables[index]

            mDataHelper.updateTraitPosition(trait.id, positions[index])

            ObservationVariableModel.updateTraitPosition(variable.id, positions[index])

        }

        checkGetAllTraitObjects()

    }

    @Test
    fun checkEditTraits() {

        val traits = mDataHelper.allTraitObjects
        val variables = ObservationVariableModel.getAllTraitObjects()

        check(traits.size == variables.size)

        traits.forEachIndexed { index, trait ->

            val variable = variables[index]

            check(trait.id == variable.id)
            check(trait.trait == variable.trait)
            check(trait.format == variable.format)
            check(trait.defaultValue == variable.defaultValue)
            check(trait.minimum == variable.minimum)
            check(trait.maximum == variable.maximum)
            check(trait.details == variable.details)
            check(trait.categories == variable.categories)

            //populate random updates to variable table
            val min = abs(Random().nextInt()).toString()
            val max = abs(Random().nextInt()).toString()
            //val details = UUID.randomUUID().toString()
            val categories = UUID.randomUUID().toString()
            val default = UUID.randomUUID().toString()

            mDataHelper.editTraits(trait.id, trait.trait, trait.format, default,
                    min, max, trait.details, categories)

            ObservationVariableModel.editTraits(variable.id, variable.trait, variable.format, default,
                    min, max, trait.details, categories)

        }

        checkGetAllTraitObjects()
    }

    @Test
    fun checkUpdateTraitVisibility() {

        val traits = mDataHelper.allTraitObjects

        val variables = ObservationVariableModel.getAllTraitObjects()

        check(traits.size == variables.size)

        traits.forEachIndexed { index, traitObject ->

            val variable = variables[index]

            //ensure the dbs match after conversion
            check(variable.visible == traitObject.visible ?: true)

            //toggle the trait visibility on each backend
            //in original db, if the visible column is null, this means true
            val toggle = !(traitObject?.visible ?: true)
            mDataHelper.updateTraitVisibility(traitObject.trait, toggle)
            ObservationVariableModel.updateTraitVisibility(variable.trait, toggle.toString())
        }

        //requery the traits which now have updated visibility
        val updatedTraits = ObservationVariableModel.getAllTraitObjects()
        val updated = mDataHelper.allTraitObjects

        //check that old traits have been toggled
        traits.forEachIndexed { index, traitObject ->
            assert(traitObject?.visible ?: true != updated[index].visible) {
                "Trait/Variable visibility mismatch: ${traitObject.visible} == ${updated[index].visible}"
            }
        }

        //check that new trait visibilities match
        updated.forEachIndexed { index, traitObject ->
            assert(traitObject?.visible == updatedTraits[index].visible) {
                "Trait/Variable visibility mismatch: ${traitObject.visible} != ${updatedTraits[index].visible} \n ${traitObject.id} vs ${updatedTraits[index].id}"
            }
        }

        checkGetAllTraitObjects()

    }

    @Test
    fun checkWriteNewPosition() {

        val traits = mDataHelper.allTraitObjects
        val variables = ObservationVariableModel.getAllTraitObjects()

        //randomly shuffle and update positions
        val positions = traits.map { it.realPosition }.shuffled()

        arrayOf("trait", "format", "isVisible").forEach { column ->

            traits.forEachIndexed { index, trait ->

                val variable = variables[index]

                when (column) {

                    "trait" -> {

                        mDataHelper.writeNewPosition(column, trait.id, positions[index])

                        ObservationVariableModel.writeNewPosition("observation_variable_name", variable.id, positions[index])
                    }
                    "format" -> {
                        mDataHelper.writeNewPosition(column, trait.id, positions[index])

                        ObservationVariableModel.writeNewPosition("observation_variable_field_book_format", variable.id, positions[index])
                    }
                    "isVisible" -> {
                        mDataHelper.writeNewPosition(column, trait.id, positions[index])

                        ObservationVariableModel.writeNewPosition("visible", variable.id, positions[index])
                    }
                }
            }
            checkGetAllTraitObjects()
        }
    }

    //endregion

    //region ObservationUnitModel Tests

    @Test
    fun checkCheckUnique() {

        val uniqueIdMaps = arrayOf(
                mutableMapOf("1" to ""), mutableMapOf("2" to ""),
                mutableMapOf(*ObservationUnitModel.getAll().map { it.observation_unit_db_id to "" }.toTypedArray())
        )

        for (idMap in uniqueIdMaps) {

            val newCheck = ObservationUnitModel.checkUnique(HashMap(idMap))
            val oldCheck = mDataHelper.checkUnique(HashMap(idMap))
            val result = newCheck == oldCheck

//            println("Checking unique ids...$result with $newCheck = $oldCheck")

            assert(result)

        }
    }

    //endregion

    //region StudyModel Tests

    @Test
    fun checkGetFieldObject() {

//        println("Checking query getFieldObject on ${mDataHelper.allFieldObjects.size} fields.")

        mDataHelper.allFieldObjects.forEach { field ->

            checkFieldEquality(mDataHelper.getFieldObject(field.exp_id),
                    StudyModel.getFieldObject(field.exp_id))
        }
    }

    /**
     * Original database exp_id table has a count column which is the number of plots correlated to that field
     * We replaced this column with a query for the new schema. This test function iterates over
     * all field objects and ensures the count is equivalent for the new query.
     */
    @Test
    fun checkGetFieldCounts() {

    }

    /**
     * This test checks the order and values of the 'getAllFieldObjects' result query.
     * Returns these columns: {"exp_id", "exp_name", "unique_id", "primary_id","secondary_id",
     *                          "date_import", "date_edit", "date_export", "count", "exp_source"},
     * TODO: Trevor, date_edit, count are missing replace unused columns with individual queries
     *
     */
    @Test
    fun checkGetAllFieldObjects() {

//        assert(false) {
//            "date_edit, count columns are missing"
//        }

//        println("Checking ${mDataHelper.allFieldObjects.size} field to study migrations")

        val fields = mDataHelper.allFieldObjects
        val studies = StudyModel.getAllFieldObjects()

        check(studies.size == fields.size) {
            "Study/Field table size mismatch. ${studies.size} != ${fields.size}"
        }

        fields.forEachIndexed { index, field ->

            val study = studies[index]

            with(field) {

                assert(exp_id == study.internal_id_study) {
                    "Study ID mismatch: $exp_id != ${study.internal_id_study}"
                }

                assert(exp_name == study.study_name)

                assert(unique_id == study.study_unique_id_name)

                assert(primary_id == study.study_primary_id_name)

                assert(secondary_id == study.study_secondary_id_name)
//
                //insert tests will have slightly different import times between dbs
//                assert(date_import == study.date_import)

                assert(date_export == study.date_export)

                assert((exp_source == null && study.study_source == null) || exp_source == study.study_source.toString()) {
                    "exp_source and study_source mismatch: $exp_source != ${study.study_source.toString()}"
                }

                val unitCount = StudyModel.getCount(study.internal_id_study)

                assert(count?.toInt() ?: 0 == unitCount) {
                    "Exp_id plot / Study unit count mismatch: $count != $unitCount"
                }

                //val editDate = StudyModel.getEditDate(study.internal_id_study)

                //assert(date_edit == editDate)
                
//                println("${study.study_name} == $exp_name verified")
            }
        }
    }

    private fun getAllPlots(): List<Row> = withDatabase { db ->

        db.query("PLOTS").toTable()

    } ?: emptyList()

    /*
    This function checks that the previous db plots have a corresponding observation unit
     */
    @Test
    fun checkAllPlots() {

        val plots = getAllPlots()
        val units = ObservationUnitModel.getAll()

        check(plots.size == units.size) {
            "Plot/Unit table size mismatch: ${plots.size} != ${units.size}"
        }

        plots.forEachIndexed { index, plot ->

            val unit = units[index]

            assert(plot["exp_id"] == unit.study_db_id) {
                "${plot["exp_id"]} != ${unit.study_db_id.toString()}"
            }

            assert(plot["plot_id"] == unit.internal_id_observation_unit)

            assert(plot["unique_id"] == unit.observation_unit_db_id)

            assert(plot["primary_id"] == unit.primary_id) {
                "${plot["primary_id"]} != ${unit.primary_id}"
            }

            assert(plot["secondary_id"] == unit.secondary_id) {
                "${plot["secondary_id"]} != ${unit.secondary_id}"
            }
        }

    }

    @Test
    fun checkCreateField() {

        for (i in 1..10) {

            with(FieldObject().apply {
                this.exp_alias = UUID.randomUUID().toString()
                this.exp_name = UUID.randomUUID().toString()
                this.unique_id = UUID.randomUUID().toString()
                this.primary_id = UUID.randomUUID().toString()
                this.secondary_id = UUID.randomUUID().toString()
                this.exp_layout = UUID.randomUUID().toString()
                this.exp_sort = "alphabetic"
                this.exp_source = ""
                this.exp_species = "potato"
            }) {
                //create columns for the field
                val alphabet = "abcdefghijklmnopqrstuvwxyz".toCharArray().map { it.toString() }
                val columns = listOf("plot_id", "row", "col") + alphabet
                mDataHelper.createField(this, columns)
                createField(this, columns)
            }
        }

        checkGetAllFieldObjects()
    }

    private fun randomField() = FieldObject().apply {
        exp_name = UUID.randomUUID().toString()
        unique_id = UUID.randomUUID().toString()
        primary_id = UUID.randomUUID().toString()
        secondary_id = UUID.randomUUID().toString()
    }

    @Test
    fun checkCreateFieldData() {

        val fields = mDataHelper.allFieldObjects
        val studies = StudyModel.getAllFieldObjects()

        check(fields.size == studies.size) {
            "Study/Field tables size mismatch ${studies.size} and ${fields.size}"
        }

        for (i in 0..10) {

            //create a random field with random attributes
            val field = randomField()

            //create a few random columns, and include the necessary unique_id and primary/secondary
            val columns = (0 until 5).map { UUID.randomUUID().toString() } + arrayOf(field.unique_id, field.primary_id, field.secondary_id)

            //in original usage, createField is always used before createFieldData
            val eid = StudyModel.createField(field, columns)
            val oldEid = mDataHelper.createField(field, columns)

            assert(eid == oldEid)

            //get number of units before inserting new field data, should be 0
            val oldNumberOfUnits = ObservationUnitModel.getAll(eid).size

            for (i in 0..1) {

                val data = columns.map { UUID.randomUUID().toString() }

                try {
                    mDataHelper.createFieldData(oldEid, columns, data)
                } catch (ae: ArrayIndexOutOfBoundsException) {
                    ae.printStackTrace()
                }

                try {
                    StudyModel.createFieldData(eid, columns, data)
                } catch (ae: ArrayIndexOutOfBoundsException) {
                    ae.printStackTrace()
                }
            }

            assert(oldNumberOfUnits < ObservationUnitModel.getAll(eid).size)

        }

//        println("After plot size: ${ObservationUnitModel.getAll().size}")

        checkAllPlots()
    }

    @Test
    fun checkUpdateStudyTable() {

        val fields = mDataHelper.allFieldObjects
        val studies = StudyModel.getAllFieldObjects()

        check(fields.size == studies.size) {
            "Study/Field tables size mismatch ${studies.size} and ${fields.size}"
        }

        fields.forEachIndexed { index, fieldObject ->

            for (i in 0..7) {

                with(i.toString(2).padStart(3, '0').toBooleanArray()) {

//                    println("${this[0]} ${this[1]} ${this[2]}")

                    //TODO Trevor causes NPE
                    mDataHelper.updateExpTable(this[0], this[1], this[2], fieldObject.exp_id)

                    StudyModel.updateStudyTable(this[0], this[1], this[2], fieldObject.exp_id)

                    checkGetFieldObject()
                }
            }
        }
    }

    private fun String.toBooleanArray(): Array<Boolean> = this.map {
        when(it) {
            '1' -> true
            else -> false
        }
    }.toTypedArray()


    @Test
    fun checkCheckFieldName() {

        val fields = mDataHelper.allFieldObjects
        val studies = StudyModel.getAllFieldObjects()

        check(fields.size == studies.size) {
            "Study/Field tables size mismatch ${studies.size} and ${fields.size}"
        }

        fields.forEachIndexed { index, fieldObject ->

            val study = studies[index]

            assert(mDataHelper.checkFieldName(fieldObject.exp_name) == study.study_name?.let { checkFieldName(it) })

        }
    }

    //endregion
}