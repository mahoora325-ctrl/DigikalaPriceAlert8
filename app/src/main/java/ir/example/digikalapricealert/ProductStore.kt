package ir.example.digikalapricealert

import android.content.Context
import org.json.JSONArray

/**
 * ذخیره و بازیابی ساده‌ی فهرست محصولاتِ تحت پایش با SharedPreferences (به‌صورت JSON).
 * برای این کاربرد (چند تا محصول، بدون نیاز به کوئری پیچیده) کافی است؛
 * اگر تعداد محصولات زیاد شد بهتر است به Room مهاجرت شود.
 */
class ProductStore(context: Context) {

    private val prefs = context.getSharedPreferences("digikala_price_alert", Context.MODE_PRIVATE)
    private val KEY = "tracked_products"

    @Synchronized
    fun getAll(): MutableList<TrackedProduct> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<TrackedProduct>()
        for (i in 0 until arr.length()) {
            list.add(TrackedProduct.fromJson(arr.getJSONObject(i)))
        }
        return list
    }

    @Synchronized
    fun saveAll(products: List<TrackedProduct>) {
        val arr = JSONArray()
        products.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun add(product: TrackedProduct) {
        val list = getAll()
        list.add(product)
        saveAll(list)
    }

    @Synchronized
    fun remove(productId: String) {
        val list = getAll()
        list.removeAll { it.productId == productId }
        saveAll(list)
    }

    @Synchronized
    fun update(product: TrackedProduct) {
        val list = getAll()
        val idx = list.indexOfFirst { it.productId == product.productId }
        if (idx >= 0) {
            list[idx] = product
            saveAll(list)
        }
    }
}
