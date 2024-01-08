package com.fieldbook.tracker.canon

import android.graphics.Bitmap
import com.fieldbook.tracker.canon.models.ContentPath
import com.fieldbook.tracker.canon.models.CurrentPath
import com.fieldbook.tracker.canon.models.DeviceInformation
import com.fieldbook.tracker.canon.models.LiveViewAction
import com.fieldbook.tracker.canon.models.LiveViewSettings
import com.fieldbook.tracker.canon.models.MovieMode
import com.fieldbook.tracker.canon.models.ShutterAction
import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface CameraControlApi {

    //Version 1.0.0 Endpoints
    @GET("ver100/deviceinformation")
    suspend fun getDeviceInformation(): DeviceInformation

    @POST("ver100/shooting/control/moviemode")
    suspend fun postMovieMode(@Body movieMode: MovieMode): JsonObject

    @GET("ver100/shooting/control/moviemode")
    suspend fun getMovieMode(): MovieMode

    @POST("ver100/shooting/control/shutterbutton")
    suspend fun postShutterButton(@Body action: ShutterAction): JsonObject

    @POST("ver100/shooting/liveview")
    suspend fun postLiveViewSettings(@Body settings: LiveViewSettings): JsonObject

    @POST("ver100/shooting/liveview/rtp")
    suspend fun postLiveViewAction(@Body action: LiveViewAction): JsonObject

    @GET("ver100/shooting/liveview/flip")
    suspend fun getLiveStream(): Bitmap

    //Version 1.1.0 Get Current Directory

    @GET("ver110/devicestatus/currentdirectory")
    suspend fun getCurrentDirectory(): CurrentPath

    @GET("ver110/devicestatus/currentstorage")
    suspend fun getCurrentStorage(): CurrentPath

    // Same through Version 1.0.0 -> 1.3.0 but devices implement different versions
    @GET("{ver}/contents/{drive}/{dir}")
    suspend fun getContents(
        @Path("ver") version: String,
        @Path("drive") drive: String,
        @Path("dir") dir: String
    ): ContentPath

    @GET("{ver}/contents/{drive}/{dir}/{name}")
    suspend fun getContents(
        @Path("ver") version: String,
        @Path("drive") drive: String,
        @Path("dir") dir: String,
        @Path("name") name: String? = ""
    ): Bitmap

}