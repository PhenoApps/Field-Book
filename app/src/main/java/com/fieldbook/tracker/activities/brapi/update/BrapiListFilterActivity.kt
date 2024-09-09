package com.fieldbook.tracker.activities.brapi.update

import android.app.Activity
import android.widget.Toast
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.lang.reflect.Type

/**
 * List Filter activity base class for BrAPI filter activities
 * Subclasses must implement mapToUiModel to convert their brapi objects to checkbox adapter models
 */
abstract class BrapiListFilterActivity<T> : ListFilterActivity<T>() {

    //TODO string resource
    protected val brapiService: BrAPIService by lazy {
        BrAPIServiceFactory.getBrAPIService(this).also { service ->
            if (service is BrAPIServiceV1) {
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@BrapiListFilterActivity,
                        "BrAPI V1 is not compatible.",
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }
    }

    override suspend fun loadListData(): Flow<Any> {
        TODO("Not yet implemented")
    }

    override fun getTypeToken(): Type =
        TypeToken.getParameterized(List::class.java, TrialStudyModel::class.java).type

}