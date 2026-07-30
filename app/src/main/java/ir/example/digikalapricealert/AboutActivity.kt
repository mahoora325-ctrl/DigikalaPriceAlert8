package ir.example.digikalapricealert

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val store = SettingsStore(this)
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        ThemeManager.applyToActivity(this, store)

        findViewById<android.widget.TextView>(R.id.txtAppName).text = getString(R.string.app_name)
        findViewById<android.widget.TextView>(R.id.txtVersion).text =
            PersianNumberUtils.forDisplay("نسخه ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        findViewById<android.widget.TextView>(R.id.txtDeveloper).text = "مسعود عبدالحی"
    }
}
