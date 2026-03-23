package com.example.medicalapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.medicalapp.face.FaceCompareHelper
import com.example.medicalapp.model.IDCardInfo
import com.example.medicalapp.ocr.IDCardOCRHelper
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var etName: TextInputEditText
    private lateinit var etIdNumber: TextInputEditText
    private lateinit var etGender: TextInputEditText
    private lateinit var etAddress: TextInputEditText
    private lateinit var ivIdCardPhoto: ImageView
    private lateinit var btnManualInput: Button
    private lateinit var btnOCRInput: Button
    private lateinit var btnFaceCompare: Button
    private lateinit var previewView: PreviewView
    private lateinit var tvCompareResult: TextView

    private var idCardInfo: IDCardInfo? = null
    private var idCardBitmap: Bitmap? = null
    
    private val ocrHelper = IDCardOCRHelper()
    private val faceCompareHelper = FaceCompareHelper()
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 图片选择器
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            loadImageFromUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        checkPermissions()
    }

    private fun initViews() {
        etName = findViewById(R.id.etName)
        etIdNumber = findViewById(R.id.etIdNumber)
        etGender = findViewById(R.id.etGender)
        etAddress = findViewById(R.id.etAddress)
        ivIdCardPhoto = findViewById(R.id.ivIdCardPhoto)
        btnManualInput = findViewById(R.id.btnManualInput)
        btnOCRInput = findViewById(R.id.btnOCRInput)
        btnFaceCompare = findViewById(R.id.btnFaceCompare)
        previewView = findViewById(R.id.previewView)
        tvCompareResult = findViewById(R.id.tvCompareResult)

        btnOCRInput.setOnClickListener {
            pickImage.launch("image/*")
        }

        btnManualInput.setOnClickListener {
            saveManualInput()
        }

        btnFaceCompare.setOnClickListener {
            startFaceVerification()
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        
        val granted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!granted) {
            requestPermissions(permissions, 1001)
        }
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            idCardBitmap = bitmap
            ivIdCardPhoto.setImageBitmap(bitmap)
            
            // 启动 OCR 识别
            scope.launch {
                try {
                    val info = withContext(Dispatchers.IO) {
                        ocrHelper.recognizeIDCard(bitmap)
                    }
                    idCardInfo = info
                    fillFormWithIDCardInfo(info)
                    btnFaceCompare.isEnabled = info.isValid()
                    tvCompareResult.text = "ID Card info loaded. Ready for face verification."
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fillFormWithIDCardInfo(info: IDCardInfo) {
        etName.setText(info.name)
        etIdNumber.setText(info.idNumber)
        etGender.setText(info.gender)
        etAddress.setText(info.address)
    }

    private fun saveManualInput() {
        idCardInfo = IDCardInfo(
            name = etName.text.toString(),
            idNumber = etIdNumber.text.toString(),
            gender = etGender.text.toString(),
            address = etAddress.text.toString()
        )
        
        if (idCardInfo!!.isValid()) {
            btnFaceCompare.isEnabled = true
            tvCompareResult.text = "Manual input saved. Ready for face verification."
            Toast.makeText(this, "ID info saved", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Please fill in valid ID info", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startFaceVerification() {
        previewView.visibility = View.VISIBLE
        btnFaceCompare.text = "Capturing..."
        btnFaceCompare.isEnabled = false
        
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
                
                // 延迟2秒后自动拍照
                scope.launch {
                    delay(2000)
                    takePhoto()
                }
                
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera failed: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    
                    image.close()
                    
                    compareFaces(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Photo failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                    resetUI()
                }
            }
        )
    }

    private fun compareFaces(cameraBitmap: Bitmap) {
        scope.launch {
            try {
                val similarity = withContext(Dispatchers.IO) {
                    idCardBitmap?.let { idBitmap ->
                        faceCompareHelper.compareFaces(idBitmap, cameraBitmap)
                    } ?: 0.0f
                }
                
                val threshold = 0.6f // 相似度阈值
                val isMatch = similarity >= threshold
                
                tvCompareResult.text = if (isMatch) {
                    "MATCH: ID card and face are consistent (Similarity: ${(similarity * 100).toInt()}%)"
                } else {
                    "MISMATCH: ID card and face are NOT consistent (Similarity: ${(similarity * 100).toInt()}%)"
                }
                
                tvCompareResult.setBackgroundColor(
                    if (isMatch) android.graphics.Color.GREEN else android.graphics.Color.RED
                )
                
            } catch (e: Exception) {
                tvCompareResult.text = "Comparison failed: ${e.message}"
            }
            
            resetUI()
        }
    }

    private fun resetUI() {
        previewView.visibility = View.GONE
        btnFaceCompare.text = "Start Face Verification"
        btnFaceCompare.isEnabled = idCardInfo?.isValid() == true
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        ocrHelper.close()
        faceCompareHelper.close()
        scope.cancel()
    }
}
