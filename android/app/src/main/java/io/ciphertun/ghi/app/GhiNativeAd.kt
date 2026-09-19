package io.ciphertun.ghi.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

private const val TAG = "GhiNativeAd"

private const val PRODUCTION_NATIVE_AD_UNIT_ID =
    "ca-app-pub-3583424243110322/9116478623"

private const val TEST_NATIVE_AD_UNIT_ID =
    "ca-app-pub-3940256099942544/2247696110"

private fun isDebugBuild(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

private fun nativeAdUnitId(context: Context): String =
    if (isDebugBuild(context)) {
        TEST_NATIVE_AD_UNIT_ID
    } else {
        PRODUCTION_NATIVE_AD_UNIT_ID
    }

@Composable
fun GhiNativeAd(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val adUnitId = remember(context) {
        nativeAdUnitId(context)
    }

    val holder = remember(context) {
        NativeAdHolder(context)
    }

    DisposableEffect(context, adUnitId) {
        holder.load(adUnitId)

        onDispose {
            holder.destroy()
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        factory = {
            holder.container
        }
    )
}

private class NativeAdHolder(
    context: Context
) {
    private var nativeAd: NativeAd? = null

    val container: NativeAdView =
        NativeAdView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    fun load(adUnitId: String) {
        val context = container.context

        val loader = AdLoader.Builder(
            context,
            adUnitId
        )
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
                bind(ad)
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(
                        NativeAdOptions.ADCHOICES_TOP_RIGHT
                    )
                    .build()
            )
            .withAdListener(
                object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d(TAG, "Native ad loaded")
                    }

                    override fun onAdImpression() {
                        Log.d(TAG, "Native ad impression recorded")
                    }

                    override fun onAdClicked() {
                        Log.d(TAG, "Native ad clicked")
                    }

                    override fun onAdFailedToLoad(
                        error: LoadAdError
                    ) {
                        Log.w(
                            TAG,
                            "Native ad failed: code=${error.code}, domain=${error.domain}, message=${error.message}"
                        )
                    }
                }
            )
            .build()

        try {
            loader.loadAd(
                AdRequest.Builder().build()
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Native ad request failed", t)
        }
    }

    private fun bind(ad: NativeAd) {
        val context = container.context

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.rgb(12, 18, 28))
                setStroke(dp(1), Color.rgb(35, 82, 125))
            }
        }

        val attribution = TextView(context).apply {
            text = "Ad"
            textSize = 11f
            setTextColor(Color.rgb(120, 190, 255))
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(48),
                dp(48)
            ).apply {
                marginEnd = dp(10)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val headline = TextView(context).apply {
            textSize = 17f
            setTextColor(Color.WHITE)
            maxLines = 2
        }

        val headlineColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        headlineColumn.addView(headline)

        ad.icon?.drawable?.let {
            icon.setImageDrawable(it)
            topRow.addView(icon)
        }

        topRow.addView(headlineColumn)

        val media = MediaView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(150)
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
        }

        val body = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.rgb(205, 215, 225))
            maxLines = 3
        }

        val cta = Button(context).apply {
            isAllCaps = false
            minHeight = dp(42)
        }

        root.addView(attribution)
        root.addView(topRow)
        root.addView(media)
        root.addView(body)
        root.addView(cta)

        container.removeAllViews()
        container.addView(root)

        headline.text = ad.headline ?: ""
        body.text = ad.body ?: ""
        cta.text = ad.callToAction ?: "Learn more"

        ad.mediaContent?.let {
            media.mediaContent = it
        }

        container.headlineView = headline
        container.bodyView = body
        container.callToActionView = cta
        container.iconView = icon
        container.mediaView = media
        container.setNativeAd(ad)
    }

    fun destroy() {
        nativeAd?.destroy()
        nativeAd = null
        container.removeAllViews()
    }

    private fun dp(value: Int): Int =
        (value * container.context.resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(1)
}
