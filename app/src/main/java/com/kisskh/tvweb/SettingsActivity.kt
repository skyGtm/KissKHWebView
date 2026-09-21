package com.kisskh.tvweb

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.*
import java.io.File

class SettingsActivity: AppCompatActivity(){
    private lateinit var store:Store; private lateinit var blocker:AdBlocker; private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val createBackup=registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->if(uri!=null)scope.launch(Dispatchers.IO){val f=File(cacheDir,"backup.zip");store.exportZip(f,blocker.stateJson(),store.customRules,blocker.backupFiles());contentResolver.openOutputStream(uri)?.use{out->f.inputStream().use{it.copyTo(out)}};withContext(Dispatchers.Main){toast("Backup exported")}}}
    private val importBackup=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch(Dispatchers.IO){val f=File(cacheDir,"import.zip");contentResolver.openInputStream(uri)?.use{input->f.outputStream().use{input.copyTo(it)}};withContext(Dispatchers.Main){android.app.AlertDialog.Builder(this@SettingsActivity).setTitle("Import backup").setItems(arrayOf("Replace existing data","Merge bookmarks")){_,which->scope.launch(Dispatchers.IO){store.importZip(f,which==1);withContext(Dispatchers.Main){toast("Backup imported");setResult(Activity.RESULT_OK);finish()}}}.show()}}}
    override fun onCreate(b:Bundle?){super.onCreate(b);store=Store(this);blocker=AdBlocker(this);blocker.load();title="KissKH Settings";setContentView(build())}
    private fun build():ScrollView{
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(32,24,32,32)}
        fun heading(t:String){box.addView(TextView(this).apply{text=t;textSize=20f;setPadding(0,20,0,8)})}
        fun button(t:String,action:()->Unit)=box.addView(Button(this).apply{text=t;setOnClickListener{action()}})
        heading("Website")
        val base=EditText(this).apply{hint="https://kisskh.id/";setText(store.baseUrl);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI}
        box.addView(base)
        button("Save base URL"){runCatching{store.baseUrl=base.text.toString();toast("Saved: ${store.baseUrl}")}.onFailure{toast(it.message?:"Invalid URL")}}
        button("Open current base URL"){startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(store.baseUrl)))}
        heading("Navigation")
        addSwitch(box,"TV DPAD navigation",store.tvNavigation){store.tvNavigation=it}
        addSwitch(box,"Double-back to exit",store.doubleBack){store.doubleBack=it}
        addSwitch(box,"Allow external links",store.externalLinks){store.externalLinks=it}
        heading("Privacy & blocking")
        addSwitch(box,"Ad/tracker blocking",store.blockAds){store.blockAds=it}
        addSwitch(box,"Allow third-party cookies",store.thirdPartyCookies){store.thirdPartyCookies=it}
        button("Update filter lists now"){toast("Updating…");scope.launch{blocker.updateAll();toast("Updated ${blocker.lists.size} lists")}}
        val rules=EditText(this).apply{hint="Custom filter rules (one per line)";setText(store.customRules);minLines=3}
        box.addView(rules)
        button("Save custom rules"){store.customRules=rules.text.toString();blocker.load();toast("Custom rules saved")} 
        box.addView(TextView(this).apply{text="Filter lists are cached locally and can be used offline. ${blocker.lists.size} lists configured.";textSize=14f})
        heading("Bookmarks")
        button("View bookmarks"){showBookmarks()}
        heading("Direct open")
        val direct=EditText(this).apply{hint="Series/episode URL or /Drama/...";setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI}
        box.addView(direct)
        button("Open") { val s=direct.text.toString().trim();if(s.isNotEmpty()){setResult(Activity.RESULT_OK,Intent().putExtra("open",store.resolve(s)));finish()} }
        heading("Backup & import")
        button("Backup all app data"){createBackup.launch("KissKH-Backup.zip")}
        button("Import backup"){importBackup.launch(arrayOf("application/zip","application/octet-stream"))}
        button("Clear WebView cookies/cache"){android.webkit.CookieManager.getInstance().removeAllCookies(null);android.webkit.CookieManager.getInstance().flush();toast("Cookies cleared")}
        button("Clear WebView cache"){toast("WebView cache cleared on next load");getSharedPreferences("web",0).edit().clear().apply()}
        heading("About")
        box.addView(TextView(this).apply{text="KissKH WebView\nPrivacy-focused WebView wrapper with Android TV DPAD support.";textSize=14f})
        return ScrollView(this).apply{addView(box)}
    }
    private fun addSwitch(box:LinearLayout,label:String,value:Boolean,change:(Boolean)->Unit){val s=SwitchMaterial(this).apply{text=label;isChecked=value;setPadding(0,8,0,8);setOnCheckedChangeListener{_,v->change(v)}};box.addView(s,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))}
    private fun showBookmarks(){val list=store.bookmarks();val labels=if(list.isEmpty())arrayOf("No bookmarks") else list.map{"${it.title}\n${it.path}"}.toTypedArray();AlertDialog.Builder(this).setTitle("Bookmarks").setItems(labels){_,which->if(list.isNotEmpty()&&which<list.size){setResult(Activity.RESULT_OK,Intent().putExtra("open",store.resolve(list[which].path)));finish()}}.setNegativeButton("Close",null).show()}
    private fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_SHORT).show()}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
