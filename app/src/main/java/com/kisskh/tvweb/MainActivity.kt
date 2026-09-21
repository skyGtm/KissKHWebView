package com.kisskh.tvweb

import android.app.UiModeManager
import android.content.*
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.*
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var store: Store
    private lateinit var blocker: AdBlocker
    private var lastBack=0L
    private var tv=false
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val focusJs="""
(function(){
if(window.__kisskhTvInstalled)return;window.__kisskhTvInstalled=true;
const css=document.createElement('style');css.id='__kisskh_tv_css';css.textContent='.__kisskh_tv_focus{outline:3px solid #80cbc4!important;outline-offset:4px!important;box-shadow:0 0 0 5px rgba(128,203,196,.25)!important;}';document.documentElement.appendChild(css);
window.__kisskhFocusables=function(){return [...document.querySelectorAll('a,button,input,select,textarea,[role=button],[tabindex]:not([tabindex="-1"])')].filter(e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>1&&r.height>1&&s.visibility!=='hidden'&&s.display!=='none'});};
window.__kisskhMove=function(dx,dy){const a=window.__kisskhFocusables();if(!a.length)return;let c=document.activeElement;if(!a.includes(c))c=a[0];const cr=c.getBoundingClientRect(),cx=cr.left+cr.width/2,cy=cr.top+cr.height/2;let best=null,score=1e18;for(const e of a){if(e===c)continue;const r=e.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2,ax=x-cx,ay=y-cy;if(dx&&ax*dx<=0||dy&&ay*dy<=0)continue;const primary=Math.abs(dx?ax:ay),secondary=Math.abs(dx?ay:ax),s=primary*primary+secondary*secondary*4;if(s<score){score=s;best=e;}}if(best){best.focus({preventScroll:true});best.scrollIntoView({behavior:'smooth',block:'center',inline:'center'});best.classList.add('__kisskh_tv_focus');for(const e of a)if(e!==best)e.classList.remove('__kisskh_tv_focus');}};
window.__kisskhFocusables().forEach(e=>{if(!e.hasAttribute('tabindex')&&e.tagName!=='INPUT'&&e.tagName!=='SELECT'&&e.tagName!=='TEXTAREA')e.setAttribute('tabindex','0');});
})();""".trimIndent()

    override fun onCreate(b:Bundle?){super.onCreate(b);store=Store(this);blocker=AdBlocker(this);blocker.load();tv=isTv();if(tv)requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;setContentView(R.layout.activity_main);web=findViewById(R.id.web);findViewById<android.view.View>(R.id.menuButton).setOnClickListener{showMenu()};setupWebView();scheduleFilterUpdates();handleIntent(intent);}
    override fun onNewIntent(i:Intent){super.onNewIntent(i);setIntent(i);handleIntent(i)}
    private fun scheduleFilterUpdates(){ val req=PeriodicWorkRequestBuilder<FilterUpdateWorker>(24,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();WorkManager.getInstance(this).enqueueUniquePeriodicWork("filter-updates",androidx.work.ExistingPeriodicWorkPolicy.UPDATE,req) }
    private fun isTv()= (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType==Configuration.UI_MODE_TYPE_TELEVISION || packageManager.hasSystemFeature("android.software.leanback")
    private fun setupWebView(){
        WebView.setWebContentsDebuggingEnabled(false)
        web.setBackgroundColor(Color.BLACK);web.isFocusable=true;web.isFocusableInTouchMode=true;web.requestFocus()
        val s=web.settings;s.javaScriptEnabled=true;s.domStorageEnabled=true;s.databaseEnabled=false;s.allowFileAccess=false;s.allowContentAccess=false;s.javaScriptCanOpenWindowsAutomatically=false;s.setSupportMultipleWindows(false);s.mediaPlaybackRequiresUserGesture=false;s.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;s.userAgentString=s.userAgentString+" KissKHWebView/1.0"
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,store.thirdPartyCookies)
        if(WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) WebSettingsCompat.setForceDark(s,WebSettingsCompat.FORCE_DARK_AUTO)
        web.webChromeClient=object:WebChromeClient(){override fun onShowFileChooser(v:WebView?,cb:ValueCallback<Array<Uri>>?,p:FileChooserParams?):Boolean{return super.onShowFileChooser(v,cb,p)}}
        web.webViewClient=object:WebViewClient(){
            override fun shouldInterceptRequest(v:WebView?,r:WebResourceRequest?):WebResourceResponse?{if(store.blockAds&&r!=null&&blocker.isBlocked(r.url.toString()))return WebResourceResponse("text/plain","utf-8",ByteArray(0).inputStream());return super.shouldInterceptRequest(v,r)}
            override fun shouldInterceptRequest(v:WebView?,url:String?):WebResourceResponse?{if(store.blockAds&&url!=null&&blocker.isBlocked(url))return WebResourceResponse("text/plain","utf-8",ByteArray(0).inputStream());return super.shouldInterceptRequest(v,url)}
            override fun shouldOverrideUrlLoading(v:WebView?,r:WebResourceRequest?):Boolean{val u=r?.url?:return false;return handleNavigation(u.toString())}
            override fun shouldOverrideUrlLoading(v:WebView?,url:String?):Boolean=handleNavigation(url?:"")
            override fun onPageFinished(v:WebView?,url:String?){super.onPageFinished(v,url);if(tv&&store.tvNavigation)v?.evaluateJavascript(focusJs,null);store.relativeFor(url ?: "")?.let{path->scope.launch(Dispatchers.IO){store.addHistory(v?.title?:"",path)}}}
        }
        web.setOnFocusChangeListener{_,has->if(has&&tv)web.evaluateJavascript(focusJs,null)}
        web.loadUrl(store.baseUrl)
    }
    private fun handleNavigation(raw:String):Boolean{if(raw.isBlank())return false;val u=runCatching{Uri.parse(raw)}.getOrNull()?:return false;if(u.scheme=="kisskh"&&u.host=="open"){val target=u.getQueryParameter("url")?:u.getQueryParameter("path")?:return true;web.loadUrl(store.resolve(target));return true};if(u.scheme=="http"||u.scheme=="https"){val base=Uri.parse(store.baseUrl);if(u.host.equals(base.host,true)||store.externalLinks)return false;startActivity(Intent(Intent.ACTION_VIEW,u));return true};return true}
    private fun showMenu(){
        val items=arrayOf("★ Bookmark current page","★ Bookmarks / Direct open","⚙ Settings","↻ Reload")
        android.app.AlertDialog.Builder(this).setTitle("KissKH").setItems(items){_,w->when(w){0->{val path=store.relativeFor(web.url?:"");if(path!=null){store.addBookmark(web.title ?: "KissKH",path,if(path.contains("/Drama/"))"SERIES" else "PAGE");Toast.makeText(this,"Bookmarked",Toast.LENGTH_SHORT).show()}else Toast.makeText(this,"Only KissKH pages can be bookmarked",Toast.LENGTH_SHORT).show()};1,2->{startActivityForResult(Intent(this,SettingsActivity::class.java),42)};3->web.reload()}}.show()
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==42&&resultCode==RESULT_OK){val open=data?.getStringExtra("open");if(open!=null)web.loadUrl(open) else web.loadUrl(store.baseUrl)}}
    private fun handleIntent(i:Intent){val data=i.data?:return;if(data.scheme=="kisskh"&&data.host=="open"){val target=data.getQueryParameter("url")?:data.getQueryParameter("path");if(target!=null)web.loadUrl(store.resolve(target))}else if(data.scheme=="http"||data.scheme=="https"){web.loadUrl(store.resolve(data.toString()))}}
    override fun dispatchKeyEvent(e:KeyEvent):Boolean{if(tv&&store.tvNavigation&&e.action==KeyEvent.ACTION_DOWN){when(e.keyCode){KeyEvent.KEYCODE_DPAD_UP->{web.evaluateJavascript("window.__kisskhMove(0,-1)",null);return true};KeyEvent.KEYCODE_DPAD_DOWN->{web.evaluateJavascript("window.__kisskhMove(0,1)",null);return true};KeyEvent.KEYCODE_DPAD_LEFT->{web.evaluateJavascript("window.__kisskhMove(-1,0)",null);return true};KeyEvent.KEYCODE_DPAD_RIGHT->{web.evaluateJavascript("window.__kisskhMove(1,0)",null);return true};KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER->{web.evaluateJavascript("document.activeElement&&document.activeElement.click()",null);return true}}};return super.dispatchKeyEvent(e)}
    override fun onBackPressed(){if(web.canGoBack()){web.goBack();return};if(!store.doubleBack){finish();return};val now=SystemClock.elapsedRealtime();if(now-lastBack<2000){finish()}else{lastBack=now;Toast.makeText(this,"Press back again to exit",Toast.LENGTH_SHORT).show()}}
    override fun onDestroy(){scope.cancel();web.destroy();super.onDestroy()}
}
