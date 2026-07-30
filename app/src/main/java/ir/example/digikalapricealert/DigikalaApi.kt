package ir.example.digikalapricealert

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import android.util.Log

data class ProductInfo(val title: String?, val priceToman: Long?, val imageUrl: String?)

/** یک نتیجه‌ی خام جستجو، قبل از افزوده شدن به فهرست پایش. */
data class SearchResultItem(
    val productId: String,
    val title: String?,
    val priceToman: Long?,
    val imageUrl: String?
)

/**
 * این کلاینت از API غیررسمی و مستندنشده‌ی دیجی‌کالا استفاده می‌کند
 * (https://api.digikala.com/v2/product/{id}/).
 * چون این API رسمی نیست، دیجی‌کالا می‌تواند ساختار پاسخ را بدون اطلاع قبلی تغییر دهد.
 * به همین دلیل پارس کردن JSON با چند مسیر احتمالی و به‌صورت defensive انجام شده؛
 * اگر در آینده کار نکرد، خروجی خام را در Logcat با تگ DigikalaApi ببینید و
 * مسیر فیلدهای جدید را در تابع parseProductJson اصلاح کنید.
 */
object DigikalaApi {

    private const val TAG = "DigikalaApi"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** از روی لینک کامل محصول یا فقط عدد، شناسه‌ی عددی محصول را استخراج می‌کند. */
    fun extractProductId(input: String): String? {
        val trimmed = input.trim()
        // حالت ۱: کاربر فقط عدد را وارد کرده
        if (trimmed.matches(Regex("^\\d+$"))) return trimmed
        // حالت ۲: لینک کامل شامل dkp-123456
        val match = Regex("dkp-(\\d+)").find(trimmed)
        if (match != null) return match.groupValues[1]
        // حالت ۳: هر رشته‌ی دیگری که یک عدد بلند داخلش باشد
        val anyNumber = Regex("(\\d{4,})").find(trimmed)
        return anyNumber?.groupValues?.get(1)
    }

    /** درخواست همزمان (synchronous) - باید از یک ترد پس‌زمینه/Coroutine IO صدا زده شود. */
    fun fetchProductInfo(productId: String): ProductInfo? {
        val url = "https://api.digikala.com/v2/product/$productId/"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android)")
            .header("Referer", "https://www.digikala.com/")
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} برای محصول $productId")
                    return null
                }
                val body = response.body?.string() ?: return null
                return parseProductJson(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطا در دریافت اطلاعات محصول $productId", e)
            return null
        }
    }

    /**
     * جستجوی محصول بر اساس نام/عبارت، با استفاده از API غیررسمی جستجوی دیجی‌کالا.
     * درخواست همزمان (synchronous) است - باید از یک ترد پس‌زمینه/Coroutine IO صدا زده شود.
     * در صورت خطا یا نبود نتیجه، فهرست خالی برمی‌گرداند (نه null) تا UI ساده‌تر بماند.
     */
    fun searchProducts(query: String): List<SearchResultItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val url = "https://api.digikala.com/v1/search/?q=$encoded"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android)")
            .header("Referer", "https://www.digikala.com/")
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} برای جستجوی «$trimmed»")
                    return emptyList()
                }
                val body = response.body?.string() ?: return emptyList()
                return parseSearchJson(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطا در جستجوی «$trimmed»", e)
            return emptyList()
        }
    }

    private fun parseSearchJson(raw: String): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()
        try {
            val root = JSONObject(raw)
            val data = root.optJSONObject("data") ?: return results
            // مسیر معمول: data.products؛ بعضی نسخه‌های API از data.items استفاده می‌کنند
            val products = data.optJSONArray("products") ?: data.optJSONArray("items") ?: return results

            for (i in 0 until products.length()) {
                val product = products.optJSONObject(i) ?: continue
                val id = product.optLong("id", -1L)
                if (id <= 0) continue

                val title = when {
                    product.has("title_fa") && !product.isNull("title_fa") -> product.getString("title_fa")
                    product.has("title_en") && !product.isNull("title_en") -> product.getString("title_en")
                    else -> null
                }

                val variant = product.optJSONObject("default_variant")
                val priceObj = variant?.optJSONObject("price")
                var priceRial: Long? = null
                if (priceObj != null) {
                    priceRial = when {
                        priceObj.has("selling_price") -> priceObj.optLong("selling_price")
                        priceObj.has("rrp_price") -> priceObj.optLong("rrp_price")
                        else -> null
                    }
                }
                if (priceRial == null) {
                    priceRial = product.optJSONObject("price")?.optLong("selling_price")
                }
                val priceToman = priceRial?.let { it / 10 }

                var imageUrl: String? = null
                val images = product.optJSONObject("images")
                val mainImage = images?.optJSONObject("main")
                val urlArray = mainImage?.optJSONArray("url")
                if (urlArray != null && urlArray.length() > 0) {
                    imageUrl = urlArray.optString(0, null)
                }
                if (imageUrl == null) {
                    imageUrl = mainImage?.optString("url", null)
                }

                results.add(
                    SearchResultItem(
                        productId = id.toString(),
                        title = title,
                        priceToman = priceToman,
                        imageUrl = imageUrl
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطا در پارس JSON جستجو: $raw", e)
        }
        return results
    }

    private fun parseProductJson(raw: String): ProductInfo? {
        return try {
            val root = JSONObject(raw)
            val product = root.optJSONObject("data")?.optJSONObject("product") ?: return null

            val title = when {
                product.has("title_fa") && !product.isNull("title_fa") -> product.getString("title_fa")
                product.has("title_en") && !product.isNull("title_en") -> product.getString("title_en")
                else -> null
            }

            // قیمت معمولاً داخل default_variant.price قرار دارد
            val variant = product.optJSONObject("default_variant")
            val priceObj = variant?.optJSONObject("price")

            var priceRial: Long? = null
            if (priceObj != null) {
                priceRial = when {
                    priceObj.has("selling_price") -> priceObj.optLong("selling_price")
                    priceObj.has("rrp_price") -> priceObj.optLong("rrp_price")
                    else -> null
                }
            }

            // اگر مسیر بالا جواب نداد، تلاش برای مسیرهای جایگزین احتمالی
            if (priceRial == null) {
                priceRial = product.optJSONObject("price")?.optLong("selling_price")
            }

            val priceToman = priceRial?.let { it / 10 }

            // آدرس تصویر اصلی محصول؛ در API معمولاً data.product.images.main.url یک آرایه است
            var imageUrl: String? = null
            val images = product.optJSONObject("images")
            val mainImage = images?.optJSONObject("main")
            val urlArray = mainImage?.optJSONArray("url")
            if (urlArray != null && urlArray.length() > 0) {
                imageUrl = urlArray.optString(0, null)
            }
            if (imageUrl == null) {
                imageUrl = mainImage?.optString("url", null)
            }

            ProductInfo(title = title, priceToman = priceToman, imageUrl = imageUrl)
        } catch (e: Exception) {
            Log.e(TAG, "خطا در پارس JSON: $raw", e)
            null
        }
    }
}
