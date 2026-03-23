package com.example.medicalapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.medicalapp.face.FaceCompareHelper
import com.example.medicalapp.model.IDCardInfo
import com.example.medicalapp.ocr.IDCardOCRHelper
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etIdNumber: EditText
    private lateinit var etGender: EditText
    private lateinit var etAddress: EditText
    private lateinit var ivIdCardPhoto: ImageView
    private lateinit var btnManualInput: Button
    private lateinit var btnOCRInput: Button
    private lateinit var btnFaceCompare: Button
    private lateinit var previewView: PreviewView
    private lateinit var tvCompareResult: TextView

    private var idCardInfo: IDCardInfo? = null
    private var idCardBitmap: Bitmap? = null
    
    private var ocrHelper: IDCardOCRHelper? = null
    private var faceCompareHelper: FaceCompareHelper? = null
    private var cameraExecutor: ExecutorService? = null
    private var imageCapture: ImageCapture? = null
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            loadImageFromUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 先检查权限
        if (!checkPermissions()) {
            requestPermissions()
            return
        }
        
        try {
            setContentView(R.layout.activity_main)
            initHelpers()
            initViews()
        } catch (e: Exception) {
            Toast.makeText(this, "Init error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
    
    private fun initHelpers() {
        try {
            ocrHelper = IDCardOCRHelper()
            faceCompareHelper = FaceCompareHelper()
            cameraExecutor = Executors.newSingleThreadExecutor()
        } catch (e: Exception) {
            Toast.makeText(this, "Helper init failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        try {
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
            
            tvCompareResult.text = "Ready - Please input ID card info"
        } catch (e: Exception) {
            Toast.makeText(this, "View init error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == 
               PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            1001
        )
    }

    private fun loadImageFromUri(uri: Uri) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    idCardBitmap = bitmap
                    
                    withContext(Dispatchers.Main) {
                        ivIdCardPhoto.setImageBitmap(bitmap)
                    }
                    
                    // OCR识别
                    ocrHelper?.let { helper ->
                        try {
                            val info = helper.recognizeIDCard(bitmap)
                            idCardInfo = info
                            
                            withContext(Dispatchers.Main) {
                                fillFormWithIDCardInfo(info)
                                btnFaceCompare.isEnabled = info.isValid()
                                tvCompareResult.text = "ID loaded. Ready for face verification."
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Load image failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
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
            Toast.makeText(this, "Please fill valid ID info", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startFaceVerification() {
        if (idCardBitmap == null) {
            Toast.makeText(this, "Please load ID card photo first", Toast.LENGTH_SHORT).show()
            return
        }
        
        previewView.visibility = View.VISIBLE
        btnFaceCompare.text = "Capturing..."
        btnFaceCompare.isEnabled = false
        
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
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

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
                
                scope.launch {
                    delay(2000)
                    takePhoto()
                }
                
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                resetUI()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        
                        image.close()
                        compareFaces(bitmap)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Capture error: ${e.message}", Toast.LENGTH_SHORT).show()
                        resetUI()
                    }
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
                        faceCompareHelper?.compareFaces(idBitmap, cameraBitmap)
                    } ?: 0.0f
                }
                
                val threshold = 0.6f
                val isMatch = similarity >= threshold
                
                withContext(Dispatchers.Main) {
                    tvCompareResult.text = if (isMatch) {
                        "MATCH: Consistent (${(similarity * 100).toInt()}%)"
                    } else {
                        "MISMATCH: Not consistent (${(similarity * 100).toInt()}%)"
                    }
                    
                    tvCompareResult.setBackgroundColor(
                        if (isMatch) android.graphics.Color.GREEN else android.graphics.Color.RED
                    )
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvCompareResult.text = "Comparison failed: ${e.message}"
                }
            }
            
            withContext(Dispatchers.Main) {
                resetUI()
            }
        }
    }

    private fun resetUI() {
        previewView.visibility = View.GONE
        btnFaceCompare.text = "Start Face Verification"
        btnFaceCompare.isEnabled = idCardInfo?.isValid() == true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            recreate()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
        ocrHelper?.close()
        faceCompareHelper?.close()
        scope.cancel()
    }
}
