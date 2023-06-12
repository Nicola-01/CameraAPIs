package com.unipd.cameraapis

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class QrCodeRunner : AppCompatActivity() {

    private lateinit var qrCodeLauncher: ActivityResultLauncher<ScanOptions>
    var url = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code_runner)

        qrCodeLauncher = registerForActivityResult(ScanContract()) { result ->
            if(!result.contents.isNullOrEmpty())
                url = result.contents
        }

        val scanOptions = ScanOptions()
        scanOptions.setPrompt("Scansiona codice QR")
        scanOptions.setBeepEnabled(false) // fastidioso xD
        scanOptions.setTorchEnabled(intent.getBooleanExtra("flashOn", false))
        scanOptions.setOrientationLocked(false)
        scanOptions.setCaptureActivity(ScannerCaptureActivity::class.java)
        qrCodeLauncher.launch(scanOptions)

    }
    var open = 0
    override fun onResume() {
        super.onResume()
        open++
        if(open > 1){ // in questo modo viene rimandato il risultato quando si chiude l'aquisizione del qr
            val resultIntent = Intent()
            resultIntent.putExtra("URL", url)
            setResult(2, resultIntent)
            finish()
        }
    }
}