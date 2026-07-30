package ir.example.digikalapricealert

import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ir.example.digikalapricealert.databinding.ActivitySearchBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * صفحه‌ی جستجوی محصول در دیجی‌کالا. کاربر عبارتی را جستجو می‌کند، از میان نتایج
 * محصول موردنظر را انتخاب و با تعیین سقف قیمت، به فهرست پایش اضافه می‌کند.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var store: ProductStore
    private lateinit var settingsStore: SettingsStore
    private lateinit var adapter: SearchResultAdapter
    private var lastResults: List<SearchResultItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = ProductStore(this)
        settingsStore = SettingsStore(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        ThemeManager.applyToActivity(this, settingsStore)

        adapter = SearchResultAdapter(onAddClicked = { result -> showAddThresholdDialog(result) })
        binding.recyclerSearchResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerSearchResults.adapter = adapter

        binding.btnDoSearch.setOnClickListener { performSearch() }
        binding.editSearchQuery.setOnEditorActionListener { _, actionId, event ->
            val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            if (isSearchAction) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performSearch() {
        val query = binding.editSearchQuery.text?.toString().orEmpty().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "عبارتی برای جستجو وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }

        binding.txtSearchStatus.text = "در حال جستجو..."
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) { DigikalaApi.searchProducts(query) }
            lastResults = results
            val trackedIds = store.getAll().map { it.productId }.toSet()
            adapter.submitList(results, trackedIds)
            binding.txtSearchStatus.text = if (results.isEmpty())
                "نتیجه‌ای پیدا نشد."
            else
                "${results.size} نتیجه پیدا شد."
        }
    }

    /** دیالوگ تعیین سقف قیمت، درست قبل از افزودن محصول انتخاب‌شده به پایش. */
    private fun showAddThresholdDialog(result: SearchResultItem) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        if (result.priceToman != null) {
            input.setText(result.priceToman.toString())
            input.setSelection(input.text.length)
        }

        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = padding
        params.rightMargin = padding
        input.layoutParams = params
        container.addView(input)

        val title = result.title ?: "محصول ${result.productId}"
        AlertDialog.Builder(this)
            .setTitle("سقف قیمت برای: $title")
            .setMessage("وقتی قیمت به این مقدار یا کمتر برسد، هشدار دریافت می‌کنید.")
            .setView(container)
            .setPositiveButton("افزودن به پایش") { _, _ ->
                val threshold = input.text?.toString()?.toLongOrNull()
                if (threshold == null || threshold <= 0) {
                    Toast.makeText(this, "سقف قیمت را به‌صورت عدد معتبر وارد کنید", Toast.LENGTH_SHORT).show()
                } else {
                    addResultToTracking(result, threshold)
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun addResultToTracking(result: SearchResultItem, threshold: Long) {
        if (store.getAll().any { it.productId == result.productId }) {
            Toast.makeText(this, "این محصول قبلاً اضافه شده است", Toast.LENGTH_SHORT).show()
            return
        }

        val product = TrackedProduct(
            productId = result.productId,
            title = result.title,
            imageUrl = result.imageUrl,
            thresholdToman = threshold
        )

        val timestamp = JalaliDateUtil.nowFormatted()
        if (result.priceToman != null) {
            product.recordPriceIfChanged(result.priceToman, timestamp)
            product.lastPriceToman = result.priceToman
            product.lastChecked = timestamp
            if (result.priceToman <= threshold) {
                NotificationHelper.notifyPriceDrop(this, product)
                product.alerted = true
            }
        }

        store.add(product)
        Toast.makeText(this, "محصول به فهرست پایش اضافه شد", Toast.LENGTH_SHORT).show()

        // وضعیت دکمه‌ها را به‌روزرسانی کن تا این آیتم به‌عنوان «قبلاً اضافه شده» نمایش داده شود
        val trackedIds = store.getAll().map { it.productId }.toSet()
        adapter.submitList(lastResults, trackedIds)
    }
}
