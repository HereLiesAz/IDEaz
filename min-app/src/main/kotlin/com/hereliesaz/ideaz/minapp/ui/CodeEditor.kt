package com.hereliesaz.ideaz.minapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.Color
import java.util.regex.Pattern

@Composable
fun EnhancedCodeEditor(
    code: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember(code) {
        mutableStateOf(TextFieldValue(annotatedString = syntaxHighlight(code)))
    }

    OutlinedTextField(
        modifier = modifier.fillMaxSize(),
        value = textFieldValue,
        onValueChange = {
            val newText = it.text
            textFieldValue = it.copy(annotatedString = syntaxHighlight(newText))
            onValueChange(newText)
        },
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        )
    )
}

private fun syntaxHighlight(code: String): AnnotatedString {
    return buildAnnotatedString {
        append(code)

        val keywordPattern = Pattern.compile("\\b(package|import|class|fun|val|var|if|else|for|while|return|true|false)\\b")
        val annotationPattern = Pattern.compile("@\\w+")
        val commentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")

        val keywordMatcher = keywordPattern.matcher(code)
        while (keywordMatcher.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFFCC7832)),
                start = keywordMatcher.start(),
                end = keywordMatcher.end()
            )
        }

        val annotationMatcher = annotationPattern.matcher(code)
        while (annotationMatcher.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFFBBB529)),
                start = annotationMatcher.start(),
                end = annotationMatcher.end()
            )
        }

        val commentMatcher = commentPattern.matcher(code)
        while (commentMatcher.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFF808080)),
                start = commentMatcher.start(),
                end = commentMatcher.end()
            )
        }
    }
}
