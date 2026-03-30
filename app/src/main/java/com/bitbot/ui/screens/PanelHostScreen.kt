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
import com.bitbot.ui.screens.pilot.PilotScreen

@Composable
fun PanelHostScreen(
    onNavigateBack: () -> Unit,
    initialPanel: PanelType = PanelType.PILOT
) {
    var activePanel by remember { mutableStateOf(initialPanel) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (activePanel) {
            PanelType.PILOT -> PilotScreen(onNavigateBack = onNavigateBack)
            PanelType.DATA -> DataScreen(onNavigateBack = onNavigateBack)
        }

        PanelSwitcher(
            activePanel = activePanel,
            onPanelSelected = { activePanel = it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 8.dp)
        )
    }
}
