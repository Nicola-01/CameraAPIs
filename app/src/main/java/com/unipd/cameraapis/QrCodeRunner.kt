package com.unipd.cameraapis

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class QrCodeRunner : AppCompatActivity() { // activity usata slo per avviare la scansione qr

    private lateinit var qrCodeLauncher: ActivityResultLauncher<ScanOptions>
    private var url = ""
    private var restoreTime = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code_runner)

        qrCodeLauncher = registerForActivityResult(ScanContract()) { result ->
            if(!result.contents.isNullOrEmpty())
                url = result.contents
            else
                Toast.makeText(this@QrCodeRunner, "Codice QR non valido", Toast.LENGTH_LONG).show()
        }

        val scanOptions = ScanOptions()
        scanOptions.setPrompt("Scansiona codice QR")
        scanOptions.setBeepEnabled(false)
        scanOptions.setTorchEnabled(intent.getBooleanExtra("flashOn", false))
        scanOptions.setOrientationLocked(false)
        scanOptions.captureActivity = ScannerCaptureActivity::class.java
        qrCodeLauncher.launch(scanOptions)

    }

    override fun onResume() {
        super.onResume()
        restoreTime++
        if(restoreTime > 1){ // in questo modo viene rimandato il risultato quando si chiude l' aquisizione del qr
            val resultIntent = Intent()
            resultIntent.putExtra("URL", url)
            setResult(2, resultIntent)
            finish()
        }
    }
}