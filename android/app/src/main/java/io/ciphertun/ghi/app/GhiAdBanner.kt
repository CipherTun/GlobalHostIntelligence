package io.ciphertun.ghi.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

private const val PRODUCTION_BANNER_AD_UNIT_ID =
    "ca-app-pub-3583424243110322/6485592223"

private const val GOOGLE_TEST_BANNER_AD_UNIT_ID =
    "ca-app-pub-3940256099942544/6300978111"

/**
 * Shared GHI banner. It is mounted by GhiAppChrome above the bottom navigation,
 * so every routed screen receives the same ad placement without duplicating
 * ad code in individual feature screens.
 *
 * Debug builds deliberately use Google's test banner to avoid invalid traffic.
 * Release builds use the GHI production banner unit.
 */
@Composable
fun GhiAdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adUnitId = if (io.ciphertun.ghi.BuildConfig.DEBUG) {
        GOOGLE_TEST_BANNER_AD_UNIT_ID
    } else {
        PRODUCTION_BANNER_AD_UNIT_ID
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            factory = { ctx ->
                AdView(ctx).apply {
