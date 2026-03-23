package com.example.medicalapp.ocr

import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.example.medicalapp.model.IDCardInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class IDCardOCRHelper {
    
    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )
    
    suspend fun recognizeIDCard(bitmap: Bitmap): IDCardInfo = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val info = parseIDCardText(visionText.text)
                continuation.resume(info)
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }
    
    private fun parseIDCardText(text: String): IDCardInfo {
        val info = IDCardInfo()
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        
        for (line in lines) {
            when {
                line.contains("姓名") || line.contains("Name") -> {
                    info.name = extractValue(line, listOf("姓名", "Name"))
                }
                line.contains("性别") || line.contains("女") || line.contains("男") -> {
                    info.gender = when {
                        line.contains("女") -> "Female"
                        line.contains("男") -> "Male"
                        else -> extractValue(line, listOf("性别", "Sex"))
                    }
                }
                line.contains("民族") || line.contains("Nation") -> {
                    info.nation = extractValue(line, listOf("民族", "Nation"))
                }
                line.contains("出生") || line.contains("Birth") -> {
                    info.birthDate = extractValue(line, listOf("出生", "Birth"))
                }
                line.contains("住址") || line.contains("Address") -> {
                    info.address = extractValue(line, listOf("住址", "Address"))
                }
                line.contains("公民身份号码") || line.contains("ID") || 
                line.matches(Regex(".*\\d{17}[\\dXx].*")) -> {
                    val idMatch = Regex("(\\d{17}[\\dXx])").find(line)
                    info.idNumber = idMatch?.value ?: extractValue(line, listOf("公民身份号码", "ID"))
                }
            }
        }
        
        return info
    }
    
    private fun extractValue(line: String, prefixes: List<String>): String {
        for (prefix in prefixes) {
            if (line.contains(prefix)) {
                return line.replace(prefix, "")
                    .replace(":", "")
                    .replace("：", "")
                    .trim()
            }
        }
        return line
    }
    
    fun close() {
        recognizer.close()
    }
}
