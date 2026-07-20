package com.example.apextracker

import androidx.biometric.BiometricManager
import org.junit.Assert.assertEquals
import org.junit.Test

class SecuritySettingsTest {

    @Test
    fun `success maps to available`() {
        assertEquals(
            BiometricAvailability.AVAILABLE,
            biometricAvailabilityFrom(BiometricManager.BIOMETRIC_SUCCESS)
        )
    }

    @Test
    fun `no enrolled credential maps to none-enrolled so the toggle can be disabled`() {
        assertEquals(
            BiometricAvailability.NONE_ENROLLED,
            biometricAvailabilityFrom(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
        )
    }

    @Test
    fun `hardware and transient error codes fail safe to unsupported`() {
        // With DEVICE_CREDENTIAL in the authenticator set these are unusual, but any code we don't
        // explicitly handle must collapse to "can't lock" rather than silently offering a broken toggle.
        val otherCodes = listOf(
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN
        )
        otherCodes.forEach { code ->
            assertEquals(
                "code $code should be UNSUPPORTED",
                BiometricAvailability.UNSUPPORTED,
                biometricAvailabilityFrom(code)
            )
        }
    }
}
