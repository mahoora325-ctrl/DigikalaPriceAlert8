package ir.example.digikalapricealert

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PriceCheckWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = ProductStore(applicationContext)
        val settingsStore = SettingsStore(applicationContext)
        val products = store.getAll()
        if (products.isEmpty()) return Result.success()

        var successCount = 0
        var failCount = 0

        withContext(Dispatchers.IO) {
            for (product in products) {
                val info = DigikalaApi.fetchProductInfo(product.productId)
                if (info == null) {
                    failCount++
                    continue
                }
                successCount++
                if (info.title != null) product.title = info.title
                if (info.imageUrl != null) product.imageUrl = info.imageUrl
                if (info.priceToman != null) {
                    val timestamp = JalaliDateUtil.nowFormatted()
                    product.recordPriceIfChanged(info.priceToman, timestamp)
                    product.lastPriceToman = info.priceToman
                    product.lastChecked = timestamp

                    val underThreshold = info.priceToman <= product.thresholdToman
                    if (underThreshold) {
                        // همیشه هشدار بده، حتی اگر قبلاً هم زیر سقف بوده و هشدار داده شده
                        NotificationHelper.notifyPriceDrop(applicationContext, product)
                        product.alerted = true
                    } else {
                        // قیمت دوباره بالای سقف رفت
                        product.alerted = false
                    }
                }
                store.update(product)
            }
        }

        settingsStore.setLastCheckResult(successCount, failCount)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "digikala_price_check"
        const val UNIQUE_ONE_TIME_WORK_NAME = "digikala_price_check_now"
    }
}
