package com.halqa.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.halqa.app.ui.navigation.HalqaNavGraph

@Composable
fun HalqaApp() {
    val navController = rememberNavController()
    HalqaNavGraph(navController = navController)
}
