package com.unipd.cameraapis

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.Chronometer
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.unipd.cameraapis.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import kotlin.math.abs
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService // todo forse si puo' eliminare

    // Seleziona la camera posteriore di default
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    // CameraSelector per attivare le funzionalita' avanzate
    private lateinit var hdrCameraSelector: CameraSelector
    private lateinit var bokehCameraSelector: CameraSelector
    private lateinit var nightCameraSelector: CameraSelector

    // Widget di activity_main.xml
    private lateinit var btFlash : Button
    private lateinit var btGallery : Button
    private lateinit var btPause : Button
    private lateinit var btPhotoMode : Button
    private lateinit var btVideoMode : Button
    private lateinit var btRotation : Button
    private lateinit var btShoot : Button
    private lateinit var btStop : Button
    private lateinit var btTimer : Button
    private lateinit var btZoom05 : Button
    private lateinit var btZoom10 : Button
    private lateinit var btZoomRec : Button
    private lateinit var btQR : Button
    private lateinit var btBokehMode : Button
    private lateinit var btNightMode : Button
    private lateinit var btSettings : Button
    private lateinit var focusCircle : View
    private lateinit var focusView : View
    private lateinit var viewPreview : View
    private lateinit var sbZoom : SeekBar
    private lateinit var cmRecTimer : Chronometer
    private lateinit var countDownText : TextView
    private lateinit var scrollViewMode: HorizontalScrollView
    private lateinit var floatingPhoto : FloatingActionButton

    // variabili
    private lateinit var availableCameraInfos: MutableList<CameraInfo>
    private lateinit var camera : androidx.camera.core.Camera
    private lateinit var cameraControl: CameraControl
    private lateinit var cameraManager : CameraManager
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var preview: Preview
    private lateinit var recorder: Recorder
    private lateinit var scaleDown: Animation
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var scaleUp: Animation
    private lateinit var timer: CountDownTimer
    private var volumeTimer: CountDownTimer? = null

    private var cmPauseAt : Long = 0
    private var countdown : Long = 0
    private var currFlashMode : FlashModes = FlashModes.OFF
    private var currTimerMode : TimerModes = TimerModes.OFF
    private var currentCamera = 0
    // 0 -> back default;   grand angolare
    // 1 -> front default;  ultra grand angolare
    // 2 -> back;           ultra grand angolare
    // 3 -> front;          grand angolare
    private var zoomLv : Float = 0.toFloat() // va da 0 a 1
    private var countMultiShot = 0
    var rotation = 0

    private var currentMode = PHOTO_MODE
    private var isRecording = false
    private var inPause = false
    private var timerOn = false
    private var captureJob: Job? = null
    // boolean per gestire diverse azioni con i pulsanti del volume a seguito di longClick
    private var isVolumeButtonClicked = false
    private var isVolumeButtonLongPressed = false

    private lateinit var volumeKey : String
    private var aspectRatioPhoto = Rational(3, 4)
    private var aspectRatioVideo = Rational(3, 4)
    private var ratioVideo = AspectRatio.RATIO_4_3
    private var videoResolution = QualitySelector.from(Quality.HIGHEST)
    private var mirror = true
    private var frontCamera = false
    private var hdr = false
    private var isHdrAvailable = true
    private var isBokehAvailable = true
    private var isNightAvailable = true
    private var feedback = true
    private var saveMode = true
    private var blockRotation = false
    private var blockChangeMode = false

    private var savedBundle: Bundle? = null
    private val permissionPopUp = PermissionFragment()
    private var qrCodePopUp = QrCodeFragment()
    private var popUpVisible = false
    private var permissionDenyAsk = false

    private lateinit var extensionsManagerFuture : ListenableFuture<ExtensionsManager>
    companion object {

        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd_HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10

        private const val KEY_CAMERA = "CurrentCamera"
        private const val KEY_FLASH = "FlashMode"
        private const val KEY_TIMER = "TimerMode"
        private const val KEY_ZOOM = "ZoomProgress"
        private const val KEY_MODE = "CurrentMode"

        private const val VIDEO_MODE = 0
        private const val PHOTO_MODE = 1
        private const val BOKEH_MODE = 2
        private const val NIGHT_MODE = 3

        /**
         * Velocita' minima per rilevare lo swipe.
         */
        private const val TRESHOLD_VELOCITY : Float = 100.0f

        /**
         * Lunghezza minima del trascinamento per rilevare lo swipe.
         */
        private const val TRESHOLD : Float = 100.0f

        /**
         * Durata pressione del pulsante del volume per far iniziare la registrazione video o chiamare [multishot].
         */
        private const val LONGCLICKDURATION = 300L

        /**
         * Angolo morto nella rotazione
         */
        private const val DEADZONEANGLE = 10

        private const val CHANGECAMERASEEKBAR = 50

        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
            }.toTypedArray()
    }

    /**
     * Chiamato all'avvio dell'app, si occupa dell'inizializzazione
     * degli elementi e dell'avvio della camera.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        savedBundle = savedInstanceState

        createElement() // inizializza le variabili

        // Controlla se sono stati forniti i permessi
        if (allPermissionsGranted())
            startCamera()
        else
            askPermission()

        // todo forse si puo' eliminare
        //cameraExecutor = Executors.newSingleThreadExecutor() //si assicura che tutte le attivita'
        // in cameraExecutor vengano eseguite in modo sequenziale su un singolo thread
    }

    /**
     *  Controlla se l'app e' stata aperta tramite shortcut, ed esegue l'azione corrispondente.
     */
    private fun openByShortCut() {
        if (intent == null)
            return
        when (intent.action) {
            "shortcut.selfie" -> {
                Log.d(TAG, "selfie_shortcut")
                rotateCamera(override = true, false)
            }

            "shortcut.photo" -> {
                Log.d(TAG, "photo_shortcut")
                rotateCamera(override = true, true)
                changeMode(PHOTO_MODE)
            }

            "shortcut.video" -> {
                Log.d(TAG, "video_shortcut")
                changeMode(VIDEO_MODE)
            }

            "shortcut.qrcode" -> {
                Log.d(TAG, "qrcode_shortcut")
                qrCode()
            }
        }
    }

    /**
     * Mostra popup di android per accettare i permessi, e' una funzione a parte perche' e' utilizzata anche un PopUpFragment.
     */
    fun askPermission() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        Log.d(TAG, "Permission asked")
    }

    /**
     * controlla se i permessi sono stati accettati.
     */
    fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * metodo che viene esegito quando il pop up che compare con [askPermission] viene chiuso.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) // se sono stati accettati tutti i permessi allora avvio la camera
                startCamera()
            else if(permissionDenyAsk) //
            // se non vengono accettati i permessi anche dopo il popup custom (leggere else if successivo)
            // allora mostro una chermata custom impedendo l'utilizzo dell'app
            {
                val intent = Intent(this, PermissionDenyActivity::class.java)
                startActivityForResult(intent, 0)
            }
            else if(!popUpVisible) // se non sono stati accettati i permessi allora lancio un popup custom
            {
                permissionPopUp.show(supportFragmentManager, "showPopUp")
                permissionPopUp.onDismissListener = {
                    popUpVisible = false
                    if (allPermissionsGranted())    // controllo se sono stati accettati i permessi
                        startCamera()               // se si lancio la camera
                    else {                          // altimenti mosto un altra activity
                        val intent = Intent(this, PermissionDenyActivity::class.java)
                        startActivityForResult(intent, 0)
                    }
                }
                popUpVisible = true
            }
        }
        Log.d(TAG, "Permission Request")
    }

    /**
     * Risultato di un activity.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == 2) {
            val url = data?.getStringExtra("URL")
            // PopUp per il qrCode
            if(url != "") {
                val bundle = Bundle()
                bundle.putString("URL", url) // impostare url qui
                qrCodePopUp.arguments = bundle
                qrCodePopUp.show(supportFragmentManager, "showPopUp")
            }
        }
        else if(resultCode == 1){
            permissionDenyAsk = true
            askPermission()
        }
        else
            finish()
    }

    /**
     * viene eseguito appena viene caricata tutta la grafica
     * mi salvo l'altezza in cui impostare le barre piu' scure per quando
     * la preview occupa un area piu' grande di 3:4, e' principalmente per
     * bellezza estetica.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {

            // aggiustamento scrollBar tasto selezionato
            if(currentMode <= PHOTO_MODE)
                scrollViewMode.fullScroll(View.FOCUS_RIGHT)
            else
                scrollViewMode.fullScroll(View.FOCUS_LEFT)

            if(allPermissionsGranted()) { // controllo di avere i permessi
                setFlashMode() // attivo il flash se sono in modalita' video
                //aggiustamenti grafici
                changeMode(currentMode, true)
                changeZoom(sbZoom.progress)
            }


            val preferences = getPreferences(MODE_PRIVATE)

            // recupero le variabili dalle preferences
            var hB = preferences.getInt("bottomBandHeight", -1)
            var hT = preferences.getInt("topBandHeight", -1)

            Log.d(TAG, "height bottom $hB")
            Log.d(TAG, "height top $hT")

            val bottomBand = findViewById<View>(R.id.VW_bottomBand)
            val topBand = findViewById<View>(R.id.VW_topBand)
            val layoutParamsB = bottomBand.layoutParams
            val layoutParamsT = topBand.layoutParams

            if (hB == -1) // primo avvio assoluto del app
            {
                val displayMetrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(displayMetrics)
                val height = displayMetrics.heightPixels

                hB = height - viewPreview.bottom // Imposta l'altezza desiderata in pixel
                hT = viewPreview.top // Imposta l'altezza desiderata in pixel

                val editor = preferences.edit()
                editor.putInt("bottomBandHeight", hB)
                editor.putInt("topBandHeight", hT)
                editor.apply()
            }

            layoutParamsB.height = hB
            bottomBand.layoutParams = layoutParamsB

            layoutParamsT.height = hT
            topBand.layoutParams = layoutParamsT
        }
    }

    /**
     * Funzione per istanziare elementi del activity_main.xml;
     * assagnazione dei widget e altre variabili.
     */
    private fun createElement()
    {
        btBokehMode = viewBinding.BTBokehMode
        btFlash = viewBinding.BTFlash
        btGallery = viewBinding.BTGallery
        btNightMode = viewBinding.BTNightMode
        btPause = viewBinding.BTPause
        btPhotoMode = viewBinding.BTPhotoMode
        btVideoMode = viewBinding.BTRecordMode
        btRotation = viewBinding.BTRotation
        btShoot = viewBinding.BTShoots
        btStop = viewBinding.BTStop
        btTimer = viewBinding.BTTimer
        btZoom05 = viewBinding.BT05
        btZoom10 = viewBinding.BT10
        btZoomRec = viewBinding.BTZoomRec
        btQR = viewBinding.BTQrcode
        btSettings = viewBinding.BTSettings
        cmRecTimer = viewBinding.CMRecTimer
        cmRecTimer.format = "%02d:%02d:%02d"
        focusCircle = viewBinding.FocusCircle
        sbZoom = viewBinding.SBZoom
        countDownText = viewBinding.TextTimer
        focusView = viewBinding.FocusCircle
        viewPreview = viewBinding.viewPreview
        floatingPhoto = viewBinding.floatingPhoto

        scrollViewMode = viewBinding.scrollMode

        scaleDown = AnimationUtils.loadAnimation(this,R.anim.scale_down)
        scaleUp = AnimationUtils.loadAnimation(this,R.anim.scale_up)

        gestureDetector = GestureDetector(this, MyGestureListener())
        scaleGestureDetector = ScaleGestureDetector(this, ScaleGestureListener())
    }

    /**
     * Funzione per l'assegnazione dei Listener ai widget.
     * Todo: da finire di commentare
     */
    private fun createListener()
    {
        /*
            Listener per il pulsante del flash:
            - Con un singolo click e' possibile passare alla successiva modalita' della lista FlashModes
            - Con un click prolungato si apre un menu' contestuale che permette di selezionare la modalita'
              desiderata
         */
        btFlash.setOnClickListener { switchFlashMode() }
        btFlash.setOnCreateContextMenuListener { menu, _, _ ->
            menu.setHeaderTitle("Flash")
            for(mode in FlashModes.values()) {
                val item: MenuItem = menu.add(Menu.NONE, mode.ordinal, Menu.NONE, mode.text)
                item.setOnMenuItemClickListener { i: MenuItem? ->
                    selectFlashMode(i?.itemId)
                    true // Signifies you have consumed this event, so propogation can stop.
                }
            }
        }
        /*
            Listener per il pulsante della galleria:
            - Con un singolo click viene selezionata la foto piu' recente e viene usato un intent per aprirla
         */
        btGallery.setOnClickListener{
            val uriExternal: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                MediaStore.Images.ImageColumns._ID,
                MediaStore.Images.Media._ID,
                MediaStore.Images.ImageColumns.DATE_ADDED,
                MediaStore.Images.ImageColumns.MIME_TYPE
            )
            val cursor: Cursor = applicationContext.contentResolver.query(uriExternal, projection, null,
                null, MediaStore.Images.ImageColumns.DATE_ADDED + " DESC"
            )!!

            Log.i("Cursor Last", cursor.moveToLast().toString())
            if (cursor.moveToFirst()) {
                val columnIndexID = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val imageId: Long = cursor.getLong(columnIndexID)
                val imageURI = Uri.withAppendedPath(uriExternal, "" + imageId)
                val intent = Intent()
                intent.action = Intent.ACTION_VIEW
                intent.setDataAndType(imageURI, "image/*")
                startActivity(intent)
            }

            cursor.close()
        }

        btVideoMode.setOnClickListener { changeMode(VIDEO_MODE)
            if (feedback) it.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
        btPhotoMode.setOnClickListener { changeMode(PHOTO_MODE)
            if (feedback) it.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
        btBokehMode.setOnClickListener { changeMode(BOKEH_MODE)
            if (feedback) it.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
        btNightMode.setOnClickListener { changeMode(NIGHT_MODE)
            if (feedback) it.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }

        btPause.setOnClickListener{ pauseVideo()
            if (feedback) it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }

        btRotation.setOnClickListener {
            rotateCamera()
            if(feedback) it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }

        /*
            Listener per il bottone di scatto:
            - Quando viene rilasciato si disattiva il multiscatto
            - Se viene premuto in modalita' foto scatta (con anche l'opzione di scatto temporizzato)
            - floatingPhoto ?
            - Se viene premuto a lungo in modalita' foto inizia il multiscatto, altrimenti ?
         */
        btShoot.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                multishot(false)
                true
            }
            false
        }

        // pulsante per scattare
        btShoot.setOnClickListener {
            timerShot(currentMode == VIDEO_MODE)
            if(feedback) it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }

        // pulsante per scattare in modalita' video
        floatingPhoto.setOnClickListener{
            takePhoto()
            if(feedback) it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }

        // gestione long click
        btShoot.setOnLongClickListener{
            if (currentMode == PHOTO_MODE)
            {
                multishot(true) // multi shot solo per modalita' foto
                if(feedback) it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            else
                timerShot(currentMode == VIDEO_MODE)
            true // Restituisce true per indicare che l'evento di click lungo e' stato gestito correttamente
        }

        btStop.setOnClickListener{
            timerShot(true) // ferma la registrazione
        }

        /*
            Listener per il pulsante del timer:
            - con un singolo click passa alla modalita' successiva, selezionando i secondi di countdown per l'autoscatto (0, 3, 5, 10);
            - con un tocco prolungato apre un menu a tendina che permette di selezionare la modalita' desiderata.
         */
        btTimer.setOnClickListener { switchTimerMode() }
        btTimer.setOnCreateContextMenuListener { menu, _, _ ->
            menu.setHeaderTitle("Timer")
            for(mode in TimerModes.values()) {
                val item: MenuItem = menu.add(Menu.NONE, mode.ordinal, Menu.NONE, mode.text)
                item.setOnMenuItemClickListener { i: MenuItem? ->
                    selectTimerMode(i?.itemId)
                    true
                }
            }
        }

        // elementi per cambiare lo zoom
        btZoom10.setOnClickListener { sbZoom.progress = CHANGECAMERASEEKBAR }
        btZoom05.setOnClickListener{ sbZoom.progress = 0 }
        sbZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            //listenere per quando cambia la progressione della seekbar
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                changeZoom(progress) // posso cambiare zoom solo in photo e video
                if(feedback && progress%5 == 0 && fromUser) // ogni 5 do un feedback, e solo se muovo manualmente la SB
                    sbZoom.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            override fun onStartTrackingTouch(seek: SeekBar) = Unit
            override fun onStopTrackingTouch(seek: SeekBar) = Unit
        })

        /*
            Listener sulla view della preview:
            - Viene impostato un riconoscitore di gesture per identificare le gesture di tocco semplice e swipe
            - Viene impostato un riconoscitore di scale gesture
         */
        viewPreview.setOnTouchListener(View.OnTouchListener setOnTouchListener@{ _, event ->
            gestureDetector.onTouchEvent(event)
            scaleGestureDetector.onTouchEvent(event)
            true
        })

        // listener per il avviare la lettura del QR
        btQR.setOnClickListener {
            qrCode()
        }

        // appertura delle impostazioni
        btSettings.setOnClickListener {view ->
            startActivity(Intent(view.context, SettingsActivity::class.java))
        }
    }

    /**
     * Lancia l'activity [QrCodeRunner] per la lettura di un codice Qr.
     */
    private fun qrCode()
    {
        val intent = Intent(this, QrCodeRunner::class.java)
        intent.putExtra("flashOn", currFlashMode == FlashModes.ON)
        startActivityForResult(intent, 1)
    }

    /**
     * Costruisce la camera.
     */
    private fun bindCamera()
    {
        try {
            cameraProvider.unbindAll()            // Unbind use cases before rebinding

            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture) // devo ricostruire la camera ogni volta, dato che cambio al camera
            // in quanto cambio la camera

            cameraControl = camera.cameraControl
        } catch(e: Exception) {
            Log.e(TAG, "Bind failed", e)
        }
    }

    /**
     * Consente di scattare foto in HDR (High Dynamic Range), ampliando la gamma di colori e i livelli di luminosita'.
     */
    private fun hdrMode()
    {
        if(isHdrAvailable) {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, hdrCameraSelector, preview, imageCapture)
            cameraControl = camera.cameraControl
        }
        else {
            Log.d(TAG, "HDR is not available")
            Toast.makeText(this, "HDR non disponibile", Toast.LENGTH_SHORT).show()
            bindCamera()
        }
    }

    /**
     * Passa alla modalita' Bokeh (Ritratto), se disponibile sul proprio dispositivo.
     * @return True se la modalita' e' disponbile, False altrimenti
     */
    private fun bokehMode() : Boolean
    {
        if(isBokehAvailable) {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, bokehCameraSelector, imageCapture, preview)
            cameraControl = camera.cameraControl

            return true
        }
        // altrimenti
        Log.d(TAG, "BOKEH is not available")
        Toast.makeText(this, "BOKEH non disponibile", Toast.LENGTH_SHORT).show()

        return false
    }

    /**
     * Passa alla modalita' Night, se disponibile sul proprio dispositivo.
     * @return True se la modalita' e' disponbile, False altrimenti
     */
    private fun nightMode() : Boolean
    {
        if(isNightAvailable) {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, nightCameraSelector, imageCapture, preview)

            return true
        }
        // altrimenti
        Log.d(TAG, "NIGHT MODE is not available")
        Toast.makeText(this, "NIGHT MODE non disponibile", Toast.LENGTH_SHORT).show()

        return false
    }

    /**
     * Crea la Preview della fotocamera e ne seleziona l'output, l'aspect ratio e la qualita' video.
     */
    private fun startCamera() {
        // si ottiene un'istanza di tipo ListenableFuture che rappresenta un'istanza di ProcessCameraProvider, disponibile in seguito
        // permette di recuperare l'istanza di ProcessCameraProvider in modo asincrono
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            Log.d(TAG, "Start Camera")
            // recupera l'istanza di ProcessCameraProvider
            cameraProvider = cameraProviderFuture.get()

            // gestione delle estensioni
            extensionsManagerFuture = ExtensionsManager.getInstanceAsync(this, cameraProvider)
            extensionsManagerFuture.addListener({

                // seleziona la fotocamera dorsale di default
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                changeSelectCamera()

                // crea la Preview
                preview = Preview.Builder()
                    .build()    // creo l'istanza di Preview
                    .also {
                        it.setSurfaceProvider(viewBinding.viewPreview.surfaceProvider)  // seleziono dove visualizzare la preview
                    }

                // crea un'istanza di ImageCapture e imposta il flash a OFF
                imageCapture = ImageCapture.Builder().setFlashMode(ImageCapture.FLASH_MODE_OFF).build()



                // Crea un oggetto CameraSelector per la fotocamera ultra grandangolare
                availableCameraInfos = cameraProvider.availableCameraInfos
                Log.i(TAG, "[startCamera] available cameras Info:$availableCameraInfos") // lista contenente le fotocamere del dispositivo
                cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

                // inizializzazione della camera
                createListener()            // crea i Listener
                createRecorder()            // crea un recorder per modificare la qualita' video e l'aspect ratio
                changeMode(currentMode, true)// forzo il bind
                loadFromSetting()           // recupera le impostazioni
                loadFromBundle(savedBundle) // carica gli elementi dal Bundle/Preferences
                openByShortCut()            // controlla come e' stata aperta l'app

            }, ContextCompat.getMainExecutor(this))

        }, ContextCompat.getMainExecutor(this)) // specifica che le operazioni del listener vengano eseguite nel thread principale
    }

    /**
     * Metodo per creare/riassegnare recorder, permettendo di modificare la qualita' video e l'aspect ratio con cui registra.
     */
    private fun createRecorder() {
        try {
            recorder = Recorder.Builder()
                .setQualitySelector(videoResolution)    // qualita' video
                .setAspectRatio(ratioVideo)             // aspect ratio
                .build()
            videoCapture = VideoCapture.withOutput(recorder)    // crea un oggetto di tipo VideoCapture e imposto record come output video
        }
        catch ( e : Exception)
        {
            Log.e(TAG, "[createRecorder]", e)
        }
    }

    /**
     * Metodo per scattare una foto usando le impostazioni di [imageCapture].
     * Todo: finire commentare il funzionamento
     */
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        if(imageCapture == null)
            return

        // creazione degli elementi necessari per avviare la cattura delle immagini

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,
                SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()))    // nome con cui salvare il file
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")                                    // formato del file
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraAPIs")                           // percorso dove salvare il file
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .setMetadata(ImageCapture.Metadata().apply {
                // salva le immagini specchiate (se attiva l'impostazione e se in fotocamera anteriore)
                isReversedHorizontal = (mirror && frontCamera)
            })
            .build()

        // Set up image capture listener, which is triggered after photo has been taken

        // imposto le animazioni per lo scatto
        if(currentMode != VIDEO_MODE)
            btShoot.startAnimation(scaleDown)
        viewPreview.startAnimation(scaleUp)

        disableButton(true) // blocco il passaggio di modalita'

        imageCapture!!.takePicture( // caso d'uso
            outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    //Toast.makeText(baseContext, "Photo capture failed:", Toast.LENGTH_SHORT).show()
                    disableButton(false) // sblocco il passaggio di modalita'
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    disableButton(false) // sblocco il passaggio di modalita'
                }
            }
        )
        btTimer.visibility = View.VISIBLE   //rendo di nuovo visibile il pulsante del timer dopo aver scattato la foto
    }

    /**
     * Blocca tutti gli elementi che potrebbero portare ad eseguire un unbind mentre si sta salvando
     * una foto, questo impedirebbe il corretto salvataggio della foto
     * @return True se si vuole bloccare False altrimenti
     */
    private fun disableButton(status : Boolean)
    {
        blockChangeMode = status // blocca gesture, tasti volume e changeMode
        btRotation.isEnabled = !status
        btTimer.isEnabled = !status
        btZoom05.isEnabled = !status
        btZoom10.isEnabled = !status
        btZoomRec.isEnabled = !status
        btVideoMode.isEnabled = !status
        btPhotoMode.isEnabled = !status
        btBokehMode.isEnabled = !status
        btNightMode.isEnabled = !status
        sbZoom.isEnabled = !status
    }

    /**
     * Metodo per iniziare o fermare il multishot.
     */
    private fun multishot(on_off: Boolean) {
        if(on_off) {
            if(captureJob == null) {
                countMultiShot = 0
                countDownText.visibility = View.VISIBLE
                // inizia una coroutine che esegue scatti intervallati da brevi pause
                captureJob = CoroutineScope(Dispatchers.Main).launch {
                    while (isActive) {
                        takePhoto()
                        countDownText.text = "${++countMultiShot}"
                        delay(300) // Intervallo tra i singoli scatti
                        if (feedback) viewPreview.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                }
            }
        }
        else {
            // termina la coroutine
            captureJob?.cancel()
            captureJob = null
            countDownText.postDelayed(Runnable {
                countDownText.visibility = View.INVISIBLE
            }, 1000)
        }
    }

    /**
     * Metodo utilizzato per avviare e fermare la registrazione video.
     */
    private fun captureVideo() : Boolean {
        if (videoCapture == null) // controllo di nullita'
            return true

        val videoCapture = this.videoCapture

        if (recording != null) { // se sto gia' registrando allora la fermo
            recording!!.stop()
            recording = null
            return true
        }

        // creazione degli elementi necessari per avviare la registrazione
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,
                SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()))    // nome con cui salvare il file
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")                                     // formato del file
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/CameraAPIs")                            // percorso dove salvare il file
        }

        // output per il salvataggio di un file video
        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        //if(currFlashMode == FlashModes.ON) cameraControl.enableTorch(true)  // se si e' impostato il flash su on questo viene acceso

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            return false // Controlla che sia presente il permesso per registrare l'audio
        // NB. e' un controllo ridondante, dato che non si puo' avviare la telecamera se
        // non si accettano tutti i permessi,

        recording = videoCapture!!.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .withAudioEnabled()  // consente di registrare l'audio
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> { // inizia la registrazione
                        inPause = false
                        startRecording(true) // cambio la grafica
                        scrollViewMode.visibility = View.INVISIBLE
                        disableButton(true) // blocco il passaggio di modalita'
                    }
                    is VideoRecordEvent.Finalize -> { // finisce la registrazione
                        if (recordEvent.hasError()) { // se c'e' un errore
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Errore nella registrazione: " + "${recordEvent.error}")
                        }

                        disableButton(false) // sblocco il passaggio di modalita'
                        //if(currFlashMode == FlashModes.ON) cameraControl.enableTorch(false)
                        // resetto la grafica..
                        startRecording(false)
                        inPause = false
                        btPause.setBackgroundResource(R.drawable.pause_button) // cambio grafica al pulsante
                        btTimer.visibility = View.VISIBLE   //rendo di nuovo visibile il pulsante del timer dopo la registrazione
                        scrollViewMode.visibility = View.VISIBLE
                    }
                }
            }
        return true
    }

    /**
     * Metodo per impostare la visuale durante la registrazione.
     *
     * @param status True se e' stata avviata la registrazione;
     *               False se e' stata interrotta.
     */
    private fun startRecording(status : Boolean)
    {
        val viewPH : Int
        val viewVI : Int
        isRecording = status
        if(status){
            viewPH = View.INVISIBLE
            viewVI = View.VISIBLE

            cmRecTimer.base = SystemClock.elapsedRealtime() // resetto il timer
            cmRecTimer.start()

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // mantiene lo schermo attivo durante la registrazione
            floatingPhoto.visibility = View.VISIBLE
        }
        else
        {
            viewPH = View.VISIBLE
            viewVI = View.INVISIBLE
            cmRecTimer.stop()

            floatingPhoto.visibility = View.INVISIBLE
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // permette allo schermo di spegnersi
        }
        recOptions() // cambio la grafica del pulsante

        // nascondo/visualizzo
        btRotation.visibility = viewPH
        btGallery.visibility = viewPH
        btZoom10.visibility = viewPH
        btZoom05.visibility = viewPH

        // visualizzo/nascondo
        btZoomRec.visibility = viewVI
        cmRecTimer.visibility = viewVI

        // se inizio a registrare non posso piu' cambiare camera,
        // quindi devo sistemare il valore della seekbar
        if(status) {
            sbZoom.progress = (zoomLv*sbZoom.max).toInt()
        }
        else
        {
            if(currentCamera==0 || currentCamera==3) // camere normali
                sbZoom.progress = (zoomLv*(sbZoom.max - CHANGECAMERASEEKBAR)).toInt() + CHANGECAMERASEEKBAR
            else // camere ultra grand angolari
                sbZoom.progress = (zoomLv*sbZoom.max*0.54).toInt()
        }
    }

    /**
     * Funzione per mettere in pausa o ripristinare la registrazione.
     */
    private fun pauseVideo(set: Boolean? = false) {
        // inPause = true se la registrazione e' in pausa
        if(inPause) {
            recording?.resume() // ripristina registrazione
            cmRecTimer.base = SystemClock.elapsedRealtime() - cmPauseAt // calcolo per riesumare il timer correttamente
            cmRecTimer.start()
            btPause.setBackgroundResource(R.drawable.pause_button) // cambio grafica al pulsante
        }
        else {
            recording?.pause() // mette in pausa la registrazione
            cmRecTimer.stop()
            cmPauseAt = SystemClock.elapsedRealtime() - cmRecTimer.base
            btPause.setBackgroundResource(R.drawable.play_button) // cambio grafica al pulsante
        }
        inPause = !inPause
    }

    /**
     * Funzione per visualizzare i comandi corretti;
     * Se sono in modalita' foto il pulsante e' bianco,
     * se inizio a registrare (sempre in modalita' foto) diventa rosso,
     * se sono in modalita' video e non sto registrando e' bianco con pallino rosso;
     * se sono in modalita' video e sto registrando mostra i tasti per fermare e riprendere la registrazione.
     */
    private fun recOptions()
    {
        btShoot.visibility = if((currentMode == VIDEO_MODE) && isRecording) View.INVISIBLE else View.VISIBLE
        findViewById<Group>(R.id.Group_rec).visibility = if((currentMode == VIDEO_MODE) && isRecording) View.VISIBLE else View.INVISIBLE
        // R.id.Group_rec e' un gruppo contenente i pulsanti per fermare e riprendere la registrazione video

        if((currentMode == VIDEO_MODE) && !isRecording) // scelta del pulsante
            btShoot.setBackgroundResource( R.drawable.in_recording_button)
        else if(currentMode != VIDEO_MODE)  {
            if(isRecording)
                btShoot.setBackgroundResource( R.drawable.rounded_corner_red)
            else
                btShoot.setBackgroundResource( R.drawable.rounded_corner)
        }
    }

    /**
     * Metodo per ricaricare i valori nel bundle o nelle preferences.
     * Non e' nel [onCreate] perche' usa variabili che vengono dichiarate nel [startCamera].
     */
    private fun loadFromBundle(savedInstanceState : Bundle?)
    {
        Log.d(TAG, "LoadFromBundle")
        val preferences = getPreferences(MODE_PRIVATE)

        // recupero le variabili dalle preferences
        currentCamera = preferences.getInt(KEY_CAMERA,0)
        val flashMode = preferences.getString(KEY_FLASH, "OFF")
        val timerMode = preferences.getString(KEY_TIMER, "OFF")

        while(currFlashMode.toString() != flashMode)
            switchFlashMode()
        setFlashMode()
        while(currTimerMode.toString() != timerMode)
            switchTimerMode()
        setTimerMode()
        var progress = CHANGECAMERASEEKBAR


        if(saveMode)
            currentMode = preferences.getInt(KEY_MODE, PHOTO_MODE)

        if (savedInstanceState != null) { // controlo che ci sia il bundle
            //recupero variabili dal bundle
            currentCamera = savedInstanceState.getInt(KEY_CAMERA)
            // se gia' ricaricato da preferences lo sovrascrivo,
            // in quanto con preference salvo solo se e' posteriore o anteriore
            // mentre nel bundle salvo effettivamente la camera corretta
            progress = savedInstanceState.getInt(KEY_ZOOM)
            currentMode = savedInstanceState.getInt(KEY_MODE)

            sbZoom.progress = progress
        }

        changeMode(currentMode)
        // uso changeZoom per cambiare lo zoom e ricostruire la camera
        changeZoom(progress, true) // cambio zoom e forzo il rebind
    }

    /**
     * Recupero i settaggi dopo che questi sono stati modificati.
     */
    private fun loadFromSetting() {
        val pm = PreferenceManager.getDefaultSharedPreferences(this)

        // -- Impostazioni Tasti
        volumeKey = pm.getString("LS_volumeKey","zoom")!!

        // -- Foto

        aspectRatioPhoto = when (pm.getString("LS_ratioPhoto", "3_4")!!) {
            "1_1" -> Rational(1, 1)
            "3_4" -> Rational(3, 4)
            "9_16" -> Rational(9, 16)
            "full" -> {
                val metrics = DisplayMetrics()
                val display = windowManager.defaultDisplay
                display.getRealMetrics(metrics)
                Rational(metrics.widthPixels, metrics.heightPixels)
            }
            else -> Rational(4, 3) // Rapporto d'aspetto predefinito se nessun caso corrisponde
        }

        try {
            blockRotation = true // imposto l'orientamento verticale prima di eseguire il crop
            changeOrientation(0)
            imageCapture?.setCropAspectRatio(aspectRatioPhoto)
            changeOrientation(rotation) // in questo modo sono sicuro che reimposta l'angolazione di prima
            blockRotation = false // ripristino il cambio di orientamento automatico
        }
        catch (e : Exception)
        {
            Log.e(TAG, "[LoadFromSetting] $e")
        }

        // -- Video
        aspectRatioVideo = when (pm.getString("LS_ratioVideo", "3_4")!!) {
            "3_4" -> {
                ratioVideo = AspectRatio.RATIO_4_3
                Rational(3, 4)
            }
            else -> {
                ratioVideo = AspectRatio.RATIO_16_9
                Rational(9, 16)
            }
        }

        videoResolution = when (pm.getString("LS_videoResolution", "UHD")!!) {
            "UHD" -> QualitySelector.from(Quality.HIGHEST)  // con il mio dispositivo non posso registrare in 4k a 4:3
                                                            // quindi non metto Quality.UHD, altrimenti crasherebbe
            "FHD" -> QualitySelector.from(Quality.FHD)
            "HD" -> QualitySelector.from(Quality.HD)
            else -> QualitySelector.from(Quality.SD)
        }

        // -- Generali
        findViewById<Group>(R.id.Group_grid).visibility =
            if(pm.getBoolean("SW_grid", true)) View.VISIBLE else View.INVISIBLE // le righe sono al interno di un gruppo, quindi prendo direttamente quello
        mirror = pm.getBoolean("SW_mirror", true)
        hdr = pm.getBoolean("SW_HDR", false)
        feedback = pm.getBoolean("SW_feedback", true)
        saveMode = pm.getBoolean("SW_mode", true)

        createRecorder() // costruita un'istanza di Recorder
        changeMode(currentMode, true) // richiamo per cambiare la grandezza della preview e
        // ri eseguire il bind nel caso in cui si attivi/disattivi l'hdr

        try {
            changeZoom(sbZoom.progress, true)
        }
        catch (e : Exception)
        {
            Log.e(TAG,"Error changeZoom", e)
        }
    }

    /**
     * Metodo usato per cambiare lo zoom della camera.
     *
     * @param progress  valore della SeekBar.
     * @param bindAnyway booleano per forzare il rebind.
     */
    private fun changeZoom(progress : Int, bindAnyway : Boolean = false)
    {
        var reBind = false // evito di costruire la camera ogni volta

        // sbZoom va da 0 a 150, quindi i primi 50 valori sono per lo zoom con la ultra grand angolare,
        // gli altri per la camera grand angolare, non sono riuscito a recuperare la telephoto
        // valori corrispondenti a quale camara (Samsung S21)
        // 0 -> back default;   grand angolare
        // 1 -> front default;  ultra grand angolare
        // 2 -> back;           ultra grand angolare
        // 3 -> front;          grand angolare


        if(isRecording) // se sto registrando, non posso cambiare camera, quindi c'e' un valore di zoom diverso
            zoomLv = progress/sbZoom.max.toFloat() // calcolo per ottenere un valore compreso tra 0 e 1 per lo zoom
        else
        {
            if(progress<CHANGECAMERASEEKBAR) // sono sulle camere ultra grand angolari (changeCameraSeekBar = 50)
            {
                // sperimentalmente ho trovato che sul mio dispositivo (S21) al valore di zoomLv = 0.525 circa
                // lo zoom della camera ultra grand angolare corrisponde al valore della camera principale a 1.0x
                // quindi 2.14 = zoomLv*sbZoom.max/maxProgress = 0.525*200/49
                zoomLv = (progress.toFloat()/sbZoom.max * 2.25f)
                // calcolo per ottenere un valore tra 0 e 1 per lo zoom

                if(currentCamera==0) // se sono in back default
                {
                    currentCamera = 2 // passo in back grand angolare
                    reBind=true
                }
                else if(currentCamera==3) // se sono in front normale
                {
                    currentCamera = 1 // passo in front grand angolare
                    reBind=true
                }
            }
            else
            {
                zoomLv = (progress-CHANGECAMERASEEKBAR)/(sbZoom.max - CHANGECAMERASEEKBAR).toFloat()
                // calcolo per ottenere un valore tra 0 e 1 per lo zoom
                // e' presente - changeCameraSeekBar perche' devo escludere i valori sotto changeCameraSeekBar
                // quindi quando progress e' a 50 (=changeCameraSeekBar) allora zoomLv deve essere 0
                // mentre quando progress e' a 150 (=sbZoom.max) allora zoomLv deve essere 1

                if(currentCamera==2) // se sono in back grand angolare
                {
                    currentCamera = 0 // passo in back default
                    reBind=true
                }
                else if(currentCamera==1) // se sono in front grand angolare
                {
                    currentCamera = 3 // front in normale
                    reBind=true
                }
            }
        }


        val zoomState = camera.cameraInfo.zoomState
        val maxZoom : Float = zoomState.value?.maxZoomRatio!!

        btZoom05.text = getString(R.string.zoom_0_5x)
        btZoom10.text = getString(R.string.zoom_1_0x)
        btZoom05.backgroundTintList = getColorStateList(R.color.gray_onyx)
        btZoom10.backgroundTintList = getColorStateList(R.color.gray_onyx)
        btZoom05.setTextColor(getColor(R.color.floral_white))
        btZoom10.setTextColor(getColor(R.color.floral_white))

        if(currentCamera==0 || currentCamera == 3) // camera normale 1 -> 8
        {
            btZoomRec.text = "${(zoomLv*(maxZoom-1)+1).toString().substring(0,3)}x" // (zoomLv*(maxzoom-1)+1) fa si che visualizzi maxzoom come massimo e 1x come minimo
            btZoom10.text = "${(zoomLv*(maxZoom-1)+1).toString().substring(0,3)}x"
            btZoom10.backgroundTintList = getColorStateList(R.color.floral_white)
            btZoom10.setTextColor(getColor(R.color.black))
        }
        else // camera grand angolare 0.5 -> 8
        {
            btZoomRec.text = "${(zoomLv*(maxZoom-0.5)+0.5).toString().substring(0,3)}x" // (zoomLv*(maxzoom-0.5)+0.5) fa si che visualizzi maxzoom come massimo e 0.5x come minimo
            btZoom05.text = "${(zoomLv+0.5).toString().substring(0,3)}x"
            btZoom05.backgroundTintList = getColorStateList(R.color.floral_white)
            btZoom05.setTextColor(getColor(R.color.black))
        }

        if(bindAnyway || (reBind && !isRecording)) // se sta registrando non cambia fotocamera
        {
            changeSelectCamera()
            changeMode(currentMode, true) // per eseguire il bind
        }

        cameraControl.setLinearZoom(zoomLv) // cambia il valore dello zoom
        Log.d(TAG,"Zoom lv: $zoomLv, zoomState: ${zoomState.value}" )
        Log.d(TAG, "[current camera] - zoom: $currentCamera")
    }

    /**
     * Metodo per gestire il tocco dei pulsanti del volume.
     * Un tocco singolo scatta una foto, cambia il volume o cambia il livello dello zoom a seconda della modalita' selezionata dalle
     * impostazioni. Tenendo premuto il pulsante per piu' di [LONGCLICKDURATION] millisecondi parte la registrazione di un video se viene
     * premuto il tasto [KeyEvent.KEYCODE_VOLUME_DOWN] o viene richiamato il metodo [multishot] premendo [KeyEvent.KEYCODE_VOLUME_UP].
     */
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean
    {
        if(!(event?.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event?.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN))
            return super.dispatchKeyEvent(event)

        // gestisco la registrazione video tenendo premuto il pulsante del volume per almeno 1 secondo e lo interrompo quando alzo il dito
        if (event?.action == KeyEvent.ACTION_DOWN) {
            when(volumeKey) {
                "zoom" -> {
                    if(blockChangeMode) return true
                    // Volume_UP -> zoom in, Volume_DOWN -> zoom out
                    sbZoom.incrementProgressBy(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1)
                    return true
                }
                "volume" ->  return super.dispatchKeyEvent(event)
            }
            if(volumeTimer==null && volumeKey == "shot") {
                if(event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { // video tenendo premuto il pulsante per abbassare il volume
                    if(blockChangeMode) return true
                    volumeTimer = object: CountDownTimer(LONGCLICKDURATION, LONGCLICKDURATION) {
                        override fun onTick(millisUntilFinished: Long) {
                            // non fa nulla
                        }

                        override fun onFinish() {
                            if(!isRecording) {
                                val temporaryCountDown = countdown
                                countdown = 0   // faccio partire direttamente il video, senza countdown
                                changeMode(VIDEO_MODE)  // passo in modalita' video
                                timerShot(true) // inizio la registrazione
                                countdown = temporaryCountDown  // re-imposto il valore del countdown
                                isVolumeButtonLongPressed = true
                            }
                        }
                    }
                    volumeTimer?.start()
                }
                else { // event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    volumeTimer = object: CountDownTimer(LONGCLICKDURATION, LONGCLICKDURATION) {
                        override fun onTick(millisUntilFinished: Long) {
                            // non fa nulla
                        }

                        override fun onFinish() {
                            when (currentMode) {
                                VIDEO_MODE   // sono in modalita' video passo in foto
                                -> changeMode(PHOTO_MODE)
                                PHOTO_MODE   // multishot disponibile solo in modalita' poto
                                -> multishot(true)
                                else -> takePhoto()
                            }
                            isVolumeButtonClicked = true
                        }
                    }
                    volumeTimer?.start()
                }
            }
            return true
        }
        else if (event?.action == KeyEvent.ACTION_UP) {
            volumeTimer?.cancel()   // fermo il timer quando sollevo il dito dal pulsante
            volumeTimer = null
            if(isVolumeButtonLongPressed) {       // se sto registrando tenendo premuto il tasto, interrompo la registrazione
                timerShot(true)
                isVolumeButtonLongPressed = false
                return true
            }
            if(isVolumeButtonClicked) {
                multishot(false)  // termina il multishot
                isVolumeButtonClicked = false
                return true
            }


            // scatto da tocco singolo
            when (volumeKey) { // volume giu -> video
                "shot" -> {
                    if(event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)   // sono in modalita' video passo in foto
                        changeMode(VIDEO_MODE)
                    else if(currentMode == VIDEO_MODE) // se e' stato premuto volume up -> foto e sono in modalita' video passo a modalita' foto
                        changeMode(PHOTO_MODE) // funziona indipendentemente dalla modalita'

                    if(!isRecording)
                        timerShot(event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) // scatto una foto o inizio la registrazione di un video
                    else // sta registrando
                    {
                        if(event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                            takePhoto() // funziona indipendentemente dalla modalita'
                        else
                        timerShot(true) // Stoppa la registrazione video
                    }

                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Funzione per switchare tra le modalita';
     * quindi cambia i pulsanti visualizzati
     *
     * @param changeMode e' un intero che corrisponde alla modalita'
     * @param force booleano per cambiare comunque la grafica
     */
    private fun changeMode(setMode : Int, force : Boolean = false) {
        if(blockChangeMode) return

        if(scrollViewMode.visibility == View.INVISIBLE) // non posso cambiare modalita' mentre registro
            return

        if(force || currentMode != setMode){

            if(!force) // se c'e' anche un cambio di modalita' allora
                sbZoom.progress = CHANGECAMERASEEKBAR // ripiristino lo zoom

            viewPreview.visibility = View.INVISIBLE // nascondo la preview mentre cambio modalita'
            countDownText.postDelayed(Runnable {
                viewPreview.visibility = View.VISIBLE
            }, 900)

            btVideoMode.backgroundTintList = getColorStateList(R.color.gray_onyx)
            btVideoMode.setTextColor(getColor(R.color.floral_white))
            btPhotoMode.backgroundTintList = getColorStateList(R.color.gray_onyx)
            btPhotoMode.setTextColor(getColor(R.color.floral_white))
            btBokehMode.backgroundTintList = getColorStateList(R.color.gray_onyx)
            btBokehMode.setTextColor(getColor(R.color.floral_white))
            btNightMode.backgroundTintList = getColorStateList(R.color.gray_onyx)
            btNightMode.setTextColor(getColor(R.color.floral_white))

            btFlash.visibility = View.VISIBLE

            // modifiche grafiche e attivazione funzioni particolari
            when(setMode) {
                NIGHT_MODE -> {
                    btFlash.visibility = View.INVISIBLE
                    btNightMode.backgroundTintList = getColorStateList(R.color.floral_white)
                    btNightMode.setTextColor(getColor(R.color.black))
                    Log.d(TAG, "NIGHT MODE")
                    nightMode()
                    if(!nightMode()) {  // tento di accedere alla modalita' night ma non e' disponibile
                        changeMode(PHOTO_MODE, true) // ritorno in modalita' foto
                        return
                    }
                }
                BOKEH_MODE -> {
                    btBokehMode.backgroundTintList = getColorStateList(R.color.floral_white)
                    btBokehMode.setTextColor(getColor(R.color.black))
                    Log.d(TAG, "BOKEH MODE")
                    if(!bokehMode()) {   // tento di accedere alla modalita' bokeh ma non e' disponibile
                        changeMode(PHOTO_MODE, true) // ritorno in modalita' foto
                        return
                    }
                }
                PHOTO_MODE -> {
                    btPhotoMode.backgroundTintList = getColorStateList(R.color.floral_white)
                    btPhotoMode.setTextColor(getColor(R.color.black))
                    Log.d(TAG, "PHOTO MODE")
                    if(hdr) // se e' attiva l'impostazione del hdr
                        hdrMode()
                    else
                        bindCamera()
                }
                VIDEO_MODE -> {
                    btVideoMode.backgroundTintList = getColorStateList(R.color.floral_white)
                    btVideoMode.setTextColor(getColor(R.color.black))
                    Log.d(TAG, "VIDEO MODE")
                    bindCamera()
                }
            }
            if(setMode <= PHOTO_MODE)
                scrollViewMode.fullScroll(View.FOCUS_RIGHT)
            else
                scrollViewMode.fullScroll(View.FOCUS_LEFT)
        }


        currentMode = setMode
        Log.d(TAG, "currentMode: $currentMode")

        val constraintLayout: ConstraintLayout = findViewById(R.id.constraintLayout)

        val layoutParamsSetting = btSettings.layoutParams as ConstraintLayout.LayoutParams
        layoutParamsSetting.endToStart =    if(currentMode == NIGHT_MODE) R.id.vertical_centerline2
                                            else  R.id.vertical_centerline1
        btSettings.layoutParams = layoutParamsSetting

        val layoutParamsTimer = btTimer.layoutParams as ConstraintLayout.LayoutParams
        layoutParamsTimer.endToStart =  if(currentMode == NIGHT_MODE) R.id.vertical_centerline3
                                        else R.id.vertical_centerline2
        btTimer.layoutParams = layoutParamsTimer

        val layoutParamsQR = btQR.layoutParams as ConstraintLayout.LayoutParams
        layoutParamsQR.endToStart =   if(currentMode == NIGHT_MODE) R.id.vertical_centerline4
                                    else R.id.vertical_centerline3
        btQR.layoutParams = layoutParamsQR

        constraintLayout.requestLayout()

        setFlashMode() // se sono in modalita' video con flash ON accende il flash, altrimenti lo spegne

        if (setMode == NIGHT_MODE) selectFlashMode(FlashModes.OFF.ordinal)

        // cambia rapporto preview
        val aspect = if (setMode == VIDEO_MODE) aspectRatioVideo else aspectRatioPhoto
        val layoutParamsPreview = viewPreview.layoutParams as ConstraintLayout.LayoutParams
        layoutParamsPreview.dimensionRatio =
            "H,${aspect.numerator}:${aspect.denominator}" // Cambia l'aspect ratio desiderato qui
        viewPreview.layoutParams = layoutParamsPreview


        if (!timerOn) // se non c'e' il timer attivato
        {
            if (!isRecording) // se non sta registrando
                btShoot.setBackgroundResource( // cambio la grafica del pulsante in base a se sto registrando o no
                    if (setMode == VIDEO_MODE) R.drawable.in_recording_button else R.drawable.rounded_corner
                )
            recOptions()
        }

    }

    /**
     * Mi permette di ottenere l'inclinazione del dispositivo
     */
    private val orientationEventListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (blockRotation || orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return

                rotation = when (orientation) {
                    in 45 + DEADZONEANGLE..135 - DEADZONEANGLE -> 270
                    in 135 + DEADZONEANGLE..225 - DEADZONEANGLE -> 180
                    in 225 + DEADZONEANGLE..315 - DEADZONEANGLE -> 90
                    in 315 + DEADZONEANGLE..360, in 0..45 - DEADZONEANGLE -> 0
                    else -> return // Angolo morto
                }
                // e' stato inserito deadZoneAngle per avere un minimo di gioco prima di cambiare rotazione

                changeOrientation(rotation) // cambia effettivamente la rotazione
            }
        }
    }

    /**
     * Funzione per impostare l'orientamento dei tasti e della foto o del video.
     */
    private fun changeOrientation(rotate: Int) {
        if(!isRecording) // gira solo se non sta registrando, per salvare i video nel orientamento iniziale
        {
            rotateButton(rotate.toFloat())
            // Surface.ROTATION_0 e' = 0, ROTATION_90 = 1, ... ROTATION_270 = 3, quindi = rotation/90
            videoCapture?.targetRotation = rotate/90
        }
        imageCapture?.targetRotation = rotate/90 // e' fuori dall'if, in questo modo l'immagine e' sempre orientata correttamente
    }
    /**
     * Classe per riconoscere le gesture semplici; nel nostro caso implementiamo:
     * - [onSingleTapUp] che gestisce la gesture di tocco per fare il focus
     * - [onFling] che gestisce gli swipe per cambiare modalita'/camera
     */
    private inner class MyGestureListener : GestureDetector.SimpleOnGestureListener() {

        /**
         * Gestisce il singolo tocco
         */
        override fun onSingleTapUp(motionEvent: MotionEvent): Boolean {
            when (motionEvent.action) {
                MotionEvent.ACTION_UP -> {
                    // calcola la posizione corretta dove inserire il cerchietto del focus
                    focusView.x = viewPreview.x - focusView.width / 2 + motionEvent.x
                    focusView.y = viewPreview.y - focusView.height / 2 + motionEvent.y
                    focusView.visibility = View.VISIBLE
                    // mantiene il cerchietto visibile per un secondo
                    focusView.postDelayed(Runnable {
                        focusView.visibility = View.INVISIBLE
                    }, 1000)
                    // crea un punto di esposizione nella zona della preview toccata per gestire il focus
                    val factory = viewBinding.viewPreview.meteringPointFactory
                    val point = factory.createPoint(motionEvent.x, motionEvent.y)
                    // crea un'azione di focus a partire dal punto trovato
                    val action = FocusMeteringAction.Builder(point).build()
                    // inizia il focus nel punto specificato
                    cameraControl.startFocusAndMetering(action)
                }
            }
            return true
        }

        /**
         * Gestisce gli swipe orizzontali e verticali.
         * @param e1 Inizio dello swipe.
         * @param e2 Fine dello swipe.
         * @param velocityX Velocita' orizzontale dello swipe.
         * @param velocityY Velocita' verticale dello swipe.
         */
        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if(blockChangeMode) return super.onFling(e1, e2, velocityX, velocityY)

            val deltaY = e2.y - e1.y
            val deltaX = e2.x - e1.x

            if(abs(deltaX) < abs(deltaY))   // se trascino verso l'alto/basso
            {
                // swipe up-down
                if(abs(deltaY) > TRESHOLD && abs(velocityY)>TRESHOLD_VELOCITY){
                    rotateCamera()
                    Log.d(TAG, "SWIPE UP DETECTED")
                }
            }
            else if(abs(deltaX) > TRESHOLD && abs(velocityX)>TRESHOLD_VELOCITY){
                var setMode = currentMode
                if(deltaX>0)  // right swipe: video -> foto -> bokeh -> night
                    setMode++
                else if(deltaX<0)    // left swipe: night -> bokeh -> foto -> video
                    setMode--
                if(setMode > NIGHT_MODE)
                    setMode = NIGHT_MODE
                else if (setMode < VIDEO_MODE)
                    setMode = VIDEO_MODE
                changeMode(setMode)
            }
            return super.onFling(e1, e2, velocityX, velocityY)
        }
    }

    /**
     * Classe per riconoscere la gesture di scaling per modificare lo zoom.
     */
    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            // Aggiorna lo zoom della fotocamera
            Log.d(TAG, "[zoom] $scaleFactor")

            if(scaleFactor>1) // se pinch in allora zoomo
                sbZoom.incrementProgressBy(1) // cambio il valore della SeekBar che a sua volta cambia il valore dello zoom
            else // altrimienti e' pinch out e allora dezoomo
                sbZoom.incrementProgressBy(-1)
            return true
        }
    }

    /**
     * Seleziona la fotocamera anteriore se attiva quella posteriore e viceversa.
     * @param override permette di decidere quale camera usare.
     * @param back se true seleziona la camera back.
     */
    private fun rotateCamera(override:Boolean = false, back: Boolean = true) { // id = 0 default back, id = 1 front default

        if((override && !back) || (currentCamera== 0 || currentCamera == 2))
            currentCamera = 3 // passo in front
        else if((override && back) || (currentCamera==1 || currentCamera==3))
            currentCamera = 0 // passo in back
        sbZoom.progress = CHANGECAMERASEEKBAR

        changeSelectCamera()
        changeMode(currentMode, true) // per eseguire il bind

        Log.d(TAG, "[current camera]  - rotate: $currentCamera")
    }

    /**
     * Cambia la camera in base a quella selezionata.
     */
    private fun changeSelectCamera()
    {
        frontCamera = currentCamera % 2 != 0

        cameraSelector =
            try { // dato che uso gli id della mia camera allora potrebbe non esistere quella camera
                availableCameraInfos[currentCamera].cameraSelector
            } catch (e : Exception) {
                frontCamera = currentCamera % 2 != 0
                if (frontCamera) // se e' camera 0 o 2 e' back
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else
                    CameraSelector.DEFAULT_BACK_CAMERA
            }

        val extensionsManager = extensionsManagerFuture.get()

        // Gestisce tre diversi use cases
        isHdrAvailable = false
        isBokehAvailable = false
        isNightAvailable = false

        // Verifica che queste funzionalita' siano disponibili per il dispositivo dell'utente
        if(extensionsManager.isExtensionAvailable(cameraSelector, ExtensionMode.HDR)) {
            hdrCameraSelector = extensionsManager.getExtensionEnabledCameraSelector(
                cameraSelector, ExtensionMode.HDR)  // attiva l'estensione HDR
            isHdrAvailable = true
        }

        if(extensionsManager.isExtensionAvailable(cameraSelector, ExtensionMode.BOKEH)){
            bokehCameraSelector = extensionsManager.getExtensionEnabledCameraSelector(
                cameraSelector, ExtensionMode.BOKEH)    // attiva l'estensione BOKEH
            isBokehAvailable = true
        }


        if(extensionsManager.isExtensionAvailable(cameraSelector, ExtensionMode.NIGHT)) {
            nightCameraSelector = extensionsManager.getExtensionEnabledCameraSelector(
                cameraSelector, ExtensionMode.NIGHT)    // attiva l'estensione NIGHT
            isNightAvailable = true
        }

        Log.d(TAG, "change Camera, current [$currentCamera], status: hdr: [$isHdrAvailable]; bokeh: [$isBokehAvailable]; Night: [$isNightAvailable]")
    }

    /**
     * Metodo che permette di scattare una foto o di registrare un video tenendo conto dei secondi di countdown per l'autoscatto.
     *
     * @param record True se in modalita' video.
     *               False se in modalita' foto.
     */
    private fun timerShot(record : Boolean){
        if(timerOn) { // se c'e' il timer in funzione allora lo blocco
            Log.d(TAG,"Timer bloccato")
            timerOn = false
            timer.cancel()
            countDownText.visibility = View.INVISIBLE
            scrollViewMode.visibility = View.VISIBLE
            findViewById<Group>(R.id.Group_extraFunc).visibility = View.VISIBLE
            changeMode(currentMode) // ripristino visuale tasti corretta
            return
        }
        if(isRecording) {
            if(record) // se sto gia' registrando e tengo premuto il pulsante rosso in modalita' foto
            // o premo il pulsante per fermare la registrazione in modalita' video
                captureVideo()
            else // se sto gia' registrando e premo il pulsante rosso in moodalita' foto
                 // scatta una foto senza usare il timer
                takePhoto()
            return
        }

        timerOn = true  // il timer si attiva
        timer = object : CountDownTimer(countdown*1000, 1000){
            override fun onTick(remainingMillis: Long) {
                //btTimer.visibility = View.INVISIBLE //rendo invisibile il pulsante del timer durante il countdown
                findViewById<Group>(R.id.Group_extraFunc).visibility = View.INVISIBLE
                btShoot.setBackgroundResource(R.drawable.rounded_stop_button)   // tasto per fermare il countdown e l'autoscatto
                countDownText.text = "${remainingMillis/1000 + 1}"  // mostra a schermo i secondi rimanenti allo scatto o alla registrazione
                countDownText.visibility = View.VISIBLE
                scrollViewMode.visibility = View.INVISIBLE
                Log.d(TAG, "Secondi rimanenti: "+remainingMillis/1000)
            }
            override fun onFinish() {
                timerOn = false
                countDownText.visibility = View.INVISIBLE
                scrollViewMode.visibility = View.VISIBLE
                findViewById<Group>(R.id.Group_extraFunc).visibility = View.VISIBLE
                if(record)
                    captureVideo()
                else
                    takePhoto()
                changeMode(currentMode) // richiamo change mode per impostare la grafica corretta
            }

        }.start()
        Log.d(TAG, "Secondi ristabiliti: $countdown")
    }

    /**
     * Ruota i pulsanti a seconda dell'orientamento dello schermo.
     *
     * @param angle e' il numero di gradi per ruotare i tasti.
     */
    private fun rotateButton(angle : Float)
    {
        btFlash.rotation = angle
        btGallery.rotation = angle
        btQR.rotation = angle
        btRotation.rotation = angle
        btSettings.rotation = angle
        btTimer.rotation = angle
        btZoom05.rotation = angle
        btZoom10.rotation = angle
        btZoomRec.rotation = angle
        cmRecTimer.rotation = angle
        countDownText.rotation = angle
    }

    /**
     * Cambia i secondi di countdown passando al valore ammissibile successivo.
     */
    private fun switchTimerMode() {
        currTimerMode = TimerModes.next(currTimerMode)
        setTimerMode()
        setTimerIcon(currTimerMode.text)
    }

    /**
     * Permette di selezionare i secondi di countdown desiderati da una lista di possibili valori (0, 3, 5, 10).
     * @param ordinal l'indice che indica l'elemento da selezionare dalla lista dei valori di [TimerModes].
     */
    private fun selectTimerMode(ordinal: Int?): Boolean{
        if(ordinal == null) {
            throw IllegalArgumentException()
        }
        currTimerMode = TimerModes.values()[ordinal]    // cambia la modalita' corrente
        setTimerMode()
        setTimerIcon(currTimerMode.text)
        return true
    }

    /**
     * Imposta i secondi di countdown per l'autoscatto.
     */
    private fun setTimerMode(){
        countdown = when(currTimerMode){
            TimerModes.OFF -> 0
            TimerModes.ON_3 -> 3
            TimerModes.ON_5 -> 5
            TimerModes.ON_10 -> 10
        }
    }

    /**
     * Cambia l'icona che indica i secondi di countdown per l'autoscatto e il colore dell'icona: bianco se l'autoscatto e' impostato su OFF,
     * giallo altrimenti.
     *
     * @param status OFF se e' disattivato l'autoscatto, oppure i secondi di countdown (3, 5, 10)
     */
    private fun setTimerIcon(status : String){
        btTimer.backgroundTintList = getColorStateList(R.color.aureolin_yellow)
        btTimer.setBackgroundResource(
            when(status){
                "OFF" -> {
                    btTimer.backgroundTintList = getColorStateList(R.color.floral_white)
                    R.drawable.timer_0
                }
                "3" -> R.drawable.timer_3
                "5" -> R.drawable.timer_5
                else -> R.drawable.timer_10
            }
        )
    }

    /**
     * Metodo per passare alla prossima modalita' del flash, nell'ordine:
     *  - OFF -> (0)
     *  - ON -> (1)
     *  - AUTO -> (2).
     */
    private fun switchFlashMode() {
        currFlashMode = FlashModes.next(currFlashMode)
        setFlashMode()
    }

    /**
     * Metodo per cambiare [currFlashMode].
     */
    private fun selectFlashMode(ordinal: Int?): Boolean {
        if(ordinal == null) {
            throw IllegalArgumentException()
        }
        currFlashMode = FlashModes.values()[ordinal]
        setFlashMode()
        return true
    }

    /**
     * Metodo che permette di impostare la modalita' del flash specificata da [currFlashMode].
     */
    private fun setFlashMode() {
        cameraControl.enableTorch(false)
        when(currFlashMode) {
            FlashModes.OFF -> {
                btFlash.setBackgroundResource(R.drawable.flash_off)
                btFlash.backgroundTintList = getColorStateList(R.color.floral_white)
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
            }
            FlashModes.ON -> {
                btFlash.setBackgroundResource(R.drawable.flash_on)
                btFlash.backgroundTintList = getColorStateList(R.color.aureolin_yellow)
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON
                //Nel caso in cui si stia registrando attiva il flash in modalita' ON
                if(currentMode == VIDEO_MODE) cameraControl.enableTorch(true)
            }
            FlashModes.AUTO -> {
                btFlash.setBackgroundResource(R.drawable.flash_auto)
                btFlash.backgroundTintList = getColorStateList(R.color.aureolin_yellow)
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_AUTO
            }
        }
    }

    /**
     * Abilita il "sensore" per l'angolo.
     */
    override fun onStart() {
        super.onStart()
        orientationEventListener.enable()
    }

    /**
     * Disabilita il "sensore" per l'angolo.
     */
    override fun onStop() {
        super.onStop()
        orientationEventListener.disable()
    }

    /**
     * Quando l'applicazione viene messa in background salva le preference
     * da ripristinare all'avvio, anche se viene completamente chiusa.
     *
     * Ho deciso di non salvare tutti i dati, quindi escludo lo zoom
     * e anche la modalita' in cui viene lasciata.
     */
    override fun onPause()
    {
        super.onPause()

        // Store values between instances here
        val preferences = getPreferences(MODE_PRIVATE)
        val editor = preferences.edit()

        if(currentCamera%2==0) // a differenza di onSaveInstanceState non salvo lo zoom e la camera corretta
        // salvo solo se e' frontale o posteriore
            editor.putInt(KEY_CAMERA, 0)
        else
        {
            if(availableCameraInfos.size == 4)
                editor.putInt(KEY_CAMERA, 3)
            else
                editor.putInt(KEY_CAMERA, 1)
        }
        editor.putString(KEY_FLASH, currFlashMode.toString())
        editor.putString(KEY_TIMER, currTimerMode.toString())
        editor.putInt(KEY_MODE, currentMode)

        editor.apply()

        disableButton(false) // sblocco il passaggio di modalita'

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Ripristina lo zoom.
     */
    override fun onResume()
    {
        super.onResume()
        Log.d(TAG, "onResume")
        try { /*
            il lifecycle dello zoom viene chiuso con la chiusura dell'app,
             e non viene ripristinato manualmente, quindi chiamo changeZoom
             con il valore sbZoom.progress che e' ancora salvato
             Se invece l'applicazione va in background e viene killata da android
             allora changeZoom(sbZoom.progress) restituisce errore che non e'
             necessario gestire, in quel caso allora i dati sono stati salvati dal Bundle
             e ripristinati da loadFromBundle; se invece viene killata dall'utente
             allora non viene ripristinato lo stato
             */
            if(::camera.isInitialized && allPermissionsGranted())
            {
                changeZoom(sbZoom.progress)
                loadFromSetting() // se camera non e' ancora impostata o se e' null da errore
            }
        }
        catch (e : Exception) {
            Log.e(TAG, "Exception $e", e)
        }
    }

    /**
     * Salva la camera corrente (a differenza delle preference in cui salvo solo
     * se e' anteriore o posteriore), lo zoom e la modalita'.
     */
    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt(KEY_CAMERA, currentCamera)
        savedInstanceState.putInt(KEY_ZOOM, sbZoom.progress)
        savedInstanceState.putInt(KEY_MODE, currentMode)
    }

    /**
     * Esegue l'unbind.
     */
    override fun onDestroy() {
        super.onDestroy()
        //cameraExecutor.shutdown() //todo forse si puo' eliminare
        // Libera le risorse della fotocamera
    }
}