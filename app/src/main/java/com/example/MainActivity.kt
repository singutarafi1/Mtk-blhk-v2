package com.example

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.model.AppNavDestination
import com.example.model.MainPlatformCategory
import com.example.protocol.TargetPhoneState
import com.example.ui.components.AiDiagnosticDialog
import com.example.ui.screens.UnlockToolFlashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MtkBridgeViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MtkBridgeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleUsbDeviceIntent(intent)
        setContent {
            MyApplicationTheme {
                MtkMainApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbDeviceIntent(intent)
    }

    private fun handleUsbDeviceIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            if (device != null) {
                if (viewModel.targetPhoneUsb.strictBromOnlyMode && !viewModel.targetPhoneUsb.isBromDevice(device)) {
                    // Strict BROM listener active: ignore non-BROM device attach
                    return
                }
                lifecycleScope.launch {
                    if (viewModel.targetPhoneUsb.usbManager.hasPermission(device)) {
                        viewModel.targetPhoneUsb.connectDevice(device)
                    } else {
                        viewModel.targetPhoneUsb.requestDevicePermission(device)
                    }
                }
            }
        }
    }
}

@Composable
fun MtkMainApp(
    viewModel: MtkBridgeViewModel,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentDestination by viewModel.currentDestination.collectAsState()

    val aiAnalysis by viewModel.aiAnalysis.collectAsState()
    val chipInfo by viewModel.chipInfo.collectAsState()
    val targetPhoneState by viewModel.targetPhoneState.collectAsState()

    var showAboutDialog by remember { mutableStateOf(false) }
    var expandedCategory by remember { mutableStateOf<MainPlatformCategory?>(MainPlatformCategory.MTK) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0A0F1D),
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.width(310.dp)
            ) {
                // Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "NATIVE UNLOCK TOOL",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Multi-Chipset USB OTG Platform",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF1D4ED8),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8))
                        ) {
                            Text(
                                text = "PRO v3.0",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Device & Bridge Status Pill in Header
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (targetPhoneState is TargetPhoneState.Connected) Color(0xFF10B981) else Color(0xFF64748B),
                                    modifier = Modifier.size(7.dp)
                                ) {}
                                Text(
                                    text = if (targetPhoneState is TargetPhoneState.Connected) "PORT CONNECTED" else "WAITING USB OTG",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (targetPhoneState is TargetPhoneState.Connected) Color(0xFF4ADE80) else Color(0xFF94A3B8)
                                )
                            }
                            Text(
                                text = chipInfo.chipIdHex.ifEmpty { "0x0E8D" },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Scrollable Hierarchical Menu
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MainPlatformCategory.values().forEach { category ->
                        val subTabs = AppNavDestination.values().filter { it.category == category }
                        val isCurrentCategory = currentDestination.category == category
                        val isExpanded = expandedCategory == category

                        if (subTabs.size == 1 && (category == MainPlatformCategory.SETTINGS || category == MainPlatformCategory.ABOUT)) {
                            // Single Destination (Settings or About)
                            val dest = subTabs.first()
                            val isSelected = currentDestination == dest
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF1E293B) else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, category.accentColor) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (category == MainPlatformCategory.ABOUT) {
                                            showAboutDialog = true
                                        } else {
                                            viewModel.navigateTo(dest)
                                        }
                                        scope.launch { drawerState.close() }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) category.accentColor else Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = category.title,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                        )
                                        Text(
                                            text = category.subtitle,
                                            fontSize = 9.5.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Expandable Category Item
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrentCategory) Color(0xFF161F30) else Color(0xFF0F172A),
                                border = BorderStroke(1.dp, if (isCurrentCategory) category.accentColor.copy(alpha = 0.5f) else Color(0xFF1E293B)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCategory = if (isExpanded) null else category
                                    }
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = category.accentColor.copy(alpha = 0.15f),
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = category.icon,
                                                    contentDescription = null,
                                                    tint = category.accentColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = category.title,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCurrentCategory) Color.White else Color(0xFFE2E8F0)
                                                )
                                                if (category.isPlaceholder) {
                                                    Surface(
                                                        shape = RoundedCornerShape(3.dp),
                                                        color = Color(0xFF334155)
                                                    ) {
                                                        Text(
                                                            text = "Soon",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF94A3B8),
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = category.subtitle,
                                                fontSize = 9.sp,
                                                color = Color(0xFF64748B),
                                                maxLines = 1
                                            )
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = if (isExpanded) category.accentColor else Color(0xFF64748B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Sub-Tabs Accordion Content
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF0B1120))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            subTabs.forEach { subTab ->
                                                val isTabSelected = currentDestination == subTab
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isTabSelected) Color(0xFF1E293B) else Color.Transparent,
                                                    border = if (isTabSelected) BorderStroke(1.dp, Color(0xFF38BDF8)) else null,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            viewModel.navigateTo(subTab)
                                                            scope.launch { drawerState.close() }
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = subTab.icon,
                                                            contentDescription = null,
                                                            tint = if (isTabSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = subTab.title,
                                                                fontSize = 11.5.sp,
                                                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                                                color = if (isTabSelected) Color(0xFF38BDF8) else Color(0xFFCBD5E1)
                                                            )
                                                            Text(
                                                                text = subTab.subtitle,
                                                                fontSize = 8.5.sp,
                                                                color = Color(0xFF64748B),
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Bottom of drawer: About & Tool Info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Android Native USB Engine",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace
                    )
                    TextButton(onClick = { showAboutDialog = true }) {
                        Text("About", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) {
        // FULL SCREEN with proper Status & Navigation Bar Insets
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("mtk_app_scaffold")
        ) {
            UnlockToolFlashScreen(
                viewModel = viewModel,
                onOpenDrawer = { scope.launch { drawerState.open() } }
            )
        }
    }

    // AI Diagnostics Modal
    aiAnalysis?.let { analysisText ->
        AiDiagnosticDialog(
            analysisText = analysisText,
            onDismiss = { viewModel.dismissAiSheet() }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Native Unlock Tool", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Version 3.0.0 (Direct USB OTG Multi-Platform)", fontSize = 12.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
                    Text("Standalone Hardware Flashing and Service Tool utilizing Android Native USB Host API, File Descriptor extraction & USB Control Transfers.", fontSize = 12.sp, color = Color(0xFF475569))
                    Text("• MediaTek (BROM / Preloader / Scatter / NVRAM)\n• Qualcomm (EDL 9008 Sahara / Firehose)\n• ADB & Fastboot Multi-Tool\n• Built-in Gemini AI Flashing Diagnostics", fontSize = 11.sp, color = Color(0xFF334155))
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
