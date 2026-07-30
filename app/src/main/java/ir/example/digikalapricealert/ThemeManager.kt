package ir.example.digikalapricealert

import android.app.Activity
import android.graphics.Color
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

object ThemeManager {

    /** پالت رنگ‌های قابل انتخاب کاربر برای رنگ اصلی برنامه. */
    val palette: List<Pair<String, String>> = listOf(
        "قرمز دیجی‌کالا" to "#EF394E",
        "آبی" to "#1E88E5",
        "سبز" to "#0DAB76",
        "بنفش" to "#8E24AA",
        "نارنجی" to "#F4511E",
        "فیروزه‌ای" to "#00897B"
    )

    /** رنگ اصلی فعلی را روی نوار بالای صفحه و دکمه‌های اصلی این اکتیویتی اعمال می‌کند (در صورت وجود). */
    fun applyToActivity(activity: Activity, store: SettingsStore) {
        val color = try {
            Color.parseColor(store.getThemeColor())
        } catch (e: Exception) {
            Color.parseColor(SettingsStore.DEFAULT_THEME_COLOR)
        }

        activity.findViewById<MaterialToolbar>(R.id.toolbar)?.setBackgroundColor(color)

        activity.findViewById<MaterialButton>(R.id.btnAdd)?.backgroundTintList =
            android.content.res.ColorStateList.valueOf(color)

        if (Build_VERSION_SUPPORTS_STATUS_BAR()) {
            activity.window?.statusBarColor = color
        }
    }

    private fun Build_VERSION_SUPPORTS_STATUS_BAR(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP
    }
}
