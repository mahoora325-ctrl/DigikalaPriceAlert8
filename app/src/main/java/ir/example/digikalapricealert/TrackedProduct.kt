package ir.example.digikalapricealert

import org.json.JSONArray
import org.json.JSONObject

/** یک نقطه در تاریخچه‌ی قیمت یک محصول. */
data class PriceHistoryEntry(
    val priceToman: Long,
    val jalaliDate: String
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("priceToman", priceToman)
        o.put("jalaliDate", jalaliDate)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject) = PriceHistoryEntry(
            priceToman = o.getLong("priceToman"),
            jalaliDate = o.getString("jalaliDate")
        )
    }
}

/**
 * یک محصول تحت پایش.
 * productId: شناسه عددی محصول در دیجی‌کالا (بدون پیشوند dkp-)
 * thresholdToman: سقف قیمتی که کاربر تعیین کرده (به تومان)
 * lastPriceToman: آخرین قیمتی که از سرور خوانده شده
 * history: تاریخچه‌ی تغییرات قیمت (برای نمایش «از چه مبلغی به چه مبلغی»)
 * alerted: برای جلوگیری از هشدار تکراری - وقتی یک‌بار هشدار داده شد،
 *          تا زمانی که قیمت دوباره بالای سقف نرود، دوباره هشدار داده نمی‌شود.
 */
data class TrackedProduct(
    val productId: String,
    var title: String? = null,
    var imageUrl: String? = null,
    var thresholdToman: Long,
    var lastPriceToman: Long? = null,
    var lastChecked: String? = null,
    var alerted: Boolean = false,
    var history: MutableList<PriceHistoryEntry> = mutableListOf()
) {
    /** اگر قیمت نسبت به آخرین رکورد تاریخچه تغییر کرده باشد، یک رکورد جدید اضافه می‌کند. */
    fun recordPriceIfChanged(priceToman: Long, jalaliDate: String) {
        val last = history.lastOrNull()
        if (last == null || last.priceToman != priceToman) {
            history.add(PriceHistoryEntry(priceToman, jalaliDate))
            // برای جلوگیری از رشد بی‌رویه، فقط ۳۰ مورد آخر نگه داشته می‌شود
            while (history.size > 30) history.removeAt(0)
        }
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("productId", productId)
        o.put("title", title ?: JSONObject.NULL)
        o.put("imageUrl", imageUrl ?: JSONObject.NULL)
        o.put("thresholdToman", thresholdToman)
        o.put("lastPriceToman", lastPriceToman ?: JSONObject.NULL)
        o.put("lastChecked", lastChecked ?: JSONObject.NULL)
        o.put("alerted", alerted)
        val historyArr = JSONArray()
        history.forEach { historyArr.put(it.toJson()) }
        o.put("history", historyArr)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): TrackedProduct {
            val historyList = mutableListOf<PriceHistoryEntry>()
            val historyArr = o.optJSONArray("history")
            if (historyArr != null) {
                for (i in 0 until historyArr.length()) {
                    historyList.add(PriceHistoryEntry.fromJson(historyArr.getJSONObject(i)))
                }
            }
            return TrackedProduct(
                productId = o.getString("productId"),
                title = if (o.isNull("title")) null else o.getString("title"),
                imageUrl = if (o.has("imageUrl") && !o.isNull("imageUrl")) o.getString("imageUrl") else null,
                thresholdToman = o.getLong("thresholdToman"),
                lastPriceToman = if (o.isNull("lastPriceToman")) null else o.getLong("lastPriceToman"),
                lastChecked = if (o.isNull("lastChecked")) null else o.getString("lastChecked"),
                alerted = o.optBoolean("alerted", false),
                history = historyList
            )
        }
    }
}
