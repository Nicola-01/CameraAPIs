package com.unipd.cameraapis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import java.util.regex.Pattern

class QrCodeFragment : DialogFragment() {

    val LINK = 0
    val WIFI = 1
    val TEXT = 2

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

        val textURL = view.findViewById<TextView>(R.id.TV_linkQR)
        val btOpen = view.findViewById<Button>(R.id.BT_openQrCodePopUp)
        val btCopy = view.findViewById<Button>(R.id.BT_copyQrCodePopUp)

        textURL.text = url

        var type = LINK
        var ssid = ""
        var password = ""

        // in base al tipo di qrCode faccio cose diverse
        if((url.startsWith("http://") || url.startsWith("https://"))) // e' un link
            ;
        else if(url.startsWith("WIFI:")) // salvataggio dati wifi
        {
            type = WIFI
            btCopy.text = "Copia Password"
            btOpen.text = "Apri impostazioni"

            // estraggo l'ssid e la password
            val ssidPattern = Pattern.compile("S:([^;]+);")
            val passwordPattern = Pattern.compile("P:([^;]+);")

            val ssidMatcher = ssidPattern.matcher(url)
            val passwordMatcher = passwordPattern.matcher(url)

            ssidMatcher.find()
            passwordMatcher.find()

            ssid = ssidMatcher.group(1)?: ""            // se è null mette stringa vuota
            password = passwordMatcher.group(1)?: ""    // se è null mette stringa vuota

            textURL.text = "Nome WiFi: $ssid\n Password: $password"
            btCopy.backgroundTintList = context?.getColorStateList(R.color.caribbean_Current)
            btOpen.backgroundTintList = context?.getColorStateList(R.color.light_gray) // do meno importanza, prima va cliccato l' altro
        }
        else // è testo o qualcosa che non è stato implementato
        {
            type = TEXT
            btCopy.text = "Copia Testo"
            btOpen.text = "Apri" // è disattivato ma non mi piace che ci sia scritto link comunque
            btOpen.isEnabled = false
            btOpen.backgroundTintList = context?.getColorStateList(R.color.light_gray)
            btOpen.alpha = 0.25f

            btCopy.backgroundTintList = context?.getColorStateList(R.color.caribbean_Current)
        }

        btOpen.setOnClickListener{
            val intent =
                if(type == WIFI)
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                else
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        btCopy.setOnClickListener {
            val clipboard = context?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            var clip = ClipData.newPlainText("URL", if(type == WIFI) password else url)
            //se è un qr code di un wifi allora copio solo la password, altrimenti tutto il teso
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