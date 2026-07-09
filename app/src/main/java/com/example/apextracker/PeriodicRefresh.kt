package com.example.apextracker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Runs [action] immediately, then every [intervalMillis], until this scope is cancelled. */
fun CoroutineScope.launchPeriodic(intervalMillis: Long, action: suspend () -> Unit) {
    launch {
        while (true) {
            action()
            delay(intervalMillis)
        }
    }
}
