package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class ProcessManifest(
    private val inputManifestPath: String,
    private val outputManifestPath: String,
    private val packageName: String?,
    private val minSdk: Int,
    private val targetSdk: Int
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        val inputFile = File(inputManifestPath)

        // Handle missing manifest
        if (!inputFile.exists()) {
            if (packageName != null) {
                callback?.onLog("ProcessManifest: Manifest missing. Generating stub.")
                generateStubManifest(outputManifestPath, packageName)
                return BuildResult(true, "Generated stub manifest")
            }
            return BuildResult(false, "Manifest not found at $inputManifestPath")
        }

        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            dbFactory.isNamespaceAware = true
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(inputFile)

            val root = doc.documentElement // <manifest>

            // 1. Ensure Namespace
            val androidNs = "http://schemas.android.com/apk/res/android"
            if (!root.hasAttribute("xmlns:android")) {
                root.setAttribute("xmlns:android", androidNs)
            }

            // 2. Inject Package
            if (packageName != null && (!root.hasAttribute("package") || root.getAttribute("package").isBlank())) {
                root.setAttribute("package", packageName)
                callback?.onLog("ProcessManifest: Injected package='$packageName'")
            }

            // 3. Inject uses-sdk
            val usesSdkList = root.getElementsByTagName("uses-sdk")
            val usesSdk: Element = if (usesSdkList.length > 0) {
                usesSdkList.item(0) as Element
            } else {
                val newUsesSdk = doc.createElement("uses-sdk")
                // Insert at beginning (after permissions usually, but standard is early)
                // Just append to root? XML order matters strictly? uses-sdk usually first or after perms.
                // Appending to root is fine for most parsers.
                root.insertBefore(newUsesSdk, root.firstChild)
                newUsesSdk
            }

            // Ensure min/target SDK
            if (!usesSdk.hasAttribute("android:minSdkVersion")) {
                usesSdk.setAttribute("android:minSdkVersion", minSdk.toString())
            }
            if (!usesSdk.hasAttribute("android:targetSdkVersion")) {
                usesSdk.setAttribute("android:targetSdkVersion", targetSdk.toString())
            }

            // 4. Inject Application Tag if missing
            val appList = root.getElementsByTagName("application")
            val app: Element
            if (appList.length == 0) {
                app = doc.createElement("application")
                root.appendChild(app)
            } else {
                app = appList.item(0) as Element
            }

            // Inject android:extractNativeLibs="true" for Python/Chaquopy (Required for loading .so directly)
            if (!app.hasAttribute("android:extractNativeLibs")) {
                app.setAttribute("android:extractNativeLibs", "true")
            }

            // Inject permissions (INTERNET for SDUI localhost, FOREGROUND_SERVICE for Python host)
            val permissions = listOf("android.permission.INTERNET", "android.permission.FOREGROUND_SERVICE")
            for (perm in permissions) {
                val existingPerms = root.getElementsByTagName("uses-permission")
                var exists = false
                for (i in 0 until existingPerms.length) {
                    val node = existingPerms.item(i) as Element
                    if (node.getAttribute("android:name") == perm) {
                        exists = true
                        break
                    }
                }
                if (!exists) {
                    val newPerm = doc.createElement("uses-permission")
                    newPerm.setAttribute("android:name", perm)
                    root.insertBefore(newPerm, root.firstChild)
                }
            }

            // 5. Fix android:exported
            val componentTags = listOf("activity", "service", "receiver", "provider", "activity-alias")
            for (tagName in componentTags) {
                val nodes = root.getElementsByTagName(tagName)
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as Element

                    if (!node.hasAttribute("android:exported")) {
                        // Check for intent-filter
                        val intentFilters = node.getElementsByTagName("intent-filter")
                        if (intentFilters.length > 0) {
                            node.setAttribute("android:exported", "true")
                            callback?.onLog("ProcessManifest: Injected android:exported=true for $tagName")
                        } else {
                             // Default to false for safety/correctness on API 31+
                            node.setAttribute("android:exported", "false")
                        }
                    }
                }
            }

            // Write output
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            val source = DOMSource(doc)
            val result = StreamResult(File(outputManifestPath))
            transformer.transform(source, result)

            return BuildResult(true, "Manifest processed successfully")

        } catch (e: Exception) {
            e.printStackTrace()
            return BuildResult(false, "ProcessManifest failed: ${e.message}")
        }
    }

    private fun generateStubManifest(path: String, pkg: String) {
        val content = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="$pkg">
                <uses-sdk android:minSdkVersion="$minSdk" android:targetSdkVersion="$targetSdk" />
                <application android:label="StubApp" />
            </manifest>
        """.trimIndent()
        File(path).writeText(content)
    }
}
