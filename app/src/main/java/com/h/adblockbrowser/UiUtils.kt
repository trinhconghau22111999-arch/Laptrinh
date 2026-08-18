package com.h.adblockbrowser

import android.app.Activity

fun Activity.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
