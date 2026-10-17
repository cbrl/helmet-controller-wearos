package com.cbrl.pixelblaze.design

/**
 * The colors of the phone app, the watch app and the tile, as ARGB values.
 *
 * The design follows the Alive 2007 stage: black, stage white for the controls, and LED colors for
 * status and accents. Color in the apps comes mostly from the LEDs themselves.
 */
object HelmetPalette {
    // Light: stage white. Primary controls and selected states.
    const val LIGHT = 0xFFF2F2F3
    const val LIGHT_DIM = 0xFFB9B9BE
    const val ON_LIGHT = 0xFF0A0A0B
    const val GRAPHITE = 0xFF2A2A2E
    const val ON_GRAPHITE = 0xFFF2F2F3
    const val MUTED = 0xFF9A9AA2

    // Black: surfaces, from dark to light.
    const val BLACK = 0xFF000000
    const val SURFACE = 0xFF030303
    const val SURFACE_LOW = 0xFF0B0B0D
    const val SURFACE_MID = 0xFF111113
    const val SURFACE_HIGH = 0xFF18181B
    const val SURFACE_HIGHEST = 0xFF222225
    const val ON_SURFACE = 0xFFF2F2F3
    const val OUTLINE = 0xFF45454B
    const val OUTLINE_VARIANT = 0xFF26262A

    // LEDs: status and accents.
    const val LED_RED = 0xFFFF3B2F
    const val LED_RED_DIM = 0xFFC92E25
    const val LED_RED_CONTAINER = 0xFF3A0D0A
    const val ON_LED_RED = 0xFF2B0300
    const val ON_LED_RED_CONTAINER = 0xFFFFD7D2
    const val LED_AMBER = 0xFFFFB224
    const val LED_AMBER_DIM = 0xFFCC8D1B
    const val LED_AMBER_CONTAINER = 0xFF3A2800
    const val ON_LED_AMBER = 0xFF2A1C00
    const val ON_LED_AMBER_CONTAINER = 0xFFFFE2A8
    const val LED_GREEN = 0xFF3CF08A

    /** An LED that is off, on black. */
    const val LED_OFF = 0xFF1C1C20

    /** The LED bars of the ear panel on the Discovery helmet, from top to bottom. */
    val EAR_PANEL = longArrayOf(0xFFFF3B2F, 0xFFFFB224, 0xFF3CF08A, 0xFF2EC8FF, 0xFF7A5CFF)
}
