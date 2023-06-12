package com.unipd.cameraapis

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment

class PermissionFragment : DialogFragment() {

    private var ask = false
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rotate() // viene mostrato gia' con l' angolazione corretta
        return inflater.inflate(R.layout.fragment_permission, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btSettings = view.findViewById<Button>(R.id.BT_settingsPopUp)
        val btClose = view.findViewById<Button>(R.id.BT_closePopUp)

        btSettings.setOnClickListener { // apre le impostazioni dell' app
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", context?.packageName, null)
            intent.data = uri
            startActivity(intent)
            ask = true
        }

        view.findViewById<View>(R.id.view_permission).setOnClickListener { } // senza di questo se si preme sopra il popUp si chiude

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
    override fun onResume() { // controllo lo stato dei permessi
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
                rotate()
            }
        }
    }

    /**
     *  giro il popUp
     */
    fun rotate()
    {
        val mainActivity = requireActivity() as MainActivity
        val dialogRootView = dialog!!.window!!.decorView.rootView
        dialogRootView.rotation = mainActivity.rotation.toFloat()
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