package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Issue #105 — the exported Activity's navigate_to extra must never reach NavController raw. */
class NavRoutesTest {

    @Test
    fun `known routes pass through`() {
        APP_ROUTES.forEach { route ->
            assertEquals(route, sanitizeRequestedRoute(route))
        }
    }

    @Test
    fun `unknown route is dropped`() {
        assertNull(sanitizeRequestedRoute("garbage"))
        assertNull(sanitizeRequestedRoute(""))
        assertNull(sanitizeRequestedRoute("dashboard/../notes"))
        assertNull(sanitizeRequestedRoute("Dashboard"))
    }

    @Test
    fun `null stays null`() {
        assertNull(sanitizeRequestedRoute(null))
    }
}
