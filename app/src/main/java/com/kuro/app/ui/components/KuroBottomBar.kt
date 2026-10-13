package com.kuro.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kuro.app.R
import com.kuro.app.ui.navigation.BottomDestination

@Composable
fun KuroBottomBar(
    current: BottomDestination,
    onSelect: (BottomDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = current == BottomDestination.Home,
            onClick = { onSelect(BottomDestination.Home) },
            icon = { Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.cd_nav_home)) },
            label = { Text(stringResource(R.string.nav_home)) }
        )
        NavigationBarItem(
            selected = current == BottomDestination.Library,
            onClick = { onSelect(BottomDestination.Library) },
            icon = { Icon(Icons.Filled.ViewModule, contentDescription = stringResource(R.string.cd_nav_library)) },
            label = { Text(stringResource(R.string.nav_library)) }
        )
        NavigationBarItem(
            selected = current == BottomDestination.Activity,
            onClick = { onSelect(BottomDestination.Activity) },
            icon = { Icon(Icons.Filled.Timeline, contentDescription = stringResource(R.string.cd_nav_activity)) },
            label = { Text(stringResource(R.string.nav_activity)) }
        )
        NavigationBarItem(
            selected = current == BottomDestination.Settings,
            onClick = { onSelect(BottomDestination.Settings) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_nav_settings)) },
            label = { Text(stringResource(R.string.nav_settings)) }
        )
    }
}
