package com.fieldbook.tracker.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.ExportStatusAdapter
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dialog to show BrAPI export progress with detailed status for different types of data
 */
class BrapiExportProgressDialog(
    context: Context,
    private val cancelListener: CancelListener? = null
) : Dialog(context) {

    // Callback interface
    interface CancelListener {
        fun onCancelExport()
    }

    // UI components
    private lateinit var overallProgressIndicator: CircularProgressIndicator
    private lateinit var overallProgressText: TextView
    private lateinit var currentOperationDetails: TextView
    private lateinit var statusRecyclerView: RecyclerView
    private lateinit var statusAdapter: ExportStatusAdapter

    // Status tracking
    private val statusItems = mutableListOf<ExportStatusItem>()
    private val exportCancelled = AtomicBoolean(false)

    // Constants for status item IDs
    companion object {
        const val NEW_OBSERVATIONS = "new_observations"
        const val EDITED_OBSERVATIONS = "edited_observations"
        const val NEW_IMAGES = "new_images"
        const val EDITED_IMAGES = "edited_images"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_brapi_export_progress)
        setCancelable(false)

        // Initialize UI components
        overallProgressIndicator = findViewById(R.id.overall_progress_indicator)
        overallProgressText = findViewById(R.id.overall_progress_text)
        currentOperationDetails = findViewById(R.id.current_operation_details)
        
        // Setup RecyclerView
        statusRecyclerView = findViewById(R.id.status_recycler_view)
        statusAdapter = ExportStatusAdapter()
        statusRecyclerView.layoutManager = LinearLayoutManager(context)
        statusRecyclerView.adapter = statusAdapter
        
        // Setup cancel button
        val cancelButton: Button = findViewById(R.id.cancel_export_button)
        cancelButton.setOnClickListener {
            exportCancelled.set(true)
            currentOperationDetails.setText(R.string.cancelling)
            cancelListener?.onCancelExport()
        }
        
        // Initialize with empty status items
        initializeStatusItems()
    }

    /**
     * Initialize the status items list with default values
     */
    private fun initializeStatusItems() {
        statusItems.clear()
        statusItems.add(ExportStatusItem(
            id = NEW_OBSERVATIONS,
            label = context.getString(R.string.brapi_export_new_observations_pending)
        ))
        statusItems.add(ExportStatusItem(
            id = EDITED_OBSERVATIONS,
            label = context.getString(R.string.brapi_export_edited_observations_pending)
        ))
        statusItems.add(ExportStatusItem(
            id = NEW_IMAGES,
            label = context.getString(R.string.brapi_export_new_images_pending)
        ))
        statusItems.add(ExportStatusItem(
            id = EDITED_IMAGES,
            label = context.getString(R.string.brapi_export_edited_images_pending)
        ))
        statusAdapter.submitList(statusItems.toList())
    }

    /**
     * Update the progress for a specific status item
     */
    private fun updateStatusItem(id: String, completed: Int, total: Int) {
        val index = statusItems.indexOfFirst { it.id == id }
        if (index != -1) {
            statusItems[index] = statusItems[index].copy(
                completedCount = completed,
                totalCount = total,
                isComplete = completed == total && total > 0
            )
            statusAdapter.submitList(statusItems.toList())
        }
    }

    /**
     * Update the progress dialog with current counts
     */
    fun updateProgress(
        newObsCompleted: Int, totalNewObs: Int, 
        editedObsCompleted: Int, totalEditedObs: Int,
        newImagesCompleted: Int, totalNewImages: Int,
        editedImagesCompleted: Int, totalEditedImages: Int
    ) {
        // Update individual status items
        updateStatusItem(NEW_OBSERVATIONS, newObsCompleted, totalNewObs)
        updateStatusItem(EDITED_OBSERVATIONS, editedObsCompleted, totalEditedObs)
        updateStatusItem(NEW_IMAGES, newImagesCompleted, totalNewImages)
        updateStatusItem(EDITED_IMAGES, editedImagesCompleted, totalEditedImages)
        
        // Calculate overall progress
        val totalItems = totalNewObs + totalEditedObs + totalNewImages + totalEditedImages
        val completedItems = newObsCompleted + editedObsCompleted + newImagesCompleted + editedImagesCompleted
        
        val progressPercent = if (totalItems > 0) (completedItems * 100) / totalItems else 0
        
        // Update overall progress
        overallProgressIndicator.progress = progressPercent
        overallProgressText.text = "$progressPercent%"
    }

    /**
     * Set the current operation text
     */
    fun setCurrentOperation(operation: String) {
        currentOperationDetails.text = operation
    }

    /**
     * Set the current operation text using a resource ID
     */
    fun setCurrentOperation(stringResourceId: Int) {
        currentOperationDetails.setText(stringResourceId)
    }

    /**
     * Check if export has been cancelled
     */
    fun isExportCancelled(): Boolean {
        return exportCancelled.get()
    }
}