package com.example.apextracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.apextracker.ui.theme.ApexTheme
import com.example.apextracker.ui.theme.EmeraldMuted
import com.example.apextracker.ui.theme.MagmaPrimary
import com.example.apextracker.ui.theme.OceanPrimary
import com.example.apextracker.ui.theme.RoyalPrimary

/**
 * App-wide settings bottom sheet — account/sign-in, dark mode, theme accent, and currency. Extracted
 * from the old MainMenu (retired in the Phase 4 nav restructure) so it can be hosted from the
 * Dashboard's settings gear. The dashboard is the app's home now, so this is the single place these
 * global controls live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsSheet(
    onDismiss: () -> Unit,
    currentTheme: ApexTheme,
    isDarkMode: Boolean,
    onThemeChange: (ApexTheme) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    currencyCode: String,
    onCurrencyChange: (String) -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val user by authViewModel.user.collectAsState()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(R.string.menu_settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            // User Profile / Auth Section
            Text(
                stringResource(R.string.menu_account),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (user != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (user?.photoUrl != null) {
                            AsyncImage(
                                model = user?.photoUrl,
                                contentDescription = stringResource(R.string.cd_profile_picture),
                                modifier = Modifier.size(48.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (user?.displayName ?: "U").take(1).uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user?.displayName ?: stringResource(R.string.user_fallback), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(user?.email ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { authViewModel.signOut(context) }) {
                            Icon(Icons.Default.Logout, contentDescription = stringResource(R.string.cd_sign_out), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    Button(
                        onClick = { authViewModel.signInWithGoogle(context) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_in_google))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                stringResource(R.string.menu_appearance),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                onClick = { onDarkModeChange(!isDarkMode) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.menu_dark_mode), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = onDarkModeChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                stringResource(R.string.menu_color_accent),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ApexTheme.entries.forEach { theme ->
                    // The same tokens Theme.kt feeds into each scheme's `primary`, so the
                    // swatch can't drift from the theme it previews (Issue #66).
                    val themeColor = when (theme) {
                        ApexTheme.EMERALD -> EmeraldMuted
                        ApexTheme.OCEAN -> OceanPrimary
                        ApexTheme.MAGMA -> MagmaPrimary
                        ApexTheme.ROYAL -> RoyalPrimary
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(themeColor.copy(alpha = 0.1f))
                            .border(
                                width = 2.dp,
                                color = if (currentTheme == theme) themeColor else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onThemeChange(theme) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(themeColor)
                        ) {
                            if (currentTheme == theme) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp).align(Alignment.Center),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                stringResource(R.string.menu_currency),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            CurrencyDropdown(currencyCode = currencyCode, onCurrencySelected = onCurrencyChange)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
