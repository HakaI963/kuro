package com.kuro.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.kuro.app.R
import com.kuro.app.ui.components.KuroBottomBar
import com.kuro.app.ui.components.KuroTopAppBar
import com.kuro.app.ui.theme.MotionTokens

@Composable
fun AppShell(
    current: BottomDestination,
    onSelect: (BottomDestination) -> Unit,
    onNewClone: () -> Unit,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val fabInteraction = remember { MutableInteractionSource() }
    val fabPressed by fabInteraction.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed && !reduceMotion) MotionTokens.PRESS_SCALE else 1f,
        animationSpec = if (reduceMotion) tween(MotionTokens.REDUCED_MS)
        else MotionTokens.interactiveSpring(),
        label = "fab-press"
    )
    Scaffold(
        bottomBar = { KuroBottomBar(current = current, onSelect = onSelect, reduceMotion = reduceMotion) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewClone,
                containerColor = MaterialTheme.colorScheme.primary,
                interactionSource = fabInteraction
            ) {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        (fadeIn(MotionTokens.dialogAlpha(reduceMotion)) +
                            scaleIn(MotionTokens.dialogScale(reduceMotion), initialScale = 0.8f))
                            .togetherWith(
                                fadeOut(MotionTokens.dialogAlpha(reduceMotion)) +
                                    scaleOut(MotionTokens.dialogScale(reduceMotion), targetScale = 0.8f)
                            )
                    },
                    label = "fab-icon"
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.cd_new_clone),
                        modifier = Modifier.graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                            rotationZ = if (it == BottomDestination.Home) 0f else 90f
                        }
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
                    BottomDestination.Home -> stringResource(R.string.app_name)
                    BottomDestination.Library -> stringResource(R.string.library_title)
                    BottomDestination.Activity -> stringResource(R.string.activity_title)
                    BottomDestination.Settings -> stringResource(R.string.settings_title)
                },
                subtitle = when (current) {
                    BottomDestination.Home -> stringResource(R.string.app_tagline)
                    BottomDestination.Library -> stringResource(R.string.library_subtitle)
                    BottomDestination.Activity -> stringResource(R.string.activity_subtitle)
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
fun AddCloneScaffold(onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_clone_title)) },
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
