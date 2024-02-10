package com.vinod.cameraxdevelopment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.vinod.cameraxdevelopment.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


/**
 * created by Vinod Kumar 22-Aug-2023
 * This Activity is a request result Activity used to capture the image from camera and preview
 * on click of ok it will send the image uri result to the Activity which launched it.
 */

class CameraViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isFlashOn = false
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private var savedUri: Uri? = null
    private var camera: Camera? = null
    private var isImageCaptureSuccess: Boolean = false
    private var isPermissionScreenOpened: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        isImageCaptureSuccess = false
        initViewsForCameraDisplay()
        addListeners()
    }

    /**
     * this method responsible for the click listeners and it's behaviours
     */
    private fun addListeners() {
        binding.tvCaptureButton.setOnClickListener {
            captureImage()
        }
        binding.tvFlashView.setOnClickListener {
            toggleFlash()
        }
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        binding.tvRejectImage.setOnClickListener {
            showCameraViews(true)
            showPreviewViews(false)
            isImageCaptureSuccess = false
        }
        binding.tvAcceptImage.setOnClickListener {
            if (savedUri != null) {
                val resultIntent = Intent()
                resultIntent.putExtra("imageUri", savedUri)
                setResult(RESULT_OK, resultIntent)
                Toast.makeText(this, "Thanks \n $savedUri", Toast.LENGTH_SHORT).show()
                isImageCaptureSuccess = true
                this@CameraViewActivity.finish()
            } else {
                Toast.makeText(this, "Please recapture the image", Toast.LENGTH_SHORT).show()
                showCameraViews(true)
                showPreviewViews(false)
            }
        }
    }

    /**
     * In this method we check if the permission is granted or not.
     * if not we request and start camera action 📷...
     */
    override fun onResume() {
        super.onResume()
        requestCameraPermission()
    }

    /**
     * this method initializes basic views.
     */
    private fun initViewsForCameraDisplay() {
        intent?.let {
            binding.toolbar.title = it.getStringExtra("title") ?: "Camera"
        }
        if (isPermissionGranted())
            setupAndStartCamera()
    }

    override fun onBackPressed() {
        if (!isImageCaptureSuccess){
            val resultIntent = Intent()
            val uri = Uri.parse("content://empty")
            //her passing empty uri instead of null due to web requirements
            resultIntent.putExtra("imageUri", uri)
            setResult(RESULT_OK, resultIntent)
            this@CameraViewActivity.finish()
        }else  this@CameraViewActivity.finish()
    }

    /**
     * this method will setup the camera executor.
     */
    private fun setupAndStartCamera() {
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }

    /**
     * helper method
     */
    private fun showCameraViews(isIt: Boolean) {
        binding.apply {
            viewFinder.visibility = if (isIt) View.VISIBLE else View.GONE
            tvCaptureButton.visibility = if (isIt) View.VISIBLE else View.GONE
            tvFlashView.visibility = if (isIt) View.VISIBLE else View.GONE
        }
    }

    /**
     * helper method
     */
    private fun showPreviewViews(isIt: Boolean) {
        binding.apply {
            ivPreview.visibility = if (isIt) View.VISIBLE else View.GONE
            tvAcceptImage.visibility = if (isIt) View.VISIBLE else View.GONE
            tvRejectImage.visibility = if (isIt) View.VISIBLE else View.GONE
        }
    }

    /**
     * this method will start the camera with minimal settings
     * with default flash off.
     */
    private fun startCamera() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetResolution(Size(720, 1080))
                    .setFlashMode(if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()
                val cameraSelector =
                    CameraSelector.DEFAULT_BACK_CAMERA //default lens is Back Camera.
                try {
                    cameraProvider.unbindAll()
                    camera =
                        cameraProvider.bindToLifecycle(
                            this,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                } catch (exc: Exception) {
                    exc.printStackTrace()
                    try {
                        if (camera == null) {
                            camera = cameraProvider.bindToLifecycle(
                                this,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                imageCapture
                            )
                        }
                    } catch (e: java.lang.Exception) {
                        e.printStackTrace()
                        /*  Here finishing because if both camera's are not available then
                            there is no point in showing the camera.
                         */
                        finish()
                    }
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    /**
     * this method responsible for capturing the image and
     * storing in the specified directory.
     */
    private fun captureImage() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(
                "yyyy-MM-dd-HH-mm-ss-SSS",
                Locale.US
            ).format(System.currentTimeMillis()) + "_damage.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    savedUri = Uri.fromFile(photoFile)
                    showCameraViews(false)
                    loadImageToPreview()
                    if (isFlashOn) {
                        toggleFlash()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                }
            })
    }

    /**
     * this method will be used to preview the captured image.
     */
    private fun loadImageToPreview() {
        savedUri?.let {
            Glide.with(binding.ivPreview)
                .load(it)
                .into(binding.ivPreview)
            showCameraViews(false)
            showPreviewViews(true)
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun toggleFlash() {
        if (isFlashOn)
            binding.tvFlashView.setBackgroundResource(R.drawable.baseline_flash_off_24)
        else binding.tvFlashView.setBackgroundResource(R.drawable.baseline_flash_on_24)
        isFlashOn = !isFlashOn
        camera?.let {
            it.cameraControl.enableTorch(isFlashOn)
        }
    }

    private fun isPermissionGranted(): Boolean {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission(): Boolean {
        var permissionGranted = false
        val cameraPermissionNotGranted =
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED
        if (cameraPermissionNotGranted) {
            val permission = arrayOf(Manifest.permission.CAMERA)
            requestPermissions(permission, CAMERA_PERMISSION_CODE)
        } else permissionGranted = true
        return permissionGranted
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted
                setupAndStartCamera()
            } else {
                // Permission was denied so open app's permission settings page. as of now showing toast as per requirement
                if (!isPermissionScreenOpened) {
//                    navigateToAppSettings()
                    isPermissionScreenOpened = true
                    Toast.makeText(this,"Please give camera access",Toast.LENGTH_SHORT).show()
                    onBackPressed()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            try {
                //after the navigating to permission setting screen and redirecting back to app
                val cameraPermissionNotGranted =
                    checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED
                if (cameraPermissionNotGranted) onBackPressed() //if still permission is denied
                else setupAndStartCamera() // if permission is granted.
            } catch (e: java.lang.Exception) {
                onBackPressed()
            }
        }
    }

    /**
     * Open the Application Settings
     * To grant permission
     */
    private fun navigateToAppSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivityForResult(intent, PERMISSION_REQUEST_CODE)
    }

    companion object {
        private const val CAMERA_PERMISSION_CODE = 1010;
        const val PERMISSION_REQUEST_CODE = 2003

    }
}