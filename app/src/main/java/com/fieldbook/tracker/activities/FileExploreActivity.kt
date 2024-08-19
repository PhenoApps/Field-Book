package com.fieldbook.tracker.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phenoapps.utils.BaseDocumentTreeUtil
import org.phenoapps.utils.BaseDocumentTreeUtil.Companion.getStem
import java.util.Arrays
import java.util.Locale

class FileExploreActivity : ActivityDialog(), CoroutineScope by MainScope() {
    private var mainListView: ListView? = null
    private var progressBar: ProgressBar? = null

    // Stores names of traversed directories
    private val str = ArrayList<String?>()
    private val mComparator = java.util.Comparator { f1: DocumentFile, f2: DocumentFile ->
        if (f1.isDirectory && !f2.isDirectory) {
            // Directory before non-directory
            return@Comparator -1
        } else if (!f1.isDirectory && f2.isDirectory) {
            // Non-directory after directory
            return@Comparator 1
        } else {
            // Alphabetic order otherwise
            val f1Name: String = f1.uri.getStem(this)
            val f2Name: String = f2.uri.getStem(this)
            return@Comparator f1Name.compareTo(f2Name, ignoreCase = true)
        }
    }

    // Check if the first level of the directory structure is the one showing
    private var firstLvl = true
    private var fileList: Array<Item?>? = null
    private var chosenFile: String? = null
    private var adapter: ListAdapter? = null

    //updates whenever we traverse a directory, loads the current level's files
    private var path: DocumentFile? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent != null && intent.extras != null) {
            val data = intent.extras!!.getString("path")
            val include = intent.extras!!.getStringArray("include")
            val exclude = intent.extras!!.getStringArray("exclude")
            val title = intent.extras!!.getString("title")

            path = DocumentFile.fromTreeUri(this, Uri.parse(data))

            if (path != null) {
                mainListView = ListView(this)
                progressBar = ProgressBar(this)

                val constraintLayout = ConstraintLayout(this)

                constraintLayout.addView(mainListView, ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
                constraintLayout.addView(progressBar, ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)

                val pbSize = resources.getDimension(R.dimen.act_file_explorer_progress_bar_size).toInt()

                progressBar?.isIndeterminate = true
                progressBar?.layoutParams = ConstraintLayout.LayoutParams(pbSize, pbSize).also {
                    it.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    it.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    it.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    it.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                }

                val params = this.window.attributes
                params.width = LinearLayout.LayoutParams.MATCH_PARENT
                params.height = LinearLayout.LayoutParams.WRAP_CONTENT
                this.window.attributes = params

                setContentView(constraintLayout)

                loadFilesProgress(path, exclude, include)

                if (!title.isNullOrEmpty()) {
                    this.title = title
                }

                mainListView?.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, which: Int, _: Long ->
                        chosenFile = fileList?.get(which)?.file
                        //File sel = new File(path + "/" + chosenFile);
                        val file = path?.findFile(chosenFile!!)
                        if (file != null && file.exists() && file.isDirectory) {
                            firstLvl = false

                            // Adds chosen directory to list
                            str.add(chosenFile)
                            fileList = null
                            path = file

                            loadFilesProgress(file, exclude, include)

                            mainListView?.adapter = adapter
                        } else if (chosenFile.equals("up", ignoreCase = true)
                            && (file == null || !file.exists())
                        ) {
                            // present directory removed from list

                            val s = str.removeAt(str.size - 1)

                            // path modified to exclude present directory
                            path = path?.parentFile

                            fileList = null

                            // if there are no more directories in the list, then
                            // its the first level
                            if (str.isEmpty()) {
                                firstLvl = true
                            }
                            loadFilesProgress(path, exclude, include)
                            mainListView?.adapter = adapter
                        } else {
                            try {
                                if (file != null && file.exists()) {
                                    val returnIntent = Intent()
                                    returnIntent.putExtra(EXTRA_RESULT_KEY, file.uri.toString())
                                    setResult(RESULT_OK, returnIntent)
                                    finish()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
            }
        } else {
            setResult(RESULT_CANCELED)

            finish()
        }
    }

    private fun loadFilesProgress(path: DocumentFile?, exclude: Array<String>? = null, include: Array<String>? = null) {

        progressBar?.visibility = View.VISIBLE
        mainListView?.visibility = View.INVISIBLE

        launch(Dispatchers.IO) {
            try {
                loadFileList(path, exclude, include)
            } catch (e: NullPointerException) {
                setResult(RESULT_CANCELED)
                finish()
            }

            withContext(Dispatchers.Main) {
                progressBar?.visibility = View.GONE
                mainListView?.visibility = View.VISIBLE
                mainListView?.adapter = adapter
            }
        }
    }

    private fun loadFileList(
        path: DocumentFile?,
        exclude: Array<String>?,
        include: Array<String>?
    ) {
        // Checks whether path exists

        if (path != null && path.exists()) {
            //list all files in the passed directory

            val files = path.listFiles()

            //created an 'accepted' list of the files that do not have the excluded (param) extension
            //and do contain the included extensions
            val accepted = ArrayList<DocumentFile>()

            //populate exclude/include lists before iteration intent extra is optional and can be null
            val excludedExtensions = if (exclude == null) ArrayList()
            else listOf(*exclude)
            val includedExtensions = if (include == null) ArrayList()
            else listOf(*include)

            for (file in files) {
                val name = file.name
                if (name != null) {
                    // Skip asset files
                    if (name.contains(".fieldbook") || name.contains("severity.txt") || name.contains(
                            "sharedpref.xml"
                        )
                    ) {
                        continue
                    }

                    if (excludedExtensions.contains(checkExtension(name))) continue

                    if (includedExtensions.contains(checkExtension(name)) || file.isDirectory) {
                        accepted.add(file)
                        continue
                    }
                }
            }

            Arrays.sort(files, mComparator)

            // collect file names
            val fileNameList = arrayOfNulls<String>(accepted.size)
            for (file in accepted) {
                fileNameList[accepted.indexOf(file)] = file.name
            }

            // create file list for
            fileList = arrayOfNulls(fileNameList.size)

            for (i in fileNameList.indices) {
                fileList!![i] = Item(
                    fileNameList[i]!!, R.drawable.ic_file_generic
                )

                val file = path.findFile(fileNameList[i]!!)

                if (file != null && file.exists()) {
                    val name = file.name

                    if (name != null) {
                        // Set drawables

                        if (file.isDirectory) {
                            fileList!![i]!!.icon = R.drawable.ic_file_directory
                            Log.d("DIRECTORY", fileList!![i]!!.file)
                        }
                        if (name.lowercase(Locale.getDefault()).contains(".csv")) {
                            fileList!![i]!!.icon = R.drawable.ic_file_csv
                        }

                        if (name.contains(".xls")) {
                            fileList!![i]!!.icon = R.drawable.ic_file_xls
                        } else {
                            Log.d("FILE", fileList!![i]!!.file)
                        }
                    }
                }
            }

            if (!firstLvl) {
                val temp = arrayOfNulls<Item>(
                    fileList!!.size + 1
                )
                System.arraycopy(fileList, 0, temp, 1, fileList!!.size)
                temp[0] = Item("Up", R.drawable.ic_file_up_dir)
                fileList = temp
            }
        }

        adapter = object : ArrayAdapter<Item?>(
            this,
            R.layout.custom_dialog_item_select, android.R.id.text1,
            fileList!!
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                // creates view
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)

                fileList?.get(position)?.let {
                    // put the image on the text view
                    textView.setCompoundDrawablesWithIntrinsicBounds(
                        it.icon, 0, 0, 0
                    )
                }


                // add margin between image and text (support various screen
                // densities)
                val dp5 = (5 * resources.displayMetrics.density + 0.5f).toInt()
                textView.compoundDrawablePadding = dp5

                return view
            }
        }
    }

    private fun checkExtension(name: String): String {
        var extension = ""

        val i = name.lastIndexOf('.')
        if (i > 0) {
            extension = name.substring(i + 1)
        }
        return extension.lowercase(Locale.getDefault())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }

        return super.onOptionsItemSelected(item)
    }

    // Wrapper class to hold file data
    private class Item(var file: String, var icon: Int) {
        override fun toString(): String {
            return file
        }
    }

    companion object {
        const val EXTRA_RESULT_KEY: String =
            "com.fieldbook.tracker.activities.FieldEditorActivity.extras.RESULT"
    }
}