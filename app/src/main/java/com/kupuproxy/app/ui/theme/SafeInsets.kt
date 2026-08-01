package com.kupuproxy.app.ui.theme

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier

/** Keeps top bars, content and floating actions outside display cutouts and system gestures. */
fun Modifier.kupuSafeScreen(): Modifier = safeDrawingPadding()

/** Keeps persistent actions reachable above gesture navigation and the on-screen keyboard. */
fun Modifier.kupuBottomActions(): Modifier = navigationBarsPadding().imePadding()

/** Keeps editable dialogs usable on short screens while the IME is visible. */
fun Modifier.kupuImeAware(): Modifier = navigationBarsPadding().imePadding()
