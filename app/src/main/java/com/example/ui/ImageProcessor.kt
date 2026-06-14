package com.example.ui

import android.graphics.*
import com.example.model.EditState
import com.example.model.TextOverlay
import com.example.model.StickerOverlay
import kotlin.math.*

object ImageProcessor {

    /**
     * Main pixel adjustments applied in a single pass over pixels.
     * Extremely fast for continuous, real-time UI updates.
     */
    fun applyAdjustments(
        bitmap: Bitmap,
        state: EditState
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val brightness = state.brightness
        val contrast = state.contrast
        val saturation = state.saturation
        val exposure = state.exposure
        val highlights = state.highlights
        val shadows = state.shadows
        val temperature = state.temperature
        val tint = state.tint
        val vignette = state.vignette
        val filterName = state.filterName

        val cx = width / 2f
        val cy = height / 2f
        val maxDist = sqrt((cx * cx + cy * cy).toDouble()).toFloat()

        // Cache adjustment calculations
        val bAmount = brightness * 2.55f
        val contrastFactor = if (contrast >= 0) 1f + (contrast / 100f) * 2f else 1f + (contrast / 100f) * 0.8f
        val expFactor = 2.0.pow((exposure / 100f).toDouble()).toFloat()
        val satFactor = (saturation + 100f) / 100f

        val tempAmount = temperature * 0.4f
        val tintAmount = tint * 0.3f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val pix = pixels[index]

                var r = ((pix shr 16) and 0xFF).toFloat()
                var g = ((pix shr 8) and 0xFF).toFloat()
                var b = (pix and 0xFF).toFloat()

                // 1. Exposure
                if (exposure != 0f) {
                    r *= expFactor
                    g *= expFactor
                    b *= expFactor
                }

                // 2. Brightness
                if (brightness != 0f) {
                    r += bAmount
                    g += bAmount
                    b += bAmount
                }

                // 3. Contrast
                if (contrast != 0f) {
                    r = (r - 128f) * contrastFactor + 128f
                    g = (g - 128f) * contrastFactor + 128f
                    b = (b - 128f) * contrastFactor + 128f
                }

                // Calculate luminance for highlights, shadows, and saturation
                val lVal = 0.299f * r + 0.587f * g + 0.114f * b

                // 4. Highlights & Shadows
                if (highlights != 0f && lVal > 128f) {
                    val weight = (lVal - 128f) / 127f
                    val hAmount = (highlights / 100f) * 40f * weight
                    r += hAmount
                    g += hAmount
                    b += hAmount
                }
                if (shadows != 0f && lVal < 128f) {
                    val weight = (128f - lVal) / 128f
                    val sAmount = (shadows / 100f) * 40f * weight
                    r += sAmount
                    g += sAmount
                    b += sAmount
                }

                // 5. Temperature & Tint
                if (temperature != 0f) {
                    r += tempAmount
                    b -= tempAmount
                }
                if (tint != 0f) {
                    g += tintAmount
                    r -= tintAmount * 0.5f
                    b -= tintAmount * 0.5f
                }

                // 6. Saturation
                if (saturation != 0f) {
                    val finalL = 0.299f * r + 0.587f * g + 0.114f * b
                    r = finalL + (r - finalL) * satFactor
                    g = finalL + (g - finalL) * satFactor
                    b = finalL + (b - finalL) * satFactor
                }

                // 7. Core Color Filters
                when (filterName) {
                    "Vintage" -> {
                        r = r * 0.9f + 20f
                        g = g * 0.82f + 15f
                        b = b * 0.72f + 8f
                    }
                    "B&W" -> {
                        val gray = 0.299f * r + 0.587f * g + 0.114f * b
                        r = gray
                        g = gray
                        b = gray
                    }
                    "Punchy" -> {
                        // High saturation/contrast LUT simulation
                        r = (r - 128f) * 1.15f + 128f
                        g = (g - 128f) * 1.15f + 128f
                        b = (b - 128f) * 1.15f + 128f
                        val gray = 0.299f * r + 0.587f * g + 0.114f * b
                        r = gray + (r - gray) * 1.25f
                        g = gray + (g - gray) * 1.25f
                        b = gray + (b - gray) * 1.25f
                    }
                    "Cinematic" -> {
                        // Orange and Teal high-quality filter
                        val gray = 0.299f * r + 0.587f * g + 0.114f * b
                        r = r * 1.15f + 12f
                        g = g * 1.02f
                        b = b * 0.92f - 4f
                        // boost cyan in shadows
                        if (gray < 128f) {
                            val w = (128f - gray) / 128f
                            g += w * 10f
                            b += w * 18f
                        }
                    }
                    "Warm" -> {
                        r += 24f
                        g += 12f
                        b -= 8f
                    }
                    "Cool" -> {
                        r -= 10f
                        g += 5f
                        b += 25f
                    }
                    "Chrome" -> {
                        val maxVal = max(r, max(g, b))
                        r = r + (maxVal - r) * 0.2f
                        g = g + (maxVal - g) * 0.1f
                        b = b * 0.95f
                    }
                }

                // 8. Vignette
                if (vignette > 0f) {
                    val dx = x - cx
                    val dy = y - cy
                    val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    val normalizedDist = dist / maxDist
                    val vigFactor = 1f - (vignette / 100f) * (normalizedDist * normalizedDist * 0.65f)
                    r *= vigFactor
                    g *= vigFactor
                    b *= vigFactor
                }

                val ir = r.coerceIn(0f, 255f).toInt()
                val ig = g.coerceIn(0f, 255f).toInt()
                val ib = b.coerceIn(0f, 255f).toInt()

                pixels[index] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Efficient Blur implementation by downscaling and applying box blur.
     * High performance and smooth results completely on-device.
     */
    fun applyBlur(src: Bitmap, blurAmount: Float): Bitmap {
        if (blurAmount <= 0) return src

        val scale = when {
            blurAmount > 75f -> 0.10f
            blurAmount > 50f -> 0.15f
            blurAmount > 25f -> 0.20f
            else -> 0.30f
        }

        val dstW = max(10, (src.width * scale).toInt())
        val dstH = max(10, (src.height * scale).toInt())

        val small = Bitmap.createScaledBitmap(src, dstW, dstH, true)

        // Perform 3x3 simple box blur pass on small bitmap to smoothen pixels
        val output = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(dstW * dstH)
        small.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
        val outerPixels = IntArray(dstW * dstH)

        val passes = if (blurAmount > 60f) 3 else if (blurAmount > 30f) 2 else 1

        for (pass in 0 until passes) {
            for (y in 1 until dstH - 1) {
                for (x in 1 until dstW - 1) {
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0

                    for (ky in -1..1) {
                        for (kx in -1..1) {
                            val pixel = pixels[(y + ky) * dstW + (x + kx)]
                            rSum += (pixel shr 16) and 0xFF
                            gSum += (pixel shr 8) and 0xFF
                            bSum += pixel and 0xFF
                        }
                    }

                    val rAvg = rSum / 9
                    val gAvg = gSum / 9
                    val bAvg = bSum / 9

                    outerPixels[y * dstW + x] = (0xFF shl 24) or (rAvg shl 16) or (gAvg shl 8) or bAvg
                }
            }
            System.arraycopy(outerPixels, 0, pixels, 0, pixels.size)
        }

        output.setPixels(pixels, 0, dstW, 0, 0, dstW, dstH)

        // Scale back up to original size
        return Bitmap.createScaledBitmap(output, src.width, src.height, true)
    }

    /**
     * Sharpness filter: original + factor * (original - blurred).
     */
    fun applySharpness(src: Bitmap, sharpnessAmount: Float): Bitmap {
        if (sharpnessAmount <= 0) return src

        // Create slightly blurred image for high pass sharpening
        // Very low blur is used to get high-frequency details
        val blurred = applyBlur(src, 10f)

        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val srcPixels = IntArray(width * height)
        val blurPixels = IntArray(width * height)

        src.getPixels(srcPixels, 0, width, 0, 0, width, height)
        blurred.getPixels(blurPixels, 0, width, 0, 0, width, height)

        val sharpFactor = (sharpnessAmount / 100f) * 1.5f

        for (i in srcPixels.indices) {
            val srcPix = srcPixels[i]
            val blurPix = blurPixels[i]

            val sR = (srcPix shr 16) and 0xFF
            val sG = (srcPix shr 8) and 0xFF
            val sB = srcPix and 0xFF

            val bR = (blurPix shr 16) and 0xFF
            val bG = (blurPix shr 8) and 0xFF
            val bB = blurPix and 0xFF

            val nR = (sR + sharpFactor * (sR - bR)).coerceIn(0f, 255f).toInt()
            val nG = (sG + sharpFactor * (sG - bG)).coerceIn(0f, 255f).toInt()
            val nB = (sB + sharpFactor * (sB - bB)).coerceIn(0f, 255f).toInt()

            srcPixels[i] = (0xFF shl 24) or (nR shl 16) or (nG shl 8) or nB
        }

        output.setPixels(srcPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Combined pipeline: Adjustments -> Blur -> Sharpness
     */
    fun processImage(bitmap: Bitmap, state: EditState): Bitmap {
        // 1. Core color adjustments & Vignette & Filters
        var out = applyAdjustments(bitmap, state)

        // 2. Blur / Background Blur
        if (state.blur > 0f) {
            out = applyBlur(out, state.blur)
        }

        // 3. Sharpness
        if (state.sharpness > 0f) {
            out = applySharpness(out, state.sharpness)
        }

        // 4. Background Blur layer
        if (state.backgroundBlur > 0f) {
            out = applyBackgroundBlurBokeh(out, state.backgroundBlur)
        }

        // 5. Build Frames/Borders
        if (state.frameType != "None") {
            out = applyFrame(out, state.frameType)
        }

        return out
    }

    /**
     * Special background bokeh/blur function: Blurs outer boundaries of a photo while keeping center sharp.
     * Simulates wide-aperture lens blur (Radial tilt-shift).
     */
    fun applyBackgroundBlurBokeh(src: Bitmap, amount: Float): Bitmap {
        val blurred = applyBlur(src, amount)
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(output)
        canvas.drawBitmap(blurred, 0f, 0f, null)

        // Draw radial mask on top
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }

        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(mask)
        val centerColor = Color.WHITE
        val outerColor = Color.TRANSPARENT

        val radius = min(width, height) * 0.42f
        val gradient = RadialGradient(
            width / 2f,
            height / 2f,
            radius,
            intArrayOf(centerColor, centerColor, outerColor),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )

        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
        }

        maskCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)

        // Draw source over blurred back with radial DST_IN mask so center stays sharp!
        val sharpOverlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val overlayCanvas = Canvas(sharpOverlay)
        overlayCanvas.drawBitmap(src, 0f, 0f, null)
        overlayCanvas.drawBitmap(mask, 0f, 0f, paint)

        canvas.drawBitmap(sharpOverlay, 0f, 0f, null)
        return output
    }

    /**
     * Orientation operations: rot, flip, reflect.
     */
    fun rotateAndFlip(src: Bitmap, angle: Float, flipX: Boolean, flipY: Boolean): Bitmap {
        if (angle == 0f && !flipX && !flipY) return src

        val matrix = Matrix()
        if (angle != 0f) {
            matrix.postRotate(angle)
        }
        val sx = if (flipX) -1f else 1f
        val sy = if (flipY) -1f else 1f
        if (flipX || flipY) {
            matrix.postScale(sx, sy)
        }

        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * Simple aspect ratio cropping.
     */
    fun cropToRatio(src: Bitmap, ratio: String): Bitmap {
        val width = src.width
        val height = src.height

        val targetRatio = when (ratio) {
            "1:1" -> 1f
            "4:3" -> 4f / 3f
            "16:9" -> 16f / 9f
            "2:3" -> 2f / 3f
            "3:4" -> 3f / 4f
            else -> return src // Free/Original
        }

        val currentRatio = width.toFloat() / height.toFloat()

        var newW = width
        var newH = height
        var xOffset = 0
        var yOffset = 0

        if (currentRatio > targetRatio) {
            // Screen is too wide
            newW = (height * targetRatio).toInt()
            xOffset = (width - newW) / 2
        } else {
            // Screen is too tall
            newH = (width / targetRatio).toInt()
            yOffset = (height - newH) / 2
        }

        return Bitmap.createBitmap(src, xOffset, yOffset, newW, newH)
    }

    /**
     * Renders beautiful solid overlays or borders onto the image canvas.
     */
    fun applyFrame(src: Bitmap, frameType: String): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(src, 0f, 0f, null)

        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        when (frameType) {
            "Classic White" -> {
                val size = min(width, height) * 0.05f
                borderPaint.color = Color.WHITE
                borderPaint.strokeWidth = size
                canvas.drawRect(size / 2f, size / 2f, width - size / 2f, height - size / 2f, borderPaint)
            }
            "Studio Black" -> {
                val size = min(width, height) * 0.05f
                borderPaint.color = Color.BLACK
                borderPaint.strokeWidth = size
                canvas.drawRect(size / 2f, size / 2f, width - size / 2f, height - size / 2f, borderPaint)
            }
            "Vintage Film" -> {
                // Draws dark retro frame on top and bottom with tiny white film hashes
                val size = min(width, height) * 0.08f
                val fillPaint = Paint().apply {
                    color = Color.parseColor("#121212")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, width.toFloat(), size, fillPaint)
                canvas.drawRect(0f, height - size, width.toFloat(), height.toFloat(), fillPaint)

                // Draw tiny sprocket holes
                val holePaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                val holeW = width * 0.015f
                val holeH = size * 0.35f
                val gap = width * 0.05f
                var xPos = gap
                while (xPos < width - gap) {
                    canvas.drawRect(xPos, size * 0.3f, xPos + holeW, size * 0.3f + holeH, holePaint)
                    canvas.drawRect(xPos, height - size * 0.7f, xPos + holeW, height - size * 0.7f + holeH, holePaint)
                    xPos += gap + holeW
                }
            }
            "Polaroid Border" -> {
                // Draw photographic white bottom border
                val outPolaroid = Bitmap.createBitmap(width, (height * 1.15f).toInt(), Bitmap.Config.ARGB_8888)
                val polCanvas = Canvas(outPolaroid)
                polCanvas.drawColor(Color.WHITE)

                // Margin sizes
                val border = min(width, height) * 0.04f
                val srcRect = Rect(0, 0, width, height)
                val destRect = RectF(
                    border,
                    border,
                    width.toFloat() - border,
                    height.toFloat() - border
                )
                polCanvas.drawBitmap(src, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                return outPolaroid
            }
        }

        return output
    }

    /**
     * Combines image and all text & sticker assets into a single final flat exported file bitmap.
     */
    fun finalizeExport(
        src: Bitmap,
        state: EditState
    ): Bitmap {
        // 1. Resize and apply cropping orientation
        var mid = rotCropResize(src, state)

        // 2. Filter & Tone adjust
        mid = processImage(mid, state)

        // 3. Draw Overlays (Text & Stickers) onto static Bitmap
        val finalBmp = Bitmap.createBitmap(mid.width, mid.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBmp)
        canvas.drawBitmap(mid, 0f, 0f, null)

        val w = mid.width.toFloat()
        val h = mid.height.toFloat()

        // Draw Stickers
        val stickerPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        for (sticker in state.stickerOverlays) {
            stickerPaint.textSize = (sticker.size / 400f) * min(w, h)
            val sx = sticker.xPct * w
            val sy = sticker.yPct * h
            canvas.drawText(sticker.sticker, sx, sy, stickerPaint)
        }

        // Draw Texts
        val textPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bgPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        for (textO in state.textOverlays) {
            val textSize = (textO.size / 400f) * min(w, h)
            textPaint.textSize = textSize
            textPaint.color = textO.color.toInt()

            val tx = textO.xPct * w
            val ty = textO.yPct * h

            // Background bounds calculation
            val rect = Rect()
            textPaint.getTextBounds(textO.text, 0, textO.text.length, rect)

            val textW = rect.width().toFloat() + textSize * 0.5f
            val textH = rect.height().toFloat() + textSize * 0.4f

            // Clean background box padding
            bgPaint.color = textO.backgroundColor.toInt()
            if (textO.backgroundColor shr 24 != 0L) {
                canvas.drawRoundRect(
                    tx - textW / 2f,
                    ty - textH * 0.85f,
                    tx + textW / 2f,
                    ty + textH * 0.25f,
                    12f, 12f,
                    bgPaint
                )
            }

            canvas.drawText(textO.text, tx, ty, textPaint)
        }

        return finalBmp
    }

    private fun rotCropResize(src: Bitmap, state: EditState): Bitmap {
        var base = src
        // Apply flip and rotates
        base = rotateAndFlip(base, state.rotationAngle, state.flipHorizontal, state.flipVertical)

        // Apply crop
        if (state.cropRatio != "Original") {
            base = cropToRatio(base, state.cropRatio)
        }

        return base
    }
}
