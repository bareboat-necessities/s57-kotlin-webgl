package io.github.s57.render

import kotlinx.browser.window

actual fun monotonicNowMs(): Double = window.performance.now()
