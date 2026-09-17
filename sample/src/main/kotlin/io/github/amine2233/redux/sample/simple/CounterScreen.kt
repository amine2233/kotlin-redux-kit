package io.github.amine2233.redux.sample.simple

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.amine2233.redux.DefaultStore
import io.github.amine2233.redux.NoEffect

/** The ViewModel owns the store; `viewModelScope` cancels in-flight middlewares with the screen. */
class CounterViewModel : ViewModel() {
    val store = DefaultStore<CounterState, CounterAction, NoEffect>(CounterState(), counterReducer, scope = viewModelScope)
}

/** Route: store-aware. Screen: plain state + dispatch, previewable without a store. */
@Composable
fun CounterRoute(viewModel: CounterViewModel = viewModel()) {
    val state by viewModel.store.state.collectAsStateWithLifecycle()
    CounterScreen(state = state, dispatch = viewModel.store::dispatch)
}

@Composable
fun CounterScreen(
    state: CounterState,
    dispatch: (CounterAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Simple: one feature, one store", style = MaterialTheme.typography.titleMedium)
        Text("${state.count}", style = MaterialTheme.typography.displayLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { dispatch(CounterAction.Decrement) }) { Text("−") }
            Button(onClick = { dispatch(CounterAction.Increment) }) { Text("+") }
        }
        OutlinedButton(onClick = { dispatch(CounterAction.Reset) }) { Text("Reset") }
    }
}

@Preview(showBackground = true)
@Composable
private fun CounterScreenPreview() {
    CounterScreen(state = CounterState(count = 3), dispatch = {})
}
