package com.revivo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.widget.Toast
import android.net.Uri
import android.content.Intent
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.PermissionRequest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import android.content.pm.PackageManager
import android.Manifest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.revivo.ui.theme.RevivoTheme
import com.revivo.ui.theme.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        try {
            val webViewCacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
            val jsDir = File(webViewCacheDir, "js")
            val wasmDir = File(webViewCacheDir, "wasm")
            if (!jsDir.exists()) jsDir.mkdirs()
            if (!wasmDir.exists()) wasmDir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            RevivoTheme {
                AppNavigation()
            }
        }
    }
}

class WebAppThemeBridge(private val onThemeUpdate: (Boolean) -> Unit) {
    @JavascriptInterface
    fun onThemeChanged(isDark: Boolean) {
        onThemeUpdate(isDark)
    }
}

class WebAppUploadBridge(private val context: Context) {
    @JavascriptInterface
    fun updateProgress(fileName: String, percent: Int, detail: String, isComplete: Boolean) {
        UploadForegroundService.updateProgress(context, fileName, percent, detail, isComplete)
    }

    @JavascriptInterface
    fun uploadComplete(fileName: String) {
        UploadForegroundService.updateProgress(context, fileName, 100, "", true)
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val activity = context as? Activity
    
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var isSplashVisible by remember { mutableStateOf(true) }
    var isOffline by remember { mutableStateOf(false) }
    var isDarkTheme by remember { mutableStateOf(false) }
    var isWebPageLoaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webProgress by remember { mutableStateOf(0) }
    var isWebLoading by remember { mutableStateOf(true) }

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    DisposableEffect(isDarkTheme, isSplashVisible, isOffline) {
        if (activity != null) {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            
            val darkActive = isOffline || isSplashVisible || isDarkTheme
            insetsController.isAppearanceLightStatusBars = !darkActive
            insetsController.isAppearanceLightNavigationBars = !darkActive
            
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        onDispose {}
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resultCode = result.resultCode
        val data = result.data
        var results: Array<Uri>? = null

        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            val dataString = data.dataString
            if (dataString != null) {
                results = arrayOf(Uri.parse(dataString))
            } else if (data.clipData != null) {
                val clipData = data.clipData!!
                val count = clipData.itemCount
                results = Array(count) { i -> clipData.getItemAt(i).uri }
            }
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    fun launchFilePickerDirectly(acceptTypes: Array<String>? = null) {
        val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            if (acceptTypes != null && acceptTypes.isNotEmpty() && acceptTypes[0].isNotEmpty()) {
                val validTypes = acceptTypes.filter { it.isNotEmpty() }.toTypedArray()
                if (validTypes.size == 1) {
                    type = validTypes[0]
                } else {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, validTypes)
                }
            } else {
                type = "*/*"
            }
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        try {
            fileChooserLauncher.launch(contentSelectionIntent)
        } catch (e: Exception) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            Toast.makeText(context, context.getString(R.string.error_files), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(reloadKey) {
        if (!isNetworkAvailable(context)) {
            isOffline = true
            isSplashVisible = false
            return@LaunchedEffect
        }
        isOffline = false
        isSplashVisible = true
        isWebLoading = true
        isWebPageLoaded = false
        delay(1200)
        var waited = 0
        while (!isWebPageLoaded && waited < 2000) {
            delay(100)
            waited += 100
        }
        isSplashVisible = false
    }

    BackHandler(enabled = !isSplashVisible && !isOffline && webViewInstance?.canGoBack() == true) {
        webViewInstance?.goBack()
    }

    val themeDetectorJs = """
        (function() {
            function notifyTheme() {
                var isDark = false;
                if (document.body && document.body.classList.contains('dark-theme')) {
                    isDark = true;
                } else if (document.documentElement && (document.documentElement.classList.contains('dark') || document.documentElement.getAttribute('data-theme') === 'dark')) {
                    isDark = true;
                }
                if (window.AndroidThemeBridge && window.AndroidThemeBridge.onThemeChanged) {
                    window.AndroidThemeBridge.onThemeChanged(Boolean(isDark));
                }
            }
            notifyTheme();
            if (window.MutationObserver && document.body) {
                var observer = new MutationObserver(function() {
                    notifyTheme();
                });
                observer.observe(document.body, { attributes: true, attributeFilter: ['class', 'data-theme'] });
                if (document.documentElement) {
                    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'data-theme'] });
                }
            }
            setInterval(notifyTheme, 400);
        })();
    """.trimIndent()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkTheme) Color.Black else Color(0xFFF2F2F7))
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewInstance = this
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    overScrollMode = android.view.View.OVER_SCROLL_NEVER
                    
                    val cookieManager = android.webkit.CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    addJavascriptInterface(
                        WebAppThemeBridge { dark ->
                            post {
                                isDarkTheme = dark
                            }
                        },
                        "AndroidThemeBridge"
                    )

                    addJavascriptInterface(
                        WebAppUploadBridge(ctx),
                        "AndroidUploadBridge"
                    )
                    addJavascriptInterface(
                        WebAppUploadBridge(ctx),
                        "RevivoNativeUpload"
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        mediaPlaybackRequiresUserGesture = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        javaScriptCanOpenWindowsAutomatically = true
                        userAgentString = userAgentString.replace("; wv", "") + " Revivo_NativeApp"
                    }

                    setOnLongClickListener { true }
                    isLongClickable = false

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val uri = request?.url ?: return false
                            val url = uri.toString()
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                return false
                            }
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                context.startActivity(intent)
                                true
                            } catch (e: Exception) {
                                true
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: android.webkit.SslErrorHandler?,
                            error: android.net.http.SslError?
                        ) {
                            handler?.proceed()
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                isOffline = true
                                isSplashVisible = false
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isWebLoading = true
                            view?.evaluateJavascript(themeDetectorJs, null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isWebLoading = false
                            isWebPageLoaded = true
                            
                            view?.evaluateJavascript(
                                "(function() { " +
                                "  var css = '* { -webkit-user-select: none !important; -webkit-touch-callout: none !important; user-select: none !important; } img { -webkit-user-drag: none !important; }'; " +
                                "  var style = document.createElement('style'); " +
                                "  style.type = 'text/css'; " +
                                "  style.appendChild(document.createTextNode(css)); " +
                                "  document.head.appendChild(style); " +
                                "})();",
                                null
                            )
                            view?.evaluateJavascript(themeDetectorJs, null)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            val message = consoleMessage?.message()
                            if (!message.isNullOrEmpty()) {
                                val uploadInfo = UploadForegroundService.parseUploadLog(message)
                                if (uploadInfo != null) {
                                    UploadForegroundService.updateProgress(
                                        context,
                                        uploadInfo.fileName,
                                        uploadInfo.percent,
                                        uploadInfo.detail,
                                        uploadInfo.isComplete
                                    )
                                }
                            }
                            return super.onConsoleMessage(consoleMessage)
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            webProgress = newProgress
                            if (newProgress >= 70) {
                                isWebPageLoaded = true
                            }
                        }

                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String?,
                            callback: android.webkit.GeolocationPermissions.Callback?
                        ) {
                            callback?.invoke(origin, true, false)
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            callback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = callback
                            launchFilePickerDirectly(fileChooserParams?.acceptTypes)
                            return true
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                    }

                    loadUrl("https://revivo.altervista.org/webapp_android_ios.html")
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isWebLoading && !isSplashVisible && !isOffline) {
            LinearProgressIndicator(
                progress = { webProgress.toFloat() / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = PurplePrimary,
                trackColor = PurpleContainer
            )
        }

        if (isOffline) {
            OfflineScreen(
                onRetry = {
                    if (isNetworkAvailable(context)) {
                        isSplashVisible = true
                        isOffline = false
                        isWebPageLoaded = false
                        isWebLoading = true
                        webViewInstance?.loadUrl("https://revivo.altervista.org/webapp_android_ios.html")
                        reloadKey++
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.offline_toast_no_internet),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = isSplashVisible,
            enter = EnterTransition.None,
            exit = fadeOut(animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing))
        ) {
            SplashScreen()
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.revivo_logo),
            contentDescription = "Revivo Logo",
            modifier = Modifier.size(170.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun OfflineScreen(
    onRetry: () -> Unit
) {
    val bgColor = Color(0xFF000000)
    val cardBg = Color(0xFF1C1C1E)
    val titleColor = Color(0xFFFFFFFF)
    val subtitleColor = Color(0xFF8E8E93)
    val iconBadgeBg = Color(0xFF2C2C2E)
    val iconTint = Color(0xFF0A84FF)
    val buttonBg = Color(0xFF0A84FF)
    val borderColor = Color.White.copy(alpha = 0.08f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(26.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = cardBg
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(iconBadgeBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = stringResource(id = R.string.offline_icon_desc),
                        tint = iconTint,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(id = R.string.offline_title),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.4).sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(id = R.string.offline_message),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = subtitleColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBg,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Text(
                        text = stringResource(id = R.string.offline_retry),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        letterSpacing = (-0.2).sp
                    )
                }
            }
        }
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
    return when {
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
        else -> false
    }
}
