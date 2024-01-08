package com.fieldbook.tracker.canon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

class BitmapConverterFactory : Converter.Factory() {

    override fun responseBodyConverter(type: Type, annotations: Array<Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
        return if (type == Bitmap::class.java) {
            Converter<ResponseBody, Bitmap> {
                value -> BitmapFactory.decodeStream(value.byteStream())
            }
        } else {
            null
        }
    }
}