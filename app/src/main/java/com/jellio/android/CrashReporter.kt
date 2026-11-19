package com.jellio.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.jellio.android.ui.common.ClickableSurfaceDefaults
import com.jellio.android.ui.common.Surface
import com.jellio.android.ui.common.primaryButtonColors
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val TAG = "JellioCrash"
    private const val CRASH_FILE = "last_crash.txt"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeReport(appContext, thread, throwable)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to save crash report", e)
            } finally {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun writeReport(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        pw.println("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        pw.println("Time: $ts")
        pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
        pw.println("Thread: ${thread.name}")
        pw.println()
        throwable.printStackTrace(pw)
        pw.flush()
        File(context.filesDir, CRASH_FILE).writeText(sw.toString())
    }

    fun read(context: Context): String? = runCatching {
        val file = File(context.applicationContext.filesDir, CRASH_FILE)
        if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching {
            File(context.applicationContext.filesDir, CRASH_FILE).delete()
        }
    }
}

@Composable
fun CrashReportOverlay(
    report: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .systemBarsPadding()
            .padding(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(JellioBgElevated)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Last Crash Report",
                color = JellioText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Tap Copy and send this trace so we can fix the crash immediately.",
                color = JellioTextSecondary,
                fontSize = 13.sp,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 380.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0A0A0D))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    text = report,
                    color = Color(0xFFFFB4AB),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                Surface(
                    onClick = {
                        runCatching {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Jellio Crash Report", report))
                            copied = true
                        }
                    },
                    colors = primaryButtonColors(),
                ) {
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Surface(
                    onClick = onDismiss,
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = JellioText,
                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                        focusedContentColor = JellioText,
                    ),
                ) {
                    Text(
                        text = "Dismiss",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
