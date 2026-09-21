package com.kisskh.tvweb

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Store(private val context: Context) {
    private val p = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    var baseUrl: String
        get() = p.getString("baseUrl", "https://kisskh.id/")!!
        set(v) { p.edit().putString("baseUrl", normalizeBase(v)).apply() }
    var blockAds: Boolean get() = p.getBoolean("blockAds", true); set(v) = p.edit().putBoolean("blockAds", v).apply()
    var tvNavigation: Boolean get() = p.getBoolean("tvNav", true); set(v) = p.edit().putBoolean("tvNav", v).apply()
    var doubleBack: Boolean get() = p.getBoolean("doubleBack", true); set(v) = p.edit().putBoolean("doubleBack", v).apply()
    var thirdPartyCookies: Boolean get() = p.getBoolean("thirdPartyCookies", false); set(v) = p.edit().putBoolean("thirdPartyCookies", v).apply()
    var externalLinks: Boolean get() = p.getBoolean("externalLinks", false); set(v) = p.edit().putBoolean("externalLinks", v).apply()
    var customRules: String get() = p.getString("customRules", "") ?: ""; set(v) = p.edit().putString("customRules", v).apply()

    fun resolve(pathOrUrl: String): String {
        val u = Uri.parse(pathOrUrl)
        if (!u.isAbsolute) return baseUrl.trimEnd('/') + "/" + pathOrUrl.trimStart('/')
        val configured = Uri.parse(baseUrl)
        return if (u.host.equals(configured.host, true)) {
            baseUrl.trimEnd('/') + "/" + (u.path ?: "/").trimStart('/') + if (u.query != null) "?${u.query}" else ""
        } else pathOrUrl
    }

    fun relativeFor(url: String): String? {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val b = Uri.parse(baseUrl)
        if (!u.scheme.equals(b.scheme, true) || !u.host.equals(b.host, true)) return null
        return buildString { append(u.path ?: "/"); if (u.encodedQuery != null) append('?').append(u.encodedQuery); if (u.encodedFragment != null) append('#').append(u.encodedFragment) }
    }

    fun normalizeBase(raw: String): String {
        var s = raw.trim()
        if (!s.contains("://")) s = "https://$s"
        val u = Uri.parse(s)
        require(u.scheme.equals("https", true)) { "Base URL must use HTTPS" }
        require(!u.host.isNullOrBlank()) { "Invalid base URL" }
        return Uri.Builder().scheme("https").authority(u.host).path("/").build().toString()
    }

    data class Bookmark(val id: Long, val title: String, val path: String, val type: String, val thumb: String?, val created: Long, val opened: Long)
    private val bookmarkFile get() = File(context.filesDir, "bookmarks.json")
    private val historyFile get() = File(context.filesDir, "history.json")

    fun bookmarks(): MutableList<Bookmark> = readBookmarks(bookmarkFile)
    private fun readBookmarks(f: File): MutableList<Bookmark> {
        if (!f.exists()) return mutableListOf()
        val a = runCatching { JSONArray(f.readText()) }.getOrElse { JSONArray() }
        return MutableList(a.length()) { i -> val o=a.getJSONObject(i); Bookmark(o.getLong("id"),o.optString("title"),o.optString("path"),o.optString("type"),o.optString("thumb").ifBlank{null},o.optLong("created"),o.optLong("opened")) }
    }
    private fun writeBookmarks(list: List<Bookmark>) { val a=JSONArray(); list.forEach { b -> a.put(JSONObject().apply { put("id",b.id);put("title",b.title);put("path",b.path);put("type",b.type);put("thumb",b.thumb ?: JSONObject.NULL);put("created",b.created);put("opened",b.opened) }) }; bookmarkFile.writeText(a.toString()) }
    fun addBookmark(title:String,path:String,type:String="PAGE",thumb:String?=null) { val list=bookmarks(); val now=System.currentTimeMillis(); val old=list.firstOrNull{it.path==path}; if(old==null) list.add(Bookmark(now,title,path,type,thumb,now,now)) else list[list.indexOf(old)]=old.copy(title=title,opened=now); writeBookmarks(list) }
    fun removeBookmark(path:String) { writeBookmarks(bookmarks().filterNot{it.path==path}) }

    fun addHistory(title:String,path:String) { val f=historyFile; val a=runCatching{JSONArray(if(f.exists())f.readText() else "[]")}.getOrElse{JSONArray()}; val out=JSONArray(); out.put(JSONObject().apply{put("title",title);put("path",path);put("time",System.currentTimeMillis())}); for(i in 0 until minOf(a.length(),99)){val o=a.getJSONObject(i);if(o.optString("path")!=path)out.put(o)}; f.writeText(out.toString()) }
    fun history(): JSONArray = runCatching{JSONArray(if(historyFile.exists())historyFile.readText() else "[]")}.getOrElse{JSONArray()}

    fun exportZip(out: File, filterState: String, customRules: String, filterFiles: List<Pair<String, File>> = emptyList()) {
        java.util.zip.ZipOutputStream(out.outputStream().buffered()).use { z ->
            fun entry(name:String,text:String){z.putNextEntry(java.util.zip.ZipEntry(name));z.write(text.toByteArray());z.closeEntry()}
            val settings=JSONObject().apply{put("baseUrl",baseUrl);put("blockAds",blockAds);put("tvNavigation",tvNavigation);put("doubleBack",doubleBack);put("thirdPartyCookies",thirdPartyCookies);put("externalLinks",externalLinks)}
            entry("manifest.json", JSONObject().put("format",1).put("app","KissKH WebView").toString(2)); entry("settings.json",settings.toString(2)); entry("bookmarks.json",bookmarkFile.takeIf{it.exists()}?.readText() ?: "[]"); entry("history.json",historyFile.takeIf{it.exists()}?.readText() ?: "[]"); entry("filter-lists.json",filterState); entry("custom-rules.txt",customRules); filterFiles.forEach { (name,file) -> if(file.exists()) { z.putNextEntry(java.util.zip.ZipEntry("filters/$name")); file.inputStream().use{it.copyTo(z)}; z.closeEntry() } }
        }
    }
    fun importZip(file: File, merge:Boolean) {
        val entries=HashMap<String,String>(); java.util.zip.ZipInputStream(file.inputStream().buffered()).use { z -> while(true){val e=z.nextEntry?:break; if(!e.isDirectory) entries[e.name]=z.readBytes().toString(Charsets.UTF_8)} }
        entries.filterKeys{it.startsWith("filters/")}.forEach { (name,data) -> val dir=File(context.filesDir,"filters").apply{mkdirs()}; File(dir,name.removePrefix("filters/")).writeText(data) }
        entries["custom-rules.txt"]?.let { customRules=it }
        entries["settings.json"]?.let { val o=JSONObject(it); baseUrl=o.optString("baseUrl",baseUrl); blockAds=o.optBoolean("blockAds",blockAds); tvNavigation=o.optBoolean("tvNavigation",tvNavigation); doubleBack=o.optBoolean("doubleBack",doubleBack); thirdPartyCookies=o.optBoolean("thirdPartyCookies",thirdPartyCookies); externalLinks=o.optBoolean("externalLinks",externalLinks) }
        fun parse(s:String)=runCatching{JSONArray(s)}.getOrElse{JSONArray()}
        if(!merge){entries["bookmarks.json"]?.let{bookmarkFile.writeText(parse(it).toString())};entries["history.json"]?.let{historyFile.writeText(parse(it).toString())}}
        else { val existing=bookmarks().associateBy{it.path}.toMutableMap(); entries["bookmarks.json"]?.let{a->val j=parse(a);for(i in 0 until j.length()){val o=j.getJSONObject(i);val b=Bookmark(o.optLong("id",System.currentTimeMillis()+i),o.optString("title"),o.optString("path"),o.optString("type","PAGE"),o.optString("thumb").ifBlank{null},o.optLong("created"),o.optLong("opened"));existing[b.path]=b};writeBookmarks(existing.values.toList())} }
    }
}
