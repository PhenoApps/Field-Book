package com.fieldbook.tracker.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
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
import java.util.Locale

class FileExploreActivity : ActivityDialog(), CoroutineScope by MainScope() {

    companion object {
        const val EXTRA_RESULT_KEY: String =
            "com.fieldbook.tracker.activities.FieldEditorActivity.extras.RESULT"

        fun getIntent(context: Context): Intent = Intent(context, FileExploreActivity::class.java)
    }

    private var mainListView: ListView? = null
    private var progressBar: ProgressBar? = null

    // Stores names of traversed directories
    private val str = ArrayList<String?>()

    // Check if the first level of the directory structure is the one showing
    private var firstLvl = true
    private var fileList = mutableListOf<Item>()
    private var chosenFile: String? = null

    //updates whenever we traverse a directory, loads the current level's files
    private var path: DocumentFile? = null

    // Wrapper class to hold file data
    private class Item(var file: String, var isDir: Boolean, var icon: Int) {
        override fun toString(): String {
            return file
        }
    }

    private val mComparator = java.util.Comparator { f1: Item, f2: Item ->
        if (f1.isDir && !f2.isDir) {
            // Directory before non-directory
            return@Comparator -1
        } else if (!f1.isDir && f2.isDir) {
            // Non-directory after directory
            return@Comparator 1
        } else {
            // Alphabetic order otherwise
            return@Comparator f1.file.compareTo(f2.file, ignoreCase = true)
        }
    }

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

                constraintLayout.addView(
                    mainListView,
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.MATCH_PARENT
                )
                constraintLayout.addView(
                    progressBar,
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.MATCH_PARENT
                )

                val pbSize =
                    resources.getDimension(R.dimen.act_file_explorer_progress_bar_size).toInt()

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

                        launch(Dispatchers.IO) {

                            chosenFile = fileList[which].file
                            //File sel = new File(path + "/" + chosenFile);
                            val file = path?.findFile(chosenFile!!)
                            if (file != null && file.exists() && file.isDirectory) {
                                firstLvl = false

                                // Adds chosen directory to list
                                str.add(chosenFile)
                                fileList.clear()
                                path = file

                                withContext(Dispatchers.Main) {
                                    loadFilesProgress(file, exclude, include)
                                }

                            } else if (chosenFile.equals(getString(R.string.activity_file_explorer_up_directory_name))
                                && (file == null || !file.exists())
                            ) {
                                // present directory removed from list
                                str.removeAt(str.size - 1)

                                // path modified to exclude present directory
                                path = path?.parentFile

                                fileList.clear()

                                // if there are no more directories in the list, then
                                // its the first level
                                if (str.isEmpty()) {
                                    firstLvl = true
                                }

                                withContext(Dispatchers.Main) {
                                    loadFilesProgress(path, exclude, include)
                                }

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
            }

        } else {

            setResult(RESULT_CANCELED)

            finish()
        }
    }

    private fun updateAdapter() {
        mainListView?.adapter = object : ArrayAdapter<Item?>(
            this,
            R.layout.custom_dialog_item_select, android.R.id.text1,
            fileList.toTypedArray()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                // creates view
                val view = super.getView(position, convertView, parent)

                if (fileList.isNotEmpty()) {

                    view.findViewById<TextView>(android.R.id.text1).also { tv ->

                        // put the image on the text view
                        tv.setCompoundDrawablesWithIntrinsicBounds(
                            fileList[position].icon, 0, 0, 0
                        )

                        // add margin between image and text (support various screen
                        // densities)
                        val dp5 = (5 * resources.displayMetrics.density + 0.5f).toInt()
                        tv.compoundDrawablePadding = dp5
                    }
                }

                return view
            }
        }
    }

    private fun loadFilesProgress(
        path: DocumentFile?,
        exclude: Array<String>? = null,
        include: Array<String>? = null
    ) {

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
                updateAdapter()
            }
        }
    }

    private fun loadFileList(
        path: DocumentFile?,
        exclude: Array<String>?,
        include: Array<String>?
    ) {
        if (path != null && path.exists()) {

            //list all files in the passed directory
            val files = path.listFiles()

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

                    // Include files only if the extension is in the `include` list or no `include` list is provided
                    if (includedExtensions.isEmpty() || includedExtensions.contains(checkExtension(name)) || file.isDirectory) {
                        val nextIndex = fileList.map { it.file }.binarySearch(name)
                        val index = if (nextIndex >= 0) nextIndex else -(nextIndex + 1)

                        val uiItem = Item(name, file.isDirectory, R.drawable.ic_file_generic)

                        if (file.exists()) {

                            // Set drawables
                            if (file.isDirectory) {
                                uiItem.icon = R.drawable.ic_file_directory
                            } else if (name.lowercase(Locale.getDefault()).contains(".csv")) {
                                uiItem.icon = R.drawable.ic_file_csv
                            } else if (name.contains(".xls")) {
                                uiItem.icon = R.drawable.ic_file_xls
                            }

                            fileList.add(index, uiItem)

                        }

                        continue
                    }
                }
            }

            fileList.sortWith(mComparator)

            if (!firstLvl) {
                fileList.add(
                    0,
                    Item(
                        getString(R.string.activity_file_explorer_up_directory_name),
                        true,
                        R.drawable.ic_file_up_dir
                    )
                )
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
}