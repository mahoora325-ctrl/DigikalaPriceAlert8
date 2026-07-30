package ir.example.digikalapricealert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    const val SILENT_MARKER = "silent"
    private const val CHANNEL_BASE_ID = "price_alerts"

    /**
     * شناسه‌ی کانال فعلی، شامل شماره‌ی نسخه. هر بار صدای هشدار عوض شود، این
     * شماره بالا می‌رود و یک کانال کاملاً تازه (با شناسه‌ی متفاوت) ساخته
     * می‌شود - چون در خیلی از گوشی‌ها (خصوصاً با رابط کاربری سفارشی مثل
     * MIUI/EMUI/...)، تغییر صدای یک کانالِ از قبل موجود (حتی با حذف و ساخت
     * دوباره با همان شناسه)، واقعاً اعمال نمی‌شود.
     */
    private fun currentChannelId(context: Context): String {
        return "${CHANNEL_BASE_ID}_v${SettingsStore(context).getChannelVersion()}"
    }

    /** کانال نوتیفیکیشن فعلی را در صورت نبودن می‌سازد، با صدای فعلی انتخاب‌شده‌ی کاربر. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = currentChannelId(context)
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "هشدار افت قیمت",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "اطلاع‌رسانی وقتی قیمت یک محصول دیجی‌کالا به زیر سقف تعیین‌شده می‌رسد"
                    enableVibration(true)
                    applySound(context, this)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun applySound(context: Context, channel: NotificationChannel) {
        val soundPref = SettingsStore(context).getAlertSoundUri()
        when {
            soundPref == SILENT_MARKER -> channel.setSound(null, null)
            soundPref != null -> {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                channel.setSound(Uri.parse(soundPref), attrs)
            }
            // اگر چیزی انتخاب نشده باشد، صدای پیش‌فرض همان صدای پیش‌فرض اندروید برای کانال است
        }
    }

    /**
     * وقتی کاربر صدای هشدار را از تنظیمات عوض می‌کند باید این تابع صدا زده شود.
     * به‌جای تلاش برای تغییر کانال قبلی، یک کانال کاملاً جدید (با شناسه‌ی
     * متفاوت) ساخته می‌شود تا صدای جدید مطمئناً اعمال شود؛ کانال قبلی هم پاک
     * می‌شود تا در تنظیمات نوتیفیکیشن گوشی تکراری/قدیمی باقی نماند.
     */
    fun recreateChannelWithNewSound(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val oldChannelId = currentChannelId(context)
            SettingsStore(context).incrementChannelVersion()
            manager.deleteNotificationChannel(oldChannelId)
        } else {
            SettingsStore(context).incrementChannelVersion()
        }
        ensureChannel(context)
    }

    fun notifyPriceDrop(context: Context, product: TrackedProduct) {
        ensureChannel(context)

        val title = product.title ?: "محصول ${product.productId}"
        val price = product.lastPriceToman ?: 0L
        val text = "قیمت به ${formatToman(price)} تومان رسید (سقف شما: ${formatToman(product.thresholdToman)} تومان)"

        val notification = NotificationCompat.Builder(context, currentChannelId(context))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("افت قیمت: $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationId = product.productId.hashCode()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun formatToman(value: Long): String {
        return PersianNumberUtils.formatToman(value)
    }
}
