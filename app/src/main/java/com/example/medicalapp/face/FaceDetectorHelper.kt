package com.example.medicalapp.face

import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FaceDetectorHelper {
    
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()
    
    private val detector = FaceDetection.getClient(options)
    
    interface FaceDetectionListener {
        fun onFacesDetected(faces: List<Face>, imageWidth: Int, imageHeight: Int)
        fun onError(e: Exception)
    }
    
    suspend fun detectFaces(image: InputImage): List<Face> = suspendCancellableCoroutine { continuation ->
        detector.process(image)
            .addOnSuccessListener { faces ->
                continuation.resume(faces)
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }
    
    fun detectFaces(image: InputImage, listener: FaceDetectionListener) {
        detector.process(image)
            .addOnSuccessListener { faces ->
                listener.onFacesDetected(faces, image.width, image.height)
            }
            .addOnFailureListener { e ->
                listener.onError(e)
            }
    }
    
    fun close() {
        detector.close()
    }
    
    companion object {
        fun getFaceBounds(face: Face): Rect {
            return face.boundingBox
        }
    }
}
