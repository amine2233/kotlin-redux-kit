package io.github.amine2233.redux.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.amine2233.redux.sample.complex.CompositionRoute
import io.github.amine2233.redux.sample.medium.TodosRoute
import io.github.amine2233.redux.sample.simple.CounterRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { SampleApp() } }
    }
}

private const val HOME = "home"
private const val SIMPLE = "simple"
private const val MEDIUM = "medium"
private const val COMPLEX = "complex"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val isHome = backStack?.destination?.route.let { it == null || it == HOME }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("kotlin-redux-kit") },
                navigationIcon = { if (!isHome) TextButton(onClick = navController::popBackStack) { Text("Back") } },
            )
        },
    ) { padding ->
        NavHost(navController, startDestination = HOME, modifier = Modifier.padding(padding)) {
            composable(HOME) { HomeScreen(onOpen = { navController.navigate(it) }) }
            composable(SIMPLE) { CounterRoute() }
            composable(MEDIUM) { TodosRoute() }
            composable(COMPLEX) { CompositionRoute() }
        }
    }
}

@Composable
private fun HomeScreen(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Three levels of the same library", style = MaterialTheme.typography.titleLarge)
        Button(onClick = { onOpen(SIMPLE) }, modifier = Modifier.fillMaxWidth()) { Text("Simple — Store + Reducer") }
        Button(onClick = { onOpen(MEDIUM) }, modifier = Modifier.fillMaxWidth()) { Text("Medium — Middleware + repository") }
        Button(onClick = { onOpen(COMPLEX) }, modifier = Modifier.fillMaxWidth()) { Text("Complex — Lens/Prism composition, undo") }
    }
}
