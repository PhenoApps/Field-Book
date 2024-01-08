package com.fieldbook.tracker.canon

import android.content.SharedPreferences
import com.fieldbook.tracker.preferences.GeneralKeys
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit


class CameraControlFactory(
    private val preferences: SharedPreferences
) {

    fun create(): CameraControlApi? = try {

        val ip = preferences.getString(GeneralKeys.CANON_IP, null)!!
        val port = preferences.getString(GeneralKeys.CANON_PORT, null)!!

        val baseUrl = "http://$ip:$port/ccapi/"

        val client = okhttp3.OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.MINUTES)
            .connectTimeout(5, TimeUnit.MINUTES)
            .addInterceptor { chain ->

                try {

                    var request = chain.request()

                    val ip = preferences.getString(GeneralKeys.CANON_IP, "").toString()
                    val port = preferences.getString(GeneralKeys.CANON_PORT, "").toString().toInt()

                    val a = request.url.newBuilder()
                        .host(ip)
                        .port(port)
                        .build()

                    request = request.newBuilder()
                        .url(a)
                        .build()

                    chain.proceed(request)

                } catch (e: Exception) {

                    chain.proceed(chain.request())
                }
            }
            .build()

        val retrofit = Retrofit.Builder()
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(BitmapConverterFactory())
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl(baseUrl)
            .build()

        retrofit.create(CameraControlApi::class.java)

    } catch (e: Exception) {

        null
    }
}