package com.kuro.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.kuro.app.R
import com.kuro.app.ui.components.KuroBottomBar
import com.kuro.app.ui.components.KuroTopAppBar

@Composable
fun AppShell(
    current: BottomDestination,
    onSelect: (BottomDestination) -> Unit,
    onClonesFab: () -> Unit,
    onTerminalFab: () -> Unit,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Step 4: contextual FAB — New Clone on Clones, Add distro on Terminal,
    // hidden on System/Settings to avoid clutter.
    val fabAction: (() -> Unit)? = when (current) {
        BottomDestination.Clones -> onClonesFab
        BottomDestination.Terminal -> onTerminalFab
        else -> null
    }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            // Step 3c: pill offset animation lives inside KuroBottomBar
            // (tween 200ms, graphicsLayer translation, centered). AppShell
            // only passes selection state.
            KuroBottomBar(current = current, onSelect = onSelect, reduceMotion = reduceMotion)
        },
        floatingActionButton = {
            if (fabAction != null) {
                FloatingActionButton(
                    onClick = fabAction,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = if (current == BottomDestination.Terminal)
                            stringResource(R.string.cd_add_distro)
                        else
                            stringResource(R.string.cd_new_clone)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            KuroTopAppBar(
                title = when (current) {
                    BottomDestination.System -> stringResource(R.string.system_title)
                    BottomDestination.Terminal -> stringResource(R.string.terminal_title)
                    BottomDestination.Clones -> stringResource(R.string.clones_title)
                    BottomDestination.Settings -> stringResource(R.string.settings_title)
                },
                subtitle = when (current) {
                    BottomDestination.System -> stringResource(R.string.system_subtitle)
                    BottomDestination.Terminal -> stringResource(R.string.terminal_subtitle)
                    BottomDestination.Clones -> stringResource(R.string.clones_subtitle)
                    BottomDestination.Settings -> stringResource(R.string.settings_subtitle)
                }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCloneScaffold(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
    title: String = ""
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (title.isNotBlank()) title
                        else stringResource(R.string.add_clone_title)
                    )
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.cd_back),
                            modifier = Modifier.graphicsLayer { rotationZ = 45f }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScaffold(onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) { content() }
    }
}
