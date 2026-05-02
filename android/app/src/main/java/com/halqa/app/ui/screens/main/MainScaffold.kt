package com.halqa.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.halqa.app.ui.screens.feed.FeedScreen
import com.halqa.app.ui.screens.golive.GoLivePrepScreen
import com.halqa.app.ui.screens.inbox.InboxScreen
import com.halqa.app.ui.screens.profile.ProfileScreen
import com.halqa.app.ui.screens.arena.ArenaTabScreen
import com.halqa.app.ui.theme.HalqaColors

private enum class MainTab(val label: String) {
    Feed("الاستكشاف"), Arena("الساحة"), Live("بث"), Inbox("الرسائل"), Profile("حسابي")
}

@Composable
fun MainScaffold(rootNavController: NavController) {
    var current by rememberSaveable { mutableStateOf(MainTab.Feed) }

    Scaffold(
        containerColor = HalqaColors.Bg,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF13132B),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, HalqaColors.Border),
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = current == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { current = tab },
                        icon = {
                            Icon(
                                when (tab) {
                                    MainTab.Feed -> Icons.Outlined.Explore
                                    MainTab.Arena -> Icons.Filled.SportsMartialArts
                                    MainTab.Live -> Icons.Filled.Add
                                    MainTab.Inbox -> Icons.Filled.MailOutline
                                    MainTab.Profile -> Icons.Filled.AccountCircle
                                },
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = HalqaColors.BrandLight,
                            selectedTextColor = HalqaColors.BrandLight,
                            unselectedIconColor = HalqaColors.TextMuted,
                            unselectedTextColor = HalqaColors.TextMuted,
                            indicatorColor = HalqaColors.Brand.copy(alpha = 0.18f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(HalqaColors.Bg),
        ) {
            when (current) {
                MainTab.Feed -> FeedScreen(rootNavController)
                MainTab.Arena -> ArenaTabScreen(rootNavController)
                MainTab.Live -> GoLivePrepScreen(rootNavController)
                MainTab.Inbox -> InboxScreen(rootNavController)
                MainTab.Profile -> ProfileScreen(rootNavController)
            }
        }
    }
}
