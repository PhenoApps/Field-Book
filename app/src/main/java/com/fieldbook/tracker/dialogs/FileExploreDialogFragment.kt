package com.fieldbook.tracker.dialogs

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.dialogs.DialogTheme
import com.fieldbook.tracker.ui.dialogs.FileExplorerContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class FileExploreDialogFragment : DialogFragment() {

    private var onFileSelectedListener: ((Uri) -> Unit)? = null

    private var currentPath: DocumentFile? = null
    private var exclude: Array<String>? = null
    private var include: Array<String>? = null
    private val directoryStack = mutableListOf<String>()

    // check if the first level of the directory structure is the one showing
    private var isFirstLevel = true

    data class FileItem(
        val name: String,
        val isDirectory: Boolean,
        val icon: Int,
        val documentFile: DocumentFile?,
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        currentPath = arguments?.getString("path")?.let {
            DocumentFile.fromTreeUri(
                requireContext(),
                it.toUri()
            )
        }
        exclude = arguments?.getStringArray("exclude")
        include = arguments?.getStringArray("include")
        val dialogTitle = arguments?.getString("dialogTitle") ?: getString(R.string.file_explorer_select_file_title)

        val dialog = Dialog(requireContext(), R.style.AppAlertDialog)

        dialog.setContentView(ComposeView(requireContext()).apply {
            setContent {
                DialogTheme {
                    FileExplorerContent(
                        title = dialogTitle,
                        currentPath = currentPath,
                        loadFiles = ::loadFiles,
                        handleItemClick = ::handleItemClick,
                        cancelButtonText = getString(R.string.dialog_cancel),
                        onDismiss = { dismiss() }
                    )
                }
            }
        })

        return dialog
    }

    private fun loadFiles(onComplete: (List<FileItem>) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val files = mutableListOf<FileItem>()

            try {
                currentPath?.let { path ->
                    if (path.exists()) {
                        // list all the files in the current directory
                        val filesInDirectory = path.listFiles()

                        val excludedExtensions = exclude?.toList() ?: emptyList()
                        val includedExtensions = include?.toList() ?: emptyList()

                        for (file in filesInDirectory) {
                            val name = file.name ?: continue

                            // skip asset files
                            if (name.contains(".fieldbook") ||
                                name.contains("severity.txt") ||
                                name.contains("sharedpref.xml")) {
                                continue
                            }

                            val extension = getFileExtension(name)
                            if (extension in excludedExtensions) continue

                            // include files only if the extension is in the include list or no include list is provided
                            if (includedExtensions.isEmpty() ||
                                extension in includedExtensions ||
                                file.isDirectory) {

                                val nextIndex = files.map { it.name }.binarySearch(name)
                                val index = if (nextIndex >= 0) nextIndex else -(nextIndex + 1)

                                val icon = when {
                                    file.isDirectory -> R.drawable.ic_file_directory
                                    name.lowercase().contains(".csv") -> R.drawable.ic_file_csv
                                    name.contains(".xls") -> R.drawable.ic_file_xls
                                    else -> R.drawable.ic_file_generic
                                }

                                files.add(
                                    index,
                                    FileItem(
                                        name = name,
                                        isDirectory = file.isDirectory,
                                        icon = icon,
                                        documentFile = file
                                    )
                                )
                            }
                        }

                        // sorting: place directories first, then place files alphabetically
                        files.sortWith { f1, f2 ->
                            when {
                                f1.isDirectory && !f2.isDirectory -> -1
                                !f1.isDirectory && f2.isDirectory -> 1
                                else -> f1.name.compareTo(f2.name, ignoreCase = true)
                            }
                        }

                        // add "up" directory if not at first level
                        if (!isFirstLevel) {
                            files.add(
                                0,
                                FileItem(
                                    name = getString(R.string.activity_file_explorer_up_directory_name),
                                    isDirectory = true,
                                    icon = R.drawable.ic_file_up_dir,
                                    documentFile = null
                                )
                            )
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    onComplete(files)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(emptyList())
                }
            }
        }
    }

    private fun handleItemClick(item: FileItem, onComplete: (List<FileItem>) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            when {
                item.name == getString(R.string.activity_file_explorer_up_directory_name) -> {
                    // navigate up

                    if (directoryStack.isNotEmpty()) {
                        // remove present dir
                        directoryStack.removeAt(directoryStack.size - 1)
                    }

                    // path modified to exclude present directory
                    currentPath = currentPath?.parentFile

                    if (directoryStack.isEmpty()) {
                        isFirstLevel = true
                    }

                    withContext(Dispatchers.Main) {
                        loadFiles(onComplete)
                    }
                }
                item.isDirectory -> { // navigate into directory

                    item.documentFile?.let { file ->
                        if (file.exists() && file.isDirectory) {

                            isFirstLevel = false

                            directoryStack.add(item.name) // add dir to list

                            currentPath = file

                            withContext(Dispatchers.Main) {
                                loadFiles(onComplete)
                            }
                        }
                    }
                }
                else -> { // selecting a file
                    item.documentFile?.let { file ->
                        if (file.exists()) {
                            withContext(Dispatchers.Main) {
                                onFileSelectedListener?.invoke(file.uri)
                                dismiss()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getFileExtension(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i > 0) {
            name.substring(i + 1).lowercase(Locale.getDefault())
        } else {
            ""
        }
    }

    fun setOnFileSelectedListener(listener: (Uri) -> Unit) {
        onFileSelectedListener = listener
    }
}