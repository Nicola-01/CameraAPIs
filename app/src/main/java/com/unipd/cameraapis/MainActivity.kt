package com.unipd.cameraapis

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Rect
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.impl.ImageCaptureConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.impl.VideoCaptureConfig
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
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
import java.util.concurrent.Executors
import kotlin.math.abs


class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    // Select back camera as a default
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    // Widget di activity_main.xml
    private lateinit var BT_flash : Button
    private lateinit var BT_gallery : Button
    private lateinit var BT_pause : Button
    private lateinit var BT_photoMode : Button
    private lateinit var BT_recMode : Button
    private lateinit var BT_rotation : Button
    private lateinit var BT_shoot : Button
    private lateinit var BT_stop : Button
    private lateinit var BT_timer : Button
    private lateinit var BT_zoom0_5 : Button
    private lateinit var BT_zoom1_0 : Button
    private lateinit var BT_zoomRec : Button
    private lateinit var BT_QR : Button
    private lateinit var BT_settings : Button
    private lateinit var focusCircle : View
    private lateinit var focusView : View
    private lateinit var viewPreview : View
    private lateinit var SB_zoom : SeekBar
    private lateinit var CM_recTimer : Chronometer
    private lateinit var countDownText : TextView

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

    private val changeCameraSeekBar = 50
    private var CM_pauseAt : Long = 0
    private var countdown : Long = 0
    private var currFlashMode : FlashModes = FlashModes.OFF
    private var currTimerMode : TimerModes = TimerModes.OFF
    private var currentCamera = 0
    // 0 -> back default;   grand angolare
    // 1 -> front default;  ultra grand angolare
    // 2 -> back;           ultra grand angolare
    // 3 -> front;          grand angolare
    private var zoomLv : Float = 0.toFloat() // va da 0 a 1

    private var recordMode = false
    private var isRecording = false
    private var inPause = false
    private var timerOn = false
    private var qrscanner = true
    private var captureJob: Job? = null
    private var isBT_shootLongClicked = false
    private var isVolumeButtonClicked : Boolean = false

    private lateinit var volumeKey : String
    private lateinit var aspectRatioPhoto : Rational
    private lateinit var aspectRatioVideo : Rational
    private var hdr = true
    private var gps = false
    private var feedback = true

    private var savedInstanceState_: Bundle? = null
    val showPopUp = PopUpFragment()
    var popUpVisible = false

    companion object {

        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd_HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10

        private const val KEY_CAMERA = "CurrentCamera"
        private const val KEY_FLASH = "FlashMode"
        private const val KEY_TIMER = "TimerMode"
        private const val KEY_ZOOM = "ZoomProgress"
        private const val KEY_REC = "RecordMode"
        private const val KEY_QRCODE = "qrscanner"

        private const val TOUCH_THRESHOLD = 0.1

        private const val LONGCLICKDURATION = 1000L

        private const val DOUBLE_CLICK_DELTA_TIME : Long = 300      // Tempo entro il quale viene rilevato il doppio tocco

        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
            }.toTypedArray()
    }

    /**
     * Todo commento
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        savedInstanceState_ = savedInstanceState

        createElement() // inizializza le variabili

        // Controlla se sono stati forniti i permessi
        if (allPermissionsGranted())
            startCamera()
        else
            askPermission()

        //Todo ???? che fa?
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun askPermission() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        Log.d(TAG, "Permission asked")
    }

    /**
     * TODO: da commentare
     */
    fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * TODO: da commentare
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            }
            else if(permissionDenyAsk)
            {
                val intent = Intent(this, PermissionDenyActivity::class.java)
                startActivityForResult(intent, 0)
            }
            else if(!popUpVisible) // limita le apperture delle schede
            {
                showPopUp.show(supportFragmentManager, "showPopUp")
                showPopUp.onDismissListener = {
                    popUpVisible = false
                    if (allPermissionsGranted())
                        startCamera()
                    else {
                        val intent = Intent(this, PermissionDenyActivity::class.java)
                        startActivityForResult(intent, 0)
                    }
                }
                popUpVisible = true
            }
        }
        Log.d(TAG, "Permission Request")
    }

    var permissionDenyAsk = false;

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == 1)
        {
            permissionDenyAsk = true
            askPermission()
        }
        else
            finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val preferences = getPreferences(MODE_PRIVATE)

            // recupero le variabili dalle preferences
            var h = preferences.getInt("bottomBandHeight", -1)

            Log.d(TAG, "height $h")

            var bottomBand = findViewById<View>(R.id.VW_bottomBand)
            val layoutParams = bottomBand.layoutParams

            if (h == -1) // primo avvio del app
            {
                val displayMetrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(displayMetrics)
                val height = displayMetrics.heightPixels
                val width = displayMetrics.widthPixels

                var bottomBand = findViewById<View>(R.id.VW_bottomBand)
                val layoutParams = bottomBand.layoutParams
                h = height - viewPreview.bottom // Imposta l'altezza desiderata in pixel

                val editor = preferences.edit()
                editor.putInt("bottomBandHeight", h)
                editor.apply()
            }

            layoutParams.height = h
            bottomBand.layoutParams = layoutParams
        }
    }

    /**
     * Funzione per istanziare elementi del activity_main.xml;
     * assagnazione dei widget e altre variabili
     */
    private fun createElement()
    {
        BT_flash = viewBinding.BTFlash
        BT_gallery = viewBinding.BTGallery
        BT_pause = viewBinding.BTPause
        BT_photoMode = viewBinding.BTPhotoMode
        BT_recMode = viewBinding.BTRecordMode
        BT_rotation = viewBinding.BTRotation
        BT_shoot = viewBinding.BTShoots
        BT_stop = viewBinding.BTStop
        BT_timer = viewBinding.BTTimer
        BT_zoom0_5 = viewBinding.BT05
        BT_zoom1_0 = viewBinding.BT10
        BT_zoomRec = viewBinding.BTZoomRec
        BT_QR = viewBinding.BTQrcode
        BT_settings = viewBinding.BTSettings
        CM_recTimer = viewBinding.CMRecTimer
        CM_recTimer.format = "%02d:%02d:%02d"
        focusCircle = viewBinding.FocusCircle
        SB_zoom = viewBinding.SBZoom
        countDownText = viewBinding.TextTimer
        focusView = viewBinding.FocusCircle
        viewPreview = viewBinding.viewPreview

        scaleDown = AnimationUtils.loadAnimation(this,R.anim.scale_down)
        scaleUp = AnimationUtils.loadAnimation(this,R.anim.scale_up)

        gestureDetector = GestureDetector(this, MyGestureListener())
        scaleGestureDetector = ScaleGestureDetector(this, ScaleGestureListener())

    }

    /**
     * Funzione per l'assegnazione dei Listener ai widget
     * Todo: da finire di commentare
     */
    private fun createListener()
    {
        //Con un click sul pulsante si passa alla modalità successiva del flash
        BT_flash.setOnClickListener { switchFlashMode() }
        /*Con un click prolungato si apre un menù contestuale che permette di selezionare una
          specifica modalità del flash*/
        BT_flash.setOnCreateContextMenuListener { menu, v, menuInfo ->
            menu.setHeaderTitle("Flash")
            for(mode in FlashModes.values()) {
                val item: MenuItem = menu.add(Menu.NONE, mode.ordinal, Menu.NONE, mode.text)
                item.setOnMenuItemClickListener { i: MenuItem? ->
                    selectFlashMode(i?.itemId)
                    true // Signifies you have consumed this event, so propogation can stop.
                }
            }
        }
        //Con un click sul pulsante di apre la galleria
        BT_gallery.setOnClickListener{
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

        BT_pause.setOnClickListener{ pauseVideo() }
        BT_photoMode.setOnClickListener { changeMode(false) }
        BT_recMode.setOnClickListener { changeMode(true) }
        BT_rotation.setOnClickListener {
            rotateCamera()
            if(feedback) it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }

        BT_shoot.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                captureJob?.cancel()
                captureJob = null
                true
            }
            false
        }

        BT_shoot.setOnClickListener {
            timerShot(recordMode)
            if(feedback) it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }

        BT_shoot.setOnLongClickListener{
            if (recordMode) {
                timerShot(true)
                if(feedback) it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                captureJob = CoroutineScope(Dispatchers.Main).launch {
                    while (isActive) {
                        takePhoto()
                        delay(500) // Intervallo tra i singoli scatti
                    }
                }
            }
            true // Restituisce true per indicare che l'evento di click lungo è stato gestito correttamente
        }

        BT_stop.setOnClickListener{
            timerShot(recordMode)
        }
        BT_timer.setOnClickListener { switchTimerMode() }
        BT_timer.setOnCreateContextMenuListener { menu, v, menuInfo ->
            menu.setHeaderTitle("Timer")
            for(mode in TimerModes.values()) {
                val item: MenuItem = menu.add(Menu.NONE, mode.ordinal, Menu.NONE, mode.text)
                item.setOnMenuItemClickListener { i: MenuItem? ->
                    selectTimerMode(i?.itemId)
                    true // Signifies you have consumed this event, so propogation can stop.
                }
            }
        }

        BT_zoom1_0.setOnClickListener { SB_zoom.progress = changeCameraSeekBar }
        BT_zoom0_5.setOnClickListener{ SB_zoom.progress = 0 }
        SB_zoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                changeZoom(progress)
                if(feedback && progress%5 == 0 && fromUser) // ogni 5 do un feedback, e solo se muovo manualmente la SB
                    SB_zoom.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            override fun onStartTrackingTouch(seek: SeekBar) = Unit
            override fun onStopTrackingTouch(seek: SeekBar) = Unit
        })

        /*Con un click sulla view che contiene la preview della camera si può spostare il focus su
          una specifica zona*/

        viewPreview.setOnTouchListener(View.OnTouchListener setOnTouchListener@{ _, event ->
            gestureDetector.onTouchEvent(event)
            scaleGestureDetector.onTouchEvent(event)
            true
        })

        /*
        // listener per il pulsante QR
        BT_QR.setOnClickListener {
            qrscanner = !qrscanner
            QrCode(qrscanner)

            //Todo: butta dentro QrCode plz, che lo richiamo dal loadBundle
            //Todo: inoltre prima di mostrare risultati contollare che il timer sia disattivato, -> "timerOn"

            val intentIntegrator = IntentIntegrator(this)
            intentIntegrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            intentIntegrator.setOrientationLocked(true)
            intentIntegrator.setPrompt("Scanning")
            intentIntegrator.initiateScan()


        } */

        BT_settings.setOnClickListener {view ->
            startActivity(Intent(view.context, SettingsActivity::class.java))
        }

    }

    fun QrCode(status: Boolean)
    {
        if(status)
            BT_QR.backgroundTintList = getColorStateList(R.color.aureolin_yellow)
        else
            BT_QR.backgroundTintList = getColorStateList(R.color.white)


    }

    // risultato dello scan
    var scanResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        result ->
        if(result.resultCode==Activity.RESULT_OK){
            val scanIntent : Intent? = result.data
            // ....
        }
    }

    /**
     * Ricostruisce la camera
     */
    private fun buildCamera()
    {
        cameraSelector =
            try { // dato che uso gli id della mia camera allora potrebbe non esistere qulla camera
                availableCameraInfos[currentCamera].cameraSelector
            } catch (e : Exception) {
                if (currentCamera % 2 == 0) // se è camera 0 o 2 è back
                    CameraSelector.DEFAULT_BACK_CAMERA
                else
                    CameraSelector.DEFAULT_FRONT_CAMERA
            }
        try {
            cameraProvider.unbindAll()            // Unbind use cases before rebinding
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture) // devo ricostruire la camera ogni volta, dato che cambio al camera
            // in quato cambio la camera
            cameraControl = camera.cameraControl
        } catch(e: Exception) {
            Log.e(TAG, "Build failed", e)
        }
    }

    /**
     * TODO: commentare e sistemare
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewPreview.surfaceProvider)
                }

            recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .setAspectRatio(AspectRatio.RATIO_16_9)
                .build()
            videoCapture = VideoCapture.withOutput(recorder)


            imageCapture = ImageCapture.Builder().setFlashMode(ImageCapture.FLASH_MODE_OFF).build()

            // Select back camera as a default
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Crea un oggetto CameraSelector per la fotocamera ultra grandangolare
            availableCameraInfos = cameraProvider.availableCameraInfos
            Log.i(TAG, "[startCamera] available cameras Info:$availableCameraInfos")
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val availableCamera : Array<String> = cameraManager.cameraIdList
            Log.i(TAG, "[startCamera] available cameras:${availableCamera}")

            try {
                createListener() // creo i Listener
                buildCamera()
                loadFromBundle(savedInstanceState_) // carico gli elementi dal Bundle/Preferences
                setFlashMode() // non so perchè ma se lo lascio al interno di loadFromBundle, viene modificato ma successivamente perde lo stato
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Metodo per scattare una foto usando le impostazioni di [imageCapture]
     */
    private fun takePhoto() {
        //Log.d(TAG,"ClickListener")
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return
        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

            //put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraAPIs")
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        BT_shoot.startAnimation(scaleDown)
        viewPreview.startAnimation(scaleUp)
        imageCapture.takePicture( // caso d'uso
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    /*val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)*/
                }
            }
        )
        BT_timer.visibility = View.VISIBLE   //rendo di nuovo visibile il pulsante del timer dopo aver scattato la foto
    }

    /**
     * TODO: commentare e sistemare
     *
     */
    private fun captureVideo() : Boolean {
        val videoCapture = this.videoCapture ?: return true

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return true
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")

            //put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/CameraAPIs")
        }

        if(currFlashMode == FlashModes.ON) cameraControl.enableTorch(true)

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        startRecording(true)
                        inPause = false
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " + "${recordEvent.outputResults.outputUri}"
                            if(currFlashMode == FlashModes.ON) { cameraControl.enableTorch(false) }
                            //Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " + "${recordEvent.error}")
                            if(currFlashMode == FlashModes.ON) { cameraControl.enableTorch(false) }
                        }
                        startRecording(false)
                        BT_timer.visibility = View.VISIBLE   //rendo di nuovo visibile il pulsante del timer dopo la registrazione
                    }
                }
            }
        return true
    }

    /**
     * Metodo per impostare la visuale durante la regisrazione
     *
     * @param status True se è stata avviata la registrazione;
     *               False se è stata interrotta
     */
    private fun startRecording(status : Boolean)
    {
        val viewPH : Int
        val viewVI : Int
        isRecording = status
        if(status){
            viewPH = View.INVISIBLE
            viewVI = View.VISIBLE

            CM_recTimer.base = SystemClock.elapsedRealtime() // resetto il timer
            CM_recTimer.start()

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // mantiene lo schermo attivo durante la registrazione
        }
        else
        {
            viewPH = View.VISIBLE
            viewVI = View.INVISIBLE
            CM_recTimer.stop()

            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // permette allo schermo di spegnersi
        }
        recOptions() // cambio la grafica del pulsante

        // nascondo/visualizzo
        BT_rotation.visibility = viewPH
        BT_gallery.visibility = viewPH
        BT_zoom1_0.visibility = viewPH
        BT_zoom0_5.visibility = viewPH

        // visualizzo/nascondo
        BT_zoomRec.visibility = viewVI
        CM_recTimer.visibility = viewVI

        // se inizio a registrare non posso più cambiare camera,
        // quindi devo sistemare il valore della seekbar
        if(status) {
            SB_zoom.progress = (zoomLv*SB_zoom.max).toInt()
        }
        else
        {
            if(currentCamera==0 || currentCamera==3) // camere normali
                SB_zoom.progress = (zoomLv*(SB_zoom.max - changeCameraSeekBar)).toInt() + changeCameraSeekBar
            else // camere ultra grand angolari
                SB_zoom.progress = (zoomLv*SB_zoom.max*0.54).toInt()
        }
    }

    /**
     * Funzione per mettere in pausa o ripristinare la registrazione
     */
    private fun pauseVideo() {
        // inPause = true se la registrazione è in pausa
        if(inPause) {
            recording?.resume() // ripristina registrazione
            CM_recTimer.base = SystemClock.elapsedRealtime() - CM_pauseAt // calcolo per riesumare il timer correttamente
            CM_recTimer.start()
            BT_pause.setBackgroundResource(R.drawable.pause_button) // cambio grafica al pulsante
        }
        else {
            recording?.pause() // mette in pausa la registrazione
            CM_recTimer.stop()
            CM_pauseAt = SystemClock.elapsedRealtime() - CM_recTimer.base
            BT_pause.setBackgroundResource(R.drawable.play_button) // cambio grafica al pulsante
        }
        inPause = !inPause
    }

    /**
     * Funzione per visualizzare i comandi corretti;
     * Se sono in modalità foto il pulsante è bianco,
     * se inizio a registrare (sempre in modalità foto) diventa rosso,
     * se sono in modalità video e non sto registrando è bianco con pallino rosso;
     * se sono in modalità video e sto registrando mostra i tasti per fermare e riprendere la registrazione
     */
    private fun recOptions()
    {
        BT_shoot.visibility = if(recordMode && isRecording) View.INVISIBLE else View.VISIBLE
        findViewById<Group>(R.id.Group_rec).visibility = if(recordMode && isRecording) View.VISIBLE else View.INVISIBLE
        // R.id.Group_rec è un gruppo contenente i pulsanti per fermare e riprendere la registrazione video

        if(recordMode && !isRecording) // scelta del pulsante
            BT_shoot.setBackgroundResource( R.drawable.in_recording_button)
        else if(!recordMode)  {
            if(isRecording)
                BT_shoot.setBackgroundResource( R.drawable.rounded_corner_red)
            else
                BT_shoot.setBackgroundResource( R.drawable.rounded_corner)
        }
    }

    /**
     * Metodo per ricaricare i valori nel bundle o nelle preferences.
     * Non è nel onCreate perchè usa variabili che vengono dichiarate nel startCamera
     */
    private fun loadFromBundle(savedInstanceState : Bundle?)
    {
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
        var progress = changeCameraSeekBar

        qrscanner = preferences.getBoolean(KEY_QRCODE, true)
        QrCode(qrscanner)

        if (savedInstanceState != null) { // controlo che ci sia il bundle
            //recupero variabili dal bundle
            currentCamera = savedInstanceState.getInt(KEY_CAMERA)
            // se già ricaricato da preferences lo sovrascrivo,
            // in quanto con preference salvo solo se è posteriore o anteriore
            // mentre nel bundle salvo effettivamente la camera corretta
            progress = savedInstanceState.getInt(KEY_ZOOM)
            recordMode = savedInstanceState.getBoolean(KEY_REC)

            SB_zoom.progress = progress
            changeZoom(progress, true) // cambio zoom e forzo il rebuild
            changeMode(recordMode)
        }
        // uso changeZoom per cambiare lo zoom e ricostruire la camera
        changeZoom(progress, true) // cambio zoom e forzo il rebuild
    }

    private fun loadFromSetting() {
        val pm = PreferenceManager.getDefaultSharedPreferences(this)

        // -- Impostazioni Tasti
        volumeKey = pm.getString("LS_volumeKey","zoom")!!

        // -- Foto

        aspectRatioPhoto = when (pm.getString("LS_ratioPhoto", "3_4")!!) {
            "3_4" -> Rational(3, 4)
            "9_16" -> Rational(9, 16)
            "1_1" -> Rational(1, 1)
            "full" -> {
                val metrics = DisplayMetrics()
                val display = windowManager.defaultDisplay
                display.getRealMetrics(metrics)
                Rational(metrics.widthPixels, metrics.heightPixels)
            }
            else -> Rational(4, 3) // Rapporto d'aspetto predefinito se nessun caso corrisponde
        }

        try {
            imageCapture?.setCropAspectRatio(aspectRatioPhoto)
        }
        catch (e : Exception)
        {
            Log.e(TAG, "[LoadFromSetting] $e")
        }

        // -- Video
        aspectRatioVideo = when (pm.getString("LS_ratioVideo", "3_4")!!) {
            "3_4" -> Rational(3, 4)
            "9_16" -> Rational(9, 16)
            "1_1" -> Rational(1, 1)
            "full" -> {
                val metrics = DisplayMetrics()
                val display = windowManager.defaultDisplay
                display.getRealMetrics(metrics)
                Rational(metrics.widthPixels, metrics.heightPixels)
            }
            else -> Rational(4, 3) // Rapporto d'aspetto predefinito se nessun caso corrisponde
        }

         var videoResolution = when (pm.getString("LS_videoResolution", "UHD")!!) {
            "UHD" -> QualitySelector.from(Quality.UHD)
            "FHD" -> QualitySelector.from(Quality.FHD)
            "HD" -> QualitySelector.from(Quality.HD)
            else -> QualitySelector.from(Quality.SD)
        }

        try {

        }
        catch (e : Exception)
        {
            Log.e(TAG, "[LoadFromSetting] $e")
        }



        changeMode(recordMode) // richiamo per cambiare la grandezza della preview

        // -- Generali
        findViewById<Group>(R.id.Group_grid).visibility =
            if(pm.getBoolean("SW_grid", true)) View.VISIBLE else View.INVISIBLE // le righe sono al interno di un gruppo, quindi prendo direttamente quello
        hdr = pm.getBoolean("SW_HDR", true)
        gps = pm.getBoolean("SW_GPS", true)
        feedback = pm.getBoolean("SW_feedback", true)


    }

    /**
     * Metodo usato per cambiare lo zoom della camera
     *
     * @param progress  valore della SeekBar
     * @param buildAnyway booleano per forzare il rebuild
     */
    private fun changeZoom(progress : Int, buildAnyway : Boolean = false)
    {
        var reBuild = false // evito di costruitr la camera ogni volta

        // SB_zoom va da 0 a 150, quindi i primi 50 valori sono per lo zoom con la ultra grand angolare,
        // gli altri per la camera grand angolare, non sono riuscito a recoperare la telephoto
        // valori corrispondenti a quale camara (Samsung S21)
        // 0 -> back default;   grand angolare
        // 1 -> front default;  ultra grand angolare
        // 2 -> back;           ultra grand angolare
        // 3 -> front;          grand angolare


        if(isRecording) // se sto registrando, non posso cambiare camera, quindi c'è un valore di zoom diverso
            zoomLv = progress/SB_zoom.max.toFloat() // calcolo per ottenere un valore compreso ltra 0 e 1 per lo zoom
        else
        {
            if(progress<changeCameraSeekBar) // sono sulle camere ultra grand angolari (changeCameraSeekBar = 50)
            {
                // sperimentalmente ho trovato che sul mio dispositivo (S21) al valore di zoomLv = 0.525 circa
                // lo zoom della camera ultra grand angolare corrisponde al valore della camera principale a 1.0x
                // quindi 2.14 = zoomLv*SB_zoom.max/maxProgress = 0.525*200/49
                zoomLv = (progress.toFloat()/SB_zoom.max * 2.25f)
                // calcolo per ottenere un valore tra 0 e 1 per lo zoom

                if(currentCamera==0) // se sono in back default
                {
                    currentCamera = 2 // passo in back grand angolare
                    reBuild=true
                }
                else if(currentCamera==3) // se sono in front normale
                {
                    currentCamera = 1 // passo in front grand angolare
                    reBuild=true
                }
            }
            else
            {
                zoomLv = (progress-changeCameraSeekBar)/(SB_zoom.max - changeCameraSeekBar).toFloat()
                // calcolo per ottenere un valore tra 0 e 1 per lo zoom
                // è presente - changeCameraSeekBar perchè devo escultere i valori sotto changeCameraSeekBar
                // quindi quando progress è a 50 (=changeCameraSeekBar) allora zoomLv deve essere 0
                // mentre quando progress è a 150 (=SB_zoom.max) allora zoomLv deve essere 1

                if(currentCamera==2) // se sono in back grand angolare
                {
                    currentCamera = 0 // passo in back default
                    reBuild=true
                }
                else if(currentCamera==1) // se sono in front grand angolare
                {
                    currentCamera = 3 // front in normale
                    reBuild=true
                }
            }
        }


        val zoomState = camera.cameraInfo.zoomState
        val maxzoom : Float = zoomState.value?.maxZoomRatio!!

        BT_zoom0_5.text = getString(R.string.zoom_0_5x)
        BT_zoom1_0.text = getString(R.string.zoom_1_0x)
        BT_zoom0_5.backgroundTintList = getColorStateList(R.color.gray_onyx)
        BT_zoom1_0.backgroundTintList = getColorStateList(R.color.gray_onyx)
        BT_zoom0_5.setTextColor(getColor(R.color.white))
        BT_zoom1_0.setTextColor(getColor(R.color.white))

        if(currentCamera==0 || currentCamera == 3) // camera normale 1 -> 8
        {
            BT_zoomRec.text = "${(zoomLv*(maxzoom-1)+1).toString().substring(0,3)}x" // (zoomLv*(maxzoom-1)+1) fa si che visualizzi maxzoom come massimo e 1x come minimo
            BT_zoom1_0.text = "${(zoomLv*(maxzoom-1)+1).toString().substring(0,3)}x"
            BT_zoom1_0.backgroundTintList = getColorStateList(R.color.white)
            BT_zoom1_0.setTextColor(getColor(R.color.black))
        }
        else // camera grand angolare 0.5 -> 8
        {
            BT_zoomRec.text = "${(zoomLv*(maxzoom-0.5)+0.5).toString().substring(0,3)}x" // (zoomLv*(maxzoom-0.5)+0.5) fa si che visualizzi maxzoom come massimo e 0.5x come minimo
            BT_zoom0_5.text = "${(zoomLv+0.5).toString().substring(0,3)}x"
            BT_zoom0_5.backgroundTintList = getColorStateList(R.color.white)
            BT_zoom0_5.setTextColor(getColor(R.color.black))
        }

        if(buildAnyway || (reBuild && !isRecording)) // se sta registrando non cambia fotocamera
            buildCamera()
        cameraControl.setLinearZoom(zoomLv) // cambia il valore dello zoom
        Log.d(TAG,"Zoom lv: $zoomLv, zoomState: ${zoomState.value}" )
        Log.d(TAG, "[current camera] - zoom: $currentCamera")
    }

    /**
     * Metodo per gestire il tocco dei pulsanti del volume
     */
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean
    {   // gestisco la registrazione video tenendo premuto il pulsante del volume per almeno 1 secondo e lo interrompo quando alzo il dito
        if (event?.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            when(volumeKey) {
                "zoom" -> {
                    // Volume_UP -> zoom in, Volume_DOWN -> zoom out
                    SB_zoom.incrementProgressBy(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1)
                    return true
                }
                "volume" ->  return super.dispatchKeyEvent(event)
            }
            if(volumeTimer==null  && volumeKey == "shot") {
                volumeTimer = object: CountDownTimer(LONGCLICKDURATION, LONGCLICKDURATION) {
                    override fun onTick(millisUntilFinished: Long) {
                        // non fa nulla
                    }

                    override fun onFinish() {
                        if(!isRecording) {
                            var temporaryCountDown = countdown
                            countdown = 0   // faccio partire direttamente il video, senza countdown
                            timerShot(true)
                            countdown = temporaryCountDown
                        }
                    }
                }
                volumeTimer?.start()
            }

            return true
        }
        else if (event?.action == KeyEvent.ACTION_UP &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            volumeTimer?.cancel()   // fermo il timer quando sollevo il dito dal pulsante
            volumeTimer = null
            if(isRecording) {       // se sto registrando interrompo la registrazione
                timerShot(true)
                return true
            }

            // scatto da tocco singolo
            when (volumeKey) {
                "shot" -> {
                    changeMode(event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                    timerShot(event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) // scatto una foto o inizio la registrazione di un video
                    return true
                }
            }
        }
        //TODO se tengo premuto più di un secondo (o meno, da testare) allora registra finchè non si molla il tasto, o scatto continuo se è per la foto
        return super.dispatchKeyEvent(event)
    }


    /**
     * Funzione per switchare tra modalità Foto e Video;
     * quindi cambia i pulsanti visualizzati
     *
     * @param record True se devo passare in modalità Video;
     *                False se devo passare in modalità Foto
     */
    private fun changeMode(record : Boolean) {
        recordMode = record
        val bt1 = if(record) BT_recMode else BT_photoMode
        val bt2 = if(record) BT_photoMode else BT_recMode
        val aspect = if(record) aspectRatioVideo else aspectRatioPhoto

        bt1.backgroundTintList = getColorStateList(R.color.white)
        bt1.setTextColor(getColor(R.color.black))

        //bt2.setBackgroundColor(getColor(R.color.gray_onyx))
        bt2.backgroundTintList = getColorStateList(R.color.gray_onyx)
        bt2.setTextColor(getColor(R.color.white))

        // cambia rapporto preview
        val layoutParams = viewPreview.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.dimensionRatio = "H,${aspect.numerator}:${aspect.denominator}" // Cambia l'aspect ratio desiderato qui
        viewPreview.layoutParams = layoutParams


        if(!timerOn) // se non c'è il timer attivato
        {
            if(!isRecording) // se non sta registrando
                BT_shoot.setBackgroundResource( // cambio la grafica del pulsante in base a se sto registrando o no
                    if(record) R.drawable.in_recording_button else R.drawable.rounded_corner
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
                if (orientation == ORIENTATION_UNKNOWN) return

                val rotation = when (orientation) {
                    in 50 .. 130 -> 270
                    in 140 .. 220 -> 180
                    in 230 .. 310 -> 90
                    in 0 .. 40,in 320 .. 360 -> 0
                    else -> -1 // Angolo morto
                }
                // non ho messo valori multipli di 45 in modo da avere un minimo di gioco prima di cambiare rotazione
                //Log.d(TAG,"[orientation] $rotation" )

                if(!isRecording && rotation != -1 ) // gira solo se non sta registrando, per salvare i video nel orientamento iniziale
                {
                    rotateButton(rotation.toFloat())
                    // Surface.ROTATION_0 è = 0, ROTATION_90 = 1, ... ROTATION_270 = 3, quindi = rotation/90
                    videoCapture?.targetRotation = rotation/90
                }
                imageCapture?.targetRotation = rotation/90 // è fuori dal if, in questo modo l'immagine è sempre orientata correttamente
            }
        }
    }

    private inner class MyGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val TRESHOLD_VELOCITY : Float = 100.0f  // velocità minima per rilevare lo swipe
        private val TRESHOLD : Float = 100.0f           // lunghezza minima del trascinamento per rilevare lo swipe
        override fun onSingleTapUp(motionEvent: MotionEvent): Boolean {
            when (motionEvent.action) {
                MotionEvent.ACTION_UP -> {
                    focusView.x = viewPreview.x - focusView.width / 2 + motionEvent.x
                    focusView.y = viewPreview.y - focusView.height / 2 + motionEvent.y
                    focusView.visibility = View.VISIBLE
                    focusView.postDelayed(Runnable {
                        focusView.visibility = View.INVISIBLE
                    }, 1000)
                    // Get the MeteringPointFactory from PreviewView
                    val factory = viewBinding.viewPreview.meteringPointFactory

                    // Create a MeteringPoint from the tap coordinates
                    val point = factory.createPoint(motionEvent.x, motionEvent.y)

                    // Create a MeteringAction from the MeteringPoint, you can configure it to specify the metering mode
                    val action = FocusMeteringAction.Builder(point).build()

                    // Trigger the focus and metering. The method returns a ListenableFuture since the operation
                    // is asynchronous. You can use it get notified when the focus is successful or if it fails.
                    cameraControl.startFocusAndMetering(action)
                }
            }
            return true
        }

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            Log.d(TAG, "CHIAMATA a onFling")
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
                if(deltaX>0 && recordMode)    // right swipe: video -> foto
                {
                    changeMode(false)
                }
                else if(deltaX<0 && !recordMode)    // left swipe: foto -> video
                    changeMode(true)
            }
            return super.onFling(e1, e2, velocityX, velocityY)
        }
    }

    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            // Aggiorna lo zoom della fotocamera
            Log.d(TAG, "[zoom] $scaleFactor")

            if(scaleFactor>1) // se pinch in allora zoommo
                SB_zoom.incrementProgressBy(1) // cambio il valore della SeekBar che a sua volta cambia il valore dello zoom
            else // altrimienti è pinch out e allora dezoommo
                SB_zoom.incrementProgressBy(-1)
            return true
        }
    }

    /**
     * todo commento
     */
    private fun rotateCamera() { // id = 0 default back, id = 1 front default
        if(currentCamera== 0 || currentCamera == 2)
            currentCamera = 3 // passo in front, è la camera frontale grand angolare
        else if(currentCamera==1 || currentCamera==3)
            currentCamera = 0 // passo in back
        SB_zoom.progress = changeCameraSeekBar

        if(!isRecording) // se sta registrando non cambia fotocamera
            buildCamera()
        Log.d(TAG, "[current camera]  - rotate: $currentCamera")
    }

    /**
     * Metodo che permette di scattare una foto o di registrare un video a seconda del valore di record
     *
     * @param record True se in modalità video
     *               False se in modalità foto
     */
    private fun timerShot(record : Boolean){
        if(timerOn) { // se c'è il timer in funzione allora lo blocco
            Log.d(TAG,"Timer bloccato")
            timerOn = false
            timer.cancel()
            countDownText.visibility = View.INVISIBLE
            findViewById<Group>(R.id.Group_extraFunc).visibility = View.VISIBLE
            changeMode(recordMode)
            return
        }
        if(isRecording && record){ // se sto già registrando e tengo premuto il pulsante rosso in modalità foto
            // o premo il pulsante per fermare in modalità vidoe
            captureVideo() // lo richiamo per fermare la registraizone
            return
        }
        if (isRecording && !record){ // se sto già registrando e premo il pulsante rosso in moodalità foto
            // scatta una foto senza usare il timer
            takePhoto()
            return
        }

        timerOn = true
        timer = object : CountDownTimer(countdown*1000, 1000){
            override fun onTick(remainingMillis: Long) {
                //BT_timer.visibility = View.INVISIBLE //rendo invisibile il pulsante del timer durante il countdown
                findViewById<Group>(R.id.Group_extraFunc).visibility = View.INVISIBLE
                BT_shoot.setBackgroundResource(R.drawable.rounded_stop_button)
                countDownText.text = "${remainingMillis/1000 + 1}"
                countDownText.visibility = View.VISIBLE
                Log.d(TAG, "Secondi rimanenti: "+remainingMillis/1000)
            }
            override fun onFinish() {
                timerOn = false
                countDownText.visibility = View.INVISIBLE
                findViewById<Group>(R.id.Group_extraFunc).visibility = View.VISIBLE
                if(record)
                    captureVideo()
                else
                    takePhoto()
                changeMode(recordMode)
            }

        }.start()
        Log.d(TAG, "Secondi ristabiliti: $countdown")
    }

    /**
     * Ruoto i pulsanti per far si che siano dritti
     *
     * @param angle è il numero di gradi per ruotare i tasti
     */
    private fun rotateButton(angle : Float)
    {
        BT_gallery.rotation = angle
        BT_rotation.rotation = angle
        BT_flash.rotation = angle
        BT_timer.rotation = angle
        BT_zoom0_5.rotation = angle
        BT_zoom1_0.rotation = angle
        BT_zoomRec.rotation = angle
        CM_recTimer.rotation = angle
        BT_recMode.rotation = angle
        BT_photoMode.rotation = angle
    }

    /**
     * TODO: da commentare
     */
    private fun switchTimerMode() {
        currTimerMode = TimerModes.next(currTimerMode)
        setTimerMode()
        setTimerIcon(currTimerMode.text)
    }

    /**
     * TODO: da commentare
     */
    private fun selectTimerMode(ordinal: Int?): Boolean{
        if(ordinal == null) {
            throw IllegalArgumentException()
        }
        currTimerMode = TimerModes.values()[ordinal]
        setTimerMode()
        setTimerIcon(currTimerMode.text)
        return true
    }

    /**
     * TODO: da commentare
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
     * TODO: da commentare
     */
    private fun setTimerIcon(status : String){
        BT_timer.backgroundTintList = getColorStateList(R.color.aureolin_yellow)
        BT_timer.setBackgroundResource(
            when(status){
                "OFF" -> {
                    BT_timer.backgroundTintList = getColorStateList(R.color.white)
                    R.drawable.timer_0
                }
                "3" -> R.drawable.timer_3
                "5" -> R.drawable.timer_5
                else -> R.drawable.timer_10
            }
        )
    }


    /**
     * Metodo per passare alla prossima modalità del flash, nell'ordine:
     *  - OFF
     *  - ON
     *  - AUTO
     */
    private fun switchFlashMode() {
        currFlashMode = FlashModes.next(currFlashMode)
        setFlashMode()
    }

    /**
     * Metodo per cambiare [currFlashMode]
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
     * Metodo che permette di imposta la modalità del flash specificata da [currFlashMode]
     */
    private fun setFlashMode() {
        when(currFlashMode) {
            FlashModes.OFF -> {
                BT_flash.setBackgroundResource(R.drawable.flash_off)
                BT_flash.backgroundTintList = getColorStateList(R.color.white)
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                //Nel caso in cui si stia registrando disattiva il flash in modalità OFF
                if(recording != null) { cameraControl.enableTorch(false) }
            }
            FlashModes.ON -> {
                BT_flash.setBackgroundResource(R.drawable.flash_on)
                BT_flash.backgroundTintList = getColorStateList(R.color.aureolin_yellow)
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON
                //Nel caso in cui si stia registrando attiva il flash in modalità ON
                if(recording != null) { cameraControl.enableTorch(true) }
            }
            FlashModes.AUTO -> {
                BT_flash.setBackgroundResource(R.drawable.flash_auto)
                BT_flash.backgroundTintList = getColorStateList(R.color.aureolin_yellow)
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_AUTO
                //Nel caso in cui si stia registrando disattiva il flash in modalità AUTO
                if(recording != null) { cameraControl.enableTorch(false) }
            }
        }
    }

    /* TODO: guardare se serve
    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    } */

    /**
     * abilita il "sensore" per l'angolo
     */
    override fun onStart() {
        super.onStart()
        orientationEventListener.enable()
    }

    /**
     * disabilita il "sensore" per l'angolo
     */
    override fun onStop() {
        super.onStop()
        orientationEventListener.disable()
    }

    /**
     * quando l'applicazione viene messa in background salvo le preference
     * da ripristinare al avvio, anche se viene completamente chiusa
     *
     * Ho deciso di non salvare tutti i dati, quindi escludo lo zoom
     * e anche la modalità in cui viene lasciata
     */
    override fun onPause()
    {
        super.onPause()

        // Store values between instances here
        val preferences = getPreferences(MODE_PRIVATE)
        val editor = preferences.edit()

        if(currentCamera%2==0) // a differenza di onSaveInstanceState non salvo lo zoom e la camera corretta
        // salvo solo se è frontale o posteriore
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
        editor.putBoolean(KEY_QRCODE, qrscanner)

        editor.apply()

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Ripristino lo zoom
     */
    override fun onResume()
    {
        super.onResume()
        Log.d(TAG, "onResume")
        try { // il lifecycle dello zoom viene chiso con la chiusura dell'app,
            // e non viene ripristinato manualmente, quindi chiamo changeZoom
            // con il valore SB_zoom.progress che è ancora salvato
            // Se invece l'applicazione va in background e viene killata da android
            // allora changeZoom(SB_zoom.progress) restituisce errore che non è
            // necessario gestire, in quel caso allora i dati sono stati salvati dul Bundle
            // e ripristinati da loadFromBundle, se invece viene killata dal utente
            // allora non viene ripristinato lo stato
            changeZoom(SB_zoom.progress)
        }
        catch (e : Exception) {
            Log.e(TAG, "Exception $e")
        }

        loadFromSetting()
    }

    /**
     * salvo la camera corrente (a differenza delle preference in cui salvo solo
     * se è anteriore o posteriore, lo zoom e la modalità
     */
    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt(KEY_CAMERA, currentCamera)
        savedInstanceState.putInt(KEY_ZOOM, SB_zoom.progress)
        savedInstanceState.putBoolean(KEY_REC, recordMode)
    }

    /**
     * TODO: boohhh
     */
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

}
