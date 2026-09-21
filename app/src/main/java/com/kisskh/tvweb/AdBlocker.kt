package com.kisskh.tvweb

import android.content.Context
import android.webkit.WebResourceRequest
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AdBlocker(private val context: Context) {
    data class ListDef(val id:String,val name:String,val url:String,val category:String)
    val lists=listOf(
        ListDef("easylist","EasyList","https://easylist.to/easylist/easylist.txt","Ads"),
        ListDef("easyprivacy","EasyPrivacy","https://easylist.to/easylist/easyprivacy.txt","Privacy"),
        ListDef("adguard-tracking","AdGuard URL Tracking Protection","https://filters.adtidy.org/extension/chromium/filters/17.txt","Privacy"),
        ListDef("peter-lowe","Peter Lowe’s Ad & tracking server list","https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext","Privacy"),
        ListDef("urlhaus","Online Malicious URL Blocklist","https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-online.txt","Security"),
        ListDef("phishing","Phishing URL Blocklist","https://malware-filter.gitlab.io/malware-filter/phishing-filter-online.txt","Security")
    )
    private val domains=CopyOnWriteArraySet<String>(); private val prefixes=CopyOnWriteArraySet<String>(); private val contains=CopyOnWriteArraySet<String>(); private val allow=CopyOnWriteArraySet<String>()
    private val dir=File(context.filesDir,"filters").apply{mkdirs()}
    var blockedCount=0; private set
    fun load(){domains.clear();prefixes.clear();contains.clear();allow.clear();lists.forEach{f->val x=File(dir,"${f.id}.txt");if(x.exists())parse(x.readLines())};parseCustom(context.getSharedPreferences("app",0).getString("customRules","") ?: "")}
    private fun parseCustom(s:String){parse(s.lines())}
    fun backupFiles():List<Pair<String,File>> = lists.map{it.id to File(dir,"${it.id}.txt")}
    private fun parse(lines:List<String>){for(raw in lines){var s=raw.trim();if(s.isEmpty()||s.startsWith("!")||s.startsWith("[")||s.startsWith("#"))continue;var exception=s.startsWith("@@");if(exception)s=s.substring(2);s=s.substringBefore("$").trim();if(s.isEmpty())continue;if(exception){allow.add(s);continue};if(s.matches(Regex("^[0-9a-fA-F:.]+\\s+[^ ]+$"))){val h=s.split(Regex("\\s+"))[1];domains.add(h.lowercase());continue};if(s.startsWith("||")){val p=s.substring(2).substringBefore('^').substringBefore('/');if(p.isNotBlank())domains.add(p.lowercase());else prefixes.add(s.substring(2))}else if(s.startsWith("|http"))prefixes.add(s.substring(1)) else if(s.startsWith("http"))contains.add(s)} }
    fun isBlocked(url:String):Boolean{val l=url.lowercase();if(l.startsWith("data:")||l.startsWith("blob:")||l.startsWith("about:"))return false;if(allow.any{l.contains(it.lowercase().trim('|'))})return false;val host=runCatching{URL(l).host}.getOrNull()?:return false;if(domains.any{host==it||host.endsWith(".$it")}){blockedCount++;return true};if(prefixes.any{l.startsWith(it.lowercase())}){blockedCount++;return true};if(contains.any{l.contains(it.lowercase())}){blockedCount++;return true};return false}
    suspend fun updateAll(){withContext(Dispatchers.IO){for(d in lists){runCatching{val c=URL(d.url).openConnection() as HttpURLConnection;c.connectTimeout=15000;c.readTimeout=30000;c.setRequestProperty("User-Agent","KissKHWebView/1.0");if(c.responseCode in 200..299){val tmp=File(dir,"${d.id}.tmp");c.inputStream.use{input->tmp.outputStream().use{input.copyTo(it)}};tmp.renameTo(File(dir,"${d.id}.txt"))};c.disconnect()}};load();context.getSharedPreferences("filters",0).edit().putLong("updated",System.currentTimeMillis()).apply()}}
    fun stateJson():String=JSONObject().apply{put("updated",context.getSharedPreferences("filters",0).getLong("updated",0));val a=JSONArray();lists.forEach{d->val f=File(dir,"${d.id}.txt");a.put(JSONObject().put("id",d.id).put("name",d.name).put("enabled",f.exists()).put("rules",if(f.exists())f.readLines().size else 0))};put("lists",a)}.toString()
}
