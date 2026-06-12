package io.github.s57.render

/*
 * Intentionally empty.
 *
 * The browser demo must use exactly one chart-rendering path: the upstream
 * S-52 WebGlS52Renderer.  Earlier incremental patches experimented with a
 * second WebGL vector-label overlay to work around browser font differences,
 * but that violated the one-renderer rule and could make the same object look
 * like it was drawn twice.  Keep this file as a tombstone so old incremental
 * worktrees overwrite the previous implementation cleanly.
 */
