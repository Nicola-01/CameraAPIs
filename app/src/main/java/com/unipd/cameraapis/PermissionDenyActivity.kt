package com.unipd.cameraapis

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Button

class PermissionDenyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_deny)

        var BT_settings = findViewById<Button>(R.id.BT_settingsPerm)
        var BT_close = findViewById<Button>(R.id.BT_closePerm)


        BT_settings.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
            ask = true
        }

        BT_close.setOnClickListener {
            setResult(0, Intent())
            finish()
        }
    }
    var ask = false
    override fun onResume() {
        super.onResume()
        if(ask) // senza questo controllo continuerebbe a fare richieste
        {
            setResult(1, Intent())
            finish()
        }
        ask = false
    }

}