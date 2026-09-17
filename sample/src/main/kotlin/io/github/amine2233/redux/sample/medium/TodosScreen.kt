package io.github.amine2233.redux.sample.medium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.amine2233.redux.DefaultStore
import io.github.amine2233.redux.NoEffect

class TodosViewModel(
    repository: TodoRepository,
) : ViewModel() {
    val store = DefaultStore<TodosState, TodosAction, NoEffect>(TodosState(), todosReducer, listOf(TodosMiddleware(repository)), emptyList(), viewModelScope)
}

@Composable
fun TodosRoute(viewModel: TodosViewModel = viewModel { TodosViewModel(FakeTodoRepository()) }) {
    val state by viewModel.store.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.store.dispatch(TodosAction.LoadRequested) }
    TodosScreen(state = state, dispatch = viewModel.store::dispatch)
}

@Composable
fun TodosScreen(
    state: TodosState,
    dispatch: (TodosAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Medium: middleware with a repository", style = MaterialTheme.typography.titleMedium)
        Text("${state.remaining} remaining", style = MaterialTheme.typography.bodyMedium)

        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

        state.error?.let { error ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { dispatch(TodosAction.LoadRequested) }) { Text("Retry") }
                TextButton(onClick = { dispatch(TodosAction.ErrorDismissed) }) { Text("Dismiss") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = { dispatch(TodosAction.DraftChanged(it)) },
                label = { Text("New todo") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { dispatch(TodosAction.AddRequested) }, enabled = state.draft.isNotBlank()) { Text("Add") }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.todos, key = Todo::id) { todo -> TodoRow(todo, dispatch) }
        }

        OutlinedButton(onClick = { dispatch(TodosAction.LoadRequested) }, enabled = !state.isLoading) { Text("Reload") }
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    dispatch: (TodosAction) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = todo.done, onCheckedChange = { dispatch(TodosAction.Toggled(todo.id)) })
        Text(
            todo.title,
            modifier = Modifier.weight(1f),
            textDecoration = if (todo.done) TextDecoration.LineThrough else null,
        )
        TextButton(onClick = { dispatch(TodosAction.Removed(todo.id)) }) { Text("Remove") }
    }
}

@Preview(showBackground = true)
@Composable
private fun TodosScreenPreview() {
    TodosScreen(
        state = TodosState(todos = listOf(Todo(1, "Write a reducer", done = true), Todo(2, "Lift it")), draft = "Ship"),
        dispatch = {},
    )
}
