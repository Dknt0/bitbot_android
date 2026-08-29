package com.bitbot.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bitbot.ui.components.PanelSwitcher
import com.bitbot.ui.components.PanelType
import com.bitbot.ui.screens.data.DataScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.bitbot.ui.screens.pilot.PilotScreen
import com.bitbot.ui.screens.pilot.PilotViewModel
import com.bitbot.ui.screens.plot.PlotScreen

@Composable
fun PanelHostScreen(
    onNavigateBack: () -> Unit,
    onNavigateToButtonEditor: () -> Unit,
    initialPanel: PanelType = PanelType.PILOT
) {
    var activePanel by remember { mutableStateOf(initialPanel) }

    // Same nav-entry-scoped instance PilotScreen uses. Deactivating the
    // velocity loop synchronously on the switch click — DisposableEffect's
    // onDispose can lag a frame or two behind on a busy main thread, which
    // showed up as a ~120 ms tail of zero-velocity frames after leaving Pilot.
    val pilotViewModel: PilotViewModel = hiltViewModel()

    Box(modifier = Modifier.fillMaxSize()) {
        when (activePanel) {
            PanelType.PILOT -> PilotScreen(
                onNavigateBack = onNavigateBack,
                onNavigateToButtonEditor = onNavigateToButtonEditor
            )
            PanelType.DATA -> DataScreen(onNavigateBack = onNavigateBack)
            PanelType.PLOT -> PlotScreen(onNavigateBack = onNavigateBack)
        }

        PanelSwitcher(
            activePanel = activePanel,
            onPanelSelected = { panel ->
                if (panel != PanelType.PILOT) pilotViewModel.setPanelActive(false)
                activePanel = panel
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 8.dp)
        )
    }
}
