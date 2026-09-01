package com.noorconnect.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noorconnect.domain.model.ModerationSettings

/** Public entry point for :app — same pattern as the other feature Routes. */
@Composable
fun SettingsRoute() {
    val viewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScreen(
        settings = settings,
        onAllowUnverifiedChannelsChange = viewModel::setAllowUnverifiedChannels,
        onAllowGroupsChange = viewModel::setAllowGroups,
        onAddKeyword = viewModel::addBlockedKeyword,
        onRemoveKeyword = viewModel::removeBlockedKeyword,
    )
}

@Composable
private fun SettingsScreen(
    settings: ModerationSettings,
    onAllowUnverifiedChannelsChange: (Boolean) -> Unit,
    onAllowGroupsChange: (Boolean) -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("إعدادات الفلترة", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)

        SettingRow(
            title = "السماح بالقنوات غير الموثّقة",
            checked = settings.allowUnverifiedChannels,
            onCheckedChange = onAllowUnverifiedChannelsChange,
        )
        SettingRow(
            title = "السماح بالجروبات",
            checked = settings.allowGroups,
            onCheckedChange = onAllowGroupsChange,
        )

        Text("كلمات محظورة", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        KeywordEditor(keywords = settings.blockedKeywords, onAdd = onAddKeyword, onRemove = onRemoveKeyword)
    }
}

@Composable
private fun SettingRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class) // AssistChip isn't stable API yet in this Compose BOM version
private fun KeywordEditor(keywords: Set<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("كلمة...") },
        )
        Button(onClick = { onAdd(draft); draft = "" }, modifier = Modifier.padding(start = 8.dp)) {
            Text("إضافة")
        }
    }

    LazyRow(modifier = Modifier.padding(top = 8.dp)) {
        items(keywords.toList()) { keyword ->
            AssistChip(
                onClick = { onRemove(keyword) },
                label = { Text(keyword) },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}
