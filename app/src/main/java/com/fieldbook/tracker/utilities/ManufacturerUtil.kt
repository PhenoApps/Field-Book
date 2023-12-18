package com.fieldbook.tracker.utilities

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class ManufacturerUtil {

    companion object {

        const val onyxIconFileName = "com_fieldbook_tracker.png"

        val possibleOnyxDirs = setOf("Icons", "icon")

        /**
         * The BOOX Palma Manufacturer is ONYX
         */
        fun isEInk() = Build.MANUFACTURER in setOf(
            "ONYX",
        )

        fun isOnyx() = Build.MANUFACTURER == "ONYX"

        /**
         * ONYX devices handle icons specially by replacing them optionally with icons available in
         * Downloads/ONYX/Icon folder. This function saves the high contrast version of the launcher
         * icon into this directory under the name "com_fieldbook_tracker.png". The new icon
         * will not show immediately and requires a device restart.
         */
        fun transferHighContrastIcon(res: Resources) {

            val bmp = BitmapFactory.decodeResource(res, R.mipmap.ic_launcher_monochrome)
            val dir = Environment.getExternalStorageDirectory().toString()

            var iconFile: File? = null

            for (parent: String in possibleOnyxDirs) {

                val parentDir = File("$dir/Download/Onyx/$parent")

                if (parentDir.exists()) {

                    iconFile = File(parentDir, onyxIconFileName)

                }
            }

            if (iconFile == null || iconFile.exists()) return

            try {

                var stream: OutputStream? = null

                iconFile.createNewFile()

                try {

                    stream = FileOutputStream(iconFile)

                    bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)

                    stream.flush()

                    stream.close()

                } catch (e: Exception) {

                    e.printStackTrace()

                } finally {

                    stream?.close()

                }

            } catch (e: Exception) {

                e.printStackTrace()

            }
        }

        fun eInkDeviceSetup(
            context: Context,
            prefs: SharedPreferences,
            resources: Resources,
            onPositive: () -> Unit
        ) {
            if (isEInk()) {
                if (isOnyx()) {

                    transferHighContrastIcon(resources)
                }
                if (!SharedPreferenceUtils.isHighContrastTheme(prefs)) {
                    askUserSwitchToHighContrastTheme(context, prefs, onPositive)
                }
            }
        }

        fun askUserSwitchToHighContrastTheme(
            context: Context,
            prefs: SharedPreferences,
            onPositive: () -> Unit
        ) {
            AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(R.string.dialog_ask_high_contrast_title)
                .setMessage(R.string.dialog_ask_high_contrast_message)
                .setPositiveButton(android.R.string.ok) { d: DialogInterface?, _: Int ->
                    prefs.edit()
                        .putString(GeneralKeys.THEME, ThemedActivity.HIGH_CONTRAST.toString())
                        .putString(GeneralKeys.TEXT_THEME, ThemedActivity.MEDIUM.toString())
                        .apply()
                    onPositive.invoke()
                }
                .setNegativeButton(R.string.dialog_no) { d, _ -> d.dismiss() }
                .create().show()
        }
    }
}