package com.translator.screen.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Secure
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import com.translator.screen.R
import com.translator.screen.service.TranslatorAccessibilityService
import com.translator.screen.util.LanguagePrefs
import com.translator.screen.util.TargetLanguages
import com.translator.screen.translate.ITranslationEngine
import com.translator.screen.translate.MlKitTranslationEngine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request notification permission (Android 13+) for the continuous-mode notification.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1,
            )
        }
        // Edge-to-edge is enforced on Android 15 (targetSdk 35); opt in explicitly so
        // the Scaffold's content padding accounts for the status-bar inset and the
        // TopAppBar doesn't slide under the status bar. (Fresh-eyes review P1-4.)
        enableEdgeToEdge()
        setContent {
            ScreenTranslatorTheme {
                ScreenTranslatorSetupScreen()
            }
        }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA4F5B6),
    onPrimaryContainer = Color(0xFF002110),
    secondary = Color(0xFF4F6354),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFDF8),
    surface = Color(0xFFFBFDF8),
    surfaceVariant = Color(0xFFDDE5DB),
    onSurfaceVariant = Color(0xFF414941)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DD09C),
    onPrimary = Color(0xFF00391E),
    primaryContainer = Color(0xFF00522C),
    onPrimaryContainer = Color(0xFFA4F5B6),
    secondary = Color(0xFFB7CCB6),
    onSecondary = Color(0xFF223527),
    background = Color(0xFF101511),
    surface = Color(0xFF101511),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF)
)

@Composable
private fun ScreenTranslatorTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenTranslatorSetupScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var sourceTag by remember { mutableStateOf(LanguagePrefs.getSourceTag(context)) }
    var targetTag by remember { mutableStateOf(LanguagePrefs.getTargetTag(context)) }
    var engine by remember { mutableStateOf<ITranslationEngine>(MlKitTranslationEngine(sourceTag, targetTag)) }
    DisposableEffect(Unit) { onDispose { engine.close() } }

    var serviceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var modelDownloaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    val sampleGermanText = stringResource(R.string.sample_german_text)
    var testInput by remember { mutableStateOf(sampleGermanText) }
    var testOutput by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }
    var showRestrictedHelp by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(!LanguagePrefs.isOnboardingCompleted(context)) }
    var updateAvailable by remember { mutableStateOf<String?>(null) }

    /**
     * Live-switch the target language: persist to SharedPreferences, recreate the UI's
     * test engine, reset the model-status + test output, and tell the accessibility
     * service (if connected) to recreate its engine too — so the Quick Settings tile
     * picks up the new target without re-enabling accessibility.
     */
    fun onTargetChange(newTag: String) {
        if (newTag == targetTag) return
        LanguagePrefs.setTargetTag(context, newTag)
        engine.close()
        engine = MlKitTranslationEngine(sourceTag, newTag)
        targetTag = newTag
        modelDownloaded = false
        testOutput = ""
        TranslatorAccessibilityService.instance?.updateTargetLanguage(newTag)
    }

    fun onSourceChange(newTag: String) {
        if (newTag == sourceTag) return
        LanguagePrefs.setSourceTag(context, newTag)
        engine.close()
        engine = MlKitTranslationEngine(newTag, targetTag)
        sourceTag = newTag
        modelDownloaded = false
        testOutput = ""
        TranslatorAccessibilityService.instance?.updateSourceLanguage(newTag)
    }

    fun onSwapLanguages() {
        if (sourceTag == "auto") return
        val newSource = targetTag
        val newTarget = sourceTag
        LanguagePrefs.setSourceTag(context, newSource)
        LanguagePrefs.setTargetTag(context, newTarget)
        engine.close()
        engine = MlKitTranslationEngine(newSource, newTarget)
        sourceTag = newSource
        targetTag = newTarget
        modelDownloaded = false
        testOutput = ""
        TranslatorAccessibilityService.instance?.updateSourceLanguage(newSource)
        TranslatorAccessibilityService.instance?.updateTargetLanguage(newTarget)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = isAccessibilityServiceEnabled(context)
                // Check for app updates (GitHub Releases API — best-effort, silent on failure).
                scope.launch {
                    try {
                        val latestTag = withContext(Dispatchers.IO) {
                            val conn = (java.net.URL("https://api.github.com/repos/AutomatedLabor/translation-app/releases/latest")
                                .openConnection() as java.net.HttpURLConnection).apply {
                                requestMethod = "GET"
                                setRequestProperty("Accept", "application/vnd.github+json")
                                connectTimeout = 5000
                                readTimeout = 5000
                            }
                            if (conn.responseCode == 200) {
                                org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                                    .optString("tag_name", "")
                            } else ""
                        }
                        val currentTag = "v${com.translator.screen.BuildConfig.VERSION_NAME}"
                        if (latestTag.isNotEmpty() && latestTag != currentTag) {
                            updateAvailable = latestTag
                        }
                    } catch (e: Exception) {
                        // Silent — update check is best-effort.
                    }
                }
                // Re-query model status so users who already downloaded the model see
                // "downloaded" on return, not the initial false. (Fresh-eyes review P1-5.)
                scope.launch {
                    val ready = try {
                        engine.isModelDownloaded()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        false
                    }
                    modelDownloaded = ready
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = {
                LanguagePrefs.setOnboardingCompleted(context)
                showOnboarding = false
            },
            title = { Text("Welcome to Screen Translator") },
            text = {
                Column {
                    Text("This app translates on-screen text from any language to any language — continuously, live, from a Quick Settings tile.\n\nSetup:")
                    Text("\n1. Enable the Accessibility Service (below)")
                    Text("2. Choose your Source + Target languages")
                    Text("3. Download the translation model")
                    Text("4. Add the 'Translate Screen' tile to Quick Settings")
                    Text("5. Open any app + tap the tile")
                    Text("\nOn Android 7-10, manually swipe the shade away after tapping the tile.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    LanguagePrefs.setOnboardingCompleted(context)
                    showOnboarding = false
                }) { Text("Get started") }
            }
        )
    }

    if (updateAvailable != null) {
        AlertDialog(
            onDismissRequest = { updateAvailable = null },
            title = { Text("Update available") },
            text = { Text("A new version ($updateAvailable) is available. Download it from GitHub Releases.") },
            confirmButton = {
                Button(onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/AutomatedLabor/translation-app/releases/latest"))
                    context.startActivity(intent)
                    updateAvailable = null
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { updateAvailable = null }) { Text("Later") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.main_activity_title)) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.card_accessibility_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            StatusChip(enabled = serviceEnabled)
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                ContextCompat.startActivity(context, intent, null)
                            },
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(stringResource(R.string.card_accessibility_open_settings))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRestrictedHelp = !showRestrictedHelp }
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.card_accessibility_help_header),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Icon(
                                imageVector = if (showRestrictedHelp) {
                                    Icons.Filled.ExpandLess
                                } else {
                                    Icons.Filled.ExpandMore
                                },
                                contentDescription = null
                            )
                        }
                        if (showRestrictedHelp) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.card_accessibility_help_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.card_target_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.card_source_body),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        OutlinedButton(
                            onClick = { showSourceDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text("From: " + TargetLanguages.displayFor(sourceTag))
                        }
                        OutlinedButton(
                            onClick = { onSwapLanguages() },
                            enabled = sourceTag != "auto",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text("⇅ Swap")
                        }
                        OutlinedButton(
                            onClick = { showTargetDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text("To: " + TargetLanguages.displayFor(targetTag))
                        }
                        if (showSourceDialog) {
                            AlertDialog(
                                onDismissRequest = { showSourceDialog = false },
                                title = { Text(stringResource(R.string.card_source_title)) },
                                text = {
                                    Column {
                                        TargetLanguages.ALL_SOURCE.forEach { entry ->
                                            TextButton(
                                                onClick = {
                                                    onSourceChange(entry.tag)
                                                    showSourceDialog = false
                                                }
                                            ) {
                                                Text(entry.display)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {}
                            )
                        }
                        if (showTargetDialog) {
                            AlertDialog(
                                onDismissRequest = { showTargetDialog = false },
                                title = { Text(stringResource(R.string.card_target_title)) },
                                text = {
                                    Column {
                                        TargetLanguages.ALL.forEach { entry ->
                                            TextButton(
                                                onClick = {
                                                    onTargetChange(entry.tag)
                                                    showTargetDialog = false
                                                }
                                            ) {
                                                Text(entry.display)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {}
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.card_model_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (modelDownloaded) {
                                    stringResource(R.string.card_model_status_downloaded)
                                } else {
                                    stringResource(R.string.card_model_status_not_downloaded)
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isDownloading = true
                                    downloadProgress = 0f
                                    try {
                                        engine.downloadModelIfNeeded()
                                        downloadProgress = 1f
                                        modelDownloaded = true
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.model_download_failed),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } finally {
                                        isDownloading = false
                                    }
                                }
                            },
                            enabled = !isDownloading && !modelDownloaded,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(stringResource(R.string.card_model_download))
                        }
                        OutlinedTextField(
                            value = testInput,
                            onValueChange = { testInput = it },
                            label = { Text("German text") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isTranslating = true
                                    testOutput = try {
                                        engine.translate(testInput)
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        "Error: ${e.message}"
                                    }
                                    isTranslating = false
                                }
                            },
                            enabled = !isTranslating && modelDownloaded,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(stringResource(R.string.card_model_test))
                        }
                        Text(
                            text = if (testOutput.isBlank()) {
                                stringResource(R.string.translation_test_placeholder)
                            } else {
                                testOutput
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.card_tile_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.card_tile_body),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(enabled: Boolean) {
    val background = if (enabled) Color(0xFF1B6B3A) else Color(0xFFB3261E)
    val label = if (enabled) {
        stringResource(R.string.card_accessibility_status_on)
    } else {
        stringResource(R.string.card_accessibility_status_off)
    }
    Surface(
        color = background,
        contentColor = Color.White,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    if (enabledServices.isBlank()) return false
    val target = ComponentName(
        context,
        TranslatorAccessibilityService::class.java
    ).flattenToString()
    return enabledServices
        .split(':', ';', ',')
        .any { it.trim() == target }
}
