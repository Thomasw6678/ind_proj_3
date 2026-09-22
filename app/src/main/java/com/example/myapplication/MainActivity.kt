package com.example.myapplication

import android.media.Image
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.unit.dp
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MyApplicationApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun MyApplicationApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    var clicked by rememberSaveable {mutableStateOf(false) }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it
                        clicked = false
                    }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            if (clicked){
                Selected(modifier = Modifier.padding(innerPadding), onBack = {clicked = false})
            }
            else {
                when (currentDestination) {
                    AppDestinations.HOME -> FrontIMAGE(
                        Modifier.padding(innerPadding),
                        onClick = { clicked = true }
                    )

                    AppDestinations.FAVORITES -> Greeting("A")
                    AppDestinations.PROFILE -> Profile()
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("LIke", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrontIMAGE(modifier: Modifier = Modifier, onClick: () -> Unit){
    Surface(color =  Color.Cyan) {
        Scaffold(topBar = { TopAppBar(title = { Text("azjsfo") }) }) { padding ->
            Image(
                painter = painterResource(R.drawable.search),
                contentDescription = "Message",
                modifier = modifier
                    .padding(padding)
                    .fillMaxSize()
                    .clickable { onClick() }
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Selected(modifier: Modifier = Modifier, onBack: () -> Unit){
    Column() {
        Button(onClick = onBack) {
            Text("Back")
        }
        Surface(color = Color.Cyan) {
            Scaffold(topBar = { TopAppBar(title = { Text("AAAAAAAAAAAA") }) }) { padding ->
                Text(
                    text = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                    modifier = modifier.padding(padding)
                )
            }
        }
    }
}
@Composable
fun Profile(modifier: Modifier = Modifier){
    Image(
        painter = painterResource(R.drawable.ic_account_box),
        contentDescription = "Message",
        modifier = modifier
    )
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Surface(color =  Color.Blue) {
        Text(
            text = "Hello $name!",
            modifier = modifier
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}