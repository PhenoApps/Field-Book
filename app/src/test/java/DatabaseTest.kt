import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.Companion.sImageObservationView
import com.fieldbook.tracker.database.Migrator.Companion.sImageObservationViewName
import com.fieldbook.tracker.database.dao.StudyDao
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileOutputStream
import kotlin.time.ExperimentalTime

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

    override lateinit var mDataHelper: DataHelper

    /**
     * These variables are initialized in the setup function.
     * They are used as values that would be in a shared preferences instance.
     * S.a firstname: row, second: col, unique: plot
     *
     * These are also repopulated in switchField.
     *
     * TODO: check if this is now redundant, might be better to query the study table each time we need these
     */
    override lateinit var firstName: String
    override lateinit var secondName: String
    override lateinit var uniqueName: String

    private val databasePath: String = "database/sample.db"

    /**
     * Handles database loading, schema migration and test runs.
     * TODO: parameter currently unimplemented, use the global variable
     */
    override fun setupDatabase(path: String) {

        loadingSqlFile()

        mDataHelper = DataHelper(ApplicationProvider.getApplicationContext())

        mDataHelper.open()

    }

    /**
     * Copies the sample.db in the assets folder to the app database file.
     */
    private fun loadingSqlFile() {

        val context = ApplicationProvider.getApplicationContext<Context>()

        context.assets.open(databasePath).use { input ->

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

            checkSwitchField()

        }

        mDataHelper.close()
    }
}