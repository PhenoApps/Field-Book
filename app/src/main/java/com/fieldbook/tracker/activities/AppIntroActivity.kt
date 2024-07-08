package com.fieldbook.tracker.activities

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.OptionalSetupAdapter
import com.fieldbook.tracker.adapters.RequiredSetupAdapter
import com.fieldbook.tracker.fragments.OptionalSetupPolicyFragment
import com.fieldbook.tracker.fragments.RequiredSetupPolicyFragment
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.Constants
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import dagger.hilt.android.AndroidEntryPoint
import pub.devrel.easypermissions.EasyPermissions

@AndroidEntryPoint
class AppIntroActivity : AppIntro() {
    var prefs: SharedPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = applicationContext

        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        // Make sure you don't call setContentView!

        // Call addSlide passing your Fragments.
        // You can use AppIntroFragment to use a pre-built fragment


        // field book info 1
        addSlide(
            AppIntroFragment.createInstance(
                context.getString(R.string.app_intro_intro_title_slide1),
                context.getString(R.string.app_intro_intro_summary_slide1),
                R.drawable.field_book_intro,
                R.color.blue_primary
            )
        )

        // field book info 2
        addSlide(
            AppIntroFragment.createInstance(
                context.getString(R.string.app_intro_intro_title_slide2),
                context.getString(R.string.app_intro_intro_summary_slide2),
                R.drawable.field_book_intro,
                R.color.blue_primary_transparent
            )
        )

        // required setup slide
        addSlide(
            RequiredSetupPolicyFragment.newInstance(
                requiredSetupModelArrayList(),
                context.getString(R.string.app_intro_required_setup_title),
                context.getString(R.string.app_intro_required_setup_summary)
            )
        )

        // optional setup slide
        addSlide(
            OptionalSetupPolicyFragment.newInstance(
                optionalSetupModelArrayList(),
                context.getString(R.string.app_intro_required_optional_title),
                context.getString(R.string.app_intro_required_optional_summary)
            )
        )

        isSkipButtonEnabled = false

        // Here we ask for camera permission on slide 2
//        if (!isPermissionsGranted())
//            askForPermissions(Constants.permissionsTemp, 3, true)
    }

    private fun isPermissionsGranted(): Boolean {
        val permissions = Constants.permissionsTemp
        for (permission in permissions) {
            val res: Int = applicationContext.checkCallingOrSelfPermission(permission)
            if (res != PackageManager.PERMISSION_GRANTED)
                return false
        }
        return true
    }


    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        // Decide what to do when the user clicks on "Skip"
        finish()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        // Decide what to do when the user clicks on "Done"
        finish()
    }

    private fun requiredSetupModelArrayList(): ArrayList<RequiredSetupAdapter.RequiredSetupModel> {
        var perms = arrayOf<String?>(
            Manifest.permission.VIBRATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        )
        val finePerms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePerms =
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            perms = arrayOf(
                Manifest.permission.VIBRATE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        }

        return arrayListOf(
            RequiredSetupAdapter.RequiredSetupModel(
                applicationContext.getString(R.string.app_intro_permissions_title),
                applicationContext.getString(R.string.permission_rationale_trait_features),
                resources.getDrawable(R.drawable.configure),
                {
                    ActivityCompat.requestPermissions(
                        this,
                        Constants.permissions,
                        Constants.PERM_REQ
                    )
                },
                {
                    return@RequiredSetupModel (EasyPermissions.hasPermissions(
                        this,
                        *perms
                    )
                            && (EasyPermissions.hasPermissions(
                        this,
                        *finePerms
                    ) || EasyPermissions.hasPermissions(this, *coarsePerms)))
                },
                applicationContext.getString(R.string.app_intro_permissions_warning)
            ),
            RequiredSetupAdapter.RequiredSetupModel(
                applicationContext.getString(R.string.app_intro_storage_title),
                applicationContext.getString(org.phenoapps.androidlibrary.R.string.frag_storage_definer_summary),
                resources.getDrawable(R.drawable.storage_lock),
                {
                    val intent = Intent(this, DefineStorageActivity::class.java)
                    startActivity(intent)
                },
                {
                    return@RequiredSetupModel true
                },

                applicationContext.getString(R.string.app_intro_storage_warning)
            )
        )
    }

    private fun optionalSetupModelArrayList(): ArrayList<OptionalSetupAdapter.OptionalSetupModel> {
        return arrayListOf(
            OptionalSetupAdapter.OptionalSetupModel(
                applicationContext.getString(R.string.app_intro_load_sample_data_title),
                applicationContext.getString(R.string.app_intro_load_sample_data_summary),
                {
                    prefs?.edit()?.putBoolean(GeneralKeys.LOAD_SAMPLE_DATA, true)?.apply()
                }, {
                    prefs?.edit()?.putBoolean(GeneralKeys.LOAD_SAMPLE_DATA, false)?.apply()
                }),
            OptionalSetupAdapter.OptionalSetupModel(
                applicationContext.getString(R.string.app_intro_tutorial_title),
                applicationContext.getString(R.string.app_intro_tutorial_summary),
                {
                    prefs?.edit()?.putBoolean(GeneralKeys.TIPS, true)?.apply()
                }, {
                    prefs?.edit()?.putBoolean(GeneralKeys.TIPS, false)?.apply()
                })
        )
    }
}