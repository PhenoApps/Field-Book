package com.fieldbook.tracker.activities

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.OptionalSetupAdapter
import com.fieldbook.tracker.adapters.RequiredSetupAdapter
import com.fieldbook.tracker.fragments.GallerySlideFragment
import com.fieldbook.tracker.fragments.OptionalSetupPolicyFragment
import com.fieldbook.tracker.fragments.RequiredSetupPolicyFragment
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.Constants
import com.fieldbook.tracker.utilities.ManufacturerUtil
import com.fieldbook.tracker.utilities.SharedPreferenceUtils
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment.Companion.createInstance
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.utils.BaseDocumentTreeUtil.Companion.getRoot
import pub.devrel.easypermissions.EasyPermissions
import javax.inject.Inject

@AndroidEntryPoint
class AppIntroActivity : AppIntro() {

    @Inject
    lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);

        val context = applicationContext

        // field book info 1
        addSlide(
            createInstance(
                context.getString(R.string.app_intro_intro_title_slide1),
                context.getString(R.string.app_intro_intro_summary_slide1),
                R.drawable.field_book_intro,
                R.color.main_primary_transparent,
                R.color.main_color_text_dark,
                R.color.main_color_text_dark,
            )
        )

        // gallery slide
        addSlide(GallerySlideFragment.newInstance())

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

        setNextArrowColor(ContextCompat.getColor(this, R.color.main_primary_dark))
        setIndicatorColor(
            ContextCompat.getColor(this, R.color.main_primary_dark),
            ContextCompat.getColor(this, R.color.main_primary_transparent)
        )
        setColorDoneText(Color.BLACK)
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        setResult(RESULT_OK)
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
                applicationContext.getString(R.string.app_intro_storage_summary),
                resources.getDrawable(R.drawable.storage_lock),
                {
                    val intent = Intent(this, DefineStorageActivity::class.java)
                    startActivity(intent)
                },
                {
                    val root = getRoot(applicationContext)
                    return@RequiredSetupModel (root != null && root.exists())
                },

                applicationContext.getString(R.string.app_intro_storage_warning)
            )
        )
    }

    private fun optionalSetupModelArrayList(): ArrayList<OptionalSetupAdapter.OptionalSetupModel> {
        val optionalSetupList = arrayListOf(
            OptionalSetupAdapter.OptionalSetupModel(
                applicationContext.getString(R.string.app_intro_load_sample_data_title),
                applicationContext.getString(R.string.app_intro_load_sample_data_summary),
                {
                    prefs.edit()?.putBoolean(GeneralKeys.LOAD_SAMPLE_DATA, true)?.apply()
                }, {
                    prefs.edit()?.putBoolean(GeneralKeys.LOAD_SAMPLE_DATA, false)?.apply()
                }),
            OptionalSetupAdapter.OptionalSetupModel(
                applicationContext.getString(R.string.app_intro_tutorial_title),
                applicationContext.getString(R.string.app_intro_tutorial_summary),
                {
                    prefs.edit()?.putBoolean(GeneralKeys.TIPS, true)?.apply()
                }, {
                    prefs.edit()?.putBoolean(GeneralKeys.TIPS, false)?.apply()
                })
        )

        // add high contrast setting for boox devices
        if (ManufacturerUtil.isEInk()) {
            if (ManufacturerUtil.isOnyx()) {
                ManufacturerUtil.transferHighContrastIcon(resources)
            }
            if (!SharedPreferenceUtils.isHighContrastTheme(prefs)) {
                optionalSetupList.add(OptionalSetupAdapter.OptionalSetupModel(
                    getString(R.string.app_intro_high_contrast_title),
                    getString(R.string.app_intro_high_contrast_summary),
                    {
                        prefs.edit().putBoolean(GeneralKeys.HIGH_CONTRAST_THEME_ENABLED, true).apply()
                    }, {
                        prefs.edit().putBoolean(GeneralKeys.HIGH_CONTRAST_THEME_ENABLED, false).apply()
                    },
                    true
                ))
            }
        }

        return optionalSetupList
    }
}