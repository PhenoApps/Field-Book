package com.fieldbook.tracker.fragments

import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import android.text.Html
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.FieldSwitchImpl
import com.fieldbook.tracker.utilities.Utils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phenoapps.utils.BaseDocumentTreeUtil
import javax.inject.Inject

@AndroidEntryPoint
class ImportDBFragment : Fragment(){

    @Inject
    lateinit var database: DataHelper

    private var dialog: ProgressDialog? = null
    private var fail: Boolean = false

    //coroutine scope for launching background process
    private val scope by lazy {
        CoroutineScope(Dispatchers.IO)
    }


    private var mContext: Context? = null
    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }

    override fun onDetach() {
        super.onDetach()
        mContext = null
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scope.launch {

            withContext(Dispatchers.Main){
                showImportDialog()
            }

            // Load database with sample data
            try {
                val sampleDatabase = BaseDocumentTreeUtil.getFile(
                    mContext,
                    R.string.dir_database, "sample_db.zip"
                )
                if (sampleDatabase != null && sampleDatabase.exists()) {
                    // database import might take some time
                    withContext(Dispatchers.IO){
                        invokeDBImport(sampleDatabase)
                    }
                }
            } catch (e: Exception){
                Log.d("Database", e.toString())
                e.printStackTrace()
                fail = true
            }

            withContext(Dispatchers.Main){

                dialog?.dismiss()
                if (fail) {
                    Utils.makeToast(mContext, context?.getString(R.string.import_error_general))
                }

                // reset the preference value
                val prefs = mContext?.let { PreferenceManager.getDefaultSharedPreferences(it) }
                prefs?.edit()?.putBoolean(GeneralKeys.LOAD_SAMPLE_DATA, false)?.apply()
                val temp = prefs?.getBoolean(GeneralKeys.LOAD_SAMPLE_DATA,true)

                try {
                    selectFirstField()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }

        }
    }

    private fun invokeDBImport(dbFile: DocumentFile) {
        database.open()
        database.importDatabase(dbFile)
    }

    private fun showImportDialog() {
        // show only if the fragment is added to the activity
        if (isAdded) {
            dialog = ProgressDialog(mContext, R.style.AppAlertDialog)
            dialog?.isIndeterminate = true
            dialog?.setCancelable(false)
            dialog?.setMessage(
                Html
                    .fromHtml(context?.getString(R.string.import_dialog_importing))
            )
            dialog?.show()
        }
    }

    /**
     * Queries the database for saved studies and calls switch field for the first one.
     */
    private fun selectFirstField() {
        try {
            val fs = database.getAllFieldObjects().toTypedArray<FieldObject>()
            if (fs.isNotEmpty()) {
                switchField(fs[0].studyId)
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Calls database switch field on the given studyId.
     *
     * @param studyId the study id to switch to
     */
    private fun switchField(studyId: Int) {
        val fieldSwitcher = mContext?.let { FieldSwitchImpl(it) }
        fieldSwitcher?.switchField(studyId)
    }

}