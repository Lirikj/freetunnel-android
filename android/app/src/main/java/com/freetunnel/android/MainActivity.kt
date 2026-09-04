package com.freetunnel.android

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService as AndroidVpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.adguard.trusttunnel.AppNotifier
import com.adguard.trusttunnel.DeepLink
import com.adguard.trusttunnel.VpnService
import java.io.File

class MainActivity : AppCompatActivity(), AppNotifier {
    private lateinit var store: ConfigStore
    private lateinit var body: FrameLayout
    private lateinit var status: TextView
    private var state = 0
    private var pendingConfig: String? = null
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (AndroidVpnService.prepare(this) == null) pendingConfig?.let { VpnService.start(this, it) }
        pendingConfig = null
    }
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importUri) }
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConfigStore(this)
        VpnService.setAppNotifier(File(filesDir, "connection_info.dat"), this)
        if (Build.VERSION.SDK_INT >= 33) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        buildShell()
        handleIntent(intent)
        showHome()
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.dataString ?: return
        if (uri.startsWith("tt://")) runCatching { DeepLink.decode(uri) }
            .onSuccess { addConfig("Imported server", TomlConfig.fromDeepLinkEndpoint(it)) }
            .onFailure { toast("Не удалось прочитать tt:// ссылку") }
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val title = TextView(this).apply {
            text = "FreeTunnel"; textSize = 20f; setTextColor(fg); gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8)); typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        body = FrameLayout(this)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(6), dp(8), dp(8)) }
        listOf("Главная" to ::showHome, "Конфиги" to ::showConfigs, "Split" to ::showSplit, "Настройки" to ::showSettings, "Логи" to ::showLogs).forEach { (label, action) ->
            nav.addView(Button(this).apply { text = label; textSize = 11f; setOnClickListener { action() } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(56)))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(62)))
        setContentView(root)
    }

    private fun page(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(20), dp(16), dp(20), dp(16))
    }
    private fun setPage(view: View) { body.removeAllViews(); body.addView(view, FrameLayout.LayoutParams(-1, -1)) }
    private fun label(text: String, size: Float = 15f) = TextView(this).apply { this.text = text; textSize = size; setTextColor(fg); setPadding(0, dp(8), 0, dp(8)) }

    private fun showHome() {
        val page = page().apply { gravity = Gravity.CENTER }
        val logo = TextView(this).apply {
            text = "F"; textSize = 70f; gravity = Gravity.CENTER; setTextColor(if (state == 2) green else dim)
            background = ContextCompat.getDrawable(this@MainActivity, R.mipmap.ic_launcher)
            setOnClickListener { toggle() }
        }
        status = label(stateText(), 17f).apply { gravity = Gravity.CENTER }
        val configs = store.configs()
        val active = configs.firstOrNull { it.id == store.selectedId } ?: configs.firstOrNull()
        val configName = label(active?.name ?: "Добавьте конфигурацию", 16f).apply {
            gravity = Gravity.CENTER; setOnClickListener { showConfigs() }
        }
        page.addView(logo, LinearLayout.LayoutParams(dp(150), dp(150)))
        page.addView(status, LinearLayout.LayoutParams(-1, dp(54)))
        page.addView(configName, LinearLayout.LayoutParams(-1, dp(54)))
        page.addView(label("Нажмите на логотип, чтобы подключиться", 12f).apply { setTextColor(dim); gravity = Gravity.CENTER })
        setPage(page)
    }

    private fun toggle() {
        if (state != 0) { VpnService.stop(this); return }
        val configs = store.configs()
        val selected = configs.firstOrNull { it.id == store.selectedId } ?: configs.firstOrNull()
        if (selected == null) { showConfigs(); toast("Сначала добавьте конфигурацию"); return }
        store.selectedId = selected.id
        val config = TomlConfig.applySplit(selected.toml, store)
        val prepare = AndroidVpnService.prepare(this)
        if (prepare == null) VpnService.start(this, config) else { pendingConfig = config; vpnPermission.launch(prepare) }
    }

    private fun showConfigs() {
        val scroll = ScrollView(this); val page = page()
        page.addView(label("Конфигурации", 20f))
        store.configs().forEach { cfg ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(Button(this).apply {
                text = (if (cfg.id == store.selectedId) "●  " else "○  ") + cfg.name
                setOnClickListener { store.selectedId = cfg.id; showConfigs() }
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
            row.addView(Button(this).apply { text = "✎"; setOnClickListener { editConfig(cfg) } }, LinearLayout.LayoutParams(dp(52), dp(52)))
            row.addView(Button(this).apply { text = "×"; setOnClickListener { deleteConfig(cfg) } }, LinearLayout.LayoutParams(dp(52), dp(52)))
            page.addView(row, LinearLayout.LayoutParams(-1, dp(58)))
        }
        page.addView(Button(this).apply { text = "+ Вставить TOML"; setOnClickListener { editConfig(null) } }, LinearLayout.LayoutParams(-1, dp(54)))
        page.addView(Button(this).apply { text = "Импортировать файл"; setOnClickListener { openDocument.launch(arrayOf("text/*", "application/octet-stream")) } }, LinearLayout.LayoutParams(-1, dp(54)))
        scroll.addView(page); setPage(scroll)
    }

    private fun editConfig(existing: TunnelConfig?) {
        val name = EditText(this).apply { hint = "Название"; setText(existing?.name ?: "Server") }
        val config = EditText(this).apply { hint = "TOML конфигурация"; setText(existing?.toml ?: ""); minLines = 12; gravity = Gravity.TOP; setHorizontallyScrolling(true) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(18), 0); addView(name); addView(config) }
        AlertDialog.Builder(this).setTitle(if (existing == null) "Новая конфигурация" else "Редактирование")
            .setView(box).setNegativeButton("Отмена", null).setPositiveButton("Сохранить") { _, _ ->
                if (config.text.isBlank() || !config.text.contains("[endpoint]")) toast("Нужна полная TOML конфигурация")
                else {
                    val items = store.configs(); val item = TunnelConfig(existing?.id ?: System.currentTimeMillis(), name.text.toString().ifBlank { "Server" }, config.text.toString())
                    val index = items.indexOfFirst { it.id == item.id }; if (index >= 0) items[index] = item else items.add(item)
                    store.save(items); if (store.selectedId < 0) store.selectedId = item.id; showConfigs()
                }
            }.show()
    }

    private fun deleteConfig(config: TunnelConfig) = AlertDialog.Builder(this).setMessage("Удалить «${config.name}»?")
        .setNegativeButton("Отмена", null).setPositiveButton("Удалить") { _, _ ->
            val items = store.configs().filterNot { it.id == config.id }; store.save(items)
            if (store.selectedId == config.id) store.selectedId = items.firstOrNull()?.id ?: -1; showConfigs()
        }.show()

    private fun importUri(uri: Uri) = runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty() }
        .onSuccess { if (it.contains("[endpoint]")) addConfig(uri.lastPathSegment ?: "Imported", it) else toast("Файл не похож на TrustTunnel TOML") }
        .onFailure { toast("Не удалось открыть файл") }
    private fun addConfig(name: String, toml: String) { val items = store.configs(); val cfg = TunnelConfig(System.currentTimeMillis(), name, toml); items.add(cfg); store.save(items); store.selectedId = cfg.id; showConfigs() }

    private fun showSplit() {
        val scroll = ScrollView(this); val page = page()
        page.addView(label("Раздельное туннелирование", 20f))
        val enabled = Switch(this).apply { text = "Включено"; isChecked = store.splitEnabled; setOnCheckedChangeListener { _, v -> store.splitEnabled = v } }
        val mode = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            val bypass = RadioButton(this@MainActivity).apply { text = "Указанные адреса обходят VPN"; id = View.generateViewId() }
            val selective = RadioButton(this@MainActivity).apply { text = "Только указанные адреса через VPN"; id = View.generateViewId() }
            addView(bypass); addView(selective); check(if (store.splitMode == "selective") selective.id else bypass.id)
            setOnCheckedChangeListener { _, id -> store.splitMode = if (id == selective.id) "selective" else "general" }
        }
        val rules = EditText(this).apply { hint = "Домены и IP, по одному на строку"; setText(store.splitRules); minLines = 8; gravity = Gravity.TOP }
        val routes = EditText(this).apply { hint = "Исключённые подсети"; setText(store.excludedRoutes); minLines = 5; gravity = Gravity.TOP }
        page.addView(enabled, LinearLayout.LayoutParams(-1, dp(56))); page.addView(mode)
        page.addView(label("Правила доменов / IP", 14f)); page.addView(rules, LinearLayout.LayoutParams(-1, dp(180)))
        page.addView(Button(this).apply { text = "Рекомендуемые для России"; setOnClickListener { rules.setText("youtube.com\ngooglevideo.com\ninstagram.com\nfacebook.com\nx.com\ntwitter.com") } })
        page.addView(label("Подсети вне Android VPN", 14f)); page.addView(routes, LinearLayout.LayoutParams(-1, dp(140)))
        page.addView(Button(this).apply { text = "Сохранить"; setOnClickListener { store.splitRules = rules.text.toString(); store.excludedRoutes = routes.text.toString(); toast("Сохранено. Активное соединение нужно переподключить") } }, LinearLayout.LayoutParams(-1, dp(54)))
        scroll.addView(page); setPage(scroll)
    }

    private fun showSettings() {
        val page = page(); page.addView(label("Настройки", 20f))
        page.addView(Button(this).apply { text = "Системные настройки VPN"; setOnClickListener { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } }, LinearLayout.LayoutParams(-1, dp(56)))
        page.addView(label("FreeTunnel Android 1.0.0\nTrustTunnel core 1.1.5-rc.6\nКонфигурации хранятся только на устройстве.", 14f))
        setPage(page)
    }

    private fun showLogs() {
        val scroll = ScrollView(this); val page = page(); page.addView(label("Журнал соединений", 20f))
        val file = File(filesDir, "connection_info.dat")
        page.addView(label(if (file.exists()) "Журнал ядра доступен для экспорта (${file.length()} байт)." else "Записей пока нет.", 14f))
        page.addView(Button(this).apply { text = "Очистить логи"; setOnClickListener { VpnService.clearLogs(); file.delete(); showLogs() } }, LinearLayout.LayoutParams(-1, dp(54)))
        scroll.addView(page); setPage(scroll)
    }

    override fun onStateChanged(state: Int) = runOnUiThread { this.state = state; if (::status.isInitialized) status.text = stateText() }
    override fun onConnectionInfo(info: String) = Unit
    private fun stateText() = when (state) { 1 -> "Подключение…"; 2 -> "Подключено"; 3, 4 -> "Восстановление…"; 5 -> "Ожидание сети"; else -> "Отключено" }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private val bg get() = Color.rgb(24, 24, 24)
    private val fg get() = Color.rgb(234, 234, 234)
    private val dim get() = Color.rgb(120, 120, 120)
    private val green get() = Color.rgb(63, 191, 147)
}
