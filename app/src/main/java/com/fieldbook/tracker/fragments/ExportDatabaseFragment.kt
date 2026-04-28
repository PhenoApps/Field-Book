package com.fieldbook.tracker.fragments

import android.app.ProgressDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Html
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.utilities.ZipUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.io.File
import java.io.IOException
import java.io.ObjectOutputStream
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class ExportDatabaseFragment : Fragment() {

    companion object {
        const val TAG = "ExportDatabaseFragment"
        const val EXTRA_FILE_NAME = "file_name"
    }

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var preferences: SharedPreferences

    private var dialog: ProgressDialog? = null

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

        val fileName = arguments?.getString(EXTRA_FILE_NAME, null)

        scope.launch {

            withContext(Dispatchers.Main) {

                showProgressBarLoadingDialog()

            }

            runCatching {

                exportDatabase(fileName!!)

            }.onSuccess {

                onCompleted(getString(R.string.export_complete))

            }.onFailure {

                onCompleted(getString(R.string.export_error_general))
            }
        }
    }

    private suspend fun onCompleted(message: String) {

        withContext(Dispatchers.Main) {

            dialog?.dismiss()

            Utils.makeToast(context, message)

        }
    }

    private fun exportDatabase(fileName: String) {
        val dbPath = DataHelper.getDatabasePath(context)
        val databaseDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_database)
        val zipFile = databaseDir!!.createFile("*/*", "$fileName.zip")
        val tempName = UUID.randomUUID().toString()
        val tempOutput = databaseDir.createFile("*/*", tempName)
        val tempStream = BaseDocumentTreeUtil.getFileOutputStream(context, R.string.dir_database, tempName)
        val objectStream = ObjectOutputStream(tempStream)
        val zipOutput = context?.contentResolver?.openOutputStream(zipFile!!.uri)
        objectStream.writeObject(preferences.all)
        objectStream.close()
        tempStream?.close()
        ZipUtil.Companion.zip(
            requireContext(),
            arrayOf(DocumentFile.fromFile(File(dbPath)), tempOutput!!),
            zipOutput
        )
        FileUtil.shareFile(context, preferences, zipFile)
        if (!tempOutput.delete()) {
            throw IOException()
        }
    }

    private fun showProgressBarLoadingDialog() {
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
}