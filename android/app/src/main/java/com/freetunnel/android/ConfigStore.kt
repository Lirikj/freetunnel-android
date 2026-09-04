package com.freetunnel.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TunnelConfig(val id: Long, val name: String, val toml: String)

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("freetunnel", Context.MODE_PRIVATE)

    fun configs(): MutableList<TunnelConfig> = runCatching {
        val a = JSONArray(prefs.getString("configs", "[]"))
        MutableList(a.length()) { i ->
            val o = a.getJSONObject(i)
            TunnelConfig(o.getLong("id"), o.getString("name"), o.getString("toml"))
        }
    }.getOrElse { mutableListOf() }

    fun save(items: List<TunnelConfig>) {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("toml", it.toml)) }
        prefs.edit().putString("configs", a.toString()).apply()
    }

    var selectedId: Long
        get() = prefs.getLong("selected", -1)
        set(value) { prefs.edit().putLong("selected", value).apply() }
    var splitEnabled: Boolean
        get() = prefs.getBoolean("split_enabled", false)
        set(value) { prefs.edit().putBoolean("split_enabled", value).apply() }
    var splitMode: String
        get() = prefs.getString("split_mode", "general") ?: "general"
        set(value) { prefs.edit().putString("split_mode", value).apply() }
    var splitRules: String
        get() = prefs.getString("split_rules", "") ?: ""
        set(value) { prefs.edit().putString("split_rules", value).apply() }
    var excludedRoutes: String
        get() = prefs.getString("excluded_routes", "10.0.0.0/8\n172.16.0.0/12\n192.168.0.0/16") ?: ""
        set(value) { prefs.edit().putString("excluded_routes", value).apply() }
}

object TomlConfig {
    private fun quoted(values: List<String>) = values.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }
    private fun lines(raw: String) = raw.split(Regex("[\\s,;]+"))
        .map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun rootValue(source: String, key: String, value: String): String {
        val pattern = Regex("(?m)^\\s*${Regex.escape(key)}\\s*=.*$")
        if (pattern.containsMatchIn(source)) return source.replace(pattern, "$key = $value")
        val firstSection = Regex("(?m)^\\s*\\[").find(source)?.range?.first ?: source.length
        return source.substring(0, firstSection) + "$key = $value\n" + source.substring(firstSection)
    }

    private fun sectionValue(source: String, section: String, key: String, value: String): String {
        val sectionStart = Regex("(?m)^\\s*\\[${Regex.escape(section)}]\\s*$").find(source)
            ?: return source.trimEnd() + "\n\n[$section]\n$key = $value\n"
        val next = Regex("(?m)^\\s*\\[").find(source, sectionStart.range.last + 1)
        val end = next?.range?.first ?: source.length
        val block = source.substring(sectionStart.range.first, end)
        val pattern = Regex("(?m)^\\s*${Regex.escape(key)}\\s*=.*$")
        val updated = if (pattern.containsMatchIn(block)) block.replace(pattern, "$key = $value") else block.trimEnd() + "\n$key = $value\n"
        return source.substring(0, sectionStart.range.first) + updated + source.substring(end)
    }

    fun applySplit(source: String, store: ConfigStore): String {
        var result = source
        val rules = if (store.splitEnabled) lines(store.splitRules) else emptyList()
        val mode = if (store.splitMode == "selective" && rules.isEmpty()) "general" else store.splitMode
        result = rootValue(result, "vpn_mode", "\"$mode\"")
        result = rootValue(result, "exclusions", quoted(rules))
        result = sectionValue(result, "listener.tun", "included_routes", quoted(listOf("0.0.0.0/0", "2000::/3")))
        result = sectionValue(result, "listener.tun", "excluded_routes", quoted(lines(store.excludedRoutes)))
        result = sectionValue(result, "listener.tun", "mtu_size", "1350")
        return result
    }

    fun fromDeepLinkEndpoint(endpoint: String): String = """
        loglevel = "info"
        vpn_mode = "general"
        killswitch_enabled = true
        exclusions = []

        ${endpoint.trim()}

        [listener.tun]
        included_routes = ["0.0.0.0/0", "2000::/3"]
        excluded_routes = ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"]
        mtu_size = 1350
    """.trimIndent()
}
