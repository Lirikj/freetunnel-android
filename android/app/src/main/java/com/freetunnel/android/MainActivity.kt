package com.freetunnel.android

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService as AndroidVpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.adguard.trusttunnel.AppNotifier
import com.adguard.trusttunnel.DeepLink
import com.adguard.trusttunnel.VpnService
import java.io.File

class MainActivity : AppCompatActivity(), AppNotifier {
    private lateinit var store: ConfigStore
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private var currentPage = 0
    private var state = 0
    private var pendingConfig: String? = null
    private var stateLabel: TextView? = null
    private var heroLogo: ImageView? = null

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (AndroidVpnService.prepare(this) == null) pendingConfig?.let { VpnService.start(this, it) }
        pendingConfig = null
    }
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::importUri) }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConfigStore(this)
        VpnService.setAppNotifier(File(filesDir, "connection_info.dat"), this)
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        buildShell()
        handleIntent(intent)
        openPage(0)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun buildShell() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        root.addView(TextView(this).apply {
            text = "FreeTunnel"; textSize = 19f; setTextColor(TEXT); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), dp(7), dp(20), 0)
        }, LinearLayout.LayoutParams(-1, dp(54)))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = rounded(SURFACE, 0f, stroke = BORDER)
            setPadding(dp(8), dp(5), dp(8), dp(8))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(70)))
        setContentView(root)
    }

    private fun renderNav() {
        nav.removeAllViews()
        val items = listOf(
            R.drawable.logo to "Главная", R.drawable.ic_configs to "Конфиги",
            R.drawable.ic_network to "Маршруты", R.drawable.ic_settings to "Настройки",
            R.drawable.ic_log to "Логи"
        )
        items.forEachIndexed { index, item ->
            nav.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = if (index == currentPage) rounded(TILE, 11f) else null
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(item.first); scaleType = ImageView.ScaleType.CENTER_INSIDE
                    imageTintList = ColorStateList.valueOf(if (index == currentPage) TEXT else FAINT)
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                }, LinearLayout.LayoutParams(-1, dp(31)))
                addView(TextView(this@MainActivity).apply {
                    text = item.second; textSize = 10f; setTextColor(if (index == currentPage) TEXT else FAINT)
                    gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(-1, dp(22)))
                setOnClickListener { openPage(index) }
            }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
    }

    private fun openPage(index: Int) {
        currentPage = index; renderNav(); content.removeAllViews()
        val page = when (index) { 1 -> configsPage(); 2 -> splitPage(); 3 -> settingsPage(); 4 -> logsPage(); else -> homePage() }
        content.addView(page, FrameLayout.LayoutParams(-1, -1))
    }

    private fun homePage(): View {
        val root = FrameLayout(this)
        val center = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        heroLogo = ImageView(this).apply {
            setImageResource(R.drawable.logo); alpha = if (state == 2) 1f else .48f
            scaleType = ImageView.ScaleType.CENTER_INSIDE; setPadding(dp(5), dp(5), dp(5), dp(5)); setOnClickListener { toggle() }
        }
        center.addView(heroLogo, LinearLayout.LayoutParams(dp(152), dp(152)))
        stateLabel = text(stateText(), 16f, if (state == 2) SUCCESS else DIM, true).apply { gravity = Gravity.CENTER }
        center.addView(stateLabel, LinearLayout.LayoutParams(-1, dp(38)))
        val configs = store.configs(); val selected = configs.firstOrNull { it.id == store.selectedId } ?: configs.firstOrNull()
        center.addView(text((selected?.name ?: "Добавить конфигурацию") + if (selected != null) "  ▾" else "  +", 15f, TEXT, true).apply {
            gravity = Gravity.CENTER; setOnClickListener { openPage(1) }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(center, FrameLayout.LayoutParams(-1, dp(270), Gravity.CENTER).apply { bottomMargin = dp(42) })

        val speeds = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; addView(speedCard("↓", "0.00 MB/s", SUCCESS)); addView(speedCard("↑", "0.00 MB/s", DIM)) }
        root.addView(speeds, FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM).apply { bottomMargin = dp(28) })
        return root
    }

    private fun speedCard(arrow: String, value: String, color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; background = rounded(TILE, 9f)
        addView(text(arrow, 16f, color), LinearLayout.LayoutParams(dp(26), -2))
        addView(text(value, 14f, TEXT, true), LinearLayout.LayoutParams(-2, -2))
    }.also { it.layoutParams = LinearLayout.LayoutParams(dp(136), dp(46)).apply { setMargins(dp(6), 0, dp(6), 0) } }

    private fun configsPage(): View = scrollPage("Конфигурации") { page ->
        val configs = store.configs()
        if (configs.isEmpty()) page.addView(emptyCard("Конфигураций пока нет", "Импортируйте TOML или tt:// ссылку"))
        configs.forEach { cfg ->
            page.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = rounded(TILE, 11f); setPadding(dp(14), 0, dp(8), 0)
                addView(TextView(this@MainActivity).apply { text = if (cfg.id == store.selectedId) "●" else "○"; textSize = 17f; setTextColor(if (cfg.id == store.selectedId) SUCCESS else FAINT) }, LinearLayout.LayoutParams(dp(32), -2))
                addView(text(cfg.name, 15f, TEXT, true).apply { setOnClickListener { store.selectedId = cfg.id; openPage(1) } }, LinearLayout.LayoutParams(0, -1, 1f))
                addView(iconAction("✎") { editConfig(cfg) }, LinearLayout.LayoutParams(dp(42), dp(42)))
                addView(iconAction("×", DANGER) { deleteConfig(cfg) }, LinearLayout.LayoutParams(dp(42), dp(42)))
            }, LinearLayout.LayoutParams(-1, dp(62)).apply { bottomMargin = dp(8) })
        }
        page.addView(action("＋  Вставить TOML") { editConfig(null) }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        page.addView(action("⇩  Импортировать файл", false) { openDocument.launch(arrayOf("text/*", "application/octet-stream")) }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(9) })
    }

    private fun splitPage(): View = scrollPage("Раздельное туннелирование") { page ->
        page.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text("Раздельное туннелирование", 15f, TEXT, true), LinearLayout.LayoutParams(0, dp(52), 1f))
            addView(Switch(this@MainActivity).apply {
                isChecked = store.splitEnabled; buttonTintList = ColorStateList.valueOf(ACCENT)
                setOnCheckedChangeListener { _, value -> store.splitEnabled = value }
            }, LinearLayout.LayoutParams(-2, dp(52)))
        })
        page.addView(section("РЕЖИМ"))
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modeRow.addView(choice("Обход VPN", store.splitMode == "general") { store.splitMode = "general"; openPage(2) }, LinearLayout.LayoutParams(0, dp(45), 1f))
        modeRow.addView(choice("Через VPN", store.splitMode == "selective") { store.splitMode = "selective"; openPage(2) }, LinearLayout.LayoutParams(0, dp(45), 1f).apply { leftMargin = dp(8) })
        page.addView(modeRow)
        if (store.splitMode == "selective" && store.splitRules.isBlank()) page.addView(infoCard("Добавьте хотя бы одно правило. Пока список пуст, весь трафик будет направлен через VPN."))
        page.addView(section(if (store.splitMode == "selective") "ПРАВИЛА — ЧЕРЕЗ VPN" else "ПРАВИЛА — ОБХОД VPN"))
        val rules = darkInput("Домены или IP, по одному на строку", store.splitRules, 7)
        page.addView(rules, LinearLayout.LayoutParams(-1, dp(170)))
        page.addView(text("Рекомендуемые для России", 13f, ACCENT, true).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL; setOnClickListener { rules.setText("youtube.com\ngooglevideo.com\ninstagram.com\nfacebook.com\nx.com\ntwitter.com") }
        }, LinearLayout.LayoutParams(-1, dp(42)))
        page.addView(section("ИСКЛЮЧЁННЫЕ ПОДСЕТИ"))
        val routes = darkInput("Например, 10.0.0.0/8", store.excludedRoutes, 4)
        page.addView(routes, LinearLayout.LayoutParams(-1, dp(120)))
        page.addView(action("Сохранить") { store.splitRules = rules.text.toString(); store.excludedRoutes = routes.text.toString(); toast("Сохранено — переподключите VPN") }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(16) })
    }

    private fun settingsPage(): View = scrollPage("Настройки") { page ->
        page.addView(settingCard("Автоподключение", "Настройте постоянное VPN в Android", "›") { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) })
        page.addView(settingCard("Системные настройки VPN", "Блокировка соединений без VPN", "›") { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) })
        page.addView(section("О ПРИЛОЖЕНИИ"))
        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = rounded(TILE, 11f); setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.logo) }, LinearLayout.LayoutParams(dp(54), dp(54)))
            addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0); addView(text("FreeTunnel Android", 16f, TEXT, true)); addView(text("1.0.2  ·  TrustTunnel 1.1.5-rc.6", 12f, DIM)) }, LinearLayout.LayoutParams(0, -2, 1f))
        }, LinearLayout.LayoutParams(-1, dp(82)))
    }

    private fun logsPage(): View = scrollPage("Логи") { page ->
        val file = File(filesDir, "connection_info.dat")
        page.addView(emptyCard(if (file.exists()) "Журнал подключений" else "Записей пока нет", if (file.exists()) "Данные ядра: ${file.length()} байт" else "События появятся после подключения"))
        page.addView(action("Очистить журнал", false) { VpnService.clearLogs(); file.delete(); openPage(4) }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) })
    }

    private fun scrollPage(title: String, builder: (LinearLayout) -> Unit): View {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(26)) }
        page.addView(text(title, 22f, TEXT, true), LinearLayout.LayoutParams(-1, dp(54)))
        builder(page); scroll.addView(page); return scroll
    }

    private fun settingCard(title: String, subtitle: String, end: String, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = rounded(TILE, 11f); setPadding(dp(15), dp(8), dp(12), dp(8)); setOnClickListener { action() }
        addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(title, 15f, TEXT, true)); addView(text(subtitle, 12f, DIM)) }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(text(end, 24f, FAINT).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(32), -1))
    }.also { it.layoutParams = LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(8) } }

    private fun section(value: String) = text(value, 11f, FAINT, true).apply { gravity = Gravity.BOTTOM; letterSpacing = .08f }.also { it.layoutParams = LinearLayout.LayoutParams(-1, dp(42)) }
    private fun emptyCard(title: String, subtitle: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = rounded(TILE, 12f); addView(text(title, 15f, TEXT, true)); addView(text(subtitle, 12f, DIM)) }.also { it.layoutParams = LinearLayout.LayoutParams(-1, dp(130)) }
    private fun infoCard(value: String) = text(value, 12f, WARN).apply { background = rounded(INFO, 9f); setPadding(dp(12), dp(10), dp(12), dp(10)) }.also { it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) } }
    private fun choice(value: String, active: Boolean, action: () -> Unit) = text(value, 13f, if (active) BG else TEXT, true).apply { gravity = Gravity.CENTER; background = rounded(if (active) ACCENT else TILE, 12f, stroke = if (active) ACCENT else BORDER); setOnClickListener { action() } }
    private fun action(value: String, primary: Boolean = true, action: () -> Unit) = text(value, 14f, if (primary) BG else TEXT, true).apply { gravity = Gravity.CENTER; background = rounded(if (primary) ACCENT else TILE, 10f, stroke = if (primary) ACCENT else BORDER); setOnClickListener { action() } }
    private fun iconAction(value: String, color: Int = TEXT, action: () -> Unit) = text(value, 19f, color).apply { gravity = Gravity.CENTER; background = rounded(SURFACE, 9f); setOnClickListener { action() } }
    private fun darkInput(hint: String, value: String, lines: Int) = EditText(this).apply { this.hint = hint; setText(value); minLines = lines; gravity = Gravity.TOP; setTextColor(TEXT); setHintTextColor(FAINT); textSize = 13f; background = rounded(INPUT, 9f, stroke = BORDER); setPadding(dp(12), dp(10), dp(12), dp(10)) }
    private fun text(value: String, size: Float, color: Int = TEXT, bold: Boolean = false) = TextView(this).apply { text = value; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL }
    private fun rounded(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius.toInt()).toFloat(); stroke?.let { setStroke(dp(1), it) } }

    private fun toggle() {
        if (state != 0) { VpnService.stop(this); return }
        val configs = store.configs(); val selected = configs.firstOrNull { it.id == store.selectedId } ?: configs.firstOrNull()
        if (selected == null) { openPage(1); toast("Сначала добавьте конфигурацию"); return }
        store.selectedId = selected.id
        val config = TomlConfig.applySplit(selected.toml, store)
        AndroidVpnService.prepare(this)?.let { pendingConfig = config; vpnPermission.launch(it) } ?: VpnService.start(this, config)
    }

    private fun editConfig(existing: TunnelConfig?) {
        val name = darkInput("Название", existing?.name ?: "Server", 1)
        val config = darkInput("Полная TOML конфигурация", existing?.toml ?: "", 12).apply { setHorizontallyScrolling(true) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(18), 0); addView(name); addView(config, LinearLayout.LayoutParams(-1, dp(330)).apply { topMargin = dp(10) }) }
        AlertDialog.Builder(this).setTitle(if (existing == null) "Новая конфигурация" else "Редактирование").setView(box).setNegativeButton("Отмена", null).setPositiveButton("Сохранить") { _, _ ->
            if (!config.text.contains("[endpoint]")) toast("Нужна полная TrustTunnel TOML конфигурация") else {
                val items = store.configs(); val item = TunnelConfig(existing?.id ?: System.currentTimeMillis(), name.text.toString().ifBlank { "Server" }, config.text.toString())
                val i = items.indexOfFirst { it.id == item.id }; if (i >= 0) items[i] = item else items.add(item)
                store.save(items); if (store.selectedId < 0) store.selectedId = item.id; openPage(1)
            }
        }.show()
    }

    private fun deleteConfig(config: TunnelConfig) = AlertDialog.Builder(this).setMessage("Удалить «${config.name}»?").setNegativeButton("Отмена", null).setPositiveButton("Удалить") { _, _ ->
        val items = store.configs().filterNot { it.id == config.id }; store.save(items); if (store.selectedId == config.id) store.selectedId = items.firstOrNull()?.id ?: -1; openPage(1)
    }.show()

    private fun handleIntent(intent: Intent?) { intent?.dataString?.takeIf { it.startsWith("tt://") }?.let { link -> runCatching { DeepLink.decode(link) }.onSuccess { addConfig("Imported server", TomlConfig.fromDeepLinkEndpoint(it)) }.onFailure { toast("Не удалось прочитать tt:// ссылку") } } }
    private fun importUri(uri: Uri) = runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty() }.onSuccess { if (it.contains("[endpoint]")) addConfig(uri.lastPathSegment ?: "Imported", it) else toast("Это не TrustTunnel TOML") }.onFailure { toast("Не удалось открыть файл") }
    private fun addConfig(name: String, toml: String) { val items = store.configs(); val cfg = TunnelConfig(System.currentTimeMillis(), name, toml); items.add(cfg); store.save(items); store.selectedId = cfg.id; openPage(1) }
    override fun onStateChanged(state: Int) = runOnUiThread { this.state = state; stateLabel?.text = stateText(); heroLogo?.alpha = if (state == 2) 1f else .48f }
    override fun onConnectionInfo(info: String) = Unit
    private fun stateText() = when (state) { 1 -> "Подключение…"; 2 -> "Подключено"; 3, 4 -> "Восстановление…"; 5 -> "Ожидание сети"; else -> "Отключено" }
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val BG = Color.rgb(24, 24, 24); private val SURFACE = Color.rgb(32, 32, 32); private val TILE = Color.rgb(38, 38, 38)
        private val INPUT = Color.rgb(16, 16, 16); private val BORDER = Color.rgb(54, 54, 54); private val TEXT = Color.rgb(234, 234, 234)
        private val DIM = Color.rgb(154, 154, 154); private val FAINT = Color.rgb(106, 106, 106); private val ACCENT = Color.rgb(176, 176, 176)
        private val SUCCESS = Color.rgb(63, 191, 147); private val WARN = Color.rgb(217, 150, 52); private val DANGER = Color.rgb(224, 106, 106); private val INFO = Color.rgb(43, 43, 43)
    }
}
