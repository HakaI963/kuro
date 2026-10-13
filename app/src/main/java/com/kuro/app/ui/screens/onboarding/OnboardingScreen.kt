package com.kuro.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.ui.components.KuroPrimaryButton
import com.kuro.app.ui.components.KuroSecondaryButton

private data class Page(val titleRes: Int, val bodyRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    vm: OnboardingViewModel = viewModel()
) {
    val pages = listOf(
        Page(R.string.onboarding_page1_title, R.string.onboarding_page1_body, Icons.Filled.Layers),
        Page(R.string.onboarding_page2_title, R.string.onboarding_page2_body, Icons.Filled.ManageAccounts),
        Page(R.string.onboarding_page3_title, R.string.onboarding_page3_body, Icons.Filled.RocketLaunch)
    )
    var index by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                vm.completeOnboarding()
                onFinish()
            }) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (slideInHorizontally { it / 2 } + fadeIn()).togetherWith(
                    slideOutHorizontally { -it / 2 } + fadeOut()
                )
            },
            label = "onboarding-pager",
            modifier = Modifier.weight(1f)
        ) { page ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = pages[page].icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(pages[page].titleRes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(pages[page].bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.onboarding_note),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f)) {
                pages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(width = if (i == index) 24.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (index < 2) {
                Box(modifier = Modifier.width(160.dp)) {
                    KuroSecondaryButton(
                        label = stringResource(R.string.onboarding_next),
                        onClick = { index++ }
                    )
                }
            } else {
                Box(modifier = Modifier.width(200.dp)) {
                    KuroPrimaryButton(
                        label = stringResource(R.string.onboarding_get_started),
                        onClick = {
                            vm.completeOnboarding()
                            onFinish()
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
