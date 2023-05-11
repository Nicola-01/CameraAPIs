package com.unipd.cameraapis

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraSelector.*
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

    var shoot : Button? = null
    private lateinit var SB_zoom : SeekBar
    private lateinit var flash : Button
    var rotation : Button? = null
    var currFlashMode : FlashModes = FlashModes.OFF
    var scaleDown: Animation? = null
    var scaleUp: Animation? = null
    private lateinit var cameraControl:CameraControl
    private lateinit var availableCameraInfos: MutableList<CameraInfo>
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var preview: Preview
    private lateinit var recorder: Recorder
    private var currentCamera = 0;
    // 0 -> back default
    // 1 -> front default grand angolare
    // 2 -> back grand angolare
    // 3 -> front normale
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

        shoot = viewBinding.BTShoots
        //var shoot : Button = viewBinding.BTShoots

        rotation = viewBinding.BTRotation


        scaleDown = AnimationUtils.loadAnimation(this,R.anim.scale_down)
        scaleUp = AnimationUtils.loadAnimation(this,R.anim.scale_up)

        // Set up the listeners for take photo and video capture buttons
        shoot?.setOnClickListener { takePhoto() }
        shoot?.setOnLongClickListener{ captureVideo() }

        SB_zoom = viewBinding.SBZoom

        SB_zoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                changeZoom(progress)
            }

            // OnSeekBarChangeListener is an interface,
            // so an implementation must be provided for all the methods
            override fun onStartTrackingTouch(seek: SeekBar) = Unit
            override fun onStopTrackingTouch(seek: SeekBar) = Unit
        })





//        shoot.setOnLongClickListener(OnLongClickListener {
//            Log.d(TAG,"LongClickListener")
//            true
//        })

        rotation?.setOnClickListener { rotateCamera() }

        cameraExecutor = Executors.newSingleThreadExecutor()
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
        shoot?.startAnimation(scaleDown)
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
                        viewBinding.BTShoots.setBackgroundResource(R.drawable.rounded_corner_red);
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        viewBinding.BTShoots.setBackgroundResource(R.drawable.rounded_corner);
                    }
                }
            }
        return true
    }

    private fun rotateCamera() {
        if(cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA){
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

            cameraProviderFuture.addListener({
                // Used to bind the lifecycle of cameras to the lifecycle owner
                //cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Preview
                /*val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                    }*/

                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                Log.d(TAG, "Front Camera selected")

                imageCapture = ImageCapture.Builder().build()

                try {
                    // Unbind use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture)

                } catch(exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }

            }, ContextCompat.getMainExecutor(this))
        }
        else {
            Log.d(TAG, "Chiamata a start Camera() dopo else") //messaggio di test
            startCamera()
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

        if(progress<25)
        {
            zoomLv = progress/24.toFloat()

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


       if(reBuild)
       {
           imageCapture = ImageCapture.Builder().build()

           try {
               cameraProvider.unbindAll()            // Unbind use cases before rebinding
               cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture) // Bind use cases to camera

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
