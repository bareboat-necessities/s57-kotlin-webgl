package io.github.s57.render

actual fun monotonicNowMs(): Double = System.nanoTime().toDouble() / 1_000_000.0
