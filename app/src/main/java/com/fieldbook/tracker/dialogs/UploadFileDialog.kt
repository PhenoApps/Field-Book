package com.fieldbook.tracker.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.model.AdditionalInfo
import com.fieldbook.tracker.brapi.model.FileUploadRequest
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.preferences.GeneralKeys
import com.serenegiant.utils.UIThreadHelper
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.log

class UploadFileDialog : DialogFragment() {
    private var selectedAudioFileSrc: String? = null
    private var selectedAudioFileUri: Uri? = null

    private var uploadLayout: View? = null
    private var pickAudioButton: Button? = null
    private var uploadFileButton: Button? = null
    private var uploadStatusMessage: TextView? = null

    private lateinit var audioPickerLauncher: ActivityResultLauncher<Intent>

    private lateinit var workManager: WorkManager
    private lateinit var firstCallWork: OneTimeWorkRequest
    private lateinit var secondCallWork: OneTimeWorkRequest

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val context = requireContext()
        val layoutInflater = this.layoutInflater
        uploadLayout = layoutInflater.inflate(R.layout.dialog_upload_files, null)
        pickAudioButton = uploadLayout?.findViewById(R.id.pickAudioButton)

        // when the dialog is opened, clear the variables
        selectedAudioFileSrc = null
        selectedAudioFileUri = null

        setOnActivityResult()

        pickAudioButton?.setOnClickListener {
            // Launch the file picker
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            audioPickerLauncher.launch(intent)
        }

        val builder = AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)

        builder.setTitle(getString(R.string.upload_file_title))?.setCancelable(true)
            ?.setView(uploadLayout)


        uploadFileButton = uploadLayout?.findViewById(R.id.uploadFileButton)
        uploadStatusMessage = uploadLayout?.findViewById(R.id.uploadStatusMessage)

        workManager = WorkManager.getInstance(context)

        uploadFileButton?.setOnClickListener {
            if (selectedAudioFileUri != null) {
                val fieldId = PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

                // initialize workers
                firstCallWork = OneTimeWorkRequestBuilder<FirstCallWorker>()
                    .setInputData(
                        workDataOf(
                            "selectedAudioFileUri" to selectedAudioFileUri.toString(),
                            "fieldId" to fieldId
                        )
                    ).build()
//                secondCallWork = OneTimeWorkRequestBuilder<SecondCallWorker>()
//                    .setInputData(
//                        workDataOf(
//                            "selectedAudioFileUri" to selectedAudioFileUri.toString()
//                        )
//                    ).build()

                // enqueue worker
                workManager.enqueue(firstCallWork)

                // observe state of the workers and update the UI
                workManager.getWorkInfoByIdLiveData(firstCallWork.id)
                    .observe(viewLifecycleOwner) { workInfo ->

                        Log.d("TAG", "onCreateView:${workInfo.state} ")
                        when (workInfo.state) {

                            WorkInfo.State.FAILED -> {
                                pickAudioButton?.visibility = View.VISIBLE
                                uploadFileButton?.visibility = View.VISIBLE
                                val error =
                                    workInfo.outputData.getString("error") ?: "Unknown error"
                                updateUI(error)
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                pickAudioButton?.visibility = View.GONE
                                uploadFileButton?.visibility = View.GONE
                                updateUI("Success")
                            }
                            WorkInfo.State.RUNNING -> {
                                pickAudioButton?.visibility = View.GONE
                                uploadFileButton?.visibility = View.GONE
                                updateUI("Uploading file")
                            }

                            else -> {}
                        }
                    }
//                workManager.getWorkInfoByIdLiveData(secondCallWork.id)
//                    .observe(viewLifecycleOwner) { workInfo ->
//                        when (workInfo.state) {
//                            WorkInfo.State.FAILED -> {
//                                pickAudioButton?.visibility = View.VISIBLE
//                                uploadFileButton?.visibility = View.VISIBLE
//                                val error =
//                                    workInfo.outputData.getString("error") ?: "Unknown error"
//                                updateUI(error)
//                            }
//
//                            WorkInfo.State.SUCCEEDED -> {
//                                pickAudioButton?.visibility = View.GONE
//                                uploadFileButton?.visibility = View.GONE
//                                updateUI("Success")
//                            }
//
//                            WorkInfo.State.RUNNING -> {
//                                pickAudioButton?.visibility = View.GONE
//                                uploadFileButton?.visibility = View.GONE
//                                updateUI("Uploading file")
//                            }
//
//                            else -> {}
//                        }
//                    }
            } else {
                updateUI("Attach a file")
            }
        }

//        builder.show()
        return uploadLayout
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        selectedAudioFileSrc = null
        selectedAudioFileUri = null
    }

    private fun setOnActivityResult() {
        audioPickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        val src = uri.path
                        selectedAudioFileSrc = src
                        selectedAudioFileUri = uri

                        val selectedFile = uri.path?.let { File(it) }
                        val fileName = selectedFile?.name

                        // Update the TextView with the selected file's name
                        val audioFileName = uploadLayout?.findViewById<TextView>(R.id.audioFileName)
                        audioFileName?.text = fileName
                    }
                }
            }
    }

    private fun updateUI(statusMessage: String) {
        UIThreadHelper.runOnUiThread {
            kotlin.run {
                uploadStatusMessage?.text = statusMessage
                Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
            }
        }
    }
}

// FirstCallWorker.kt
class FirstCallWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {
    override fun doWork(): Result {
        try {
            val selectedAudioFileUriString = inputData.getString("selectedAudioFileUri")
            val selectedAudioFileUri = Uri.parse(selectedAudioFileUriString)
            val fieldId = inputData.getString("fieldId")

            Log.d("TAG", "doWork: $fieldId $selectedAudioFileUri")

            //        val apiUrl = "http://192.168.232.119:3000/brapi/v2/images"

            val brAPIService = BrAPIServiceFactory.getBrAPIService(applicationContext)

            val timeStamp = OffsetDateTime.now(ZoneId.systemDefault())

            val mInputPFD = selectedAudioFileUri?.let {
                applicationContext.contentResolver?.openFileDescriptor(
                    it, "r"
                )
            }
            val fd: FileDescriptor? = mInputPFD?.fileDescriptor
            val inputStream = FileInputStream(fd)
            val base64String = Base64.encodeToString(inputStream.readBytes(), Base64.NO_WRAP)

            val selectedFile = selectedAudioFileUri?.path?.let { File(it) }
            val fileName = selectedFile?.name

            var result: Result = Result.failure()

            if (fieldId != null) {
                val fileUploadRequest = FileUploadRequest(
                    AdditionalInfo("audio/mp4", fieldId),
                    fileName.toString(),
                    base64String,
                    timeStamp
                )
                brAPIService.postFileMetaData(fileUploadRequest,
                    { _ ->
                        result = Result.success()
                        null
                    },
                    { statusCode ->
                        Log.d("TAG", "doWork: $statusCode")
                        if (statusCode == 200)
                            result = Result.success()
                        else
                            result = Result.failure(workDataOf("error" to statusCode.toString()))
                        null
                    }
                )
            } else {
                result = Result.failure(workDataOf("error" to "Something went wrong"))
            }

            return  result

            // Create the JSON request body
            //        val requestBody = """
            //                {
            //                    "description": $base64String,
            //                    "additionalInfo": {"media": "audio", "fieldId": "167"},
            //                    "imageFileName": "$fileName",
            //                    "imageTimeStamp": "$timeStamp"
            //                }
            //            """.trimIndent()
            //
            //        try {
            //            val url = URL(apiUrl)
            //
            //            val connection = url.openConnection() as HttpURLConnection
            //            connection.requestMethod = "POST"
            //            connection.setRequestProperty("Content-Type", "application/json")
            //
            //            connection.connectTimeout = 10000
            //            connection.readTimeout = 10000
            //
            //            connection.doInput = true
            //            connection.doOutput = true
            //
            //            // Write the request body to the output stream
            //            val outputStream = DataOutputStream(connection.outputStream)
            //            outputStream.writeBytes(requestBody)
            //            outputStream.flush()
            //            outputStream.close()
            //
            //            // Get the HTTP response code
            //            val responseCode = connection.responseCode
            //
            //            connection.disconnect()
            //
            //            if (responseCode == HttpURLConnection.HTTP_OK) {
            //                val inputStream = BufferedReader(InputStreamReader(connection.inputStream))
            //                val response = StringBuilder()
            //                var line: String?
            //                while (inputStream.readLine().also { line = it } != null) {
            //                    response.append(line)
            //                }
            //                inputStream.close()
            //
            //                val jsonResponse = JSONObject(response.toString())
            //                val imageDbId =
            //                    jsonResponse.getJSONObject("result").getJSONArray("data").getJSONObject(0)
            //                        .getInt("imageDbId")
            //
            //                return Result.success(workDataOf("imageDbId" to imageDbId))
            //            } else {
            //                // Handle the error response (e.g., non-200 status code)
            //                val error = "Status code $responseCode"
            //                return Result.failure(workDataOf("error" to error))
            //            }

        } catch (e: Exception) {
            e.printStackTrace()

            Log.d("TAG", "doWork: STILL EXECUTING")
            return Result.failure(workDataOf("error" to e.message))
        }
    }
}

// SecondCallWorker.kt
//class SecondCallWorker(context: Context, workerParams: WorkerParameters) :
//    Worker(context, workerParams) {
//    override fun doWork(): Result {
//        val imageDbId = inputData.getInt("imageDbId", -1)
//        val selectedAudioFileUriString = inputData.getString("selectedAudioFileUri")
//        val selectedAudioFileUri = Uri.parse(selectedAudioFileUriString)
//
//        val apiUrl = "http://192.168.232.119:3000/brapi/v2/images/$imageDbId/imageContent"
//
//        try {
//            val url = URL(apiUrl)
//
//            val mInputPFD = selectedAudioFileUri?.let {
//                applicationContext.contentResolver?.openFileDescriptor(
//                    it, "r"
//                )
//            }
//            val fd: FileDescriptor? = mInputPFD?.fileDescriptor
//            val inputStream = FileInputStream(fd)
//            val buffer = ByteArray(1024) // Define a buffer for reading data
//            var bytesRead: Int
//
//            val connection = url.openConnection() as HttpURLConnection
//            connection.requestMethod = "PUT"
//            connection.doOutput = true
//            connection.connectTimeout = 30000
//            connection.readTimeout = 30000
//            connection.setRequestProperty("Content-Type", "application/octet-stream")
//
//            // write the byte array to the output stream
//            connection.outputStream.use { outputStream ->
//                while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
//                    outputStream.write(buffer, 0, bytesRead)
//                }
//            }
//            val responseCode = connection.responseCode
//
//            mInputPFD?.close()
//            inputStream.close()
//            connection.disconnect()
//
//            return if (responseCode == HttpURLConnection.HTTP_OK) {
//                Result.success()
//            } else {
//                val error = "Status code $responseCode"
//                Result.failure(workDataOf("error" to error))
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            return Result.failure(workDataOf("error" to e.message))
//        }
//    }
//}
