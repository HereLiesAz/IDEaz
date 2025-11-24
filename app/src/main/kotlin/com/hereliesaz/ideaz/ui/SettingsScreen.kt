package com.hereliesaz.ideaz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hereliesaz.ideaz.R

@Composable
fun SettingsScreen() {
    Column {
        Text(text = stringResource(id = R.string.settings_title))
        Text(text = stringResource(id = R.string.backup_and_restore))
    }
}
