package com.unipd.cameraapis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment

class QrCodeFragment : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rotate() // viene mostrato gia' con l' angolazione corretta in base allo stato del telefono
        return inflater.inflate(R.layout.fragment_qrcode, container, false)
    }

    private var url : String = ""
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog!!.window?.attributes?.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        url = arguments?.getString("URL")!!
        val isLink = (url.startsWith("http://") || url.startsWith("https://"))

        view.findViewById<TextView>(R.id.TV_linkQR).text = url

        val btOpen = view.findViewById<Button>(R.id.BT_openQrCodePopUp)
        val btCopy = view.findViewById<Button>(R.id.BT_copyQrCodePopUp)

        if(!isLink) // cambio la grafica nel caso sia solo testo
        {
            btCopy.text = "Copia Testo"
            btOpen.text = "Apri" // è disattivato ma non mi piace che ci sia scritto link comunque
            btOpen.isEnabled = false
            btOpen.backgroundTintList = context?.getColorStateList(R.color.light_gray)
            btOpen.alpha = 0.25f

            btCopy.backgroundTintList = context?.getColorStateList(R.color.caribbean_Current)
        }



        btOpen.setOnClickListener{
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        }

        btCopy.setOnClickListener {
            val clipboard = context?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("URL", url)
            clipboard.setPrimaryClip(clip)
        }

        view.findViewById<View>(R.id.view_qrCode).setOnClickListener { } // senza di questo se si preme sopra il popUp si chiude

        view.findViewById<View>(R.id.vw_backQr).setOnClickListener {
            dismiss()
        }
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