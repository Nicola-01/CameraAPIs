package com.unipd.cameraapis


import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.DialogFragment


class QrCodeFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_qrcode, container, false)
    }

    lateinit var tv : TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog!!.window?.attributes?.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        var url = arguments?.getString("URL")
        if(url == null)
            dismiss()


        tv = view.findViewById<TextView>(R.id.TV_linkQR)

        view.findViewById<Button>(R.id.BT_openQrCodePopUp).setOnClickListener{
            if (!url!!.startsWith("http://") && !url!!.startsWith("https://"))
                url = "http://$url"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
        }

        view.findViewById<Button>(R.id.BT_copyQrCodePopUp).setOnClickListener {

            val clipboard = context?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("URL", url)
            clipboard.setPrimaryClip(clip)
        }
    }
    private val orientationEventListener by lazy {
        object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {


                /*
                val decorView = dialog!!.window?.decorView

                val mainActivity = requireActivity() as MainActivity

                var land = false
                when (mainActivity.mOrientation) {
                    in 50..130 ->
                    {
                        decorView?.rotation = -90F
                        land = true
                    }
                    in 140..220 ->
                        decorView?.rotation = 180F

                    in 230..310 -> {
                        decorView?.rotation = 90F
                        land = true
                    }
                    in 0..40, in 320..360 ->
                        decorView?.rotation = 0F
                }


                val layoutName = "fragment_qrcode" // Sostituisci con il nome del tuo layout

                val resources = requireContext().resources
                val packageName = requireContext().packageName

                val layoutId = resources.getIdentifier(layoutName, "layout", packageName)

                val landscapeLayoutId = resources.getIdentifier("layout-land/$layoutName", "layout", packageName)
                if (land)
                    dialog?.setContentView(landscapeLayoutId)
                    //setLandscapeOrientation()
                else
                    dialog?.setContentView(layoutId)

                 */


                tv.text = "${mainActivity.mOrientation}"
            }
        }
    }

    private fun setPortraitOrientation() {
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog?.window?.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL)
        dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        dialog?.window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_VISIBLE)
    }

    private fun setLandscapeOrientation() {
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog?.window?.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL)
        dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog?.window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        dialog?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
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