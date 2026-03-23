package com.example.medicalapp.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.example.medicalapp.face.FaceDetectorHelper
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val faceOverlay: FaceOverlayView,
    private val onFaceDetected: (Int) -> Unit
) {
    private val TAG = "CameraManager"
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    
    private val faceDetector = FaceDetectorHelper()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var isRunning = false
    
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImage(imageProxy)
                        }
                    }
                
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                
                isRunning = true
                Log.d(TAG, "相机启动成功")
                
            } catch (e: Exception) {
                Log.e(TAG, "相机启动失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun processImage(imageProxy: ImageProxy) {
        if (!isRunning) {
            imageProxy.close()
            return
        }
        
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        
        scope.launch {
            try {
                val faces = faceDetector.detectFaces(image)
                
                withContext(Dispatchers.Main) {
                    updateFaceOverlay(faces, imageProxy.width, imageProxy.height)
                    onFaceDetected(faces.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "人脸检测失败: ${e.message}")
            } finally {
                imageProxy.close()
            }
        }
    }
    
    private fun updateFaceOverlay(faces: List<com.google.mlkit.vision.face.Face>, imgW: Int, imgH: Int) {
        val faceRects = faces.map { face ->
            face.boundingBox
        }
        
        faceOverlay.updateFaces(
            faceRects,
            previewView.width,
            previewView.height,
            imgW,
            imgH
        )
    }
    
    fun stopCamera() {
        isRunning = false
        cameraProvider?.unbindAll()
        faceOverlay.clearFaces()
        Log.d(TAG, "相机已停止")
    }
    
    fun release() {
        stopCamera()
        faceDetector.close()
        cameraExecutor.shutdown()
        scope.cancel()
    }
}
