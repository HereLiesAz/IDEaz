package com.hereliesaz.ideaz.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeployUrlTest {

    @Test
    fun githubAndPagesDevOverHttpsAreOfferedForOpen() {
        listOf(
            "https://github.com/HereLiesAz/app/actions/runs/1",
            "https://my-app.pages.dev",
            "https://abc123.my-app.pages.dev/index.html",
            "HTTPS://My-App.Pages.Dev/",
        ).forEach { assertTrue(it, isSafeDeployUrl(it)) }
    }

    @Test
    fun everythingElseIsNotOffered() {
        listOf(
            "http://github.com/o/r",
            "http://my-app.pages.dev",
            "https://evil.example/",
            "https://pages.dev/",
            "https://evilpages.dev/",
            "https://github.com.evil.example/",
            "https://github.com@evil.example/",
            "https://my-app.pages.dev.evil.example/",
            "intent://github.com/#Intent;scheme=https;end",
            "javascript:alert(1)",
            "file:///data/data/com.hereliesaz.ideaz/files/x",
            "content://x/y",
            "",
        ).forEach { assertFalse(it, isSafeDeployUrl(it)) }
    }
}
