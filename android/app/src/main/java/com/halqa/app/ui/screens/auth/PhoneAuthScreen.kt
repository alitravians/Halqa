package com.halqa.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.data.Countries
import com.halqa.app.ui.components.HalqaTextField
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors

/**
 * Phone-number entry step for the OTP sign-up flow. Defaults to Saudi Arabia (+966) and
 * lets the user switch to any country via the [CountryPickerDialog].
 */
@Composable
fun PhoneAuthScreen(navController: NavController) {
    var phone by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(Countries.saudiArabia) }
    var pickerOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(HalqaColors.Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Back",
                        tint = HalqaColors.Text,
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "رقم الجوال",
                color = HalqaColors.Text,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "اختر الدولة ثم أدخل رقمك. سنرسل لك رمز تحقق مرة واحدة (OTP).",
                color = HalqaColors.TextMuted,
                fontSize = 14.sp,
            )

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, HalqaColors.BorderStrong, RoundedCornerShape(16.dp))
                        .clickable { pickerOpen = true }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(country.flag, fontSize = 22.sp)
                    Text(
                        "+${country.dial}",
                        color = HalqaColors.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = "تغيير الدولة",
                        tint = HalqaColors.TextMuted,
                    )
                }

                HalqaTextField(
                    value = phone,
                    onValueChange = { input ->
                        val digits = input.filter { c -> c.isDigit() }
                        phone = digits.take(country.maxDigits)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = "رقم الجوال",
                    keyboardType = KeyboardType.Phone,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "${country.nameAr} • +${country.dial}",
                color = HalqaColors.TextDim,
                fontSize = 12.sp,
            )

            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                text = "إرسال رمز التحقق",
                onClick = { navController.navigate(Routes.Main) { popUpTo(Routes.Auth) { inclusive = true } } },
                enabled = phone.length >= minOf(7, country.maxDigits),
            )
        }
    }

    if (pickerOpen) {
        CountryPickerDialog(
            onDismiss = { pickerOpen = false },
            onPick = {
                country = it
                phone = ""
                pickerOpen = false
            },
        )
    }
}
