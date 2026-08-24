package com.hereliesaz.ideaz.ai

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IdeToolsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var tools: IdeTools

    @Before
    fun setUp() {
        tools = IdeTools(tempFolder.root)
    }

    // --- readFile ---

    @Test
    fun `readFile returns file contents`() {
        tempFolder.newFile("index.html").writeText("<h1>Hello</h1>")
        val result = tools.readFile("index.html")
        assertEquals("<h1>Hello</h1>", result)
    }

    @Test
    fun `readFile returns error message for missing file`() {
        val result = tools.readFile("nonexistent.html")
        assertTrue("Expected error prefix, got: $result", result.startsWith("Error:"))
    }

    // --- writeFile ---

    @Test
    fun `writeFile creates file with content and returns OK`() {
        val result = tools.writeFile("app.js", "console.log('hi')")
        assertEquals("OK", result)
        assertEquals("console.log('hi')", File(tempFolder.root, "app.js").readText())
    }

    @Test
    fun `writeFile creates intermediate directories`() {
        val result = tools.writeFile("src/js/app.js", "const x = 1;")
        assertEquals("OK", result)
        assertTrue(File(tempFolder.root, "src/js/app.js").exists())
    }

    @Test
    fun `writeFile overwrites existing content`() {
        tempFolder.newFile("style.css").writeText("old")
        tools.writeFile("style.css", "new")
        assertEquals("new", File(tempFolder.root, "style.css").readText())
    }

    // --- listFiles ---

    @Test
    fun `listFiles lists directory entries`() {
        tempFolder.newFile("a.html")
        tempFolder.newFile("b.css")
        val result = tools.listFiles(".")
        assertTrue(result.contains("a.html"))
        assertTrue(result.contains("b.css"))
    }

    @Test
    fun `listFiles returns error for non-directory path`() {
        tempFolder.newFile("file.txt")
        val result = tools.listFiles("file.txt")
        assertTrue("Expected error prefix, got: $result", result.startsWith("Error:"))
    }

    // --- applyPatch ---

    @Test
    fun `applyPatch applies unified diff and returns OK`() {
        val file = tempFolder.newFile("style.css")
        file.writeText("body { color: red; }\n")
        val patch = "--- a/style.css\n+++ b/style.css\n@@ -1 +1 @@\n-body { color: red; }\n+body { color: blue; }\n"
        val result = tools.applyPatch(patch)
        assertEquals("OK", result)
        assertEquals("body { color: blue; }\n", File(tempFolder.root, "style.css").readText())
        assertFalse(".git should be cleaned up after patch", File(tempFolder.root, ".git").exists())
    }

    @Test
    fun `applyPatch returns error for malformed patch`() {
        val result = tools.applyPatch("this is not a valid patch")
        assertTrue("Expected error prefix, got: $result", result.startsWith("Error:"))
    }

    // --- edit review checkpoints ---

    @Test
    fun `reviewEdits lists changed files and rejects invalid JSON`() {
        tempFolder.newFile("manifest.json").writeText("{\"name\":\"before\"}")
        val checkpoint = tools.createEditCheckpoint("before AI edit")
        tools.captureEditTargets(checkpoint, listOf("manifest.json", "new.css"))

        tools.writeFile("manifest.json", "{broken")
        tools.writeFile("new.css", "body { color: blue; }")

        val review = tools.reviewEdits(checkpoint)
        assertEquals(listOf("manifest.json", "new.css"), review.changedFiles)
        assertEquals(listOf("manifest.json contains invalid JSON"), review.validationErrors)
        tools.discardEditCheckpoint(checkpoint)
    }

    @Test
    fun `restoreEditCheckpoint preserves pre-edit work and removes AI files`() {
        tempFolder.newFile("index.html").writeText("<h1>user draft</h1>")
        val checkpoint = tools.createEditCheckpoint("before AI edit")
        tools.captureEditTargets(checkpoint, listOf("index.html", "generated.js"))

        tools.writeFile("index.html", "<h1>AI rewrite</h1>")
        tools.writeFile("generated.js", "alert('generated')")
        tools.restoreEditCheckpoint(checkpoint)

        assertEquals("<h1>user draft</h1>", tempFolder.root.resolve("index.html").readText())
        assertFalse(tempFolder.root.resolve("generated.js").exists())
        assertFalse(File(checkpoint.snapshotPath).exists())
    }

    @Test
    fun `review fingerprint changes when reviewed content changes`() {
        tempFolder.newFile("index.html").writeText("before")
        val checkpoint = tools.createEditCheckpoint("before AI edit")
        tools.captureEditTargets(checkpoint, listOf("index.html"))
        tools.writeFile("index.html", "AI draft")
        val reviewed = tools.reviewEdits(checkpoint)

        tools.writeFile("index.html", "user changed after review")
        val drifted = tools.reviewEdits(checkpoint)

        assertEquals(reviewed.changedFiles, drifted.changedFiles)
        assertNotEquals(reviewed.contentFingerprint, drifted.contentFingerprint)
        tools.discardEditCheckpoint(checkpoint)
    }

    @Test
    fun `restore refuses to erase content changed after review`() {
        tempFolder.newFile("index.html").writeText("before")
        val checkpoint = tools.createEditCheckpoint("before AI edit")
        tools.captureEditTargets(checkpoint, listOf("index.html"))
        tools.writeFile("index.html", "AI draft")
        val reviewed = tools.reviewEdits(checkpoint)

        tools.writeFile("index.html", "user changed after review")
        assertThrows(IllegalStateException::class.java) {
            tools.restoreEditCheckpoint(checkpoint, reviewed.contentFingerprint)
        }
        assertEquals("user changed after review", tempFolder.root.resolve("index.html").readText())
        tools.discardEditCheckpoint(checkpoint)
    }

    @Test
    fun `captureToolEdit snapshots every apply patch target`() {
        tempFolder.newFile("a.css").writeText("a")
        val checkpoint = tools.createEditCheckpoint("before AI edit")
        tools.captureToolEdit(
            checkpoint,
            "apply_patch",
            mapOf(
                "patch" to "--- a/a.css\n+++ b/a.css\n@@ -1 +1 @@\n-a\n+b\n" +
                    "--- /dev/null\n+++ b/new.js\n@@ -0,0 +1 @@\n+new\n"
            ),
        )
        tools.writeFile("a.css", "b")
        tools.writeFile("new.js", "new")

        assertEquals(listOf("a.css", "new.js"), tools.reviewEdits(checkpoint).changedFiles)
        tools.discardEditCheckpoint(checkpoint)
    }

    @Test
    fun `captureToolEdit restores a file deleted by patch`() {
        tempFolder.newFile("obsolete.js").writeText("old")
        val checkpoint = tools.createEditCheckpoint("before AI edit")
        tools.captureToolEdit(
            checkpoint,
            "apply_patch",
            mapOf("patch" to "--- a/obsolete.js\n+++ /dev/null\n@@ -1 +0,0 @@\n-old\n"),
        )
        tempFolder.root.resolve("obsolete.js").delete()
        val reviewed = tools.reviewEdits(checkpoint)

        tools.restoreEditCheckpoint(checkpoint, reviewed.contentFingerprint)

        assertEquals("old", tempFolder.root.resolve("obsolete.js").readText())
    }

    @Test
    fun `captureToolEdit does not lose a deletion under a top-level b directory`() {
        // Regression for a chained removePrefix("a/").removePrefix("b/") bug:
        // a "---" line's own git-diff prefix is "a/", but once that's
        // stripped, a second unconditional removePrefix("b/") could still
        // match and over-strip if the real project path's own top-level
        // directory happened to be named "b" - turning "b/config.js" into
        // the nonexistent "config.js" and silently dropping the deletion
        // from review entirely.
        tempFolder.newFolder("b")
        tempFolder.newFile("b/config.js").writeText("old")
        val checkpoint = tools.createEditCheckpoint("before AI edit")
        tools.captureToolEdit(
            checkpoint,
            "apply_patch",
            mapOf("patch" to "--- a/b/config.js\n+++ /dev/null\n@@ -1 +0,0 @@\n-old\n"),
        )
        tempFolder.root.resolve("b/config.js").delete()

        assertEquals(listOf("b/config.js"), tools.reviewEdits(checkpoint).changedFiles)
        tools.discardEditCheckpoint(checkpoint)
    }

    @Test
    fun `captureToolEdit handles a top-level a directory on both diff lines`() {
        tempFolder.newFolder("a")
        tempFolder.newFile("a/style.css").writeText("old")
        val checkpoint = tools.createEditCheckpoint("before AI edit")
        tools.captureToolEdit(
            checkpoint,
            "apply_patch",
            mapOf("patch" to "--- a/a/style.css\n+++ b/a/style.css\n@@ -1 +1 @@\n-old\n+new\n"),
        )
        tools.writeFile("a/style.css", "new")

        assertEquals(listOf("a/style.css"), tools.reviewEdits(checkpoint).changedFiles)
        tools.discardEditCheckpoint(checkpoint)
    }

    @Test
    fun `startup reconciliation recovers a completed write for explicit review`() {
        tempFolder.newFile("index.html").writeText("before")
        val checkpoint = tools.createEditCheckpoint("before AI edit")
        tools.captureEditTargets(checkpoint, listOf("index.html"))
        tools.markEditMutationStarted(checkpoint)
        tools.writeFile("index.html", "interrupted AI edit")
        tools.updateEditCheckpointFingerprint(
            checkpoint,
            tools.reviewEdits(checkpoint).contentFingerprint,
        )
        tools.markEditAwaitingReview(checkpoint)

        val recovered = IdeTools(tempFolder.root).reconcileEditCheckpoints()
        assertEquals(1, recovered.size)
        assertTrue(recovered.single().rollbackAllowed)
        assertEquals("interrupted AI edit", tempFolder.root.resolve("index.html").readText())
        tools.restoreEditCheckpoint(checkpoint, recovered.single().contentFingerprint)
        assertEquals("before", tempFolder.root.resolve("index.html").readText())
        assertFalse(File(checkpoint.snapshotPath).exists())
    }

    @Test
    fun `startup reconciliation preserves an approved edit`() {
        tempFolder.newFile("index.html").writeText("before")
        val checkpoint = tools.createEditCheckpoint("before AI edit")
        tools.captureEditTargets(checkpoint, listOf("index.html"))
        tools.writeFile("index.html", "approved AI edit")
        tools.markEditCheckpointApproved(checkpoint)

        assertTrue(IdeTools(tempFolder.root).reconcileEditCheckpoints().isEmpty())
        assertEquals("approved AI edit", tempFolder.root.resolve("index.html").readText())
        tools.discardEditCheckpoint(checkpoint)
    }

    @Test
    fun `startup reconciliation never rolls back an ambiguous interrupted write`() {
        tempFolder.newFile("index.html").writeText("before")
        val checkpoint = tools.createEditCheckpoint("before AI edit")
        tools.captureEditTargets(checkpoint, listOf("index.html"))
        tools.updateEditCheckpointFingerprint(
            checkpoint,
            tools.reviewEdits(checkpoint).contentFingerprint,
        )
        tools.markEditMutationStarted(checkpoint)
        tools.writeFile("index.html", "uncertain bytes")

        val recovered = IdeTools(tempFolder.root).reconcileEditCheckpoints().single()

        assertFalse(recovered.rollbackAllowed)
        assertEquals("uncertain bytes", tempFolder.root.resolve("index.html").readText())
        tools.discardEditCheckpoint(checkpoint)
    }

    @Test
    fun `startup reconciliation retains validation errors for approval gate`() {
        tempFolder.newFile("manifest.json").writeText("{\"name\":\"before\"}")
        val checkpoint = tools.createEditCheckpoint("before AI edit")
        tools.captureEditTargets(checkpoint, listOf("manifest.json"))
        tools.markEditMutationStarted(checkpoint)
        tools.writeFile("manifest.json", "{broken")
        val fingerprint = tools.reviewEdits(checkpoint).contentFingerprint
        tools.updateEditCheckpointFingerprint(checkpoint, fingerprint)
        tools.markEditAwaitingReview(checkpoint)

        val recovered = IdeTools(tempFolder.root).reconcileEditCheckpoints().single()

        assertEquals(listOf("manifest.json contains invalid JSON"), recovered.validationErrors)
        tools.discardEditCheckpoint(checkpoint)
    }

    @Test
    fun `validation refresh never re-enables ambiguous rollback`() {
        tempFolder.newFile("manifest.json").writeText("{\"name\":\"before\"}")
        val checkpoint = tools.createEditCheckpoint("before AI edit")
        tools.captureEditTargets(checkpoint, listOf("manifest.json"))
        tools.markEditMutationStarted(checkpoint)
        tools.writeFile("manifest.json", "{broken")
        val ambiguous = tools.reviewEdits(checkpoint).copy(rollbackAllowed = false)

        tools.writeFile("manifest.json", "{\"name\":\"fixed\"}")
        val refreshed = ambiguous.refreshedFrom(tools.reviewEdits(checkpoint))

        assertFalse(refreshed.rollbackAllowed)
        assertTrue(refreshed.validationErrors.isEmpty())
        tools.discardEditCheckpoint(checkpoint)
    }
}
