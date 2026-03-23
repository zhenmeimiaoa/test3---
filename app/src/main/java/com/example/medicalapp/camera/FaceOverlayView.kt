package com.example.medicalapp.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val faceRects = mutableListOf<Rect>()
    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 40f
        style = Paint.Style.FILL
    }
    
    private var previewWidth: Int = 0
    private var previewHeight: Int = 0
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    
    fun updateFaces(
        faces: List<Rect>,
        previewW: Int,
        previewH: Int,
        imgW: Int,
        imgH: Int
    ) {
        faceRects.clear()
        faceRects.addAll(faces)
        previewWidth = previewW
        previewHeight = previewH
        imageWidth = imgW
        imageHeight = imgH
        invalidate()
    }
    
    fun clearFaces() {
        faceRects.clear()
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (faceRects.isEmpty() || previewWidth == 0 || imageWidth == 0) return
        
        val scaleX = previewWidth.toFloat() / imageWidth
        val scaleY = previewHeight.toFloat() / imageHeight
        
        faceRects.forEachIndexed { index, rect ->
            val scaledRect = Rect(
                (rect.left * scaleX).toInt(),
                (rect.top * scaleY).toInt(),
                (rect.right * scaleX).toInt(),
                (rect.bottom * scaleY).toInt()
            )
            
            canvas.drawRect(scaledRect, paint)
            canvas.drawText(
                "»À¡≥ ${index + 1}",
                scaledRect.left.toFloat(),
                scaledRect.top.toFloat() - 10,
                textPaint
            )
        }
    }
}
