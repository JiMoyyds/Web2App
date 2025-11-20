package com.example.web2app
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    //写入目标网站
    private val TARGET_URL = "https://xxxxxxx"

    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val denied = permissions.filterValues { !it }.keys
            if (denied.isNotEmpty()) {
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        clearWebViewData()

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        requestCameraAndMicPermissions()

        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowContentAccess = true
        webSettings.databaseEnabled = true
        webSettings.setSupportZoom(false)
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.allowFileAccess = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true

        WebView.setWebContentsDebuggingEnabled(true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                view?.loadUrl(request?.url.toString())
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    try {
                        val requestedResources = request.resources
                        val grantList = mutableListOf<String>()

                        for (res in requestedResources) {
                            if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                                res == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                            ) {
                                grantList.add(res)
                            }
                        }

                        val originUrl = request.origin?.toString() ?: ""
                        val useFrontCamera = originUrl.contains("camera=1")
                        val useBackCamera = originUrl.contains("camera=0") || !useFrontCamera

                        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
                        var selectedCameraId: String? = null

                        for (id in cameraManager.cameraIdList) {
                            val characteristics = cameraManager.getCameraCharacteristics(id)
                            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                            if (useBackCamera && facing == CameraCharacteristics.LENS_FACING_BACK) {
                                selectedCameraId = id
                                break
                            } else if (useFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                                selectedCameraId = id
                                break
                            }
                        }

                        android.util.Log.d(
                            "WebViewCamera",
                            "Selected cameraId=$selectedCameraId (front=$useFrontCamera, back=$useBackCamera)"
                        )

                        request.grant(grantList.toTypedArray())

                    } catch (e: Exception) {
                        e.printStackTrace()
                        request.deny()
                    }
                }
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message
            ): Boolean {
                val newWebView = WebView(this@MainActivity)
                newWebView.settings.javaScriptEnabled = true
                newWebView.webChromeClient = this
                newWebView.webViewClient = WebViewClient()

                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = newWebView
                resultMsg.sendToTarget()
                return true
            }
        }

        webView.loadUrl(TARGET_URL)
    }

    private fun requestCameraAndMicPermissions() {
        val needRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needRequest) {
            requestPermissionsLauncher.launch(permissions)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        clearWebViewData()
        webView.destroy()
    }

    private fun clearWebViewData() {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            WebStorage.getInstance().deleteAllData()

            WebView(this).apply {
                clearCache(true)
                clearHistory()
                clearFormData()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

