package com.unipd.cameraapis

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.widget.TextView
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraX
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
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
import java.nio.ByteBuffer
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
    private lateinit var flash : Button //TODO sarebbe meglio se si chiamassa BT_flash
    //TODO (Per rinominare tutte le occorrenze (fare attenzione perchè rinomina tutto cio che contiene la parola da sostituire in tutti i file) ctrl + shift+ R)
    private lateinit var BT_rotation : Button
    //TODO (Per rinominare tutte le occorrenze (fare attenzione perchè rinomina tutto cio che contiene la parola da sostituire in tutti i file) ctrl + shift+ R)
    var currFlashMode : FlashModes = FlashModes.OFF

    private lateinit var BT_zoom1_0 : Button
    private lateinit var BT_zoom0_5 : Button
    private lateinit var BT_zoomRec : Button
    private lateinit var SB_zoom : SeekBar

    private lateinit var scaleDown: Animation
    private lateinit var scaleUp: Animation
    private lateinit var cameraControl:CameraControl
    private lateinit var availableCameraInfos: MutableList<CameraInfo>
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var preview: Preview
    private lateinit var recorder: Recorder
    private var currentCamera = 0
    // 0 -> back default
    // 1 -> front default grand angolare
    // 2 -> back grand angolare
    // 3 -> front normale
    private var isRecording = false

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

        //<editor-fold desc= "FLASH INIT">
        flash = viewBinding.BTFlash
        flash.text = currFlashMode.text
        flash.setOnClickListener { switchFlashMode() }
        flash.setOnCreateContextMenuListener { menu, v, menuInfo ->
            menu.setHeaderTitle("Flash")
            for(mode in FlashModes.values()) {
                var item: MenuItem = menu.add(Menu.NONE, mode.ordinal, Menu.NONE, mode.text)
                item.setOnMenuItemClickListener { i: MenuItem? ->
                    selectFlashMode(i?.itemId)
                    true // Signifies you have consumed this event, so propogation can stop.
                }
            }
        }
        flash.setOnLongClickListener(View::showContextMenu)
        //flash.setOnLongClickListener { selectFlashMode() }
        //</editor-fold>

        BT_shoot = viewBinding.BTShoots
        BT_zoom1_0 = viewBinding.BT10
        BT_zoom0_5 = viewBinding.BT05
        BT_zoomRec = viewBinding.BTZoomRec

        BT_rotation = viewBinding.BTRotation


        SB_zoom = viewBinding.SBZoom

        scaleDown = AnimationUtils.loadAnimation(this,R.anim.scale_down)
        scaleUp = AnimationUtils.loadAnimation(this,R.anim.scale_up)

        // Set up the listeners for take photo and video capture buttons
        BT_shoot.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            takePhoto()
        }
        BT_shoot.setOnLongClickListener{ captureVideo() }
        BT_zoom1_0.setOnClickListener { SB_zoom.setProgress(25) }
        BT_zoom0_5.setOnClickListener{ SB_zoom.setProgress(0) }


        SB_zoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                changeZoom(progress)
                SB_zoom.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            override fun onStartTrackingTouch(seek: SeekBar) = Unit
            override fun onStopTrackingTouch(seek: SeekBar) = Unit
        })

        scaleGestureDetector = ScaleGestureDetector(this, ScaleGestureListener()) //TODO: pinch in/out


        BT_rotation.setOnClickListener { rotateCamera() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Log.d(TAG, "[Here] $event")
        scaleGestureDetector.onTouchEvent(event!!)
        return true
    }

    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var scaleFactor = detector.scaleFactor
            // Aggiorna lo zoom della fotocamera
            Log.d(TAG, "[zoom] $scaleFactor")

            if(scaleFactor>1)
                SB_zoom.setProgress(SB_zoom.progress + 1)
            else
                SB_zoom.setProgress(SB_zoom.progress - 1)
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
            Log.i(TAG, "[startCamera] available cameras:$availableCameraInfos")

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture)
                cameraControl = camera.cameraControl;
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

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

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
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
                        isRecording = true

                        BT_rotation.visibility = View.GONE
                        BT_zoom1_0.visibility = View.INVISIBLE
                        BT_zoom0_5.visibility = View.INVISIBLE
                        BT_zoomRec.visibility = View.VISIBLE
                        BT_shoot.setBackgroundResource(R.drawable.rounded_corner_red);
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " + "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " + "${recordEvent.error}")
                        }
                        BT_shoot.setBackgroundResource(R.drawable.rounded_corner);
                        BT_rotation.visibility = View.VISIBLE
                        BT_zoom1_0.visibility = View.VISIBLE
                        BT_zoom0_5.visibility = View.VISIBLE
                        BT_zoomRec.visibility = View.INVISIBLE
                        isRecording = false
                    }
                }
            }
        return true
    }

    private fun rotateCamera() { // id = 0 default back, id = 1 front default
        var reBuild = false
        if(currentCamera==0)
        {
            cameraSelector = availableCameraInfos[1].cameraSelector // passo in front
            currentCamera = 1
            reBuild=true
        }
        else if(currentCamera==1)
        {
            cameraSelector = availableCameraInfos[0].cameraSelector // passo in back
            currentCamera = 0
            reBuild=true
        }
        else if(currentCamera==2)
        {
            cameraSelector = availableCameraInfos[0].cameraSelector // passo in back, dovrei mettere la camera 3
            currentCamera = 0
            reBuild=true
        }
        /*
        else if(currentCamera==3)
        {
            cameraSelector = availableCameraInfos[2].cameraSelector // passo in front grand-angolare
            currentCamera = 2
            reBuild=true
        }
        */
        if(reBuild && !isRecording) // se sta registrando non cambia fotocamera
        {
            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()            // Unbind use cases before rebinding
                val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture) // devo ricostruire la camera ogni volta
                // in quato cambio la camera
                cameraControl = camera.cameraControl
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }

    }

    private fun changeZoom(progress : Int)
    {
        var reBuild = false; // evito di costruitr la camera ogni volta
        var zoomLv : Float = 0.toFloat() // va da 0 a 100
        // SB_zoom va da 0 a 100, quindi i primi 25 valori sono per lo zoom con la grand angolare, gli altri per la camera normale
        // non sono riuscito a recoperare la telephoto
        // 0 -> back default
        // 1 -> front default grand angolare
        // 2 -> back grand angolare
        // 3 -> front normale

        //? sperimentalmente ho trovato che sul mio dispositivo (S21) al valore di zoom = 0.54 (progress = 13)
        // circa lo zoom della camera grand angolare corrisponde al valore della camera principale a 1.0x

        // lo zoom della grand angolare va da 0.5 a 1


        //val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        //val cameraId = cameraManager.cameraIdList.first() // seleziona la prima fotocamera
        //val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        //val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
        // -> zoom massimo fotocamera principale = 8;



        if(isRecording)
        {
            zoomLv = progress/100.toFloat()
        }
        else
        {
            if(progress<25)
            {
                zoomLv = progress/46.toFloat()

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
                    reBuild=true;
                }
            }
            else
            {
                zoomLv = (progress-25)/75.toFloat()

                if(currentCamera==2) // se sono in back grand angolare
                {
                    cameraSelector = availableCameraInfos[0].cameraSelector // passo in back default
                    currentCamera = 0
                    reBuild=true;
                }
                else if(currentCamera==1) // se sono in front grand angolare
                {
                    cameraSelector = availableCameraInfos[3].cameraSelector // front in normale
                    currentCamera = 3
                    reBuild=true;
                }
            }
        }

        BT_zoom0_5.setText("0.5x")
        BT_zoom1_0.setText("1.0x")

        if(currentCamera==0) // camera normale 1 -> 8
        {
            BT_zoomRec.setText((zoomLv*7+1).toString().substring(0,3) + "x")
            BT_zoom1_0.setText((zoomLv*7+1).toString().substring(0,3) + "x")
        }
        else // camera grand angolare 0.5 -> 8
        {
            BT_zoomRec.setText((zoomLv*7.5+0.5).toString().substring(0,3) + "x")
            BT_zoom0_5.setText((zoomLv+0.5).toString().substring(0,3) + "x")
        }

        //TODO dovrei far si che quando passo da video a foto e vic. sistema il valore dello zoom mostrato


       if(reBuild && !isRecording) // se sta registrando non cambia fotocamera
       {
           imageCapture = ImageCapture.Builder().build()

           try {
               cameraProvider.unbindAll()            // Unbind use cases before rebinding
               val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture) // devo ricostruire la camera ogni volta
               // in quato cambio la camera
               cameraControl = camera.cameraControl
           } catch(exc: Exception) {
               Log.e(TAG, "Use case binding failed", exc)
           }
       }

        cameraControl.setLinearZoom(zoomLv)
        Log.d(TAG,"Zoom lv: " + zoomLv)
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

    //<editor-fold desc= "FLASH METHODS">
    private fun switchFlashMode() {
        currFlashMode = FlashModes.next(currFlashMode)
        setFlashMode()
        flash.text = currFlashMode.text
    }

    private fun selectFlashMode(ordinal: Int?): Boolean {
        if(ordinal == null) {
            throw IllegalArgumentException()
        }
        currFlashMode = FlashModes.values()[ordinal]
        setFlashMode()
        flash.text = currFlashMode.text
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
    }
    //</editor-fold>

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

}
