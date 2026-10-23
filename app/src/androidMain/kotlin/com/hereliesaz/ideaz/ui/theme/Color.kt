package com.hereliesaz.ideaz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val Black = Color(0xFF000000)
val DarkGrey = Color(0xFF1C1C1E)
val MediumGrey = Color(0xFF3A3A3C)
val LightGrey = Color(0xFF8E8E93)
val White = Color(0xFFFFFFFF)
val OffWhite = Color(0xFFF2F2F7)

private val RailActiveOnDark = Color(0xFFFFC107)
private val RailActiveOnLight = Color(0xFF8A5A00)

private val SuccessOnDark = Color(0xFF81C784)
private val SuccessOnLight = Color(0xFF1B5E20)
private val SelectionOnDark = Color(0xFF69F0AE)
private val SelectionOnLight = Color(0xFF00695C)

/** True when the current Material theme draws on a dark surface (honours the in-app theme override). */
@Composable
@ReadOnlyComposable
fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** "Granted"/"done" green with enough contrast on the current theme's background. */
@Composable
@ReadOnlyComposable
fun successColor(): Color = if (isDarkSurface()) SuccessOnDark else SuccessOnLight

/** Outline and fill for the visual-select overlay, drawn over the dimmed preview. */
@Composable
@ReadOnlyComposable
fun selectionColor(): Color = if (isDarkSurface()) SelectionOnDark else SelectionOnLight

/** Base colour of nav rail items: readable on the current theme's background. */
@Composable
@ReadOnlyComposable
fun railItemColor(): Color = MaterialTheme.colorScheme.onBackground

/** The nav rail's active-item accent (amber), darkened on a light theme for contrast. */
@Composable
@ReadOnlyComposable
fun railActiveColor(): Color = if (isDarkSurface()) RailActiveOnDark else RailActiveOnLight

/** Translucent backdrop behind the nav rail's menus and help overlay. */
@Composable
@ReadOnlyComposable
fun railScrimColor(): Color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
