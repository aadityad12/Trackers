package com.example.apextracker

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Opt-in biometric/device-credential lock for the Budget and Notes modules (Issue #45).
 *
 * **This is a convenience shield, not encryption.** Room stays plaintext; nothing here protects
 * data at rest. It only gates the in-app UI behind a fresh device unlock, so a shoulder-surfer
 * with the phone already in hand can't open Budget/Notes without re-authenticating. Anyone who can
 * unlock the device can also flip these toggles — that's inherent and acceptable for the goal.
 */

/** The authenticators we prompt with: any enrolled biometric, falling back to the device PIN/
 *  pattern/password. Using DEVICE_CREDENTIAL means it also works on phones with no biometric
 *  hardware, and canAuthenticate() only fails when the user has no screen lock at all. */
const val LOCK_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

enum class BiometricAvailability {
    /** A biometric or device credential is set up; locking can be enabled and prompts will work. */
    AVAILABLE,
    /** No screen lock configured at all — the toggle must stay disabled or the user locks themselves out. */
    NONE_ENROLLED,
    /** No/again-unavailable hardware and no credential path — locking can't be offered. */
    UNSUPPORTED
}

/**
 * Pure mapping from [BiometricManager.canAuthenticate] result codes to a 3-way state, so the UI
 * decision (offer the toggle / grey it out / hide) is testable without the framework. Codes other
 * than the two we act on collapse to UNSUPPORTED — with DEVICE_CREDENTIAL in the authenticator set
 * they're rare (transient sensor unavailability, unsupported OS), and treating them as "can't lock"
 * fails safe.
 */
fun biometricAvailabilityFrom(canAuthenticateResult: Int): BiometricAvailability = when (canAuthenticateResult) {
    BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
    else -> BiometricAvailability.UNSUPPORTED
}

/** Reads the current availability for [LOCK_AUTHENTICATORS] on this device. */
fun currentBiometricAvailability(context: Context): BiometricAvailability =
    biometricAvailabilityFrom(BiometricManager.from(context).canAuthenticate(LOCK_AUTHENTICATORS))

val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "security_settings")

class SecuritySettings(private val context: Context) {
    companion object {
        val BUDGET_LOCK = booleanPreferencesKey("budget_lock_enabled")
        val NOTES_LOCK = booleanPreferencesKey("notes_lock_enabled")
    }

    val budgetLockEnabled: Flow<Boolean> = context.securityDataStore.data.map { it[BUDGET_LOCK] ?: false }
    val notesLockEnabled: Flow<Boolean> = context.securityDataStore.data.map { it[NOTES_LOCK] ?: false }

    suspend fun setBudgetLock(enabled: Boolean) {
        context.securityDataStore.edit { it[BUDGET_LOCK] = enabled }
    }

    suspend fun setNotesLock(enabled: Boolean) {
        context.securityDataStore.edit { it[NOTES_LOCK] = enabled }
    }
}

/**
 * Which module routes have been unlocked in the current foreground session. Process-scoped and
 * Compose-observable; [lockAll] is called from `MainActivity.onStop` so backgrounding the app
 * re-locks everything (the "unlocked-until-backgrounded" policy — no re-prompt while navigating
 * within one foreground session).
 */
object UnlockSession {
    private val unlocked = mutableStateListOf<String>()

    fun isUnlocked(route: String): Boolean = route in unlocked

    fun markUnlocked(route: String) {
        if (route !in unlocked) unlocked.add(route)
    }

    fun lockAll() {
        unlocked.clear()
    }
}

/**
 * Launches the system biometric/credential prompt. [onSuccess]/[onFailure] are posted on the main
 * executor. A single wrong attempt is not terminal (the system keeps the prompt up and does not
 * call back here); only a real error/cancel resolves to [onFailure], so callers fail closed.
 */
fun promptUnlock(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onFailure: () -> Unit
) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onFailure()
        }
    }
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        // No negative-button text: DEVICE_CREDENTIAL supplies the "use PIN" path and disallows one.
        .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
        .build()
    prompt.authenticate(info)
}

/**
 * Wraps a module's [content], gating it behind an unlock when [lockEnabled] and the route hasn't
 * already been unlocked this session. On first entry it shows [LockOverlay] and fires the prompt;
 * success reveals the content for the rest of the session, cancel/error invokes [onCancelled]
 * (the caller pops back to the menu). The overlay's button re-fires the prompt if it was dismissed.
 */
@Composable
fun LockGate(
    route: String,
    lockEnabled: Boolean?,
    promptTitle: String,
    promptSubtitle: String,
    onCancelled: () -> Unit,
    content: @Composable () -> Unit
) {
    // null = the DataStore flag hasn't loaded yet. Render nothing rather than the content, so a
    // locked module can't flash its data for a frame before the flag resolves (fail closed).
    if (lockEnabled == null) return
    if (!lockEnabled || UnlockSession.isUnlocked(route)) {
        content()
        return
    }

    val activity = LocalActivity.current as? FragmentActivity
    var attempt by remember { mutableStateOf(0) }

    LockOverlay(onUnlockClick = { attempt++ })

    LaunchedEffect(route, attempt) {
        if (activity == null) {
            onCancelled()
            return@LaunchedEffect
        }
        promptUnlock(
            activity = activity,
            title = promptTitle,
            subtitle = promptSubtitle,
            onSuccess = { UnlockSession.markUnlocked(route) },
            onFailure = { onCancelled() }
        )
    }
}

/**
 * Settings-sheet row that toggles "require unlock to open" for one module. Greyed out with an
 * explanation when the device has no screen lock (enabling it then would be a guaranteed lockout).
 * Turning it ON requires one successful auth first (per Issue #45) — so someone can't silently
 * lock you out of your own data; turning it OFF is immediate.
 */
@Composable
fun ModuleLockSetting(
    checked: Boolean,
    titleRes: Int,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val available = remember { currentBiometricAvailability(context) } == BiometricAvailability.AVAILABLE
    val enabled = available && activity != null
    // Resolved here because stringResource() can't be called from the (non-composable) Switch callback.
    val confirmTitle = stringResource(R.string.security_confirm_title)
    val confirmSubtitle = stringResource(R.string.security_lock_subtitle)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (available) R.string.security_lock_subtitle else R.string.security_lock_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { wantOn ->
                if (!wantOn) {
                    onCheckedChange(false)
                } else activity?.let {
                    promptUnlock(
                        activity = it,
                        title = confirmTitle,
                        subtitle = confirmSubtitle,
                        onSuccess = { onCheckedChange(true) },
                        onFailure = { /* leave the toggle off */ }
                    )
                }
            }
        )
    }
}

/** Full-screen shield shown behind the system prompt while a module is locked. */
@Composable
fun LockOverlay(onUnlockClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.security_locked_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlockClick) {
            Text(stringResource(R.string.security_unlock_button))
        }
    }
}
