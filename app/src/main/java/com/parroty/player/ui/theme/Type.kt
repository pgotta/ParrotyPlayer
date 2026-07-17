package com.parroty.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.parroty.player.R

/**
 * The same three faces the desktop loads. index.html asks Google Fonts for:
 *
 *   Fraunces:opsz,wght@9..144,400;9..144,600;9..144,900
 *   Spline+Sans:wght@400;500;600
 *   Spline+Sans+Mono:wght@400;500;600
 *
 * so those are the weights cut here, and no others.
 *
 * All three are variable fonts, which matters more than it looks. Fraunces ships
 * with defaults of wght=900 and opsz=9: drop it in without setting axes and every
 * title renders Black at display contrast. Each weight below pins its own axes.
 *
 * The browser resolves `opsz` against the rendered size automatically. Android has
 * no equivalent, so [fraunces] takes the size it will be used at and pins opsz to
 * match, which is why there is a family per size rather than one shared family.
 *
 * FontVariation is still marked experimental in Compose, hence the opt-ins. It is
 * the only way to reach the axes, and the shape of the call is stable enough that
 * a rename is the worst case.
 */

@OptIn(ExperimentalTextApi::class)
private fun frauncesFont(weight: Int, opsz: TextUnit) = Font(
    resId = R.font.fraunces_variable,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.opticalSizing(opsz)
    )
)

/** Fraunces cut for one specific size, with opsz pinned to it. */
private fun fraunces(opsz: TextUnit) = FontFamily(
    frauncesFont(400, opsz),
    frauncesFont(600, opsz),
    frauncesFont(900, opsz)
)

@OptIn(ExperimentalTextApi::class)
private fun splineFont(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

private val SplineSans = FontFamily(
    splineFont(R.font.spline_sans_variable, 400),
    splineFont(R.font.spline_sans_variable, 500),
    splineFont(R.font.spline_sans_variable, 600)
)

private val SplineSansMono = FontFamily(
    splineFont(R.font.spline_sans_mono_variable, 400),
    splineFont(R.font.spline_sans_mono_variable, 500),
    splineFont(R.font.spline_sans_mono_variable, 600)
)

val ParrotyTypography = Typography(
    // Book titles.
    headlineSmall = TextStyle(
        fontFamily = fraunces(22.sp),
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // .chapter-title: serif, 1.05rem, weight 600.
    // Every TopAppBar title lands here. Left undefined it fell back to Material's
    // default sans, which is why the app bar did not match the body copy.
    titleLarge = TextStyle(
        fontFamily = fraunces(20.sp),
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fraunces(17.sp),
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = SplineSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = SplineSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    // .chapter-meta: mono, .75rem, --ink-soft.
    labelMedium = TextStyle(
        fontFamily = SplineSansMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = SplineSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)
