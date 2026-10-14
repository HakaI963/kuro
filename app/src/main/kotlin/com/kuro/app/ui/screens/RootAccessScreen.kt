package com.kuro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuro.app.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootAccessScreen(
    onBack: () -> Unit,
    onEnableChroot: () -> Unit
) {
    var isRooted by remember { mutableStateOf(false) }
    var checkComplete by remember { mutableStateOf(false) }
    // Step 6: explicit su validation (user-initiated only).
    var testing by remember { mutableStateOf(false) }
    var suResult by remember { mutableStateOf<String?>(null) }
    var suFailed by remember { mutableStateOf(false) }

    fun runSuTest() {
        if (testing) return
        testing = true
        suResult = null
        suFailed = false
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val (out, err, code) = withContext(Dispatchers.IO) {
                runSuId()
            }
            testing = false
            if (code == 0 && "uid=" in out) {
                suResult = out.trim().take(120)
            } else {
                suFailed = true
                suResult = err.trim().take(120).ifBlank { null }
            }
        }
    }

    LaunchedEffect(Unit) {
        isRooted = checkForRoot()
        checkComplete = true
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Root Access", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back", tint = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.background(Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // Icon
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (checkComplete && isRooted) Icons.Default.Lock else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (checkComplete && isRooted) androidx.compose.material3.MaterialTheme.colorScheme.secondary else androidx.compose.material3.MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (!checkComplete) {
                    CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Checking for root access...", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                } else {
                    if (isRooted) {
                        Text(
                            "Root Access Detected",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Your device is rooted. You can enable Chroot mode for better performance and full hardware access.",
                            textAlign = TextAlign.Center,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(40.dp))
                        Button(
                            onClick = onEnableChroot,
                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Enable Chroot Mode", color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            "No Root Access",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Your device does not appear to be rooted. Chroot mode requires root access to function.",
                            textAlign = TextAlign.Center,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Please continue using PRoot mode (Default). It works on all devices without root.",
                            textAlign = TextAlign.Center,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(40.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Return to Settings", color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    // Step 6: explicit su check — runs only when tapped, so the
                    // KernelSU grant prompt appears only on user action.
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { runSuTest() },
                        enabled = !testing,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(if (testing) "Testing…" else "Test root access (su -c id)")
                    }
                    val suOut = suResult
                    if (suOut != null && !suFailed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            suOut,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    } else if (suFailed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Grant root access to Kuro in KernelSU settings.",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        if (suOut != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                suOut,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Step 6: runs `su -c id` with a timeout. Never throws; caller is user-initiated. */
private fun runSuId(): Triple<String, String, Int> {
    val proc = try {
        ProcessBuilder("su", "-c", "id").redirectErrorStream(false).start()
    } catch (e: Exception) {
        return Triple("", "exec: ${e.message}".take(120), -1)
    }
    try {
        val finished = try {
            proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {
            false
        }
        if (!finished) return Triple("", "timeout after 10s", -2)
        val out = runCatching { proc.inputStream.readBytes().toString(Charsets.UTF_8) }.getOrDefault("")
        val err = runCatching { proc.errorStream.readBytes().toString(Charsets.UTF_8) }.getOrDefault("")
        val code = runCatching { proc.exitValue() }.getOrDefault(-3)
        return Triple(out, err.take(120), code)
    } finally {
        runCatching { proc.destroy() }
    }
}

private fun checkForRoot(): Boolean {
    val paths = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su"
    )
    return try {
        paths.any { File(it).exists() }
    } catch (e: Exception) {
        false
    }
}
