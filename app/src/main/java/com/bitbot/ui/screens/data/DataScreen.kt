package com.bitbot.ui.screens.data

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bitbot.data.model.ConnectionState
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    onNavigateBack: () -> Unit,
    viewModel: DataViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    var userDisconnecting by remember { mutableStateOf(false) }

    if (connectionState !is ConnectionState.Connected && !userDisconnecting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Connection Lost", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.disconnect(); onNavigateBack() }) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { userDisconnecting = true; viewModel.disconnect(); onNavigateBack() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
            Spacer(Modifier.width(4.dp))
            Text("Connected", fontSize = 10.sp, color = Color(0xFF4CAF50))

            Spacer(Modifier.width(12.dp))

            // Kernel stats bar
            val kv = uiState.kernelValues
            val kh = uiState.kernelHeaders
            if (kh.isNotEmpty() && kv.size >= kh.size) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val seenLabels = mutableSetOf<String>()
                        kh.forEachIndexed { i, header ->
                            val label = shortLabel(header)
                            if (!seenLabels.add(label)) return@forEachIndexed
                            val value = kv[i]
                            val display = if (header.equals("state", ignoreCase = true) && value != 0.0) {
                                val stateName = uiState.stateNames[value.toInt()]
                                if (stateName != null) "$label=$stateName" else "$label=${fmtValue(value)}"
                            } else {
                                "$label=${fmtValue(value)}"
                            }
                            Text(
                                text = display,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = kernelColor(header)
                            )
                        }
                    }
                }
            }
        }

        // Tab row
        if (uiState.tabs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                uiState.tabs.forEach { tab ->
                    FilterChip(
                        selected = tab == uiState.selectedTab,
                        onClick = { viewModel.selectTab(tab) },
                        label = { Text(tab, fontSize = 10.sp, maxLines = 1) },
                        modifier = Modifier.height(32.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Data table
        if (uiState.selectedTab == "Extra") {
            ExtraTable(headers = uiState.extraHeaders, values = uiState.extraValues)
        } else {
            DeviceTable(rows = uiState.rows)
        }
    }
}

/** Width for the device name column (longest joint name ~18 chars at 9sp mono) */
private val NameColWidth = 110.dp

/** Width for value columns (formatted values ~8 chars at 9sp mono) */
private val ValColWidth = 64.dp

/** Width for short value columns (mode/state: 1-2 digits) */
private val ShortValColWidth = 28.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceTable(rows: List<DataRow>) {
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Waiting for data...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val colHeaders = rows.firstOrNull()?.headers ?: emptyList()
    val horizScroll = rememberScrollState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        contentPadding = PaddingValues(bottom = 56.dp)
    ) {
        stickyHeader(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizScroll)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Name", style = headerStyle, modifier = Modifier.width(NameColWidth))
                colHeaders.forEach { h ->
                    val w = if (isShortField(h)) ShortValColWidth else ValColWidth
                    Text(shortLabel(h), style = headerStyle, modifier = Modifier.width(w))
                }
            }
        }

        items(rows, key = { it.deviceName }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizScroll)
                    .padding(vertical = 1.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    row.deviceName,
                    style = cellStyle,
                    color = Color(0xFF80CBC4),
                    maxLines = 1,
                    modifier = Modifier.width(NameColWidth)
                )
                row.values.forEachIndexed { i, v ->
                    val header = if (i < row.headers.size) row.headers[i] else ""
                    val w = if (isShortField(header)) ShortValColWidth else ValColWidth
                    Text(formatValue(v, header), style = cellStyle, modifier = Modifier.width(w))
                }
            }
        }
    }
}

@Composable
private fun ExtraTable(headers: List<String>, values: List<Double>) {
    if (headers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No extra data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val horizScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 56.dp)
    ) {
        Column(modifier = Modifier.horizontalScroll(horizScroll)) {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                headers.forEach { h ->
                    Text(h, style = headerStyle, modifier = Modifier.width(ValColWidth))
                }
            }
            Row(
                modifier = Modifier.padding(vertical = 2.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                values.forEachIndexed { i, v ->
                    val header = if (i < headers.size) headers[i] else ""
                    Text(formatValue(v, header), style = cellStyle, modifier = Modifier.width(ValColWidth))
                }
            }
        }
    }
}

private val headerStyle = TextStyle(
    fontSize = 9.sp,
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    color = Color(0xFF90A4AE)
)

private val cellStyle = TextStyle(
    fontSize = 9.sp,
    fontFamily = FontFamily.Monospace,
    color = Color(0xFFCFD8DC)
)

private fun shortLabel(header: String): String = when {
    header.length <= 7 -> header
    header.contains("position", ignoreCase = true) -> header.take(3) + "Pos"
    header.contains("velocity", ignoreCase = true) -> header.take(3) + "Vel"
    header.contains("torque", ignoreCase = true) -> header.take(3) + "Trq"
    else -> header.take(6)
}

/** Fields that display short integer values (1-2 chars) — use narrower column. */
private fun isShortField(header: String): Boolean =
    header.contains("mode", ignoreCase = true) || header.equals("state", ignoreCase = true)

/** Format a value, using integer display for mode-like fields. */
private fun formatValue(v: Double, header: String): String {
    if (v == 0.0 && !header.contains("mode", ignoreCase = true)) return "0"
    if (header.contains("mode", ignoreCase = true) || header.equals("state", ignoreCase = true)) {
        return v.toInt().toString()
    }
    return fmtValue(v)
}

private fun fmtValue(v: Double): String {
    if (v == 0.0) return "0"
    val a = abs(v)
    if (a >= 10000.0) return "%+.3e".format(v)
    if (a < 0.0001) return "%+.3e".format(v)
    val s = "%+.4f".format(v)
    return s.trimEnd('0').trimEnd('.')
}

private fun kernelColor(header: String): Color = when {
    header.contains("cpu", ignoreCase = true) -> Color(0xFFFF9800)
    header.contains("period", ignoreCase = true) -> Color(0xFF4FC3F7)
    header.contains("state", ignoreCase = true) -> Color(0xFF81C784)
    header.contains("process", ignoreCase = true) -> Color(0xFF81C784)
    header.contains("kernel", ignoreCase = true) -> Color(0xFFCE93D8)
    else -> Color(0xFFB0BEC5)
}
