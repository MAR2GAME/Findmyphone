package com.findmyphone.gpstracker.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.activity.OnBackPressedCallback
import com.findmyphone.gpstracker.base.activity.BaseViewActivity
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.databinding.ActivityWebViewBinding
import java.net.URI

class WebViewActivity : BaseViewActivity<ActivityWebViewBinding>() {

    companion object {
        const val EXTRA_URL = "extra_url"
        private const val TAG_SPLASH = "splash"
    }

    override fun initializeActivity() {
        super.initializeActivity()
        setupWebView()
        loadUrl()
        setupBackPressHandler()
    }

    override fun onPause() {
        super.onPause()
        // 暂停 WebView 的所有操作，节省资源
        binding.webview.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webview.onResume()
    }

    override fun onDestroy() {
        // 清理 WebView，避免内存泄漏
        binding.webview.apply {
            stopLoading()
            clearHistory()
            clearCache(true)
            clearFormData()
            destroy()
        }

        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 保存 WebView 状态，用于配置变更（如旋转屏幕）
        binding.webview.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // 恢复 WebView 状态
        binding.webview.restoreState(savedInstanceState)
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webview.canGoBack()) {
                    binding.webview.goBack()
                } else {
                    if (intent.getStringExtra("tag") == TAG_SPLASH) {
                        readyGoThenKill(SplashActivity::class.java)
                    } else {
                        finish()
                    }
                }
            }
        })
    }

    private fun setupWebView() {
        val webSettings = binding.webview.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // 安全设置：禁止 file 协议访问，避免安全漏洞
            allowFileAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            // 混合内容：允许 HTTPS 页面加载 HTTP 内容（按需开启）
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // 缓存策略：默认使用缓存，但可调整
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        binding.webview.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                showProgress(true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                showProgress(false)
            }

            @Deprecated("Deprecated in Java", ReplaceWith("shouldOverrideUrlLoading(view, request)"))
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                // 兼容旧 API 的重载
                view.loadUrl(url)
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // 在 WebView 内部加载所有链接
                view.loadUrl(request.url.toString())
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // 加载错误时隐藏进度条，可显示错误页面
                showProgress(false)
                // 可选：加载一个本地错误页面
                // view?.loadUrl("file:///android_asset/error.html")
            }

        }

        binding.webview.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding.progressBar.progress = newProgress
            }
        }
    }

    private fun loadUrl() {
        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        if (url.isNotEmpty() && isValidUrl(url)) {
            binding.webview.loadUrl(url)
        } else {
            // 无效 URL 时关闭页面或显示错误
            finish()
        }
    }

    private fun showProgress(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun isValidUrl(url: String): Boolean {
        // 简单的 URL 校验，防止注入或非法协议
        return url.startsWith("http://") || url.startsWith("https://")
    }
}