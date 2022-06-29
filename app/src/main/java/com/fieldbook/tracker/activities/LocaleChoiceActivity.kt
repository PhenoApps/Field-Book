package com.fieldbook.tracker.activities

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.result.contract.ActivityResultContracts
import com.fieldbook.tracker.R
import org.phenoapps.utils.TextToSpeechHelper

class LocaleChoiceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_language)
        initialize()
    }

    private val checkLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        if (result.resultCode != TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {

            installLauncher.launch(TextToSpeechHelper.installTtsIntent)

        }
    }

    private val installLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    private fun initialize() {

        checkLauncher.launch(TextToSpeechHelper.checkTtsPresenceIntent)

    }
}