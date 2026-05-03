package com.halqa.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.halqa.app.data.Countries
import com.halqa.app.data.Country
import com.halqa.app.ui.components.HalqaTextField
import com.halqa.app.ui.theme.HalqaColors

/**
 * Full-screen modal for picking a country dial code. Shown above [PhoneAuthScreen] when the
 * user taps the dial-code chip. Search matches Arabic name, English name, ISO, or dial prefix.
 *
 * The component is intentionally state-light: it caches the search query locally and emits the
 * chosen [Country] back to the parent through [onPick]. Dismissing without picking calls
 * [onDismiss] only.
 */
@Composable
fun CountryPickerDialog(
    onDismiss: () -> Unit,
    onPick: (Country) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results by remember(query) {
        derivedStateOf { Countries.search(query) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HalqaColors.Bg),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "إغلاق",
                            tint = HalqaColors.Text,
                        )
                    }
                    Text(
                        "اختر الدولة",
                        color = HalqaColors.Text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                HalqaTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "ابحث (اسم الدولة أو رمزها)",
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = HalqaColors.TextMuted)
                    },
                )

                Spacer(Modifier.height(12.dp))

                if (results.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "لا توجد نتائج لـ \"$query\"",
                            color = HalqaColors.TextMuted,
                            fontSize = 15.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(results, key = { it.iso }) { country ->
                            CountryRow(
                                country = country,
                                onClick = { onPick(country) },
                            )
                            HorizontalDivider(
                                color = HalqaColors.Border,
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(
    country: Country,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(country.flag, fontSize = 24.sp)
        Spacer(Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                country.nameAr,
                color = HalqaColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                country.nameEn,
                color = HalqaColors.TextMuted,
                fontSize = 12.sp,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                "+${country.dial}",
                color = HalqaColors.BrandLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
