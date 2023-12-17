package com.fieldbook.tracker.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.serenegiant.utils.UIThreadHelper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UploadFileDialog : DialogFragment() {
    private var selectedAudioFileSrc: String? = null
    private var selectedAudioFileUri: Uri? = null

    private var uploadLayout: View? = null
    private lateinit var audioPickerLauncher: ActivityResultLauncher<Intent>

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val inflater = this.layoutInflater
        uploadLayout = inflater.inflate(R.layout.dialog_upload_files, null) as ViewGroup
        val pickAudioButton = uploadLayout?.findViewById<Button>(R.id.pickAudioButton)

        // when the dialog is opened, clear the variables
        selectedAudioFileSrc = null
        selectedAudioFileUri = null

        setOnActivityResult()

        pickAudioButton?.setOnClickListener {
            // Launch the file picker
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*" // You can specify a MIME type here, e.g., "audio/*"
            audioPickerLauncher.launch(intent)
        }

        val builder = AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)

        builder.setTitle(getString(R.string.upload_file_title))?.setCancelable(true)
            ?.setView(uploadLayout)


        val uploadFileButton = uploadLayout?.findViewById<Button>(R.id.uploadFileButton)
        val uploadStatusMessage = uploadLayout?.findViewById<TextView>(R.id.uploadStatusMessage)

        uploadFileButton?.setOnClickListener {
            if (selectedAudioFileUri != null) {
                Log.d(CollectActivity.TAG, "uploadDialog: not empty")

                firstCall().start()

            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Attach a file", Toast.LENGTH_LONG).show()
                }
                updateUI("Attach a file")
            }
        }

//        builder.show()
        return builder.create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        selectedAudioFileSrc = null
        selectedAudioFileUri = null
    }

    private fun setOnActivityResult(){
        audioPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    val src = uri.path
                    selectedAudioFileSrc = src
                    selectedAudioFileUri = uri

                    val selectedFile = File(uri.path!!)
                    val fileName = selectedFile.name

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
                val uploadStatusMessage =
                    uploadLayout?.findViewById<TextView>(R.id.uploadStatusMessage)
                uploadStatusMessage?.text = statusMessage
            }
        }
    }

    private fun firstCall(): Thread {
        return Thread {
            Log.d(CollectActivity.TAG, "doInBackground: Making First CALL")
            val apiUrl = "http://192.168.151.120:3000/brapi/v2/images"

            val timeStamp = SimpleDateFormat(
                "yyyy-MM-dd hh:mm:ss", Locale.getDefault()
            ).format(Calendar.getInstance().time)
            Log.d(CollectActivity.TAG, "firstCall: $timeStamp")


            val selectedFile = selectedAudioFileUri?.path?.let { File(it) }
            val fileName = selectedFile?.name
            // Create the JSON request body
            val requestBody = """
                {
                    "additionalInfo": {"media": "audio", "fieldId": "167"},
                    "imageFileName": "$fileName",
                    "imageTimeStamp": "$timeStamp"
                }
            """.trimIndent()

            try {
                val url = URL(apiUrl)

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")

                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                connection.doInput = true
                connection.doOutput = true

                // Write the request body to the output stream
                val outputStream = DataOutputStream(connection.outputStream)
                outputStream.writeBytes(requestBody)
                outputStream.flush()
                outputStream.close()

                // Get the HTTP response code
                val responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // The request was successful, so read and return the response
                    val inputStream = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (inputStream.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    inputStream.close()
                    Log.d(CollectActivity.TAG, "doInBackground: Success")

                    val jsonResponse = JSONObject(response.toString())
                    val imageDbId =
                        jsonResponse.getJSONObject("result").getJSONArray("data").getJSONObject(0)
                            .getInt("imageDbId")

                    Log.d(CollectActivity.TAG, "onPostExecute: $imageDbId")

                    secondCall(imageDbId.toString()).start()
                } else {
                    // Handle the error response (e.g., non-200 status code)
                    Log.d(CollectActivity.TAG, "ERROR: Response code $responseCode")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Uploaded Unsuccessful", Toast.LENGTH_LONG).show()
                    }
                    updateUI("First Call Error: $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context, "Uploaded Unsuccessful: ${e.message}", Toast.LENGTH_LONG
                    ).show()
                }
                updateUI("First Call Error: ${e.message}")
            }
        }
    }

    private fun secondCall(imageId: String): Thread {
        return Thread {
            Log.d(CollectActivity.TAG, "doInBackground: Making Second Call")
//         Define the API endpoint URL
            val apiUrl =
                "http://192.168.151.120:3000/brapi/v2/images/$imageId/imageContent" // Replace with your PC's IP address or hostname

            try {
                val url = URL(apiUrl)/*
                 * Get the content resolver instance for this context, and use it
                 * to get a ParcelFileDescriptor for the file.
                 */
                val mInputPFD = selectedAudioFileUri?.let {
                    context?.contentResolver?.openFileDescriptor(
                        it, "r"
                    )
                }
                // Get a regular file descriptor for the file
                val fd: FileDescriptor? = mInputPFD?.fileDescriptor
                val inputStream = FileInputStream(fd)
                val buffer = ByteArray(1024) // Define a buffer for reading data
                var bytesRead: Int

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "PUT"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/octet-stream")

                // Write the byte array to the output stream
                val outputStream1 = connection.outputStream
                while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
                    outputStream1.write(buffer, 0, bytesRead)
                }
                outputStream1.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // The request was successful, so read and return the response
                    val inputStream = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (inputStream.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    inputStream.close()

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Uploaded Successfully", Toast.LENGTH_LONG).show()
                    }
//
//                response.toString()
                    updateUI("Uploaded successfully")
                } else {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Uploaded Unsuccessful", Toast.LENGTH_LONG).show()
                    }
                    updateUI("Error: $responseCode")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context, "Uploaded Unsuccessful: ${e.message}", Toast.LENGTH_LONG
                    ).show()
                }
                updateUI("Second Call Error: ${e.message}")
            }
        }
    }
}