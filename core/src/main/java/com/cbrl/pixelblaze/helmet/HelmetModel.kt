package com.cbrl.pixelblaze.helmet

import kotlin.math.floor

/** The LED segments of the helmet, in wiring order. [id] is the segment number in the pattern. */
enum class HelmetSegment(val id: Int, val label: String, val shortLabel: String) {
    Visor(0, "Visor", "Visor"),
    VisorSide1(1, "Visor Side 1", "Side 1"),
    VisorSide2(2, "Visor Side 2", "Side 2"),
    VisorSide3(3, "Visor Side 3", "Side 3"),
    VisorSide4(4, "Visor Side 4", "Side 4"),
    LeftEar(5, "Left Ear", "L Ear"),
    RightEar(6, "Right Ear", "R Ear");

    companion object {
        fun fromId(id: Int): HelmetSegment? = entries.firstOrNull { it.id == id }
    }
}

/** How an effect uses the segment's hue and saturation. */
enum class ColorUse {
    /** The effect uses the hue and the saturation. */
    Full,

    /** The effect makes its own hues and uses only the saturation. */
    SaturationOnly,

    /** The effect makes its own colors. */
    None,
}

enum class EffectCategory(val label: String) {
    Solid("Solid"),
    Motion("Motion"),
    Sparkle("Sparkle"),
    Rainbow("Rainbow"),
    Matrix("Matrix"),
}

/** The effects in the pattern. [id] is the index in the pattern's renderer table. */
enum class HelmetEffect(
    val id: Int,
    val label: String,
    val category: EffectCategory,
    val colorUse: ColorUse,
    val usesSpeed: Boolean = true,
    val requires2D: Boolean = false,
) {
    SolidColor(0, "Solid Color", EffectCategory.Solid, ColorUse.Full, usesSpeed = false),
    Glitter(1, "Glitter", EffectCategory.Sparkle, ColorUse.Full),
    RainbowBounce(2, "Rainbow Bounce", EffectCategory.Rainbow, ColorUse.SaturationOnly),
    Kitt(3, "KITT Scanner", EffectCategory.Motion, ColorUse.Full),
    Breathe(4, "Breathe", EffectCategory.Motion, ColorUse.Full),
    SlowColor(5, "Slow Color", EffectCategory.Rainbow, ColorUse.SaturationOnly),
    Snow(6, "Snow", EffectCategory.Sparkle, ColorUse.Full),
    ChaserUp(7, "Chaser Up", EffectCategory.Motion, ColorUse.Full),
    ChaserDown(8, "Chaser Down", EffectCategory.Motion, ColorUse.Full),
    Strobe(9, "Strobe", EffectCategory.Motion, ColorUse.Full),
    WipeUp(10, "Wipe Up", EffectCategory.Rainbow, ColorUse.SaturationOnly),
    WipeDown(11, "Wipe Down", EffectCategory.Rainbow, ColorUse.SaturationOnly),
    SpringyTheater(12, "Springy Theater", EffectCategory.Motion, ColorUse.Full),
    ColorTwinkles(13, "Color Twinkles", EffectCategory.Sparkle, ColorUse.None),
    Plasma(14, "Plasma", EffectCategory.Rainbow, ColorUse.None),
    Ripples(15, "Ripples", EffectCategory.Motion, ColorUse.Full),
    SpinCycle(16, "Spin Cycle", EffectCategory.Rainbow, ColorUse.None),
    RainbowUp(17, "Rainbow Up", EffectCategory.Rainbow, ColorUse.SaturationOnly),
    RainbowDown(18, "Rainbow Down", EffectCategory.Rainbow, ColorUse.SaturationOnly),
    Xorcery(19, "Xorcery", EffectCategory.Rainbow, ColorUse.None),
    Scanner(20, "Matrix Scanner", EffectCategory.Matrix, ColorUse.Full, requires2D = true),
    TunnelSquares(21, "Tunnel Squares", EffectCategory.Matrix, ColorUse.None, requires2D = true),
    LineDancer(22, "Line Dancer", EffectCategory.Matrix, ColorUse.None, requires2D = true),
    Eyes(23, "Eyes", EffectCategory.Matrix, ColorUse.Full, requires2D = true),
    Snake(24, "Snake", EffectCategory.Matrix, ColorUse.None, requires2D = true),
    Text(25, "Scrolling Text", EffectCategory.Matrix, ColorUse.Full, requires2D = true);

    companion object {
        fun fromId(id: Int): HelmetEffect? = entries.firstOrNull { it.id == id }

        fun availableFor(segment: HelmetSegment, layout: HelmetLayout = HelmetLayout.Default): List<HelmetEffect> =
            entries.filter { !it.requires2D || layout[segment].is2D }
    }
}

/** The controls of one segment. Use [normalized] to clamp values to the ranges of the pattern. */
data class SegmentState(
    val on: Boolean = true,
    val hue: Float = 0f,
    val saturation: Float = 1f,
    val brightness: Float = 1f,
    val effect: HelmetEffect = HelmetEffect.SolidColor,
    val speed: Float = 1f,
    val fadeSeconds: Float = 0f,
) {
    fun normalized(): SegmentState = copy(
        // Wrap only when needed: float arithmetic would change an in-range hue a little.
        hue = if (hue >= 0f && hue < 1f) hue else hue - floor(hue),
        saturation = saturation.coerceIn(0f, 1f),
        brightness = brightness.coerceIn(0f, 1f),
        speed = speed.coerceIn(MIN_SPEED, MAX_SPEED),
        fadeSeconds = fadeSeconds.coerceIn(0f, MAX_FADE_SECONDS),
    )

    companion object {
        const val MIN_SPEED = 0.05f
        const val MAX_SPEED = 10f
        const val MAX_FADE_SECONDS = 30f
    }
}

data class SegmentLayout(val size: Int, val width: Int, val height: Int) {
    val is2D: Boolean get() = height > 1
}

/** The pixel layout that the pattern reports in __layout. */
data class HelmetLayout(val segments: List<SegmentLayout>) {
    init {
        require(segments.size == HelmetSegment.entries.size)
    }

    operator fun get(segment: HelmetSegment): SegmentLayout = segments[segment.id]

    /** The index of the first pixel of [segment]. */
    fun start(segment: HelmetSegment): Int = segments.take(segment.id).sumOf { it.size }

    val pixelCount: Int get() = segments.sumOf { it.size }

    companion object {
        val Default = HelmetLayout(
            listOf(
                SegmentLayout(440, 44, 10),
                SegmentLayout(10, 10, 1),
                SegmentLayout(9, 9, 1),
                SegmentLayout(7, 7, 1),
                SegmentLayout(6, 6, 1),
                SegmentLayout(12, 12, 1),
                SegmentLayout(12, 12, 1),
            ),
        )
    }
}

/** The part of the helmet state that a preset stores: the segments and the message. */
data class HelmetLook(val segments: List<SegmentState>, val message: String) {
    init {
        require(segments.size == HelmetSegment.entries.size)
    }

    operator fun get(segment: HelmetSegment): SegmentState = segments[segment.id]
}

/** Everything that the apps control on the helmet. */
data class HelmetState(
    val segments: List<SegmentState>,
    val message: String = "",
    val master: Float = 1f,
    val blackout: Boolean = false,
) {
    init {
        require(segments.size == HelmetSegment.entries.size)
    }

    operator fun get(segment: HelmetSegment): SegmentState = segments[segment.id]

    fun with(segment: HelmetSegment, state: SegmentState): HelmetState =
        copy(segments = segments.toMutableList().also { it[segment.id] = state })

    val look: HelmetLook get() = HelmetLook(segments, message)

    fun withLook(look: HelmetLook): HelmetState = copy(segments = look.segments, message = look.message)

    companion object {
        /** The look that the pattern shows after it starts. */
        val Default = HelmetState(
            segments = HelmetSegment.entries.map { segment ->
                when (segment) {
                    HelmetSegment.Visor -> SegmentState(brightness = 0.15f, effect = HelmetEffect.Scanner)
                    HelmetSegment.LeftEar -> SegmentState(effect = HelmetEffect.RainbowUp)
                    else -> SegmentState(effect = HelmetEffect.RainbowDown)
                }
            },
            message = "  Hello!  ",
        )
    }
}

object HelmetMessage {
    const val MAX_LENGTH = 32

    /** Keeps the first [MAX_LENGTH] characters. Characters outside printable ASCII become '?'. */
    fun sanitize(value: String): String = buildString {
        value.codePoints().limit(MAX_LENGTH.toLong()).forEach { code ->
            append(if (code in 32..126) code.toChar() else '?')
        }
    }
}
