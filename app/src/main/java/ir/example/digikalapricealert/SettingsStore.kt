package ir.example.digikalapricealert

import android.content.Context

/**
 * ذخیره‌ی تنظیمات ظاهری و صدای هشدار کاربر.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("digikala_price_alert_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_ALERT_SOUND_URI = "alert_sound_uri"
        private const val KEY_ASKED_BATTERY_OPTIMIZATION = "asked_battery_optimization"
        private const val KEY_LAST_CHECK_SUCCESS_COUNT = "last_check_success_count"
        private const val KEY_LAST_CHECK_FAIL_COUNT = "last_check_fail_count"
        private const val KEY_CHANNEL_VERSION = "notification_channel_version"
        private const val KEY_SORT_MODE = "sort_mode"
        const val DEFAULT_THEME_COLOR = "#EF394E"

        // حالت‌های مرتب‌سازی فهرست پایش
        const val SORT_DEFAULT = "default"
        const val SORT_PRICE_ASC = "price_asc"
        const val SORT_PRICE_DESC = "price_desc"
        const val SORT_DATE_NEWEST = "date_newest"
        const val SORT_DATE_OLDEST = "date_oldest"
    }

    fun getThemeColor(): String = prefs.getString(KEY_THEME_COLOR, DEFAULT_THEME_COLOR) ?: DEFAULT_THEME_COLOR

    fun setThemeColor(hex: String) {
        prefs.edit().putString(KEY_THEME_COLOR, hex).apply()
    }

    /** null یعنی از صدای پیش‌فرض سیستم استفاده شود. رشته‌ی "silent" یعنی بی‌صدا. */
    fun getAlertSoundUri(): String? = prefs.getString(KEY_ALERT_SOUND_URI, null)

    fun setAlertSoundUri(uri: String?) {
        prefs.edit().putString(KEY_ALERT_SOUND_URI, uri).apply()
    }

    /** آیا قبلاً یک‌بار از کاربر خواسته‌ایم بهینه‌سازی باتری را برای این اپ غیرفعال کند. */
    fun hasAskedBatteryOptimization(): Boolean = prefs.getBoolean(KEY_ASKED_BATTERY_OPTIMIZATION, false)

    fun setAskedBatteryOptimization() {
        prefs.edit().putBoolean(KEY_ASKED_BATTERY_OPTIMIZATION, true).apply()
    }

    /**
     * نتیجه‌ی آخرین بررسی قیمت‌ها را ذخیره می‌کند: چند محصول با موفقیت دریافت شد
     * و چند محصول ناموفق بود (مثلاً به دلیل تغییر ساختار API دیجی‌کالا یا مسدودشدن درخواست).
     * این عدد برای تشخیص مشکل «هشدار نمی‌آید» به کاربر نمایش داده می‌شود.
     */
    fun setLastCheckResult(successCount: Int, failCount: Int) {
        prefs.edit()
            .putInt(KEY_LAST_CHECK_SUCCESS_COUNT, successCount)
            .putInt(KEY_LAST_CHECK_FAIL_COUNT, failCount)
            .apply()
    }

    fun getLastCheckResult(): Pair<Int, Int> {
        val success = prefs.getInt(KEY_LAST_CHECK_SUCCESS_COUNT, -1)
        val fail = prefs.getInt(KEY_LAST_CHECK_FAIL_COUNT, -1)
        return success to fail
    }

    /**
     * شماره‌ی نسخه‌ی فعلی کانال نوتیفیکیشن. هر بار صدای هشدار عوض می‌شود، این
     * عدد بالا می‌رود تا یک شناسه‌ی کانال کاملاً تازه ساخته شود - چون در برخی
     * گوشی‌ها (به‌خصوص با رابط کاربری سفارشی)، حذف و ساخت دوباره‌ی کانال با
     * همان شناسه‌ی قبلی، صدای جدید را واقعاً اعمال نمی‌کند.
     */
    fun getChannelVersion(): Int = prefs.getInt(KEY_CHANNEL_VERSION, 0)

    fun incrementChannelVersion(): Int {
        val next = getChannelVersion() + 1
        prefs.edit().putInt(KEY_CHANNEL_VERSION, next).apply()
        return next
    }

    /** ترتیب نمایش فهرست پایش (یکی از مقادیر SORT_*). */
    fun getSortMode(): String = prefs.getString(KEY_SORT_MODE, SORT_DEFAULT) ?: SORT_DEFAULT

    fun setSortMode(mode: String) {
        prefs.edit().putString(KEY_SORT_MODE, mode).apply()
    }
}
