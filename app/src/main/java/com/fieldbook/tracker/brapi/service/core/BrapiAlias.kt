package com.fieldbook.tracker.brapi.service.core

import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack

typealias ApiFailCallback = (Int) -> Void?
typealias ApiListSuccess<T> = (T?) -> Void?
//typealias ApiCall<T, R> = (params: T, onSuccess: ApiListSuccess<R>, onFail: ApiFailCallback) -> Unit

/*
Helper classes to avoid nullable-void "Void?" java type usage
 */
class OnApiFail(private val callback: (code: Int) -> Unit) :
    ApiFailCallback by FailCallback(callback)

class OnApiSuccess<R>(private val callback: (response: R) -> Unit) :
    ApiListSuccess<R> by SuccessListCallback(callback)

class FailCallback(private val failCallback: (code: Int) -> Unit) : ApiFailCallback {
    override fun invoke(apiError: Int): Void? {
        failCallback(apiError)
        return null
    }
}

class SuccessListCallback<R>(private val successCallback: (response: R) -> Unit) :
    ApiListSuccess<R> {
    override fun invoke(listResponse: R?): Void? {
        listResponse?.let {
            successCallback(it)
        }
        return null
    }
}

class ApiCall<T>(private val onSuccess: (T) -> Unit) : BrapiV2ApiCallBack<T>() {

    override fun onSuccess(
        result: T,
        statusCode: Int,
        responseHeaders: MutableMap<String, MutableList<String>>?
    ) {
        onSuccess(result)
    }
}
