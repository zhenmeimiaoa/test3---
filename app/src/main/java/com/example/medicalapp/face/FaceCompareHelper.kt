package com.example.medicalapp.face

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FaceCompareHelper {
    
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )
    
    data class FaceFeatures(
        val boundingBox: Rect,
        val leftEyeOpenProbability: Float?,
        val rightEyeOpenProbability: Float?,
        val smilingProbability: Float?,
        val headEulerAngleX: Float,
        val headEulerAngleY: Float,
        val headEulerAngleZ: Float
    )
    
    suspend fun extractFaceFeatures(bitmap: Bitmap): FaceFeatures? = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0] // 取第一张人脸
                    continuation.resume(
                        FaceFeatures(
                            boundingBox = face.boundingBox,
                            leftEyeOpenProbability = face.leftEyeOpenProbability,
                            rightEyeOpenProbability = face.rightEyeOpenProbability,
                            smilingProbability = face.smilingProbability,
                            headEulerAngleX = face.headEulerAngleX,
                            headEulerAngleY = face.headEulerAngleY,
                            headEulerAngleZ = face.headEulerAngleZ
                        )
                    )
                } else {
                    continuation.resume(null)
                }
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }
    
    /**
     * 比对两张人脸照片
     * 返回相似度 0.0 - 1.0
     */
    suspend fun compareFaces(idCardBitmap: Bitmap, cameraBitmap: Bitmap): Float {
        val idCardFace = extractFaceFeatures(idCardBitmap)
        val cameraFace = extractFaceFeatures(cameraBitmap)
        
        if (idCardFace == null || cameraFace == null) {
            return 0.0f
        }
        
        // 简单的特征比对（实际应用建议使用专业的人脸识别SDK）
        val boxSimilarity = calculateBoxSimilarity(
            idCardFace.boundingBox,
            cameraFace.boundingBox
        )
        
        val angleSimilarity = calculateAngleSimilarity(
            idCardFace.headEulerAngleX to cameraFace.headEulerAngleX,
            idCardFace.headEulerAngleY to cameraFace.headEulerAngleY,
            idCardFace.headEulerAngleZ to cameraFace.headEulerAngleZ
        )
        
        return (boxSimilarity * 0.4f + angleSimilarity * 0.6f)
    }
    
    private fun calculateBoxSimilarity(box1: Rect, box2: Rect): Float {
        val intersection = Rect(
            maxOf(box1.left, box2.left),
            maxOf(box1.top, box2.top),
            minOf(box1.right, box2.right),
            minOf(box1.bottom, box2.bottom)
        )
        
        if (intersection.width() <= 0 || intersection.height() <= 0) {
            return 0.0f
        }
        
        val intersectionArea = intersection.width() * intersection.height()
        val area1 = box1.width() * box1.height()
        val area2 = box2.width() * box2.height()
        val unionArea = area1 + area2 - intersectionArea
        
        return intersectionArea.toFloat() / unionArea
    }
    
    private fun calculateAngleSimilarity(vararg angles: Pair<Float, Float>): Float {
        var totalSimilarity = 0.0f
        for ((a1, a2) in angles) {
            val diff = kotlin.math.abs(a1 - a2)
            totalSimilarity += 1.0f - (diff / 180.0f).coerceIn(0.0f, 1.0f)
        }
        return totalSimilarity / angles.size
    }
    
    fun close() {
        faceDetector.close()
    }
}
