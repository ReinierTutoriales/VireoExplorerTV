package com.reiniertutoriales.vireoexplorertv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*

@Composable
internal fun ExplorerNavigation(
    page: ExplorerPage,
    onNavigate: (ExplorerPage) -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val itemColors = NavigationDrawerItemDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = colors.onSurfaceVariant,
        selectedContainerColor = colors.primaryContainer,
        selectedContentColor = colors.onPrimaryContainer,
        focusedContainerColor = colors.inverseSurface,
        focusedContentColor = colors.inverseOnSurface,
        focusedSelectedContainerColor = colors.inverseSurface,
        focusedSelectedContentColor = colors.inverseOnSurface
    )
    ModalNavigationDrawer(
        drawerState = rememberDrawerState(DrawerValue.Closed),
        scrimBrush = SolidColor(colors.background.copy(alpha = 0.64f)),
        drawerContent = {
            Column(
                modifier = Modifier.fillMaxHeight()
                    .background(colors.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 24.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ExplorerPage.entries.forEach { destination ->
                    NavigationDrawerItem(
                        selected = page == destination,
                        enabled = enabled,
                        onClick = { onNavigate(destination) },
                        leadingContent = { ExplorerIcon(destination.icon) },
                        shape = NavigationDrawerItemDefaults.shape(shape = VireoPillShape),
                        colors = itemColors,
                        scale = NavigationDrawerItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f)
                    ) {
                        Text(stringResource(destination.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        content = content
    )
}
