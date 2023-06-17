package com.unipd.cameraapis

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
            val  preferences = getPreferences(MODE_PRIVATE)
            val editor = preferences.edit() // in questo modo sono sicuro anche quando viene eliminato il contenuto
            editor.putBoolean("PermissionDenyAsk", true)
            editor.apply()
            Log.d("CameraXApp", "PermissionDeny Setting")
        }

        BT_close.setOnClickListener {
            setResult(0, Intent())
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val  preferences = getPreferences(MODE_PRIVATE)
        Log.d("CameraXApp", "PermissionDeny Resume")
        if(preferences.getBoolean("PermissionDenyAsk", false)) // controlo se ho fatto la richiesta
        {
            setResult(1, Intent())
            finish()
        }
        val editor = preferences.edit()
        editor.putBoolean("PermissionDenyAsk", false)
        editor.apply()
    }

}