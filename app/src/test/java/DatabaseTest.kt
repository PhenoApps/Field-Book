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
 * This file is for single database testing. Batched testing can be found in DatabaseBatchTest.kt
 * All tests are inherited into this file, they are effectively "hidden" but can be called within a test function.
 * A simple "individualTest" function was created as an example, which can call any test function.
 *
 * author: Chaney
 */
@Config(sdk = [Build.VERSION_CODES.O_MR1])
@RunWith(RobolectricTestRunner::class)
@ExperimentalTime
class DatabaseTest: DatabaseBatchTest() {

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

    private val databasePath: String = "database/samples/backup.db"

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
        }
    }

    /**
     * Copies the sample.db in the assets folder to the app database file.
     */
    private fun loadingSqlFile(path: String) {

        val context = ApplicationProvider.getApplicationContext<Context>()

//        context.assets.open("database/sample.db").use { input ->
        context.assets.open(path).use { input ->

            val size = input.available()

            val buffer = ByteArray(size)

//            println("Loading db filesize: $size B")

            FileOutputStream(context.getDatabasePath("fieldbook.db")).use {

                input.read(buffer)

                it.write(buffer)
            }

        }
    }

    /**
     * This function can be used to call individual tests.
     * All tests are available in DatabaseBatchTest, which are inherited in this class.
     */
    @Test
    fun individualTest() {

        setupDatabase(databasePath)

        if (::mDataHelper.isInitialized) {

            checkAllStudyTests()

        }

        mDataHelper.close()
    }
}