package com.fieldbook.tracker.preferences

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity.TAG
import com.fieldbook.tracker.activities.PreferencesActivity
import com.serenegiant.utils.UIThreadHelper.runOnUiThread
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


class BetaPreferencesFragment : PreferenceFragmentCompat() {
    private val AUDIO_REQUEST_CODE = 1
    private var selectedAudioFileSrc: String? = null
    private var selectedAudioFileUri: Uri? = null

    private var uploadLayout: View? = null
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.preferences_beta, rootKey)

        (this.activity as PreferencesActivity?)?.supportActionBar?.title =
            getString(R.string.preferences_beta_title)

        val pref = findPreference<CheckBoxPreference>(GeneralKeys.REPEATED_VALUES_PREFERENCE_KEY)
        pref?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue == false) {

                if (isAdded) {
                    AlertDialog.Builder(context)
                        .setTitle(getString(R.string.pref_beta_repeated_values_disabled_title))
                        .setMessage(getString(R.string.pref_beta_repeated_values_disabled_message))
                        .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }

            true
        }

        val uploadPref = findPreference<Preference>("pref_beta_upload_files")
        uploadPref?.setOnPreferenceClickListener {
            uploadDialog()
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == AUDIO_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { selectedFileUri ->

                val uri: Uri? = data?.data
                val src = uri?.path

                this.selectedAudioFileSrc = src
                this.selectedAudioFileUri = uri

                val selectedFile = selectedAudioFileUri?.path?.let { File(it) }
                val fileName = selectedFile?.name

                // Update the TextView with the selected file's name
                val audioFileName = uploadLayout?.findViewById<TextView>(R.id.audioFileName)
                audioFileName?.text = fileName
            }
        }
    }

    private fun uploadDialog() {
        val context = requireContext()
        val inflater = this.layoutInflater
        uploadLayout = inflater.inflate(R.layout.dialog_upload_files, null)
        var pickAudioButton = uploadLayout?.findViewById<Button>(R.id.pickAudioButton)

        // when the dialog is opened, clear the variables
        selectedAudioFileSrc = null
        selectedAudioFileUri = null

        pickAudioButton?.setOnClickListener {
            // Launch the file picker
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*" // You can specify a MIME type here, e.g., "audio/*"
            startActivityForResult(intent, AUDIO_REQUEST_CODE)
        }
        val builder = androidx.appcompat.app.AlertDialog.Builder(context, R.style.AppAlertDialog)

        builder.setTitle("Upload File")
            .setCancelable(true)
            .setView(uploadLayout)

        val dialog = builder.create()
        dialog.setOnDismissListener {
            selectedAudioFileSrc = null
            selectedAudioFileUri = null
        }
        
        val uploadFileButton = uploadLayout?.findViewById<Button>(R.id.uploadFileButton)
        val uploadStatusMessage = uploadLayout?.findViewById<TextView>(R.id.uploadStatusMessage)

        uploadFileButton?.setOnClickListener{
            if (selectedAudioFileUri != null) {
                Log.d(TAG, "uploadDialog: KHALI NAHI H")
//                val apiUrl = "http://192.168.226.119:3000/brapi/v2/images"

//                val firstApiCallTask =
//                    FirstApiCallTask(requireActivity(), selectedAudioFileUri!!, requestBody)
//                firstApiCallTask.execute()

                firstCall().start()

            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Attach a file", Toast.LENGTH_LONG).show()
                }
                updateUI("Attach a file")
            }
        }

//        builder.setPositiveButton(
//            getString(R.string.traits_dialog_export)
//        ) { _: DialogInterface?, _: Int ->
//            if (selectedAudioFileSrc != null) {
//                Log.d(TAG, "uploadDialog: KHALI NAHI H")
////                val apiUrl = "http://192.168.226.119:3000/brapi/v2/images"
//                val selectedFile = selectedAudioFileUri?.path?.let { File(it) }
//                val fileName = selectedFile?.name
//                // Create the JSON request body
//                val requestBody = """
//                {
//                    "additionalInfo": {"media": "audio", "fieldId": "167"},
//                    "imageFileName": "$fileName",
//                    "imageTimeStamp": "2018-01-01T14:47:23-0600"
//                }
//            """.trimIndent()
//
//                val firstApiCallTask =
//                    FirstApiCallTask(requireActivity(), selectedAudioFileUri!!, requestBody)
//                firstApiCallTask.execute()
//
//            } else {
//                Handler(Looper.getMainLooper()).post {
//                    Toast.makeText(context, "Attach a file", Toast.LENGTH_LONG).show()
//                }
//            }
//        }
        builder.show()
    }


    private fun updateUI(statusMessage: String){
        runOnUiThread{
            kotlin.run {
                val uploadStatusMessage = uploadLayout?.findViewById<TextView>(R.id.uploadStatusMessage)
                uploadStatusMessage?.text = statusMessage
            }
        }
    }

    private fun firstCall() : Thread{
        return Thread{
            Log.d(TAG, "doInBackground: Making First CALL")
            val apiUrl = "http://192.168.15.119:3000/brapi/v2/images"


            val selectedFile = selectedAudioFileUri?.path?.let { File(it) }
            val fileName = selectedFile?.name
            // Create the JSON request body
            val requestBody = """
                {
                    "additionalInfo": {"media": "audio", "fieldId": "167"},
                    "imageFileName": "$fileName",
                    "imageTimeStamp": "2018-01-01T14:47:23-0600"
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
                    Log.d(TAG, "doInBackground: Success")

                    val jsonResponse = JSONObject(response.toString())
                    val imageDbId = jsonResponse.getJSONObject("result")
                        .getJSONArray("data")
                        .getJSONObject(0)
                        .getInt("imageDbId")

                    Log.d(TAG, "onPostExecute: ${imageDbId}")

                    secondCall(imageDbId.toString()).start()
                } else {
                    // Handle the error response (e.g., non-200 status code)
                    Log.d(TAG, "doInBackground: Some Error")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Uploaded Unsuccessful", Toast.LENGTH_LONG).show()
                    }
                    updateUI("First Call Error: $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Uploaded Unsuccessful: ${e.message}", Toast.LENGTH_LONG).show()
                }
                updateUI("First Call Error: ${e.message}")
            }
        }
    }

    private fun secondCall(imageId: String): Thread {
        return Thread{
            Log.d(TAG, "doInBackground: Making Second Call")
//         Define the API endpoint URL
            val apiUrl =
                "http://192.168.15.119:3000/brapi/v2/images/" + imageId + "/imageContent" // Replace with your PC's IP address or hostname

            try {
                val url = URL(apiUrl)
                /*
                 * Get the content resolver instance for this context, and use it
                 * to get a ParcelFileDescriptor for the file.
                 */
                val mInputPFD =
                    selectedAudioFileUri?.let { context?.contentResolver?.openFileDescriptor(it, "r") }
                // Get a regular file descriptor for the file
                val fd: FileDescriptor = mInputPFD!!.fileDescriptor
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
                    outputStream1.write(buffer, 0, bytesRead);
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
                    Toast.makeText(context, "Uploaded Unsuccessful: ${e.message}", Toast.LENGTH_LONG).show()
                }
                updateUI("Second Call Error: ${e.message}")
            }
        }
    }

}


class FirstApiCallTask(
    private val context: Context,
    private val selectedAudioFileUri: Uri,
    private val requestBody: String
) : AsyncTask<Void, Void, String>() {
    override fun doInBackground(vararg params: Void?): String {
        Log.d(TAG, "doInBackground: Making First CALL")
        val apiUrl = "http://192.168.15.119:3000/brapi/v2/images"

        try {
            val url = URL(apiUrl)

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")

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
                Log.d(TAG, "doInBackground: Success")


                return response.toString()
            } else {
                // Handle the error response (e.g., non-200 status code)
                Log.d(TAG, "doInBackground: Some Error")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Uploaded Unsuccessful", Toast.LENGTH_LONG).show()
                }
                return "Error: $responseCode"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Uploaded Unsuccessful: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return "doInBackground Error: ${e.message}"
        }
    }

    override fun onPostExecute(result: String?) {
        if (result != null) {
            println(result)

            val jsonResponse = JSONObject(result)
            val imageDbId = jsonResponse.getJSONObject("result")
                .getJSONArray("data")
                .getJSONObject(0)
                .getInt("imageDbId")

            Log.d(TAG, "onPostExecute: ${imageDbId}")

            var secondApiCallTask =
                SecondApiCallTask(context, selectedAudioFileUri, imageDbId.toString())
            secondApiCallTask.execute()
        }
    }
}

class SecondApiCallTask(
    private val context: Context,
    private val selectedAudioFileUri: Uri,
    private val imageId: String
) : AsyncTask<Void, Void, String>() {
    override fun doInBackground(vararg params: Void?): String {
        Log.d(TAG, "doInBackground: Making Second Call")
//         Define the API endpoint URL
        val apiUrl =
            "http://192.168.15.119:3000/brapi/v2/images/" + imageId + "/imageContent" // Replace with your PC's IP address or hostname

        try {
            val url = URL(apiUrl)
            /*
             * Get the content resolver instance for this context, and use it
             * to get a ParcelFileDescriptor for the file.
             */
            val mInputPFD =
                selectedAudioFileUri?.let { context.contentResolver.openFileDescriptor(it, "r") }
            // Get a regular file descriptor for the file
            val fd: FileDescriptor = mInputPFD!!.fileDescriptor
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
                outputStream1.write(buffer, 0, bytesRead);
            }
            outputStream1.close()

            val responseCode = connection.responseCode
            return if (responseCode == HttpURLConnection.HTTP_OK) {
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

                response.toString()
            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Uploaded Unsuccessful", Toast.LENGTH_LONG).show()
                }
                "Error: $responseCode"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Uploaded Unsuccessful: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return "doInBackground Error: ${e.message}"
        }
    }

    override fun onPostExecute(result: String?) {
        if (result != null) {
            println(result)
        }
    }
}

