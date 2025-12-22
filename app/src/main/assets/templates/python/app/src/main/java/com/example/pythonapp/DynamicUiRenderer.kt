package com.example.pythonapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.key
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun DynamicUiRenderer(
    component: JSONObject,
    onAction: (String) -> Unit
) {
    val type = component.optString("type")
    val props = component.optJSONObject("properties") ?: JSONObject()
    val children = props.optJSONArray("children") ?: JSONArray()

    when (type) {
        "Scaffold" -> {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    RenderChildren(children, onAction)
                }
            }
        }
        "Column" -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center, // Defaulting for demo
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RenderChildren(children, onAction)
            }
        }
        "Row" -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                RenderChildren(children, onAction)
            }
        }
        "Text" -> {
            val text = props.optString("text", "")
            val fontSize = props.optInt("fontSize", 14).sp
            val colorStr = props.optString("color", "#000000")
            // Simple color parsing, assuming #RRGGBB
            val color = try { Color(android.graphics.Color.parseColor(colorStr)) } catch(e:Exception) { Color.Black }

            Text(text = text, fontSize = fontSize, color = color)
        }
        "Button" -> {
            val text = props.optString("text", "Button")
            val action = props.optString("onClick", "")
            Button(onClick = { onAction(action) }) {
                Text(text)
            }
        }
        "TextField" -> {
            val value = props.optString("value", "")
            val onValueChangeAction = props.optString("onValueChange", "")
            // Note: TextField requires state hoisting in Compose.
            // A truly dynamic TextField needs the VM to handle the change loop.
            // For now, we render it as read-only or with a placeholder callback.
            TextField(
                value = value,
                onValueChange = {
                    // This is tricky in pure SDUI without a local state buffer.
                    // The 'onAction' usually sends a discrete event, not every char.
                    // We might need a Debounce or specific 'submit' logic.
                    // For the template, we'll ignore or log.
                },
                label = { Text("Input") }
            )
        }
        else -> {
            Text("Unknown component: $type", color = Color.Red)
        }
    }
}

@Composable
fun RenderChildren(children: JSONArray, onAction: (String) -> Unit) {
    for (i in 0 until children.length()) {
        key(i) {
            val child = children.getJSONObject(i)
            DynamicUiRenderer(child, onAction)
        }
    }
}
