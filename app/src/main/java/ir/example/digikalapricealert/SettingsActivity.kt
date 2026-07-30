package ir.example.digikalapricealert

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var store: SettingsStore
    private lateinit var colorContainer: GridLayout
    private lateinit var txtCurrentSound: android.widget.TextView

    private val soundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                // به‌جای اعتماد به مجوز موقتِ فایل انتخاب‌شده (که با خیلی از فایل‌های
                // دانلودشده یا مدیرهای فایل مختلف ممکن است بعد از بسته‌شدن این صفحه
                // از بین برود و صدا بی‌صدا شکست بخورد)، خودِ فایل را داخل حافظه‌ی
                // اختصاصی برنامه کپی می‌کنیم و همیشه از همان کپی استفاده می‌کنیم.
                val copiedUri = copySoundToInternalStorage(uri)
                if (copiedUri != null) {
                    store.setAlertSoundUri(copiedUri.toString())
                } else {
                    Toast.makeText(
                        this,
                        "خواندن فایل صوتی انتخاب‌شده ممکن نشد؛ صدای پیش‌فرض باقی می‌ماند",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                store.setAlertSoundUri(NotificationHelper.SILENT_MARKER)
            }
            NotificationHelper.recreateChannelWithNewSound(this)
            updateCurrentSoundLabel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        store = SettingsStore(this)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        ThemeManager.applyToActivity(this, store)

        colorContainer = findViewById(R.id.colorContainer)
        buildColorSwatches()

        txtCurrentSound = findViewById(R.id.txtCurrentSound)
        updateCurrentSoundLabel()

        findViewById<View>(R.id.btnPickSound).setOnClickListener { openSoundPicker() }
        findViewById<View>(R.id.btnTestAlert).setOnClickListener { sendTestAlert() }
    }

    /**
     * فایل صوتی انتخاب‌شده را از هر منبعی (مدیر فایل، دانلودها، و غیره) به
     * حافظه‌ی اختصاصی برنامه کپی می‌کند و آدرس آن را از طریق FileProvider
     * برمی‌گرداند تا سیستم همیشه، حتی بعد از ری‌استارت گوشی، بتواند آن را بخواند.
     */
    private fun copySoundToInternalStorage(sourceUri: Uri): Uri? {
        return try {
            val soundsDir = File(filesDir, "sounds")
            if (!soundsDir.exists()) soundsDir.mkdirs()
            val destFile = File(soundsDir, "alert_sound")

            contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            FileProvider.getUriForFile(this, "$packageName.fileprovider", destFile)
        } catch (e: Exception) {
            null
        }
    }

    private fun sendTestAlert() {
        val fakeProduct = TrackedProduct(
            productId = "test",
            title = "محصول آزمایشی",
            thresholdToman = 100000,
            lastPriceToman = 90000
        )
        NotificationHelper.notifyPriceDrop(this, fakeProduct)
        Toast.makeText(
            this,
            "یک هشدار آزمایشی ارسال شد؛ اگر ندیدی یا صدا نداشت، مجوز نوتیفیکیشن و بهینه‌سازی باتری را از تنظیمات گوشی چک کن",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun buildColorSwatches() {
        colorContainer.removeAllViews()
        val currentColor = store.getThemeColor()
        val sizePx = (56 * resources.displayMetrics.density).toInt()
        val marginPx = (8 * resources.displayMetrics.density).toInt()

        for ((name, hex) in ThemeManager.palette) {
            val swatch = View(this)
            val params = GridLayout.LayoutParams()
            params.width = sizePx
            params.height = sizePx
            params.setMargins(marginPx, marginPx, marginPx, marginPx)
            swatch.layoutParams = params
            swatch.contentDescription = name

            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.OVAL
            drawable.setColor(Color.parseColor(hex))
            if (hex.equals(currentColor, ignoreCase = true)) {
                drawable.setStroke((3 * resources.displayMetrics.density).toInt(), Color.BLACK)
            }
            swatch.background = drawable

            swatch.setOnClickListener {
                store.setThemeColor(hex)
                ThemeManager.applyToActivity(this, store)
                buildColorSwatches()
            }

            colorContainer.addView(swatch)
        }
    }

    private fun updateCurrentSoundLabel() {
        val soundPref = store.getAlertSoundUri()
        txtCurrentSound.text = when {
            soundPref == NotificationHelper.SILENT_MARKER -> "صدای فعلی: بی‌صدا"
            soundPref == null -> "صدای فعلی: پیش‌فرض سیستم"
            else -> "صدای فعلی: سفارشی (فایل انتخاب‌شده)"
        }
    }

    private fun openSoundPicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            // چون آدرس ذخیره‌شده حالا آدرس کپیِ داخلی ماست (نه فایل اصلی کاربر)،
            // نمایش آن به‌عنوان «انتخاب فعلی» در پیکر سیستم دیگر معنی ندارد.
        }
        soundPickerLauncher.launch(intent)
    }
}
