package ir.example.digikalapricealert

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ir.example.digikalapricealert.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: ProductStore
    private lateinit var settingsStore: SettingsStore
    private lateinit var adapter: ProductAdapter

    private val notificationPermissionRequestCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        store = ProductStore(this)
        settingsStore = SettingsStore(this)
        NotificationHelper.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        requestIgnoreBatteryOptimizationIfNeeded()

        adapter = ProductAdapter(
            onRemove = { product ->
                store.remove(product.productId)
                refreshList()
            },
            onItemClick = { product -> showPriceHistoryDialog(product) },
            onEditThreshold = { product -> showEditThresholdDialog(product) }
        )
        binding.recyclerProducts.layoutManager = LinearLayoutManager(this)
        binding.recyclerProducts.adapter = adapter

        binding.btnAdd.setOnClickListener { onAddClicked() }
        binding.btnSearchProduct.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        binding.btnCheckNow.setOnClickListener { onCheckNowClicked() }
        setupSortSpinner()

        refreshList()
        schedulePeriodicCheck()
        ThemeManager.applyToActivity(this, settingsStore)
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.applyToActivity(this, settingsStore)
        // اگر کاربر از صفحه‌ی جستجو محصولی اضافه کرده باشد، لیست باید به‌روز شود
        refreshList()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val sortLabels = arrayOf(
        "پیش‌فرض (ترتیب افزودن)",
        "قیمت فعلی: کم به زیاد",
        "قیمت فعلی: زیاد به کم",
        "آخرین تغییر قیمت: جدیدترین اول",
        "آخرین تغییر قیمت: قدیمی‌ترین اول"
    )
    private val sortModes = arrayOf(
        SettingsStore.SORT_DEFAULT,
        SettingsStore.SORT_PRICE_ASC,
        SettingsStore.SORT_PRICE_DESC,
        SettingsStore.SORT_DATE_NEWEST,
        SettingsStore.SORT_DATE_OLDEST
    )

    /** اسپینر مرتب‌سازی: با لمس، گزینه‌ها باز می‌شوند و با انتخاب هرکدام بلافاصله چیدمان لیست عوض می‌شود. */
    private fun setupSortSpinner() {
        val spinner = binding.spinnerSort
        val spinnerAdapter = android.widget.ArrayAdapter(
            this,
            R.layout.spinner_item,
            sortLabels
        )
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinner.adapter = spinnerAdapter

        val currentIndex = sortModes.indexOf(settingsStore.getSortMode()).coerceAtLeast(0)
        spinner.setSelection(currentIndex, false)

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                settingsStore.setSortMode(sortModes[position])
                refreshList()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun onAddClicked() {
        val input = binding.editProductInput.text?.toString().orEmpty()
        val thresholdText = binding.editThreshold.text?.toString().orEmpty()

        val productId = DigikalaApi.extractProductId(input)
        val threshold = thresholdText.toLongOrNull()

        if (productId == null) {
            Toast.makeText(this, "لینک یا کد محصول معتبر نیست", Toast.LENGTH_SHORT).show()
            return
        }
        if (threshold == null || threshold <= 0) {
            Toast.makeText(this, "سقف قیمت را به‌صورت عدد (تومان) وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        if (store.getAll().any { it.productId == productId }) {
            Toast.makeText(this, "این محصول قبلاً اضافه شده است", Toast.LENGTH_SHORT).show()
            return
        }

        val product = TrackedProduct(productId = productId, thresholdToman = threshold)
        store.add(product)
        binding.editProductInput.text?.clear()
        binding.editThreshold.text?.clear()
        refreshList()

        binding.txtStatus.text = "در حال دریافت اطلاعات اولیه محصول..."
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { DigikalaApi.fetchProductInfo(productId) }
            if (info != null) {
                val timestamp = JalaliDateUtil.nowFormatted()
                product.title = info.title
                product.imageUrl = info.imageUrl
                if (info.priceToman != null) {
                    product.recordPriceIfChanged(info.priceToman, timestamp)
                }
                product.lastPriceToman = info.priceToman
                product.lastChecked = timestamp
                if (info.priceToman != null && info.priceToman <= threshold) {
                    NotificationHelper.notifyPriceDrop(this@MainActivity, product)
                    product.alerted = true
                }
                store.update(product)
                refreshList()
                binding.txtStatus.text = "محصول اضافه شد و پایش دوره‌ای (هر ۱۵ دقیقه) فعال است."
            } else {
                binding.txtStatus.text = "محصول اضافه شد، اما دریافت قیمت اولیه ناموفق بود. پایش دوره‌ای همچنان تلاش می‌کند."
            }
        }
    }

    private fun onCheckNowClicked() {
        binding.txtStatus.text = "در حال بررسی فوری همه محصولات..."
        val request = OneTimeWorkRequestBuilder<PriceCheckWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            PriceCheckWorker.UNIQUE_ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        // پس از چند ثانیه لیست را رفرش می‌کنیم تا نتیجه دیده شود
        binding.recyclerProducts.postDelayed({
            refreshList()
            val (successCount, failCount) = settingsStore.getLastCheckResult()
            if (failCount > 0) {
                binding.txtStatus.text =
                    "بررسی انجام شد: ${PersianNumberUtils.forDisplay(successCount.toString())} محصول موفق، " +
                    "${PersianNumberUtils.forDisplay(failCount.toString())} محصول ناموفق " +
                    "(احتمالاً دیجی‌کالا درخواست را مسدود کرده یا ساختار پاسخش تغییر کرده - در Logcat با تگ DigikalaApi جزئیات هست)"
            } else if (successCount >= 0) {
                binding.txtStatus.text = "بررسی همه‌ی ${PersianNumberUtils.forDisplay(successCount.toString())} محصول با موفقیت انجام شد."
            }
        }, 4000)
    }

    private fun schedulePeriodicCheck() {
        // حداقل بازه‌ی مجاز WorkManager برای کار دوره‌ای ۱۵ دقیقه است
        val request = PeriodicWorkRequestBuilder<PriceCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PriceCheckWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun refreshList() {
        val all = store.getAll()
        val sorted = when (settingsStore.getSortMode()) {
            SettingsStore.SORT_PRICE_ASC -> all.sortedBy { it.lastPriceToman ?: Long.MAX_VALUE }
            SettingsStore.SORT_PRICE_DESC -> all.sortedByDescending { it.lastPriceToman ?: Long.MIN_VALUE }
            // فرمت تاریخ شمسی ذخیره‌شده صفر-پد است (yyyy/MM/dd HH:mm)، پس مقایسه‌ی
            // رشته‌ای همان ترتیب زمانی درست را می‌دهد
            SettingsStore.SORT_DATE_NEWEST -> all.sortedByDescending { it.lastChecked ?: "" }
            SettingsStore.SORT_DATE_OLDEST -> all.sortedBy { it.lastChecked ?: "" }
            else -> all
        }
        adapter.submitList(sorted)
    }

    /** نمایش لیست «از چه مبلغی به چه مبلغی» تغییر کرده، به همراه تاریخ شمسی هر تغییر. */
    private fun showPriceHistoryDialog(product: TrackedProduct) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_price_history, null)
        val title = product.title ?: "محصول ${product.productId}"
        view.findViewById<TextView>(R.id.txtHistoryTitle).text = "تاریخچه‌ی قیمت: $title"

        val container = view.findViewById<android.widget.LinearLayout>(R.id.historyContainer)
        container.removeAllViews()

        if (product.history.size < 2) {
            val empty = TextView(this)
            empty.text = "هنوز تغییری در قیمت این محصول ثبت نشده است."
            empty.setTextColor(getColor(R.color.text_secondary))
            container.addView(empty)
        } else {
            // جدیدترین تغییر بالای لیست نمایش داده شود
            for (i in product.history.size - 1 downTo 1) {
                val from = product.history[i - 1]
                val to = product.history[i]
                val row = LayoutInflater.from(this).inflate(R.layout.item_price_history_row, container, false)
                row.findViewById<TextView>(R.id.txtFromPrice).text =
                    "${PersianNumberUtils.formatToman(from.priceToman)} تومان"
                row.findViewById<TextView>(R.id.txtToPrice).text =
                    "${PersianNumberUtils.formatToman(to.priceToman)} تومان"
                row.findViewById<TextView>(R.id.txtChangeDate).text = PersianNumberUtils.toPersianDigits(to.jalaliDate)
                container.addView(row)
            }
        }

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("بستن", null)
            .show()
    }

    /** یک دیالوگ ساده برای تغییر سقف قیمت یک محصول از قبل اضافه‌شده. */
    private fun showEditThresholdDialog(product: TrackedProduct) {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.setText(product.thresholdToman.toString())
        input.setSelection(input.text.length)

        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = padding
        params.rightMargin = padding
        input.layoutParams = params
        container.addView(input)

        val title = product.title ?: "محصول ${product.productId}"
        AlertDialog.Builder(this)
            .setTitle("ویرایش سقف قیمت: $title")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val newThreshold = input.text?.toString()?.toLongOrNull()
                if (newThreshold == null || newThreshold <= 0) {
                    Toast.makeText(this, "سقف قیمت را به‌صورت عدد معتبر وارد کنید", Toast.LENGTH_SHORT).show()
                } else {
                    val wasAlerted = product.alerted
                    product.thresholdToman = newThreshold
                    val nowQualifies = product.lastPriceToman != null && product.lastPriceToman!! <= newThreshold
                    if (nowQualifies && !wasAlerted) {
                        // با سقف جدید، قیمت فعلی از قبل واجد شرایط هشدار است؛
                        // باید همین الان هم به کاربر اطلاع داده شود، نه فقط علامت زده شود.
                        NotificationHelper.notifyPriceDrop(this, product)
                    }
                    product.alerted = nowQualifies
                    store.update(product)
                    refreshList()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    notificationPermissionRequestCode
                )
            }
        }
    }

    /**
     * فقط یک‌بار (اولین اجرا) از کاربر می‌خواهد بهینه‌سازی باتری را برای این اپ
     * غیرفعال کند تا اندروید پایش دوره‌ای در پس‌زمینه را نبندد.
     */
    private fun requestIgnoreBatteryOptimizationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (settingsStore.hasAskedBatteryOptimization()) return

        settingsStore.setAskedBatteryOptimization()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // برخی گوشی‌ها (مثلاً با رابط کاربری سفارشی) ممکن است این Intent را نداشته باشند؛
            // در این صورت کاری نمی‌کنیم، کاربر می‌تواند دستی از تنظیمات گوشی این کار را انجام دهد.
        }
    }
}
