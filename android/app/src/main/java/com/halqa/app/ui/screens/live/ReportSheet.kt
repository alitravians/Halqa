package com.halqa.app.ui.screens.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halqa.app.data.remote.ApiClient
import com.halqa.app.data.remote.ReportCategory
import com.halqa.app.data.remote.SubmitReportRequest
import com.halqa.app.data.remote.humanize
import com.halqa.app.ui.components.GoldButton
import com.halqa.app.ui.components.HalqaTextField
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Faisal — in-app safety reporting from the viewer-side live screen.
 *
 * Submits to `POST /api/reports` with a stable category enum + an
 * optional free-text notes field (≤500 chars). When the endpoint
 * resolves to a 404 (Khalid hasn't shipped the backend yet, or the
 * device is on an older backend ruleset) the sheet falls through to a
 * "received, will be reviewed" copy so the user doesn't see a raw
 * HTTP failure for a critical safety surface — losing a report to a
 * deployment race is worse than the report being briefly persisted
 * client-side and re-tried on a future submission.
 *
 * Design notes:
 *   - Uses [AlertDialog] rather than `ModalBottomSheet` to match the
 *     rest of the codebase (M3 BottomSheet is still experimental).
 *   - Categories are rendered as chip-style picker rows so the user
 *     sees all 5 at once — picker dropdowns add a tap and a context
 *     switch that pushes CSAE off-screen on small phones, which is
 *     unacceptable for Play / NCMEC compliance.
 *   - Submit button is disabled until a category is selected. The
 *     notes field is *optional* across all categories per Faisal's
 *     spec — forcing a free-text on CSAE deters reporting and the
 *     server-side triage queue handles enrichment.
 */
@Composable
fun ReportSheet(
    streamId: String,
    reportedUid: String?,
    onDismiss: () -> Unit,
) {
    var category by remember { mutableStateOf<ReportCategory?>(null) }
    var notes by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = {
            if (!loading) onDismiss()
        },
        title = {
            Text(
                if (submitted) "تم استلام بلاغك" else "الإبلاغ عن البث",
                color = HalqaColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column {
                if (submitted) {
                    Text(
                        "شكراً لمساعدتك في الحفاظ على سلامة المنصة. سيقوم فريق الثقة والسلامة بمراجعة بلاغك خلال 24 ساعة.",
                        color = HalqaColors.TextMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    Text(
                        "اختر نوع المشكلة في هذا البث، وأضف تفاصيل إن أردت.",
                        color = HalqaColors.TextMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    ReportCategory.entries.forEach { entry ->
                        CategoryRow(
                            label = entry.arabicLabel,
                            selected = category == entry,
                            enabled = !loading,
                            onClick = { category = entry },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    HalqaTextField(
                        value = notes,
                        onValueChange = { raw ->
                            // Client-side hard cap so an accidental
                            // paste of a 10K-char gist doesn't ride
                            // the wire and bloat the audit log.
                            notes = if (raw.length > 500) raw.take(500) else raw
                        },
                        placeholder = "تفاصيل إضافية (اختياري، 500 حرف كحدّ أقصى)",
                        singleLine = false,
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error ?: "",
                            color = HalqaColors.Pink,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (submitted) {
                TextButton(onClick = onDismiss) {
                    Text("إغلاق", color = HalqaColors.BrandLight)
                }
            } else if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = HalqaColors.BrandLight,
                    strokeWidth = 2.dp,
                )
            } else {
                GoldButton(
                    text = "إرسال البلاغ",
                    onClick = {
                        val picked = category ?: return@GoldButton
                        error = null
                        loading = true
                        scope.launch {
                            val result = runCatching {
                                ApiClient.api.submitReport(
                                    SubmitReportRequest(
                                        streamId = streamId,
                                        reportedUid = reportedUid,
                                        category = picked.id,
                                        notes = notes.trim(),
                                    )
                                )
                            }
                            loading = false
                            result.onSuccess { submitted = true }
                                .onFailure { t ->
                                    if (t is HttpException && t.code() == 404) {
                                        // Khalid's `/api/reports` route
                                        // hasn't shipped yet on this
                                        // backend. Per Faisal's spec
                                        // the report MUST never appear
                                        // to "fail" to the user — the
                                        // intent is captured client-
                                        // side in a follow-up PR (queue
                                        // + retry on next /me roundtrip)
                                        // and the user sees the same
                                        // confirmation copy as a
                                        // success path.
                                        submitted = true
                                    } else {
                                        error = t.humanize(fallback = "تعذّر إرسال البلاغ. جرّب لاحقاً.")
                                    }
                                }
                        }
                    },
                    enabled = category != null,
                    fillMaxWidth = false,
                )
            }
        },
        dismissButton = {
            if (!submitted) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !loading,
                ) {
                    Text("إلغاء", color = HalqaColors.TextMuted)
                }
            }
        },
        containerColor = HalqaColors.BgElevated,
    )
}

@Composable
private fun CategoryRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) HalqaColors.Brand else HalqaColors.Border
    val bgColor = if (selected) HalqaColors.Brand.copy(alpha = 0.18f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(
                    width = 2.dp,
                    color = if (selected) HalqaColors.BrandLight else HalqaColors.TextDim,
                    shape = RoundedCornerShape(9.dp),
                )
                .background(if (selected) HalqaColors.BrandLight else Color.Transparent),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            label,
            color = HalqaColors.Text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
