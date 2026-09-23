package dev.zomboidds.companion.ui

import androidx.compose.ui.graphics.Color

// The app's colours for good / needs attention / bad, close to the ones the game uses for the same
// things (its health panel's green, orange and red), so every screen says it the same way.

/** Fine, treated, fresh, running. */
internal val Good = Color(0xFF6BD36B)

/** Worth a look: worn down, stale, the worn-clothes group. */
internal val Caution = Color(0xFFE8C547)

/** Needs attention soon (the game's orange: dirty bandage, infection). */
internal val Warning = Color(0xFFFF9447)

/** Bad: a wound, rotten, broken, overloaded, almost out of fuel. */
internal val Danger = Color(0xFFE35050)

/** A message that something didn't work ("Take: That container is out of reach"). */
internal val ErrorText = Color(0xFFE57373)
