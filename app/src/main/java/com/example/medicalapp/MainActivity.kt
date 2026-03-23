package com.example.medicalapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.medicalapp.camera.CameraManager
import com.example.medicalapp.camera.FaceOverlayView

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }
    
    private lateinit var previewView: PreviewView
    private lateinit var faceOverlay: FaceOverlayView
    private lateinit var btnToggleCamera: Button
    private lateinit var tvStatus: TextView
    
    private var cameraManager: CameraManager? = null
    private var isCameraRunning = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        checkCameraPermission()
    }
    
    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        faceOverlay = findViewById(R.id.faceOverlay)
        btnToggleCamera = findViewById(R.id.btnToggleCamera)
        tvStatus = findViewById(R.id.tvStatus)
        
        btnToggleCamera.setOnClickListener {
            toggleCamera()
        }
    }
    
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            initCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    private fun initCamera() {
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = previewView,
            faceOverlay = faceOverlay,
            onFaceDetected = { faceCount ->
                updateFaceStatus(faceCount)
            }
        )
    }
    
    private fun toggleCamera() {
        if (isCameraRunning) {
            cameraManager?.stopCamera()
            btnToggleCamera.text = getString(R.string.start_camera)
            tvStatus.text = getString(R.string.no_face)
            isCameraRunning = false
        } else {
            cameraManager?.startCamera()
            btnToggleCamera.text = getString(R.string.stop_camera)
            isCameraRunning = true
        }
    }
    
    private fun updateFaceStatus(faceCount: Int) {
        tvStatus.text = getString(R.string.face_detected, faceCount)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initCamera()
            } else {
                Toast.makeText(
                    this,
                    R.string.camera_permission_required,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.release()
    }
}
