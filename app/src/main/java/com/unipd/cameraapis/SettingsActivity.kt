package com.unipd.cameraapis

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager


class SettingsActivity : AppCompatActivity() {

    private lateinit var btBack : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        btBack = findViewById(R.id.BT_back)
        btBack.setOnClickListener{ finish() }

        //var LS = findViewById(r.id.LS)


        /*
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "LS_volumeKey") {
                var weight = prefs.getString("entries", "120")
                // Esegui le operazioni desiderate con il nuovo valore
            }
            // Gestisci gli altri cambiamenti delle preferenze qui
        }

        preferenceManager.registerOnSharedPreferenceChangeListener(listener)
        */

    }

    fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
        if (key == "weightValues") {
            var weight = prefs.getString("weightPref", "120")!!.toInt()
        }
        // etc
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }


}