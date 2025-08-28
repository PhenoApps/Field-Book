package com.fieldbook.tracker.utilities

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import com.fieldbook.tracker.R
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
    }
}