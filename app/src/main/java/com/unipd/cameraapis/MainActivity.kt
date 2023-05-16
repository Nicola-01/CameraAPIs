package com.unipd.cameraapis

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.Camera
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.unipd.cameraapis.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {


    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    // Select back camera as a default
    // dichiarato qui per poterlo usare in rotateCamera()
    var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private lateinit var BT_shoot : Button
    private lateinit var BT_flash : Button
    private lateinit var BT_rotation : Button
    var currFlashMode : FlashModes = FlashModes.OFF
    var currTimerMode : TimerModes = TimerModes.OFF
    var countdown : Long = 0

    private lateinit var viewFinder : View
    private lateinit var focusView : View
    private lateinit var BT_gallery : Button
    private lateinit var BT_zoom1_0 : Button
    private lateinit var BT_zoom0_5 : Button
    private lateinit var BT_zoomRec : Button
    private lateinit var BT_timer : Button
    private lateinit var BT_grid : Button
    private lateinit var SB_zoom : SeekBar
    private val changeCameraSeekBar = 50
    private lateinit var CM_recTimer : Chronometer
    private lateinit var countDownText : TextView
    private lateinit var timer: CountDownTimer
    private lateinit var FocusCircle : View

    private lateinit var focusCircle: Drawable
    private lateinit var scaleDown: Animation
    private lateinit var scaleUp: Animation
    private lateinit var cameraControl:CameraControl
    private lateinit var availableCameraInfos: MutableList<CameraInfo>
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraManager : CameraManager
    private lateinit var camera : androidx.camera.core.Camera
    private lateinit var preview: Preview
    private lateinit var recorder: Recorder
    private var currentCamera = 0
    private var zoomLv : Float = 0.toFloat() // va da 0 a 1
    // 0 -> back default
    // 1 -> front default grand angolare
    // 2 -> back grand angolare
    // 3 -> front normale
    private var isRecording = false
    private var grid = true

    private lateinit var scaleGestureDetector: ScaleGestureDetector

    companion object {
        //private val TAG = MainActivity::class.simpleName
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd_HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        createElement()

        if (savedInstanceState != null) {
            currentCamera = savedInstanceState.getInt("CurrentCamera")
            //TODO: Sistemare zoom e camera
            //val progress : Int= savedInstanceState.getInt("zoomProgress")
            //SB_zoom.setProgress(progress)
            grid = savedInstanceState.getBoolean("gridMode")
            viewGrid(grid);

            var flashMode = savedInstanceState.getString("flashMode")
            while(currFlashMode.toString() != flashMode)
                switchFlashMode()

            var timerMode = savedInstanceState.getString("timerMode")
            while(timerMode.toString() != timerMode)
                switchTimerMode()
        }
    }

    /** Funzione per istanziare elementi dal activity_main.xml
     * quindi assagnazione per pulsanti e vari elementi, e i loro lissener
     */
    private fun createElement()
    {
        BT_shoot = viewBinding.BTShoots
        BT_zoom1_0 = viewBinding.BT10
        BT_zoom0_5 = viewBinding.BT05
        BT_zoomRec = viewBinding.BTZoomRec
        CM_recTimer = viewBinding.CMRecTimer
        CM_recTimer.format = "%02d:%02d:%02d"
        BT_timer = viewBinding.BTTimer
        BT_grid = viewBinding.BTGrid
        countDownText = viewBinding.TextTimer

        BT_rotation = viewBinding.BTRotation
        FocusCircle = viewBinding.FocusCircle

        SB_zoom = viewBinding.SBZoom

        scaleDown = AnimationUtils.loadAnimation(this,R.anim.scale_down)
        scaleUp = AnimationUtils.loadAnimation(this,R.anim.scale_up)

        // Set up the listeners for take photo and video capture buttons
        BT_shoot.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            timerShot()
        }
        BT_shoot.setOnLongClickListener{ captureVideo() }
        BT_zoom1_0.setOnClickListener { SB_zoom.setProgress(changeCameraSeekBar) }
        BT_zoom0_5.setOnClickListener{ SB_zoom.setProgress(0) }
        BT_grid.setOnClickListener { grid =! grid; viewGrid(grid) }

        SB_zoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                changeZoom(progress)
                if(progress%5 == 0 && fromUser) // ogni 5 do un feedback, e solo se muovo manualmente la SB
                    SB_zoom.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            override fun onStartTrackingTouch(seek: SeekBar) = Unit
            override fun onStopTrackingTouch(seek: SeekBar) = Unit
        })

        BT_gallery = findViewById(R.id.BT_gallery)

        BT_gallery.setOnClickListener{//TODO: aprire galleria
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
        }

        scaleGestureDetector = ScaleGestureDetector(this, ScaleGestureListener()) //pinch in/out

        BT_rotation.setOnClickListener { rotateCamera()
            SB_zoom.performHapticFeedback(HapticFeedbackConstants.CONFIRM)}

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

        BT_flash = viewBinding.BTFlash
        //flash.text = currFlashMode.text
        setFlashIcon(currFlashMode.text)
        BT_flash.setOnClickListener { switchFlashMode() }
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

        focusView = viewBinding.FocusCircle
        focusView.visibility = View.INVISIBLE
        viewFinder = viewBinding.viewFinder
        viewFinder.setOnTouchListener(View.OnTouchListener setOnTouchListener@{ view: View, motionEvent: MotionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    focusView.x = viewFinder.x - focusView.width / 2 + motionEvent.x
                    focusView.y = viewFinder.y - focusView.height / 2 + motionEvent.y
                    focusView.visibility = View.VISIBLE
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
                    focusView.visibility = View.INVISIBLE
                    // Get the MeteringPointFactory from PreviewView
                    val factory = viewBinding.viewFinder.getMeteringPointFactory()

                    // Create a MeteringPoint from the tap coordinates
                    val point = factory.createPoint(motionEvent.x, motionEvent.y)

                    // Create a MeteringAction from the MeteringPoint, you can configure it to specify the metering mode
                    val action = FocusMeteringAction.Builder(point).build()

                    // Trigger the focus and metering. The method returns a ListenableFuture since the operation
                    // is asynchronous. You can use it get notified when the focus is successful or if it fails.
                    cameraControl.startFocusAndMetering(action)

                    return@setOnTouchListener true
                }

                else -> return@setOnTouchListener false
            }
        })
    }

    private val orientationEventListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) {
                    return
                }

                val rotation = when (orientation) {
                    in 45 .. 135 -> 270
                    in 135 .. 225 -> 180
                    in 225 .. 315 -> 90
                    else -> 0
                }
                Log.d(TAG,"[orientation] $rotation" )

                if(!isRecording) // gira solo se non sta registrando, per salvare i video nel orientamento corretto
                {
                    rotateButton(rotation.toFloat())
                    // Surface.ROTATION_0 è = 0, ROTATION_90 = 1, ... ROTATION_270 = 3, quindi = orientation/90
                    videoCapture?.targetRotation = rotation/90
                }
                imageCapture?.targetRotation = rotation/90 // è fuori dal if, in questo modo l'immagine è sempre orientata correttamente
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Log.d(TAG, "[event type] $event")
        scaleGestureDetector.onTouchEvent(event!!)
        return true
    }

    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            // Aggiorna lo zoom della fotocamera
            Log.d(TAG, "[zoom] $scaleFactor")

            if(scaleFactor>1)
                SB_zoom.incrementProgressBy(1)
            else
                SB_zoom.incrementProgressBy(-1)
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            return super.onScaleBegin(detector)
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
        }
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            imageCapture = ImageCapture.Builder().setFlashMode(ImageCapture.FLASH_MODE_OFF).build()

            // Select back camera as a default
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Crea un oggetto CameraSelector per la fotocamera ultra grandangolare
            availableCameraInfos = cameraProvider.availableCameraInfos
            Log.i(TAG, "[startCamera] available cameras Info:$availableCameraInfos")
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var availableCamera : Array<String> = cameraManager.getCameraIdList()
            Log.i(TAG, "[startCamera] available cameras:${availableCamera.toString()}")

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture)
                cameraControl = camera.cameraControl
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    //<editor-fold desc= "FOCUS METHODS">


    //</editor-fold>

    private fun takePhoto() {
        Log.d(TAG,"ClickListener")
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
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
        viewBinding.viewFinder.startAnimation(scaleUp)
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun captureVideo() : Boolean {
        val videoCapture = this.videoCapture ?: return true

        //viewBinding.videoCaptureButton.isEnabled = false

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
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        if(currFlashMode == FlashModes.ON) { cameraControl.enableTorch(true) }

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
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " + "${recordEvent.outputResults.outputUri}"
                            if(currFlashMode == FlashModes.ON) { cameraControl.enableTorch(false) }
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " + "${recordEvent.error}")
                            if(currFlashMode == FlashModes.ON) { cameraControl.enableTorch(false) }
                        }
                        startRecording(false)
                    }
                }
            }
        return true
    }

    private fun viewGrid(status : Boolean)
    {
        val view : Int
        if(status){
            BT_grid.setBackgroundResource(R.drawable.grid_off)
            view = View.VISIBLE
        }
        else{
            BT_grid.setBackgroundResource(R.drawable.grid_on)
            view = View.INVISIBLE
        }
        viewBinding.GRVert1.visibility = view
        viewBinding.GRVert2.visibility = view
        viewBinding.GRHoriz1.visibility = view
        viewBinding.GRHoriz2.visibility = view
    }
    private fun rotateCamera() { // id = 0 default back, id = 1 front default
        if(currentCamera== 0 || currentCamera == 2)
        {
            cameraSelector = availableCameraInfos[3].cameraSelector // passo in front
            currentCamera = 3
        }
        else if(currentCamera==1 || currentCamera==3)
        {
            cameraSelector = availableCameraInfos[0].cameraSelector // passo in back
            currentCamera = 0
        }
        SB_zoom.progress = changeCameraSeekBar
        /*
        else if(currentCamera==2)
        {
            cameraSelector = availableCameraInfos[0].cameraSelector // passo in back, dovrei mettere la camera 3
            currentCamera = 0
            reBuild=true
        } */
        /*
        else if(currentCamera==3)
        {
            cameraSelector = availableCameraInfos[2].cameraSelector // passo in front grand-angolare
            currentCamera = 2
            reBuild=true
        }
        */
        if(!isRecording) // se sta registrando non cambia fotocamera
        {
            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()            // Unbind use cases before rebinding
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture) // devo ricostruire la camera ogni volta
                // in quato cambio la camera
                cameraControl = camera.cameraControl
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }
        Log.d(TAG,"[current camera]  - rotate: " + currentCamera)
    }

    private fun timerShot(){
        timer = object : CountDownTimer(countdown*1000, 1000){
            override fun onTick(remainingMillis: Long) {
                countDownText.text = (remainingMillis/1000 + 1).toString()
                Log.d(TAG, "Secondi rimanenti: "+remainingMillis/1000)
            }

            override fun onFinish() {
                countDownText.text = ""
                takePhoto()
            }

        }.start()
        Log.d(TAG, "Secondi ristabiliti: "+countdown)
    }

    private fun startRecording(status : Boolean)
    {
        val viewPH : Int
        val viewVI : Int
        isRecording = status
        if(status){
            viewPH = View.INVISIBLE
            viewVI = View.VISIBLE

            BT_shoot.setBackgroundResource(R.drawable.rounded_corner_red)
            CM_recTimer.base = SystemClock.elapsedRealtime()
            CM_recTimer.start()
        }
        else
        {
            viewPH = View.VISIBLE
            viewVI = View.INVISIBLE
            CM_recTimer.stop()
            BT_shoot.setBackgroundResource(R.drawable.rounded_corner)
        }

        BT_rotation.visibility = viewPH
        BT_zoom1_0.visibility = viewPH
        BT_zoom0_5.visibility = viewPH
        BT_zoomRec.visibility = viewVI
        CM_recTimer.visibility = viewVI

        // se inizio a registrare non posso più cambiare camera,
        // quindi devo sistemare il valore della progration bar
        if(status) {
            SB_zoom.progress = (zoomLv*SB_zoom.max).toInt()
        }
        else
        {
            if(currentCamera==0 || currentCamera==3)
                SB_zoom.progress = (zoomLv*(SB_zoom.max - changeCameraSeekBar)).toInt() + changeCameraSeekBar
            else
                SB_zoom.progress = (zoomLv*SB_zoom.max*0.54).toInt()
        }
    }

    private fun changeZoom(progress : Int)
    {
        var reBuild = false // evito di costruitr la camera ogni volta
        val maxzoom = 8
        // SB_zoom va da 0 a 100, quindi i primi 25 valori sono per lo zoom con la grand angolare, gli altri per la camera normale
        // non sono riuscito a recoperare la telephoto
        // 0 -> back default
        // 1 -> front default grand angolare
        // 2 -> back grand angolare
        // 3 -> front normale

        //? sperimentalmente ho trovato che sul mio dispositivo (S21) al valore di zoomLv = 0.54  circa
        // lo zoom della camera grand angolare corrisponde al valore della camera principale a 1.0x



        // lo zoom della grand angolare va da 0.5 a 1
        if(isRecording)
            zoomLv = progress/SB_zoom.max.toFloat()
        else
        {
            if(progress<changeCameraSeekBar)
            {
                zoomLv = progress/(SB_zoom.max*0.54).toFloat()

                if(currentCamera==0) // se sono in back default
                {
                    cameraSelector = availableCameraInfos[2].cameraSelector // passo in back grand angolare
                    currentCamera = 2
                    reBuild=true
                }
                else if(currentCamera==3) // se sono in front normale
                {
                    cameraSelector = availableCameraInfos[1].cameraSelector // passo in front grand angolare
                    currentCamera = 1
                    reBuild=true
                }
            }
            else
            {
                zoomLv = (progress-changeCameraSeekBar)/(SB_zoom.max - changeCameraSeekBar).toFloat()

                if(currentCamera==2) // se sono in back grand angolare
                {
                    cameraSelector = availableCameraInfos[0].cameraSelector // passo in back default
                    currentCamera = 0
                    reBuild=true
                }
                else if(currentCamera==1) // se sono in front grand angolare
                {
                    cameraSelector = availableCameraInfos[3].cameraSelector // front in normale
                    currentCamera = 3
                    reBuild=true
                }
            }
        }

        BT_zoom0_5.text = "0.5x"
        BT_zoom1_0.text = "1.0x"

        if(currentCamera==0 || currentCamera == 3) // camera normale 1 -> 8
        {
            BT_zoomRec.text = (zoomLv*(maxzoom-1)+1).toString().substring(0,3) + "x" // (zoomLv*(maxzoom-1)+1) fa si che visualizzi 8x come massimo e 1x come minimo
            BT_zoom1_0.text = (zoomLv*(maxzoom-1)+1).toString().substring(0,3) + "x"
        }
        else // camera grand angolare 0.5 -> 8
        {
            BT_zoomRec.text = (zoomLv*(maxzoom-0.5)+0.5).toString().substring(0,3) + "x" // (zoomLv*(maxzoom-0.5)+0.5) fa si che visualizzi 8x come massimo e 0.5x come minimo
            BT_zoom0_5.text = (zoomLv+0.5).toString().substring(0,3) + "x"
        }



       if(reBuild && !isRecording) // se sta registrando non cambia fotocamera
       {
           imageCapture = ImageCapture.Builder().build()

           try {
               cameraProvider.unbindAll()            // Unbind use cases before rebinding
               camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture) // devo ricostruire la camera ogni volta
               // in quato cambio la camera
               cameraControl = camera.cameraControl
           } catch(exc: Exception) {
               Log.e(TAG, "Use case binding failed", exc)
           }
       }

        cameraControl.setLinearZoom(zoomLv)
        var zoomState = camera.cameraInfo.zoomState
        // getZoomRatio -> camerainfo.getZoomRatio
        // getCameraInfo()
        zoomState.value?.maxZoomRatio
        Log.d(TAG,"Zoom lv: $zoomLv, zoomState: ${zoomState.value}" )
        Log.d(TAG,"[current camera] - zoom: " + currentCamera)
    }

    private fun rotateButton(angle : Float)
    {
        BT_gallery.rotation = angle
        BT_rotation.rotation = angle
        BT_flash.rotation = angle
        BT_timer.rotation = angle
        BT_zoom0_5.rotation = angle
        BT_zoom1_0.rotation = angle
        CM_recTimer.rotation = angle
        BT_grid.rotation = angle
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun switchTimerMode() {
        currTimerMode = TimerModes.next(currTimerMode)
        setTimerMode()
        setTimerIcon(currTimerMode.text)
    }

    private fun selectTimerMode(ordinal: Int?): Boolean{
        if(ordinal == null) {
            throw IllegalArgumentException()
        }
        currTimerMode = TimerModes.values()[ordinal]
        setTimerMode()
        setTimerIcon(currTimerMode.text)
        return true
    }
    private fun setTimerMode(){
        when(currTimerMode){
            TimerModes.OFF -> {
                countdown = 0
            }
            TimerModes.ON_3 -> {
                countdown = 3
            }
            TimerModes.ON_5 -> {
                countdown = 5
            }
            TimerModes.ON_10 -> {
                countdown = 10
            }
        }
    }
    private fun setTimerIcon(status : String){
        BT_timer.setBackgroundResource(
            when(status){
                "OFF" -> R.drawable.timer_0
                "3" -> R.drawable.timer_3
                "5" -> R.drawable.timer_5
                else -> R.drawable.timer_10
            }
        )
    }

    //<editor-fold desc= "FLASH METHODS">
    private fun switchFlashMode() {
        currFlashMode = FlashModes.next(currFlashMode)
        setFlashMode()
        setFlashIcon(currFlashMode.text)
    }

    private fun selectFlashMode(ordinal: Int?): Boolean {
        if(ordinal == null) {
            throw IllegalArgumentException()
        }
        currFlashMode = FlashModes.values()[ordinal]
        setFlashMode()
        setFlashIcon(currFlashMode.text)
        return true
    }
    private fun setFlashMode() {
        when(currFlashMode) {
            FlashModes.OFF -> {
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
            }
            FlashModes.ON -> {
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON
            }
            FlashModes.AUTO -> {
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_AUTO
            }
        }
    }

    private fun setFlashIcon(status : String){
        BT_flash.setBackgroundResource(
        when(status){
            "OFF" -> R.drawable.flash_off
            "ON" -> R.drawable.flash_on
            else -> R.drawable.flash_auto
        }
        )
    }

    /*
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
    //</editor-fold>

    override fun onResume()
    {
        super.onResume()
    }
    override fun onPause()
    {
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        orientationEventListener.enable()
    }

    override fun onStop() {
        super.onStop()
        orientationEventListener.disable()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("CurrentCamera",currentCamera)
        savedInstanceState.putInt("zoomProgress",SB_zoom.progress)
        savedInstanceState.putString("flashMode",currFlashMode.toString())
        savedInstanceState.putString("timerMode",currFlashMode.toString())
        savedInstanceState.putBoolean("gridMode",grid)
    }
}
