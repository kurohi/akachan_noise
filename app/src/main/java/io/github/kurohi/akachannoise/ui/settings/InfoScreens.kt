package io.github.kurohi.akachannoise.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SocialDistance
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kurohi.akachannoise.BuildConfig
import io.github.kurohi.akachannoise.R
import io.github.kurohi.akachannoise.ui.components.IconLabelRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            content()
        }
    }
}

@Composable
fun SafetyScreen(onBack: () -> Unit) {
    InfoScaffold(title = stringResource(R.string.settings_safety), onBack = onBack) {
        Text(stringResource(R.string.safety_intro), style = MaterialTheme.typography.bodyLarge)
        IconLabelRow(
            icon = Icons.Filled.SocialDistance,
            title = stringResource(R.string.safety_distance_title),
            body = stringResource(R.string.safety_distance_body),
        )
        IconLabelRow(
            icon = Icons.Filled.Speed,
            title = stringResource(R.string.safety_volume_title),
            body = stringResource(R.string.safety_volume_body),
        )
        IconLabelRow(
            icon = Icons.Filled.Timer,
            title = stringResource(R.string.safety_timer_title),
            body = stringResource(R.string.safety_timer_body),
        )
        IconLabelRow(
            icon = Icons.Filled.Warning,
            title = stringResource(R.string.safety_limits_title),
            body = stringResource(R.string.safety_limits_body),
        )
        IconLabelRow(
            icon = Icons.Filled.BatteryAlert,
            title = stringResource(R.string.settings_reliability),
            body = stringResource(R.string.settings_reliability_body),
        )
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    InfoScaffold(title = stringResource(R.string.settings_privacy), onBack = onBack) {
        Text(stringResource(R.string.privacy_short), style = MaterialTheme.typography.bodyLarge)
        IconLabelRow(
            icon = Icons.Filled.Security,
            title = stringResource(R.string.privacy_no_internet_title),
            body = stringResource(R.string.privacy_no_internet_body),
        )
        IconLabelRow(
            icon = Icons.Filled.Lock,
            title = stringResource(R.string.privacy_local_title),
            body = stringResource(R.string.privacy_local_body),
        )
        IconLabelRow(
            icon = Icons.Filled.Mic,
            title = stringResource(R.string.privacy_mic_title),
            body = stringResource(R.string.privacy_mic_body),
        )
        IconLabelRow(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.privacy_permissions_title),
            body = stringResource(R.string.privacy_permissions_body),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onShowOnboarding: () -> Unit) {
    val context = LocalContext.current
    InfoScaffold(title = stringResource(R.string.settings_about), onBack = onBack) {
        IconLabelRow(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.app_name),
            body = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
        )
        IconLabelRow(
            icon = Icons.Filled.Gavel,
            title = stringResource(R.string.settings_license),
            body = stringResource(R.string.privacy_short),
        )
        Button(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(SOURCE_URL),
                )
                runCatching { context.startActivity(intent) }
            },
        ) {
            Text(stringResource(R.string.settings_source))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onShowOnboarding) {
            Text(stringResource(R.string.onboarding_next))
        }
    }
}

private const val SOURCE_URL = "https://github.com/kurohi/akachan-noise"
