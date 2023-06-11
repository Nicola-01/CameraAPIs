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
import android.util.Log
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.DialogFragment

class QrCodeFragment : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_qrcode, container, false)
    }

    var url : String = ""
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog!!.window?.attributes?.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        url = arguments?.getString("URL")!!

        view.findViewById<TextView>(R.id.TV_linkQR).text = url
        Log.d("QrCode", "btqrcode load")

        view.findViewById<Button>(R.id.BT_openQrCodePopUp).setOnClickListener{
            if (!url!!.startsWith("http://") && !url!!.startsWith("https://"))
                url = "http://$url"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            Log.d("QrCode", "btqrcode open")
            startActivity(browserIntent)
        }

        view.findViewById<Button>(R.id.BT_copyQrCodePopUp).setOnClickListener {

            val clipboard = context?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("URL", url)
            clipboard.setPrimaryClip(clip)
            Log.d("QrCode", "btqrcode copy")
        }
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