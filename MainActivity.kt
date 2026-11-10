package com.teamadt.playercut

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private var original: Bitmap? = null
    private var result: Bitmap? = null
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var toleranceLabel: TextView
    private lateinit var fadeLabel: TextView
    private var tolerance = 48
    private var fadeStrength = 75

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildScreen() {
        val bg = Color.rgb(13, 10, 24)
        val purple = Color.rgb(159, 92, 245)
        val white = Color.rgb(245, 241, 255)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val title = TextView(this).apply {
            text = "eFootball Player Cut"
            textSize = 24f; setTextColor(white); gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }
        page.addView(title, LinearLayout.LayoutParams(-1, dp(48)))
        val subtitle = TextView(this).apply {
            text = "قص اللاعب وإضافة تلاشي دائري شفاف — بدون إنترنت"
            textSize = 13f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
        }
        page.addView(subtitle, LinearLayout.LayoutParams(-1, dp(34)))

        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(42, 38, 52))
            setImageDrawable(CheckerDrawable())
            contentDescription = "معاينة الصورة"
        }
        val previewParams = LinearLayout.LayoutParams(-1, dp(300))
        previewParams.setMargins(0, dp(10), 0, dp(12))
        page.addView(preview, previewParams)

        val choose = makeButton("اختيار صورة اللاعب", purple) { picker.launch("image/*") }
        page.addView(choose, buttonParams())
        val process = makeButton("إزالة الخلفية + التلاشي الدائري", Color.rgb(36, 190, 105)) {
            processImage()
        }
        page.addView(process, buttonParams())

        toleranceLabel = label("قوة إزالة الخلفية: $tolerance")
        page.addView(toleranceLabel)
        val tol = SeekBar(this).apply {
            max = 100; progress = tolerance
            setOnSeekBarChangeListener(simpleSeek { tolerance = it; toleranceLabel.text = "قوة إزالة الخلفية: $tolerance" })
        }
        page.addView(tol, LinearLayout.LayoutParams(-1, dp(38)))

        fadeLabel = label("قوة التلاشي الدائري: $fadeStrength%")
        page.addView(fadeLabel)
        val fade = SeekBar(this).apply {
            max = 100; progress = fadeStrength
            setOnSeekBarChangeListener(simpleSeek { fadeStrength = it; fadeLabel.text = "قوة التلاشي الدائري: $fadeStrength%" })
        }
        page.addView(fade, LinearLayout.LayoutParams(-1, dp(38)))

        val save = makeButton("حفظ PNG بخلفية شفافة", Color.rgb(104, 67, 190)) { savePng() }
        page.addView(save, buttonParams())
        status = TextView(this).apply {
            text = "اختر صورة للبدء. أفضل نتيجة تكون مع خلفية موحدة."
            textSize = 12f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
        }
        page.addView(status, LinearLayout.LayoutParams(-1, dp(48)))

        val scroll = ScrollView(this)
        scroll.addView(page)
        setContentView(scroll)
    }

    private fun label(s: String) = TextView(this).apply {
        text = s; textSize = 13f; setTextColor(Color.WHITE)
        setPadding(dp(2), dp(8), dp(2), 0)
    }

    private fun buttonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, dp(50)).apply { setMargins(0, dp(5), 0, dp(5)) }

    private fun makeButton(text: String, color: Int, action: () -> Unit) =
        Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            textSize = 14f
            setOnClickListener { action() }
        }

    private fun simpleSeek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { onChange(progress) }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun loadImage(uri: Uri) {
        try {
            val raw = if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val maxSide = 1800
                    val scale = min(1f, maxSide.toFloat() / max(info.size.width, info.size.height))
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            original = raw.copy(Bitmap.Config.ARGB_8888, true)
            result = null
            preview.setImageBitmap(original)
            status.text = "تم تحميل الصورة. اضغط على المعالجة."
        } catch (e: Exception) {
            status.text = "تعذر فتح الصورة: ${e.message}"
        }
    }

    private fun processImage() {
        val src = original ?: run { status.text = "اختر صورة أولاً."; return }
        status.text = "جارٍ المعالجة..."
        Thread {
            try {
                val out = removeBorderBackgroundAndFade(src, tolerance, fadeStrength)
                runOnUiThread {
                    result = out
                    preview.setImageBitmap(out)
                    status.text = "اكتملت المعالجة. احفظ النتيجة بصيغة PNG."
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "حدث خطأ أثناء المعالجة: ${e.message}" }
            }
        }.start()
    }

    // Flood-fill only removes background-coloured pixels connected to the image edges.
    // This keeps similarly coloured details inside the player much safer than global colour removal.
    private fun removeBorderBackgroundAndFade(src: Bitmap, tol: Int, fade: Int): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        val seeds = intArrayOf(
            pixels[0], pixels[w - 1], pixels[(h - 1) * w], pixels[h * w - 1]
        )
        val threshold = tol * 3
        var head = 0; var tail = 0

        fun add(i: Int) {
            if (i in pixels.indices && !visited[i]) {
                visited[i] = true
                queue[tail++] = i
            }
        }
        for (x in 0 until w) { add(x); add((h - 1) * w + x) }
        for (y in 0 until h) { add(y * w); add(y * w + w - 1) }

        while (head < tail) {
            val i = queue[head++]
            val c = pixels[i]
            var nearest = Int.MAX_VALUE
            for (s in seeds) {
                val dr = Color.red(c) - Color.red(s)
                val dg = Color.green(c) - Color.green(s)
                val db = Color.blue(c) - Color.blue(s)
                val d = abs(dr) + abs(dg) + abs(db)
                if (d < nearest) nearest = d
            }
            if (nearest <= threshold) {
                val feather = (nearest.toFloat() / max(1, threshold)).coerceIn(0f, 1f)
                pixels[i] = Color.argb((feather * 150).toInt(), Color.red(c), Color.green(c), Color.blue(c))
                val x = i % w; val y = i / w
                if (x > 0) add(i - 1)
                if (x < w - 1) add(i + 1)
                if (y > 0) add(i - w)
                if (y < h - 1) add(i + w)
            }
        }

        // Soft elliptical fade at the lower edge: transparent around the circular perimeter.
        if (fade > 0) {
            val cx = w * 0.5f
            val cy = h * 0.82f
            val rx = w * 0.54f
            val ry = h * 0.25f
            val strength = fade / 100f
            val start = 0.58f
            val end = 1.04f
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    val oldA = Color.alpha(pixels[i])
                    if (oldA == 0) continue
                    val dx = (x - cx) / rx
                    val dy = (y - cy) / ry
                    val d = sqrt(dx * dx + dy * dy)
                    val t = ((d - start) / (end - start)).coerceIn(0f, 1f)
                    val smooth = t * t * (3f - 2f * t)
                    val factor = 1f - smooth * strength
                    val a = (oldA * factor).toInt().coerceIn(0, 255)
                    pixels[i] = Color.argb(a, Color.red(pixels[i]), Color.green(pixels[i]), Color.blue(pixels[i]))
                }
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    private fun savePng() {
        val bmp = result ?: run { status.text = "عالج الصورة أولاً قبل الحفظ."; return }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "player_cut_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/eFootball Player Cut")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("لم يتم إنشاء ملف")
            contentResolver.openOutputStream(uri)?.use { stream ->
                if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw Exception("فشل حفظ PNG")
            }
            status.text = "تم الحفظ في Pictures/eFootball Player Cut"
        } catch (e: Exception) {
            status.text = "فشل الحفظ: ${e.message}"
        }
    }

    private class CheckerDrawable : android.graphics.drawable.Drawable() {
        private val paint = Paint()
        override fun draw(canvas: Canvas) {
            val size = 28f
            val b = bounds
            for (y in 0..(b.height() / size).toInt()) {
                for (x in 0..(b.width() / size).toInt()) {
                    paint.color = if ((x + y) % 2 == 0) Color.rgb(225,225,225) else Color.rgb(185,185,185)
                    canvas.drawRect(b.left + x*size, b.top + y*size, b.left + (x+1)*size, b.top + (y+1)*size, paint)
                }
            }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
    }
}
