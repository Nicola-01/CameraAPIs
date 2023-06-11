package com.unipd.cameraapis

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import kotlin.concurrent.thread


class PopUpFragment : DialogFragment() {

    var ask = false
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_pop_up, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btSettings = view.findViewById<Button>(R.id.BT_settingsPopUp)
        val btClose = view.findViewById<Button>(R.id.BT_closePopUp)



        btSettings.setOnClickListener { // apre le impostazioni dell'app
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", context?.packageName, null)
            intent.data = uri
            startActivity(intent)
            ask = true
        }

        btClose.setOnClickListener{ // chiude il popup
            dismiss()
        }

        view.findViewById<View>(R.id.vw_backPr).setOnClickListener {
            dismiss()
        }
    }

    lateinit var onDismissListener : () -> Any

    override fun onDismiss(dialog: DialogInterface) {
        if (this::onDismissListener.isInitialized)
            onDismissListener()
        super.onDismiss(dialog)
    }
    override fun onResume() {
        super.onResume()
        val mainActivity = requireActivity() as MainActivity
        if(ask) // senza questo controllo continuerebbe a fare richieste
            mainActivity.askPermission()
        ask = false
        if(mainActivity.allPermissionsGranted())
            dismiss()
    }

    private val orientationEventListener by lazy {
        object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                val mainActivity = requireActivity() as MainActivity
                val rotation = mainActivity.rotation
                val dialogRootView = dialog!!.window!!.decorView.rootView
                dialogRootView.rotation = rotation.toFloat()
            }
        }
    }
    override fun onStart() {
        super.onStart()
        orientationEventListener.enable()
    }

    override fun onStop() {
        super.onStop()
        orientationEventListener.disable()
    }
}