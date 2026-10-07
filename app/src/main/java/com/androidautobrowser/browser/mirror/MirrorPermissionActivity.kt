package com.androidautobrowser.browser.mirror

import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Runs on the phone display so the system capture dialog is shown there.
 * Android Auto cannot show that dialog on the car screen.
 */
class MirrorPermissionActivity : AppCompatActivity() {

    private val consent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            ScreenMirrorService.start(this, result.resultCode, data)
        } else {
            MirrorController.onDenied()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
        }
        if (savedInstanceState == null) {
            consent.launch(ScreenMirrorService.createConsentIntent(this))
        }
    }
}
