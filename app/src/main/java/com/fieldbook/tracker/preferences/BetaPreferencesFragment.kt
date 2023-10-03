package com.fieldbook.tracker.preferences

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.TextView
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity.TAG
import com.fieldbook.tracker.activities.PreferencesActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


class BetaPreferencesFragment : PreferenceFragmentCompat() {
    private val AUDIO_REQUEST_CODE = 1
    private var selectedAudioFileUri: String? = null

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

    fun getAbsolutePathFromUri(context: Context, uri: Uri): String? {
        var filePath: String? = null
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)

        try {
            cursor?.let {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    filePath = it.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("getAbsolutePathFromUri", "Error: ${e.message}")
        } finally {
            cursor?.close()
        }

        return filePath
    }

    private fun getFileType(uri: Uri): String? {
        val r = requireContext().contentResolver
        val mimeTypeMap = MimeTypeMap.getSingleton()
        return mimeTypeMap.getExtensionFromMimeType(r.getType(uri))
    }

    @Throws(IOException::class)
    private fun copyInputStreamToFile(inputStream: InputStream, file: File) {
        try {
            FileOutputStream(file, false).use { outputStream ->
                var read: Int
                val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
                while (inputStream.read(bytes).also { read = it } != -1) {
                    outputStream.write(bytes, 0, read)
                }
            }
        }catch (e: IOException){
            Log.e("Failed to load file: ", e.message.toString())
        }
    }

    @Throws(IOException::class)
    fun readBytes(inputStream: InputStream): ByteArray {
        val bufferSize = 1024
        val buffer = ByteArray(bufferSize)
        val outputStream = ByteArrayOutputStream()

        var bytesRead: Int
        while (inputStream.read(buffer, 0, bufferSize).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }

        return outputStream.toByteArray()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == AUDIO_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { selectedFileUri ->
                val uri: Uri? = data?.data
                val src = uri?.path

//                this.selectedAudioFileUri = selectedFileUri.path
                this.selectedAudioFileUri = src
                Log.d(TAG, "onActivityResult: $selectedFileUri $src")


                val selectedFilePath = getAbsolutePathFromUri(requireContext(), selectedFileUri)
                Log.d(TAG, "onActivityResult: $selectedFilePath")
                val selectedFile = File(selectedFileUri.path)
                val fileName = selectedFile.name
                Log.d("FilePicker", "File name: $fileName")


                val inputStream: InputStream = requireContext().contentResolver.openInputStream(selectedFileUri)!!
                val audioData = ByteArray(inputStream.available())
                inputStream.read(audioData)
                inputStream.close()
                Log.d(TAG, "uploadDialog: ${audioData[0]}")

                // Update the TextView with the selected file's name
                val audioFileName = view?.findViewById<TextView>(R.id.audioFileName)
                audioFileName?.text = fileName
                audioFileName?.visibility = View.VISIBLE

            }
        }
    }

    private fun uploadDialog() {
        val context = requireContext()
        val inflater = this.layoutInflater
        val layout = inflater.inflate(R.layout.dialog_upload_files, null)
        var pickAudioButton = layout.findViewById<Button>(R.id.pickAudioButton)

        pickAudioButton.setOnClickListener{
            // Launch the file picker
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*" // You can specify a MIME type here, e.g., "audio/*"
            startActivityForResult(intent, AUDIO_REQUEST_CODE)
        }
        val builder = androidx.appcompat.app.AlertDialog.Builder(context, R.style.AppAlertDialog)

        builder.setTitle(R.string.import_dialog_title_fields)
            .setCancelable(true)
            .setView(layout)

        // when dialog is dismissed, clear the variables
        val dialog = builder.create()
        dialog.setOnDismissListener {
            selectedAudioFileUri = null
        }

        builder.setPositiveButton(
            getString(R.string.dialog_import)
        ) { dialogInterface: DialogInterface?, i: Int ->
            if (selectedAudioFileUri != null) {
                Log.d(TAG, "uploadDialog: KHALI NAHI H")
//                val apiUrl = "http://192.168.226.119:3000/brapi/v2/images"
                // Create the JSON request body
                val requestBody = """
                {
                    "additionalInfo": {"media": "audio", "fieldId": 167},
                    "imageFileName": "majorlazer.mp3",
                    "imageTimeStamp": "2018-01-01T14:47:23-0600"
                }
            """.trimIndent()


                //Get file extension e.g mp4, flv, pdf...
                val fileType = getFileType(Uri.parse(selectedAudioFileUri))

                //Temporary file to hold content of actual file
                val file = File.createTempFile("vid", fileType)

                //Copy content from actual file to Temporary file using Input Stream
                copyInputStreamToFile(inputStream = requireContext().contentResolver.openInputStream(Uri.parse(selectedAudioFileUri))!!, file = file)

                val fileInputStream = FileInputStream(file)
                val fileBytes = readBytes(fileInputStream)
                fileInputStream.close()

                Log.d(TAG, "onActivityResult: $file $fileBytes")



                val firstApiCallTask = FirstApiCallTask(requireActivity(), selectedAudioFileUri!!, requestBody, fileBytes)
                firstApiCallTask.execute()

            }else{
                Log.d(TAG, "uploadDialog: KHALI H")
            }
        }
        builder.show()
    }

}


class FirstApiCallTask(
    private val context: Context,
    private val selectedAudioFileUri: String,
    private val requestBody: String,
    private val fileBytes: ByteArray
) : AsyncTask<Void, Void, String>() {
    override fun doInBackground(vararg params: Void?): String {
        Log.d(TAG, "doInBackground: Making First CALL")
//         Define the API endpoint URL
        val apiUrl = "http://192.168.15.119:3000/brapi/v2/images" // Replace with your PC's IP address or hostname

        try {
            // Create a URL object
            val url = URL(apiUrl)

            // Open a connection to the URL
            val connection = url.openConnection() as HttpURLConnection

            // Set the HTTP request method (e.g., POST, GET)
            connection.requestMethod = "POST"

            // Set request headers (if needed)
            connection.setRequestProperty("Content-Type", "application/json")

            // Enable input and output streams
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
                return "Error: $responseCode"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "doInBackground Error: ${e.message}"
        }
    }

    override fun onPostExecute(result: String?) {
        // Handle the API response in the UI thread
        if (result != null) {
            // Process the API response here
            println(result)

            val jsonResponse = JSONObject(result)
            val imageDbId = jsonResponse.getJSONObject("result")
                .getJSONArray("data")
                .getJSONObject(0)
                .getInt("imageDbId")

            Log.d(TAG, "onPostExecute: ${imageDbId}")

            var secondApiCallTask = SecondApiCallTask(context, selectedAudioFileUri, imageDbId.toString(),fileBytes)
            secondApiCallTask.execute()
        }
    }
}

class SecondApiCallTask(
    private val context: Context,
    private val selectedAudioFileUri: String,
    private val imageId: String,
    private val fileBytes: ByteArray
) : AsyncTask<Void, Void, String>() {
    override fun doInBackground(vararg params: Void?): String {
        Log.d(TAG, "doInBackground: Making Second Call")
//         Define the API endpoint URL
        val apiUrl = "http://192.168.15.119:3000/brapi/v2/images/" + imageId + "/imageContent" // Replace with your PC's IP address or hostname

        try {
            // Create a URL object
            val url = URL(apiUrl)

            val file = File(selectedAudioFileUri)
            val filename: String = file.name
            Log.d(TAG, "doInBackground: ${GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY}")
            Log.d(TAG, "doInBackground: ${Environment.getExternalStorageDirectory().absolutePath + "/Temp/plot_data/field_sample/field_audio/" + filename}")
//            val destination =
//                File(Environment.getExternalStorageDirectory().absolutePath + "/Temp/plot_data/field_sample/field_audio/" + filename)

            val destination = Environment.getExternalStorageDirectory().absolutePath + "/Temp/plot_data/field_sample/field_audio/" + filename

            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(Uri.parse(destination), "r", null)

//            parcelFileDescriptor?.let {
//                val inputStream = FileInputStream(parcelFileDescriptor.fileDescriptor)
////                val file = File(context.cacheDir, filename)
////
////                val outputStream = FileOutputStream(file)
////                IOUtils.copy(inputStream, outputStream)
////
////                val fileInputStream = FileInputStream(file)
////                val fileBytes = readBytes(fileInputStream)
////                fileInputStream.close()
//
//                // Open a connection to the URL
//
//            }
            val connection = url.openConnection() as HttpURLConnection

            // Set the HTTP request method (e.g., POST, GET)
            connection.requestMethod = "PUT"

            // Set the appropriate headers for binary data
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("Content-Length", fileBytes.size.toString())


            // Write the byte array to the output stream
            val outputStream1 = connection.outputStream
            outputStream1.write(fileBytes)
            outputStream1.close()

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
                return "Error: $responseCode"
            }



        } catch (e: Exception) {
            e.printStackTrace()
            return "doInBackground Error: ${e.message}"
        }
    }

    override fun onPostExecute(result: String?) {
        // Handle the API response in the UI thread
        if (result != null) {
            // Process the API response here
            println(result)

//            val jsonResponse = JSONObject(result)
//            val imageDbId = jsonResponse.getJSONObject("result")
//                .getJSONArray("data")
//                .getJSONObject(0)
//                .getInt("imageDbId")

            Log.d(TAG, "onPostExecute: inside second")

        }
    }

    @Throws(IOException::class)
    fun readBytes(inputStream: InputStream): ByteArray {
        val bufferSize = 1024
        val buffer = ByteArray(bufferSize)
        val outputStream = ByteArrayOutputStream()

        var bytesRead: Int
        while (inputStream.read(buffer, 0, bufferSize).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }

        return outputStream.toByteArray()
    }
}

