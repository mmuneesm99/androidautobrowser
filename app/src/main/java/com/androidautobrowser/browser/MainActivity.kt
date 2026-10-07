package com.androidautobrowser.browser

import android.Manifest
import android.animation.ObjectAnimator
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.androidautobrowser.browser.info.InfoDashboardLoader
import com.androidautobrowser.browser.location.LocationStatusHelper
import com.androidautobrowser.browser.mirror.BrowserSync
import com.androidautobrowser.browser.mirror.MirrorController
import com.androidautobrowser.browser.mirror.MirrorPermissionActivity
import com.androidautobrowser.browser.mirror.ScreenMirrorService
import com.androidautobrowser.browser.web.BrowserWebViewFactory
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Phone + Android Auto browser.
 * Home dashboard shows weather, location, battery, tips, and headlines —
 * plus clock and Chrome / Resume launchers.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var startPageRoot: View
    private lateinit var browserChrome: View
    private lateinit var controlBarHost: View
    private lateinit var controlBar: View
    private lateinit var btnRevealControls: ImageButton
    private lateinit var pageProgress: LinearProgressIndicator
    private lateinit var pageTitle: TextView
    private lateinit var pageHost: TextView
    private lateinit var secureIcon: ImageView
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnReload: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnYoutube: ImageButton
    private lateinit var btnChrome: ImageButton
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var statusTime: TextView
    private lateinit var statusDay: TextView
    private lateinit var statusDate: TextView
    private lateinit var statusLocation: TextView
    private lateinit var locationRow: View
    private lateinit var tileChrome: View
    private lateinit var tileMirror: View
    private lateinit var tileMirrorLabel: TextView
    private lateinit var tileResume: View
    private lateinit var mirrorOverlay: View
    private lateinit var mirrorSurface: android.view.SurfaceView
    private lateinit var mirrorStatus: TextView
    private lateinit var btnStopMirror: ImageButton

    private lateinit var infoGreeting: TextView
    private lateinit var infoTemp: TextView
    private lateinit var infoCondition: TextView
    private lateinit var infoFeelsLike: TextView
    private lateinit var infoWeatherMeta: TextView
    private lateinit var infoPlace: TextView
    private lateinit var infoBattery: TextView
    private lateinit var infoTip: TextView
    private lateinit var infoNews1: TextView
    private lateinit var infoNews2: TextView
    private lateinit var infoNews3: TextView
    private lateinit var infoWeatherAlert: View
    private lateinit var infoWeatherAlertText: TextView
    private lateinit var infoWeatherAlertSecondary: TextView

    private var latestWeatherNewsAlerts: List<String> = emptyList()
    private var liveSevereWeatherText: String? = null
    private var lastKnownLocation: Location? = null
    private var lastLocationLabel: String = ""

    private var currentDestination: BrowserDestination = BrowserDestination.DEFAULT
    private var currentUrl: String = ""
    private var mirrorRequested = false
    private var isLoading: Boolean = false
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideBrowserChrome() }
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, CLOCK_TICK_MS)
        }
    }

    private val timeFormat: DateFormat =
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    private lateinit var locationHelper: LocationStatusHelper

    private val urlSyncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val url = intent?.getStringExtra(BrowserSync.EXTRA_URL) ?: return
            val fromDisplay = intent.getIntExtra(BrowserSync.EXTRA_DISPLAY, currentDisplayId())
            if (fromDisplay == currentDisplayId()) return
            applyRemoteUrl(url)
        }
    }

    private val mirrorListener: (MirrorController.State) -> Unit = { state ->
        when (state) {
            MirrorController.State.ACTIVE -> {
                mirrorRequested = !isPhoneDisplay()
                if (isPhoneDisplay()) {
                    Toast.makeText(this, R.string.mirror_look_at_car, Toast.LENGTH_LONG).show()
                }
            }
            MirrorController.State.IDLE -> mirrorRequested = false
            MirrorController.State.DENIED -> {
                if (isPhoneDisplay()) {
                    Toast.makeText(this, R.string.mirror_denied, Toast.LENGTH_LONG).show()
                    mirrorRequested = false
                }
            }
        }
        refreshMirrorUi()
        updateMirrorTile()
    }

    private val mirrorConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            ScreenMirrorService.start(this, result.resultCode, data)
        } else {
            MirrorController.onDenied()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            locationHelper.start()
        } else {
            val message = getString(R.string.location_permission_needed)
            statusLocation.text = message
            infoPlace.text = message
            infoCondition.setText(R.string.info_weather_unavailable)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupInfoDashboard()
        setupLocationStatus()
        setupLandingTiles()
        setupMirror()
        setupWebView()
        setupControls()
        updateResumeButton()
        updateClock()
        showStartPage()
        requestLocationIfNeeded()
        consumePendingSync()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        mirrorOverlay.isVisible -> stopMirror()
                        customView != null -> exitFullscreen()
                        startPageRoot.isVisible -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                        webView.canGoBack() -> webView.goBack()
                        else -> showStartPage()
                    }
                }
            },
        )
    }

    private fun bindViews() {
        startPageRoot = findViewById(R.id.startPageRoot)
        browserChrome = findViewById(R.id.browserChrome)
        controlBarHost = findViewById(R.id.controlBarHost)
        controlBar = findViewById(R.id.controlBar)
        btnRevealControls = findViewById(R.id.btnRevealControls)
        pageProgress = findViewById(R.id.pageProgress)
        pageTitle = findViewById(R.id.pageTitle)
        pageHost = findViewById(R.id.pageHost)
        secureIcon = findViewById(R.id.secureIcon)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnReload = findViewById(R.id.btnReload)
        btnHome = findViewById(R.id.btnHome)
        btnYoutube = findViewById(R.id.btnYoutube)
        btnChrome = findViewById(R.id.btnChrome)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        statusTime = findViewById(R.id.statusTime)
        statusDay = findViewById(R.id.statusDay)
        statusDate = findViewById(R.id.statusDate)
        statusLocation = findViewById(R.id.statusLocation)
        locationRow = findViewById(R.id.locationRow)
        tileChrome = findViewById(R.id.tileChrome)
        tileMirror = findViewById(R.id.tileMirror)
        tileMirrorLabel = findViewById(R.id.tileMirrorLabel)
        tileResume = findViewById(R.id.tileResume)
        mirrorOverlay = findViewById(R.id.mirrorOverlay)
        mirrorSurface = findViewById(R.id.mirrorSurface)
        mirrorStatus = findViewById(R.id.mirrorStatus)
        btnStopMirror = findViewById(R.id.btnStopMirror)
        infoGreeting = findViewById(R.id.infoGreeting)
        infoTemp = findViewById(R.id.infoTemp)
        infoCondition = findViewById(R.id.infoCondition)
        infoFeelsLike = findViewById(R.id.infoFeelsLike)
        infoWeatherMeta = findViewById(R.id.infoWeatherMeta)
        infoPlace = findViewById(R.id.infoPlace)
        infoBattery = findViewById(R.id.infoBattery)
        infoTip = findViewById(R.id.infoTip)
        infoNews1 = findViewById(R.id.infoNews1)
        infoNews2 = findViewById(R.id.infoNews2)
        infoNews3 = findViewById(R.id.infoNews3)
        infoWeatherAlert = findViewById(R.id.infoWeatherAlert)
        infoWeatherAlertText = findViewById(R.id.infoWeatherAlertText)
        infoWeatherAlertSecondary = findViewById(R.id.infoWeatherAlertSecondary)
    }

    private fun setupInfoDashboard() {
        infoCondition.setText(R.string.info_weather_loading)
        infoTemp.text = "—"
        infoFeelsLike.text = ""
        infoWeatherMeta.text = ""
        infoPlace.setText(R.string.location_loading)
        infoNews1.setText(R.string.info_news_loading)
        infoNews2.text = ""
        infoNews3.text = ""
        infoWeatherAlert.isVisible = false
        updateGreetingAndTip()
        updateBatteryInfo()
        loadHeadlines()
        loadWeatherAlerts()
        // Don't wait forever for GPS — IP location unlocks weather quickly.
        handler.postDelayed({ ensureLocationFallback() }, 2_500L)
    }

    /** If GPS is slow/denied, approximate with IP so weather still loads. */
    private fun ensureLocationFallback() {
        if (lastKnownLocation != null) return
        InfoDashboardLoader.loadIpLocation { result ->
            runOnUiThread {
                if (lastKnownLocation != null) return@runOnUiThread
                result.onSuccess { ip ->
                    val location = Location("ip").apply {
                        latitude = ip.latitude
                        longitude = ip.longitude
                    }
                    lastKnownLocation = location
                    lastLocationLabel = ip.label
                    if (statusLocation.text.isNullOrBlank() ||
                        statusLocation.text == getString(R.string.location_loading) ||
                        statusLocation.text == getString(R.string.location_permission_needed) ||
                        statusLocation.text == getString(R.string.location_disabled)
                    ) {
                        statusLocation.text = ip.label
                        infoPlace.text = ip.label
                    }
                    refreshWeather(location)
                }.onFailure {
                    if (infoCondition.text == getString(R.string.info_weather_loading)) {
                        infoCondition.setText(R.string.info_weather_unavailable)
                    }
                }
            }
        }
    }

    private fun setupLocationStatus() {
        locationHelper = LocationStatusHelper(this) { label, location ->
            statusLocation.text = label
            infoPlace.text = label
            lastLocationLabel = label
            lastKnownLocation = location
            location?.let { refreshWeather(it) }
        }
        locationRow.setOnClickListener {
            if (!locationHelper.hasPermission()) {
                requestLocationIfNeeded()
            } else {
                locationHelper.start()
            }
        }
    }

    private fun refreshWeather(location: Location) {
        InfoDashboardLoader.loadWeather(location) { result ->
            runOnUiThread {
                result.onSuccess { weather ->
                    infoTemp.text = getString(R.string.info_temp_format, weather.temperatureC)
                    infoCondition.text = weather.condition
                    infoFeelsLike.text =
                        getString(R.string.info_feels_like, weather.feelsLikeC)
                    infoWeatherMeta.text = getString(
                        R.string.info_weather_meta,
                        weather.humidityPercent,
                        weather.windKmh,
                    )
                    liveSevereWeatherText = weather.severeAlertText?.let {
                        getString(R.string.info_weather_alert_live, it)
                    }
                    renderWeatherAlert()
                }.onFailure {
                    infoCondition.setText(R.string.info_weather_unavailable)
                }
            }
        }
    }

    private fun loadHeadlines() {
        InfoDashboardLoader.loadHeadlines { result ->
            runOnUiThread {
                val views = listOf(infoNews1, infoNews2, infoNews3)
                result.onSuccess { headlines ->
                    views.forEachIndexed { index, view ->
                        val title = headlines.getOrNull(index)
                        view.text = if (title.isNullOrBlank()) {
                            ""
                        } else {
                            getString(R.string.info_news_item, title)
                        }
                    }
                    if (headlines.isEmpty()) {
                        infoNews1.setText(R.string.info_news_unavailable)
                    }
                }.onFailure {
                    infoNews1.setText(R.string.info_news_unavailable)
                    infoNews2.text = ""
                    infoNews3.text = ""
                }
            }
        }
    }

    private fun loadWeatherAlerts() {
        InfoDashboardLoader.loadWeatherAlerts { result ->
            runOnUiThread {
                latestWeatherNewsAlerts = result.getOrDefault(emptyList())
                renderWeatherAlert()
            }
        }
    }

    private fun renderWeatherAlert() {
        val newsPrimary = latestWeatherNewsAlerts.getOrNull(0)
        val newsSecondary = latestWeatherNewsAlerts.getOrNull(1)
        val live = liveSevereWeatherText

        when {
            newsPrimary != null -> {
                infoWeatherAlert.isVisible = true
                infoWeatherAlertText.text = newsPrimary
                if (newsSecondary != null) {
                    infoWeatherAlertSecondary.isVisible = true
                    infoWeatherAlertSecondary.text = newsSecondary
                } else if (live != null) {
                    infoWeatherAlertSecondary.isVisible = true
                    infoWeatherAlertSecondary.text = live
                } else {
                    infoWeatherAlertSecondary.isVisible = false
                }
            }
            live != null -> {
                infoWeatherAlert.isVisible = true
                infoWeatherAlertText.text = live
                infoWeatherAlertSecondary.isVisible = false
            }
            else -> {
                infoWeatherAlert.isVisible = false
            }
        }
    }

    private fun updateGreetingAndTip() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        infoGreeting.setText(
            when (hour) {
                in 5..11 -> R.string.greeting_morning
                in 12..16 -> R.string.greeting_afternoon
                in 17..21 -> R.string.greeting_evening
                else -> R.string.greeting_night
            },
        )
        val tips = resources.getStringArray(R.array.driving_tips)
        val index = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % tips.size
        infoTip.text = tips[index]
    }

    private fun updateBatteryInfo() {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            val percent = level * 100 / scale
            infoBattery.text = getString(R.string.info_battery_format, percent)
        } else {
            infoBattery.setText(R.string.info_battery_unknown)
        }
    }

    private fun requestLocationIfNeeded() {
        if (locationHelper.hasPermission()) {
            locationHelper.start()
            handler.postDelayed({ ensureLocationFallback() }, 2_500L)
            return
        }
        // Still load news/alerts; approximate location via IP for weather.
        ensureLocationFallback()
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    private fun updateClock() {
        val now = Date()
        statusTime.text = timeFormat.format(now)
        statusDay.text = dayFormat.format(now)
        statusDate.text = dateFormat.format(now)
        if (startPageRoot.isVisible) {
            updateGreetingAndTip()
            updateBatteryInfo()
        }
    }

    private fun setupLandingTiles() {
        findViewById<ImageButton>(R.id.btnHomeExit).setOnClickListener { finish() }
        tileChrome.setOnClickListener {
            pulse(tileChrome)
            open(BrowserDestination.CHROME)
        }
        tileMirror.setOnClickListener {
            pulse(tileMirror)
            onMirrorClicked()
        }
        tileResume.setOnClickListener {
            pulse(tileResume)
            val last = prefs.getString(KEY_LAST_URL, null)
            if (!last.isNullOrBlank()) openUrl(last)
        }
    }

    private fun setupWebView() {
        val webContainer = findViewById<FrameLayout>(R.id.webContainer)
        webView = BrowserWebViewFactory.create(
            context = this,
            callbacks = BrowserWebViewFactory.Callbacks(
                onProgress = { updateProgress(it) },
                onTitle = { title ->
                    if (!title.isNullOrBlank() && title != currentUrl) {
                        pageTitle.text = title
                    }
                },
                onUrl = { url ->
                    currentUrl = url.orEmpty()
                    if (url != null && BrowserSync.isSyncable(url)) {
                        currentDestination = destinationForUrl(url)
                        prefs.edit().putString(KEY_LAST_URL, url).apply()
                        BrowserSync.publish(this@MainActivity, url, currentDisplayId())
                        highlightActiveApp()
                    }
                    updateAddressBar(url)
                    updateNavButtons()
                },
                onPageStarted = {
                    isLoading = true
                    pageTitle.setText(R.string.page_loading)
                    updateReloadIcon()
                    scheduleHideControls()
                },
                onPageFinished = {
                    isLoading = false
                    updateReloadIcon()
                    updateNavButtons()
                    scheduleHideControls()
                },
                onReceivedError = {
                    isLoading = false
                    pageTitle.setText(R.string.page_error_title)
                    pageHost.setText(R.string.page_error_retry)
                    updateReloadIcon()
                },
                onEnterFullscreen = { view, callback -> enterFullscreen(view, callback) },
                onExitFullscreen = { exitFullscreen() },
            ),
        ).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN && !startPageRoot.isVisible) {
                    showBrowserChrome()
                    scheduleHideControls()
                }
                false
            }
        }
        webContainer.addView(webView)
    }

    private fun setupControls() {
        btnBack.setOnClickListener {
            if (webView.canGoBack()) webView.goBack() else showStartPage()
            scheduleHideControls()
        }
        btnForward.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
            scheduleHideControls()
        }
        btnReload.setOnClickListener {
            if (isLoading) webView.stopLoading() else webView.reload()
            scheduleHideControls()
        }
        btnHome.setOnClickListener { showStartPage() }
        btnYoutube.setOnClickListener { open(BrowserDestination.YOUTUBE) }
        btnChrome.setOnClickListener { open(BrowserDestination.CHROME) }
        btnRevealControls.setOnClickListener { showBrowserChrome(force = true) }
        findViewById<View>(R.id.addressField).setOnClickListener {
            showBrowserChrome(force = true)
            scheduleHideControls()
        }
    }

    private fun open(destination: BrowserDestination) {
        currentDestination = destination
        openUrl(destination.url)
    }

    private fun openUrl(url: String) {
        currentDestination = destinationForUrl(url)
        startPageRoot.isVisible = false
        showBrowserChrome(force = true)
        highlightActiveApp()
        webView.loadUrl(url)
        scheduleHideControls()
    }

    private fun destinationForUrl(url: String): BrowserDestination {
        val host = Uri.parse(url).host?.lowercase().orEmpty()
        return when {
            host.contains("youtube") || host.contains("youtu.be") -> BrowserDestination.YOUTUBE
            else -> BrowserDestination.CHROME
        }
    }

    private fun showStartPage() {
        exitFullscreen()
        handler.removeCallbacks(hideControlsRunnable)
        startPageRoot.isVisible = true
        browserChrome.isVisible = false
        controlBarHost.isVisible = false
        btnRevealControls.isVisible = false
        pageProgress.isVisible = false
        webView.stopLoading()
        webView.loadUrl("about:blank")
        updateResumeButton()
        updateClock()
        updateGreetingAndTip()
        updateBatteryInfo()
        loadHeadlines()
        loadWeatherAlerts()
    }

    private fun showBrowserChrome(force: Boolean = false) {
        if (startPageRoot.isVisible) return
        // GONE/VISIBLE in a vertical LinearLayout reserves space — bar never covers the page.
        browserChrome.isVisible = true
        controlBarHost.isVisible = true
        browserChrome.alpha = 1f
        controlBarHost.alpha = 1f
        btnRevealControls.isVisible = false
        updateNavButtons()
        highlightActiveApp()
    }

    private fun hideBrowserChrome() {
        if (startPageRoot.isVisible || customView != null || isLoading) return
        browserChrome.isVisible = false
        controlBarHost.isVisible = false
        btnRevealControls.isVisible = true
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS)
    }

    private fun updateProgress(progress: Int) {
        if (startPageRoot.isVisible) {
            pageProgress.isVisible = false
            return
        }
        if (progress in 1..99) {
            pageProgress.isVisible = true
            pageProgress.setProgressCompat(progress, true)
        } else {
            pageProgress.isVisible = false
        }
    }

    private fun updateAddressBar(url: String?) {
        if (url.isNullOrBlank() || url == "about:blank") {
            pageHost.text = ""
            secureIcon.isVisible = false
            return
        }
        val uri = Uri.parse(url)
        pageHost.text = uri.host ?: url
        secureIcon.isVisible = uri.scheme.equals("https", ignoreCase = true)
        if (pageTitle.text == getString(R.string.page_loading) || pageTitle.text.isNullOrBlank()) {
            pageTitle.text = uri.host ?: getString(R.string.app_name)
        }
    }

    private fun updateNavButtons() {
        btnBack.alpha = if (webView.canGoBack()) 1f else 0.35f
        btnForward.alpha = if (webView.canGoForward()) 1f else 0.35f
        btnForward.isEnabled = webView.canGoForward()
    }

    private fun updateReloadIcon() {
        btnReload.setImageResource(if (isLoading) R.drawable.ic_close else R.drawable.ic_refresh)
        btnReload.contentDescription = getString(
            if (isLoading) R.string.action_stop else R.string.action_reload,
        )
    }

    private fun highlightActiveApp() {
        styleAppButton(btnYoutube, currentDestination == BrowserDestination.YOUTUBE)
        styleAppButton(btnChrome, currentDestination == BrowserDestination.CHROME)
    }

    private fun styleAppButton(button: ImageButton, active: Boolean) {
        button.background = ContextCompat.getDrawable(
            this,
            if (active) R.drawable.bg_control_button_active else R.drawable.bg_control_button,
        )
        button.alpha = if (active) 1f else 0.75f
    }

    private fun updateResumeButton() {
        val last = prefs.getString(KEY_LAST_URL, null)
        tileResume.isVisible = !last.isNullOrBlank() && last != "about:blank"
    }

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        exitFullscreen()
        customView = view
        customViewCallback = callback
        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        fullscreenContainer.isVisible = true
        browserChrome.isVisible = false
        controlBarHost.isVisible = false
        btnRevealControls.isVisible = false
        webView.isVisible = false
        handler.removeCallbacks(hideControlsRunnable)
    }

    private fun exitFullscreen() {
        customView?.let { fullscreenContainer.removeView(it) }
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        fullscreenContainer.isVisible = false
        webView.isVisible = true
        if (!startPageRoot.isVisible) {
            showBrowserChrome(force = true)
            scheduleHideControls()
        }
    }

    private fun pulse(view: View) {
        view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        }.start()
    }

    private fun fadeIn(view: View) {
        view.alpha = 0f
        view.isVisible = true
        ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).setDuration(180).start()
    }

    private fun fadeOut(view: View, endAction: () -> Unit) {
        ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f).apply {
            duration = 180
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    endAction()
                    view.alpha = 1f
                }
            })
            start()
        }
    }

    private fun setupMirror() {
        mirrorSurface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                attachMirrorSurface(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                attachMirrorSurface(holder)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                MirrorController.clearConsumer(holder.surface)
            }
        })
        btnStopMirror.setOnClickListener { stopMirror() }
    }

    private fun attachMirrorSurface(holder: SurfaceHolder) {
        if (isPhoneDisplay() || !mirrorOverlay.isVisible) return
        val frame = holder.surfaceFrame
        if (frame.width() < 16 || frame.height() < 16) return
        MirrorController.setConsumer(
            holder.surface,
            frame.width(),
            frame.height(),
            resources.displayMetrics.densityDpi,
        )
    }

    private fun onMirrorClicked() {
        if (MirrorController.state == MirrorController.State.ACTIVE) {
            stopMirror()
            return
        }
        if (isPhoneDisplay()) {
            mirrorConsentLauncher.launch(ScreenMirrorService.createConsentIntent(this))
            return
        }
        mirrorRequested = true
        refreshMirrorUi()
        val options = ActivityOptions.makeBasic().apply {
            launchDisplayId = Display.DEFAULT_DISPLAY
        }
        try {
            startActivity(
                Intent(this, MirrorPermissionActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
                ),
                options.toBundle(),
            )
        } catch (error: RuntimeException) {
            mirrorStatus.setText(R.string.mirror_open_on_phone)
        }
    }

    private fun stopMirror() {
        mirrorRequested = false
        ScreenMirrorService.stop(this)
        refreshMirrorUi()
        updateMirrorTile()
    }

    private fun refreshMirrorUi() {
        val showOnCar = !isPhoneDisplay() &&
            (mirrorRequested || MirrorController.state == MirrorController.State.ACTIVE)
        mirrorOverlay.isVisible = showOnCar
        if (!showOnCar) {
            if (::webView.isInitialized &&
                lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
            ) {
                webView.onResume()
            }
            return
        }
        mirrorStatus.setText(
            when (MirrorController.state) {
                MirrorController.State.ACTIVE -> R.string.mirror_live
                MirrorController.State.DENIED -> R.string.mirror_denied
                MirrorController.State.IDLE -> R.string.mirror_waiting
            },
        )
        mirrorSurface.holder.let { holder ->
            if (holder.surface?.isValid == true) attachMirrorSurface(holder)
        }
        webView.onPause()
    }

    private fun updateMirrorTile() {
        tileMirrorLabel.setText(
            if (MirrorController.state == MirrorController.State.ACTIVE) {
                R.string.action_mirror_stop
            } else {
                R.string.action_mirror
            },
        )
    }

    private fun applyRemoteUrl(url: String) {
        if (!BrowserSync.isSyncable(url)) return
        if (mirrorOverlay.isVisible) return
        if (url == currentUrl && !startPageRoot.isVisible) return
        openUrl(url)
    }

    private fun consumePendingSync() {
        val url = BrowserSync.recentFromOtherDisplay(this, currentDisplayId()) ?: return
        applyRemoteUrl(url)
    }

    private fun currentDisplayId(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.displayId ?: Display.DEFAULT_DISPLAY
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.displayId
        }
    }

    private fun isPhoneDisplay(): Boolean = currentDisplayId() == Display.DEFAULT_DISPLAY

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            urlSyncReceiver,
            IntentFilter(BrowserSync.ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        MirrorController.addListener(mirrorListener)
        refreshMirrorUi()
        updateMirrorTile()
    }

    override fun onStop() {
        MirrorController.removeListener(mirrorListener)
        unregisterReceiver(urlSyncReceiver)
        super.onStop()
    }

    override fun onPause() {
        handler.removeCallbacks(clockRunnable)
        locationHelper.stop()
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        updateClock()
        handler.removeCallbacks(clockRunnable)
        handler.post(clockRunnable)
        if (locationHelper.hasPermission()) {
            locationHelper.start()
        }
        if (startPageRoot.isVisible) {
            updateBatteryInfo()
            loadHeadlines()
            loadWeatherAlerts()
        }
        refreshMirrorUi()
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.removeCallbacks(clockRunnable)
        locationHelper.destroy()
        if (::mirrorSurface.isInitialized) {
            MirrorController.clearConsumer(mirrorSurface.holder.surface)
        }
        exitFullscreen()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val PREFS = "browser_prefs"
        private const val KEY_LAST_URL = "last_url"
        private const val CONTROLS_HIDE_DELAY_MS = 4_500L
        private const val CLOCK_TICK_MS = 1_000L
    }
}
