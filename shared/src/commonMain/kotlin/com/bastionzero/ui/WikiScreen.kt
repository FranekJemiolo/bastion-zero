package com.bastionzero.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bastionzero.db.WikiArticleModel
import com.bastionzero.db.WikiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WikiScreen(wiki: WikiRepository, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WikiArticleModel>>(emptyList()) }
    var selected by remember { mutableStateOf<WikiArticleModel?>(null) }

    LaunchedEffect(query) {
        results = withContext(Dispatchers.Default) {
            if (query.isBlank()) wiki.all() else wiki.search(query)
        }
    }

    val open = selected
    if (open != null) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = { selected = null }) { Text("← Back") }
            Text(open.title, style = MaterialTheme.typography.headlineSmall)
            Text(open.category.uppercase(), color = BastionColors.DimRed)
            Text(open.body)
        }
        return
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search manuals") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BastionColors.Red,
                unfocusedBorderColor = BastionColors.Outline,
                focusedLabelColor = BastionColors.Red,
                unfocusedLabelColor = BastionColors.DimRed,
                cursorColor = BastionColors.Red,
                focusedTextColor = BastionColors.Red,
                unfocusedTextColor = BastionColors.Red,
            ),
        )
        if (results.isEmpty()) Text("No matches.", color = BastionColors.DimRed)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results, key = { it.id }) { a ->
                Column(Modifier.fillMaxWidth().clickable { selected = a }.padding(vertical = 6.dp)) {
                    Text(a.title, style = MaterialTheme.typography.titleMedium)
                    Text(a.category.uppercase(), color = BastionColors.DimRed,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
