package com.example.onnlauncher

import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap

data class AppEntry(
    val packageName: String,
    val label: String,
    val intent: Intent,
    val banner: ImageBitmap?,
    val icon: ImageBitmap,
    val isGame: Boolean,
)
