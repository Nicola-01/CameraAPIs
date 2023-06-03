package com.unipd.cameraapis

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment


class PopUpFragment : DialogFragment() {


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_pop_up, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bt_settings = view.findViewById<Button>(R.id.BT_settingsPopUp)
        val bt_close = view.findViewById<Button>(R.id.BT_closePopUp)

        bt_settings.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", context?.packageName, null)
            intent.data = uri
            startActivity(intent)
        }

        bt_close.setOnClickListener{
            dismiss()
        }
    }

    lateinit var onDismissListener : () -> Any

    override fun onDismiss(dialog: DialogInterface) {
        if (this::onDismissListener.isInitialized) {
            onDismissListener()
        }

        super.onDismiss(dialog)
    }
    override fun onResume() {
        super.onResume()
        val mainActivity = requireActivity() as MainActivity
        if(mainActivity.allPermissionsGranted())
            dismiss()
    }


}