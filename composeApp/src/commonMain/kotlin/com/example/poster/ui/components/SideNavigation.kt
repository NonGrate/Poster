package com.example.poster.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.poster.ui.platform.NavDestination
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.nav_collapse
import poster.composeapp.generated.resources.nav_expand

/**
 * The tabs down the left edge, for windows wide enough that a bottom bar would
 * be a long way from everything (tablet landscape, desktop, web). Collapsed it
 * is a Material rail of icons; expanded, a permanent drawer with icons and
 * titles. The toggle at the top switches and the choice is the caller's to
 * remember. Same [NavDestination] list and test tags as the bottom bar.
 */
@Composable
fun SideNavigation(
    destinations: List<NavDestination>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggle: @Composable () -> Unit = {
        IconButton(onClick = onToggleExpanded, modifier = Modifier.testTag("nav_rail_toggle")) {
            Icon(
                imageVector = if (expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                contentDescription = stringResource(if (expanded) Res.string.nav_collapse else Res.string.nav_expand),
            )
        }
    }
    if (expanded) {
        PermanentDrawerSheet(
            modifier = modifier.width(240.dp).testTag("nav_rail"),
            drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 8.dp)) { toggle() }
            destinations.forEach { destination ->
                val selected = destination.route == selectedRoute
                NavigationDrawerItem(
                    label = { Text(destination.label) },
                    icon = { Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null) },
                    selected = selected,
                    onClick = { onSelect(destination.route) },
                    // The same indigo the bottom bar's indicator wears, not Material's default.
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp).testTag(destination.testTag),
                )
            }
        }
    } else {
        NavigationRail(
            modifier = modifier.testTag("nav_rail"),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            header = { toggle() },
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            destinations.forEach { destination ->
                val selected = destination.route == selectedRoute
                NavigationRailItem(
                    selected = selected,
                    onClick = { onSelect(destination.route) },
                    icon = { Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = destination.label) },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.testTag(destination.testTag),
                )
            }
        }
    }
}
