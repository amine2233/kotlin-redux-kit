package io.github.amine2233.redux.sample.complex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.amine2233.redux.sample.medium.TodosAction
import io.github.amine2233.redux.sample.medium.TodosScreen
import io.github.amine2233.redux.sample.simple.CounterAction
import io.github.amine2233.redux.sample.simple.CounterScreen

/**
 * One screen, one store. Each card is a slice of [AppState]; the simple and medium screens are reused
 * unchanged — they only ever see their own state and dispatch their own actions.
 */
@Composable
fun CompositionRoute(viewModel: AppViewModel = viewModel { AppViewModel() }) {
    val state by viewModel.store.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.todos(TodosAction.LoadRequested) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Complex: one app store, six slices", style = MaterialTheme.typography.titleMedium)

        Section("lifted — counter (reuses the simple example)") {
            CounterScreen(state = state.counter, dispatch = viewModel::counter, modifier = Modifier.height(220.dp))
        }

        Section("lifted — todos + lifted middleware (reuses the medium example)") {
            TodosScreen(state = state.todos, dispatch = viewModel::todos, modifier = Modifier.height(360.dp))
        }

        Section("offset — List<CounterState>, addressed by index") {
            state.tabs.forEachIndexed { index, tab ->
                CounterRow(label = "tab[$index]", count = tab.count) { viewModel.tab(index, it) }
            }
            TextButton(onClick = { viewModel.store.dispatch(AppAction.TabAdded) }) { Text("Add tab") }
        }

        Section("keyed — Map<String, CounterState>, addressed by key") {
            state.named.forEach { (key, counter) ->
                CounterRow(label = key, count = counter.count) { viewModel.named(key, it) }
            }
            CounterRow(label = "unknown key (ignored)", count = 0) { viewModel.named("missing", it) }
        }

        Section("optional — ProfileState? loaded on sign-in") {
            val profile = state.profile
            if (profile == null) {
                Button(onClick = { viewModel.store.dispatch(AppAction.SignedIn("Amine")) }) { Text("Sign in") }
                Text("RefreshRequested is a no-op while profile is null", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { viewModel.profile(ProfileAction.RefreshRequested) }) { Text("Try refresh anyway") }
            } else {
                Text(profile.name, style = MaterialTheme.typography.titleSmall)
                Text(profile.email)
                if (profile.isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.profile(ProfileAction.RefreshRequested) }) { Text("Refresh") }
                    TextButton(onClick = { viewModel.store.dispatch(AppAction.SignedOut) }) { Text("Sign out") }
                }
            }
        }

        Section("undoable — Undoable<EditorState> with history") {
            val editor = state.editor
            Text("Committed: \"${editor.present.text}\"", style = MaterialTheme.typography.bodyLarge)
            Text("past=${editor.past.size} future=${editor.future.size}", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = state.editorDraft,
                onValueChange = { viewModel.store.dispatch(AppAction.EditorDraftChanged(it)) },
                label = { Text("Draft (not in history)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.editor(EditorAction.Committed(state.editorDraft)) }) { Text("Commit") }
                OutlinedButton(onClick = viewModel::undo, enabled = editor.canUndo) { Text("Undo") }
                OutlinedButton(onClick = viewModel::redo, enabled = editor.canRedo) { Text("Redo") }
                TextButton(onClick = { viewModel.editor(EditorAction.Cleared) }) { Text("Clear") }
            }
        }

        HorizontalDivider()
        OutlinedButton(onClick = { viewModel.store.dispatch(AppAction.ResetAll) }) { Text("Reset everything") }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun CounterRow(
    label: String,
    count: Int,
    dispatch: (CounterAction) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { dispatch(CounterAction.Decrement) }) { Text("−") }
        Button(onClick = { dispatch(CounterAction.Increment) }) { Text("+") }
    }
}

@Preview(showBackground = true)
@Composable
private fun CounterRowPreview() {
    CounterRow(label = "likes", count = 4, dispatch = {})
}
