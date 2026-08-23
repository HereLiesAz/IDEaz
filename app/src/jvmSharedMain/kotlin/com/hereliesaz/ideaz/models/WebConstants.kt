package com.hereliesaz.ideaz.models

const val ACTION_AI_LOG = "com.hereliesaz.ideaz.AI_LOG"
const val EXTRA_MESSAGE = "MESSAGE"

/**
 * Broadcast by [com.hereliesaz.ideaz.services.WasmCompilerService] after a
 * successful Kotlin/Wasm compilation of an Android project. The preview
 * WebView listens for it, clears its cache and re-mounts the new binary.
 */
const val ACTION_WASM_COMPILE_SUCCESS = "com.hereliesaz.ideaz.WASM_COMPILE_SUCCESS"

/** Absolute path of the `filesDir/www` directory holding the Wasm output. */
const val EXTRA_WWW_DIR = "WWW_DIR"
