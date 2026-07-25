package com.iris.wallet

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.android.play.core.review.ReviewManagerFactory
import com.iris.IrisNavGraph
import com.iris.base.legacy.Theme
import com.iris.base.time.TimeConverter
import com.iris.base.time.TimeProvider
import com.iris.design.api.IrisDesign
import com.iris.design.api.IrisUI
import com.iris.design.system.IrisMaterial3Theme
import com.iris.domain.RootScreen
import com.iris.home.customerjourney.CustomerJourneyCardsProvider
import com.iris.legacy.Constants
import com.iris.legacy.IrisWalletCtx
import com.iris.legacy.appDesign
import com.iris.legacy.utils.activityForResultLauncher
import com.iris.legacy.utils.simpleActivityForResultLauncher
import com.iris.navigation.Navigation
import com.iris.navigation.NavigationRoot
import com.iris.ui.R
import com.iris.ui.time.TimeFormatter
import com.iris.ui.time.impl.DateTimePicker
import com.iris.wallet.ui.applocked.AppLockedScreen
import com.iris.widget.balance.WalletBalanceWidgetReceiver
import com.iris.widget.transaction.AddTransactionWidget
import com.iris.widget.transaction.AddTransactionWidgetCompact
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
@Suppress("TooManyFunctions")
class RootActivity : AppCompatActivity(), RootScreen {
    @Inject
    lateinit var irisContext: IrisWalletCtx

    @Inject
    lateinit var navigation: Navigation

    @Inject
    lateinit var customerJourneyLogic: CustomerJourneyCardsProvider

    @Inject
    lateinit var timeConverter: TimeConverter

    @Inject
    lateinit var timeProvider: TimeProvider

    @Inject
    lateinit var timeFormatter: TimeFormatter

    @Inject
    lateinit var dateTimePicker: DateTimePicker

    private lateinit var createFileLauncher: ActivityResultLauncher<String>
    private lateinit var onFileCreated: (fileUri: Uri) -> Unit

    private lateinit var openFileLauncher: ActivityResultLauncher<Unit>
    private lateinit var onFileOpened: (fileUri: Uri) -> Unit

    private val viewModel: RootViewModel by viewModels()

    @OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setupApp()
        setContent {
            val viewModel: RootViewModel = viewModel()
            val isSystemInDarkTheme = isSystemInDarkTheme()

            LaunchedEffect(isSystemInDarkTheme) {
                viewModel.start(isSystemInDarkTheme, intent)
            }

            val appLocked by viewModel.appLocked.collectAsState()
            when (appLocked) {
                null -> { // display nothing
                }
                true -> {
                    IrisUI(
                        design = appDesign(irisContext),
                        timeConverter = timeConverter,
                        timeProvider = timeProvider,
                        timeFormatter = timeFormatter,
                    ) {
                        AppLockedScreen(
                            onShowOSBiometricsModal = {
                                authenticateWithOSBiometricsModal(
                                    biometricPromptCallback = viewModel.handleBiometricAuthResult()
                                )
                            },
                            onContinueWithoutAuthentication = {
                                viewModel.unlockApp()
                            }
                        )
                    }
                }

                false -> {
                    NavigationRoot(navigation = navigation) { screen ->
                        IrisUI(
                            design = appDesign(irisContext),
                            includeSurface = screen?.isLegacy ?: true,
                            timeConverter = timeConverter,
                            timeProvider = timeProvider,
                            timeFormatter = timeFormatter,
                        ) {
                            IrisNavGraph(screen)
                        }
                    }
                }
            }

            IrisMaterial3Theme(
                dark = isDarkThemeEnabled(
                    irisDesign = appDesign(irisContext),
                    systemDarkTheme = isSystemInDarkTheme
                ),
                isTrueBlack = appDesign(irisContext).context().theme == Theme.AMOLED_DARK
            ) {
                dateTimePicker.Content()
            }
        }
    }

    private fun setupApp() {
        setupActivityForResultLaunchers()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupDatePicker()
        setupTimePicker()
        AddTransactionWidget.updateBroadcast(this)
        AddTransactionWidgetCompact.updateBroadcast(this)
        WalletBalanceWidgetReceiver.updateBroadcast(this)
    }

    private companion object {
        private const val MILLISECONDS_IN_DAY = 24 * 60 * 60 * 1000
    }

    private fun setupDatePicker() {
        irisContext.onShowDatePicker = { minDate,
                                        maxDate,
                                        initialDate,
                                        onDatePicked ->
            val datePicker =
                MaterialDatePicker.Builder.datePicker()
                    .setSelection(
                        if (initialDate != null) {
                            initialDate.toEpochDay() * MILLISECONDS_IN_DAY
                        } else {
                            MaterialDatePicker.todayInUtcMilliseconds()
                        }
                    )
                    .build()
            datePicker.show(supportFragmentManager, "datePicker")
            datePicker.addOnPositiveButtonClickListener {
                onDatePicked(LocalDate.ofEpochDay(it / MILLISECONDS_IN_DAY))
            }

            if (minDate != null) {
                datePicker.addOnCancelListener {
                    onDatePicked(minDate)
                }
            }

            if (maxDate != null) {
                datePicker.addOnCancelListener {
                    onDatePicked(maxDate)
                }
            }

            if (initialDate != null) {
                datePicker.addOnCancelListener {
                    onDatePicked(initialDate)
                }
            }
        }
    }

    private fun setupTimePicker() {
        irisContext.onShowTimePicker = { initialTime,
                                        onTimePicked ->
            val nowLocal = initialTime ?: timeProvider.localTimeNow()
            val is24Hour = android.text.format.DateFormat.is24HourFormat(this)
            val timeFormat = if (is24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H

            val picker =
                MaterialTimePicker.Builder()
                    .setTimeFormat(timeFormat)
                    .setHour(nowLocal.hour)
                    .setMinute(nowLocal.minute)
                    .build()
            picker.show(supportFragmentManager, "timePicker")
            picker.addOnPositiveButtonClickListener {
                onTimePicked(
                    LocalTime.of(picker.hour, picker.minute).withSecond(0)
                )
            }
        }
    }

    private fun isDarkThemeEnabled(irisDesign: IrisDesign, systemDarkTheme: Boolean): Boolean {
        return when (irisDesign.context().theme) {
            Theme.LIGHT -> false
            Theme.DARK -> true
            Theme.AMOLED_DARK -> true
            else -> systemDarkTheme
        }
    }

    private fun setupActivityForResultLaunchers() {
        createFileLauncher()

        openFileLauncher()
    }

    private fun createFileLauncher() {
        createFileLauncher = activityForResultLauncher(
            createIntent = { _, fileName ->
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/csv"
                    putExtra(Intent.EXTRA_TITLE, fileName)

                    // Optionally, specify a URI for the directory that should be opened in
                    // the system file picker before your app creates the document.
                    putExtra(
                        DocumentsContract.EXTRA_INITIAL_URI,
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            .toURI()
                    )
                }
            }
        ) { _, intent ->
            intent?.data?.also {
                onFileCreated(it)
            }
        }

        irisContext.createNewFile = { fileName, onFileCreatedCallback ->
            onFileCreated = onFileCreatedCallback

            createFileLauncher.launch(fileName)
        }
    }

    private fun openFileLauncher() {
        openFileLauncher = simpleActivityForResultLauncher(
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
        ) { _, intent ->
            intent?.data?.also {
                onFileOpened(it)
            }
        }

        irisContext.openFile = { onFileOpenedCallback ->
            onFileOpened = onFileOpenedCallback

            openFileLauncher.launch(Unit)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (viewModel.isAppLockEnabled() && !hasFocus) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.isAppLockEnabled()) {
            viewModel.checkUserInactiveTimeStatus()
        }
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.isAppLockEnabled()) {
            viewModel.startUserInactiveTimeCounter()
        }
    }

    private fun authenticateWithOSBiometricsModal(
        biometricPromptCallback: BiometricPrompt.AuthenticationCallback
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            biometricPromptCallback
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(
                getString(R.string.authentication_required)
            )
            .setSubtitle(
                getString(R.string.authentication_required_description)
            )
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .setConfirmationRequired(false)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    override fun onBackPressed() {
        if (viewModel.isAppLocked()) {
            super.onBackPressed()
        } else {
            if (!navigation.onBackPressed()) {
                super.onBackPressed()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "PrintStackTrace")
    override fun openUrlInBrowser(url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW)
            browserIntent.data = Uri.parse(url)
            startActivity(browserIntent)
        } catch (e: Exception) {
            Timber.e(e, "Cannot open URL in browser, intent not supported.")
            Toast.makeText(
                this,
                "No browser app found. Visit manually: $url",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun shareIrisWallet() {
        val share = Intent.createChooser(
            Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, Constants.URL_IRIS_WALLET_REPO)
                type = "text/plain"
            },
            null
        )
        startActivity(share)
    }

    @Suppress("SwallowedException")
    override fun openGooglePlayAppPage(appId: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appId")))
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$appId")
                )
            )
        }
    }

    override fun shareCSVFile(fileUri: Uri) {
        val intent = Intent.createChooser(
            Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, fileUri)
                type = "text/csv"
            },
            null
        )
        startActivity(intent)
    }

    override fun shareZipFile(fileUri: Uri) {
        val intent = Intent.createChooser(
            Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, fileUri)
                type = "application/zip"
            },
            null
        )
        startActivity(intent)
    }

    override val isDebug: Boolean
        get() = BuildConfig.DEBUG
    override val buildVersionName: String
        get() = BuildConfig.VERSION_NAME
    override val buildVersionCode: Int
        get() = BuildConfig.VERSION_CODE

    override fun reviewIrisWallet(dismissReviewCard: Boolean) {
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // We got the ReviewInfo object
                val reviewInfo = task.result
                reviewInfo.let { review ->
                    val flow = manager.launchReviewFlow(this, review!!)
                    flow.addOnCompleteListener {
                        // The flow has finished. The API does not indicate whether the user
                        // reviewed or not, or even whether the review dialog was shown. Thus, no
                        // matter the result, we continue our app flow.
                        if (dismissReviewCard) {
                            customerJourneyLogic.dismissCard(CustomerJourneyCardsProvider.rateUsCard())
                        }

                        openGooglePlayAppPage(packageName)
                    }
                }
            } else {
                openGooglePlayAppPage(packageName)
            }
        }
    }

    override fun <T> pinWidget(widget: Class<T>) {
        val appWidgetManager: AppWidgetManager = this.getSystemService(AppWidgetManager::class.java)
        val addTransactionWidget = ComponentName(this, widget)
        appWidgetManager.requestPinAppWidget(addTransactionWidget, null, null)
    }
}
