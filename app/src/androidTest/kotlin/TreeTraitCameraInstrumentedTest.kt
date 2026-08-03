package com.fieldbook.tracker.traits.tree

import android.app.Activity
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fieldbook.tracker.activities.CameraActivity
import com.fieldbook.tracker.activities.TraitActivity
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.AbstractCameraTrait
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * R-19 CameraActivity RESULT_OK / media_path contract (shutter onImageSaved path).
 *
 * Headless CameraX takePicture is unreliable; this drives
 * [CameraActivity.finishWithCapturedPhoto] after launch with tree extras.
 * Collect → requestNodePhoto extras + store: see
 * [TreeTraitCollectInstrumentedTest.requestNodePhoto_cameraMonitorStub_storesPortablePath].
 */
@RunWith(AndroidJUnit4::class)
class TreeTraitCameraInstrumentedTest {

    companion object {
        private lateinit var traitName: String

        @JvmStatic
        @BeforeClass
        fun prepareDeviceAndSeed() {
            TreeInstrumentedDevice.prepareHeadlessEmulator()
            traitName = "soy tree camera ${System.currentTimeMillis()}"
        }
    }

    @Before
    fun setUp() {
        TreeInstrumentedDevice.ensureWindowFocus()
        TreeInstrumentedSeed.enableExperimentalTraits()
    }

    @Test
    fun cameraActivity_treeExtras_skipSave_returnsMediaPath() {
        var studyId = ""
        var traitId = -1

        ActivityScenario.launch(TraitActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                TreeInstrumentedSeed.seedStudyTraits(activity)
                studyId = TreeInstrumentedSeed.seedAndSelectSampleField(activity).toString()
                TreeInstrumentedSeed.ensureDocumentTree(activity)
                runBlocking {
                    val repo = activity.traitRepo
                    if (repo.getTraitByName(traitName) == null) {
                        repo.insertTrait(
                            TraitObject().apply {
                                name = traitName
                                alias = traitName
                                synonyms = listOf(traitName)
                                format = "tree architecture"
                                visible = true
                                realPosition = repo.getMaxPosition() + 1
                                resourceFile = "trait/tree_collect_required_smoke.trt"
                            },
                        )
                    }
                    traitId = repo.getTraitByName(traitName)!!.id.toInt()
                }
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, CameraActivity::class.java).apply {
            putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_PHOTO)
            putExtra(CameraActivity.EXTRA_SKIP_SAVE, true)
            putExtra(CameraActivity.EXTRA_LAUNCHED_FOR_PHOTO_TRAIT, true)
            putExtra(CameraActivity.EXTRA_TRAIT_NAME, "branch photo")
            putExtra(CameraActivity.EXTRA_STUDY_ID, studyId)
            putExtra(CameraActivity.EXTRA_OBS_UNIT, TreeInstrumentedSeed.SAMPLE_ID)
            putExtra(CameraActivity.EXTRA_TRAIT_ID, traitId)
        }

        lateinit var mediaPath: String
        ActivityScenario.launchActivityForResult<CameraActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(
                    "CameraActivity early-finished — missing study/obs/trait extras?",
                    activity.isFinishing,
                )
                val tmp = File(activity.cacheDir, AbstractCameraTrait.TEMPORARY_IMAGE_NAME)
                tmp.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
                mediaPath = tmp.absolutePath
                activity.finishWithCapturedPhoto(mediaPath)
            }
            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            assertEquals(mediaPath, result.resultData?.getStringExtra("media_path"))
            assertEquals("photo", result.resultData?.getStringExtra("media_type"))
            assertTrue(result.resultData?.getBooleanExtra(CameraActivity.EXTRA_SKIP_SAVE, false) == true)
            assertTrue(File(mediaPath).exists())
        }
    }
}
