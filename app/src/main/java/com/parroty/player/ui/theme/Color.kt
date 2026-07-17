package com.parroty.player.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ── THE ONLY FILE YOU NEED TO EDIT TO RE-SKIN THE APP ──────────────────────
 *
 * A one-to-one mirror of the `:root` block in Parroty's `app/static/style.css`.
 * The names match the CSS custom properties exactly, so a change there is a
 * change here with nothing to reason about in between.
 *
 * Nothing else in the project hardcodes a colour.
 */
object ParrotyPalette {

    // ── Straight from :root ──────────────────────────────────────────────────
    val Paper = Color(0xFFECE4D6)      // --paper
    val PaperDeep = Color(0xFFE3D8C4)  // --paper-deep
    val Ink = Color(0xFF1F1B16)        // --ink
    val InkSoft = Color(0xFF4A4239)    // --ink-soft
    val Rule = Color(0xFFB9AB92)       // --rule
    val Spine = Color(0xFF8C3B2E)      // --spine       book-cloth red
    val SpineDeep = Color(0xFF6E2C22)  // --spine-deep
    val Gilt = Color(0xFFB08642)       // --gilt        foil gold accent
    val Ok = Color(0xFF3D6B4A)         // --ok
    val WarnBg = Color(0xFFF0E2C2)     // --warn-bg

    // ── What the app calls them ──────────────────────────────────────────────
    // Aliases only. Every one points at a value above.
    val Bg = Paper
    val Surface = PaperDeep            // .chapter card background
    val Border = Rule                  // .chapter border, slider track
    val Text = Ink
    val TextMuted = InkSoft            // .chapter-meta
    val Accent = Spine                 // .primary button background
    val AccentDim = SpineDeep          // .primary:hover
    val OnAccent = Paper               // .primary text colour on --spine
    val Good = Ok
    val Bad = Spine                    // .preview-err reuses the spine red
}
