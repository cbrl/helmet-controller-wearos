package com.cbrl.pixelblaze.helmet

/**
 * Looks that the apps add to the preset list once (see [addNew]). The user can change or delete
 * them as their own presets.
 *
 * The helmet has the parts of Thomas Bangalter's Discovery helmet: the visor matrix, four side
 * lines that are stacked from top to bottom, and two round ear rings. An effect runs along each
 * line, and around each ring. The right ear runs the mirror effect of the left ear, so that both
 * rings turn the same way from the front. The visor stays dim, because it is in front of the eyes.
 */
object BuiltInPresets {
    /** The last version that added presets. Add 1 when you add presets, and give them this version. */
    const val VERSION = 1

    /**
     * 2025-01-01. The built-in presets are older than the presets that the user saves, so they show
     * after the user's presets. Each preset is 1 ms older than the preset before it.
     */
    private const val BASE_TIME = 1_735_689_600_000L

    // Hues on the LEDs
    private const val RED = 0f
    private const val ORANGE = 0.05f
    private const val AMBER = 0.1f
    private const val YELLOW = 0.15f
    private const val GREEN = 0.33f
    private const val CYAN = 0.5f
    private const val BLUE = 0.62f
    private const val VIOLET = 0.72f
    private const val MAGENTA = 0.83f
    private const val PINK = 0.92f

    private const val HELLO = "  Hello!  "

    private class Entry(val since: Int, val preset: HelmetPreset)

    private val entries: List<Entry> = listOf(
        entry(
            since = 1,
            id = "discovery",
            name = "Discovery",
            message = "  DISCOVERY  ",
            visor = SegmentState(effect = HelmetEffect.Scanner, hue = RED, brightness = 0.2f),
            sides = lines(HelmetEffect.SolidColor, RED, AMBER, GREEN, BLUE),
            leftEar = SegmentState(effect = HelmetEffect.RainbowUp, speed = 0.5f),
        ),
        entry(
            since = 1,
            id = "alive-2007",
            name = "Alive 2007",
            message = "  ALIVE 2007  ",
            visor = SegmentState(effect = HelmetEffect.Text, hue = RED, brightness = 0.25f),
            sides = listOf(
                SegmentState(effect = HelmetEffect.Kitt, saturation = 0f),
                SegmentState(effect = HelmetEffect.Kitt, hue = RED),
                SegmentState(effect = HelmetEffect.Kitt, saturation = 0f),
                SegmentState(effect = HelmetEffect.Kitt, hue = RED),
            ),
            leftEar = SegmentState(effect = HelmetEffect.ChaserUp, hue = RED),
        ),
        entry(
            since = 1,
            id = "around-the-world",
            name = "Around the World",
            message = "  AROUND THE WORLD  ",
            visor = SegmentState(effect = HelmetEffect.LineDancer, brightness = 0.2f),
            sides = allLines(SegmentState(effect = HelmetEffect.RainbowUp)).alternating(),
            leftEar = SegmentState(effect = HelmetEffect.RainbowUp),
        ),
        entry(
            since = 1,
            id = "harder-better",
            name = "Harder Better Faster",
            message = "HARDER BETTER FASTER STRONGER   ",
            visor = SegmentState(effect = HelmetEffect.Text, hue = YELLOW, brightness = 0.25f, speed = 1.5f),
            sides = lines(HelmetEffect.ChaserUp, RED, AMBER, GREEN, BLUE, speed = 2f),
            leftEar = SegmentState(effect = HelmetEffect.ChaserUp, hue = AMBER, speed = 2f),
        ),
        entry(
            since = 1,
            id = "digital-love",
            name = "Digital Love",
            message = "  DIGITAL LOVE  ",
            visor = SegmentState(effect = HelmetEffect.Text, hue = PINK, brightness = 0.25f, speed = 0.75f),
            sides = lines(HelmetEffect.Breathe, MAGENTA, 0.88f, PINK, 0.97f, speed = 0.5f),
            leftEar = SegmentState(effect = HelmetEffect.Breathe, hue = PINK, speed = 0.5f),
        ),
        entry(
            since = 1,
            id = "robot-rock",
            name = "Robot Rock",
            message = "  ROBOT ROCK  ",
            visor = SegmentState(effect = HelmetEffect.Scanner, hue = RED, brightness = 0.25f, speed = 2f),
            sides = allLines(SegmentState(effect = HelmetEffect.SpringyTheater, hue = RED)),
            leftEar = SegmentState(effect = HelmetEffect.Strobe, hue = RED),
        ),
        entry(
            since = 1,
            id = "one-more-time",
            name = "One More Time",
            message = "  ONE MORE TIME  ",
            visor = SegmentState(effect = HelmetEffect.Xorcery, brightness = 0.15f, speed = 0.5f),
            sides = allLines(SegmentState(effect = HelmetEffect.RainbowBounce)),
            leftEar = SegmentState(effect = HelmetEffect.RainbowBounce),
        ),
        entry(
            since = 1,
            id = "technologic",
            name = "Technologic",
            message = "  TECHNOLOGIC  ",
            visor = SegmentState(effect = HelmetEffect.TunnelSquares, brightness = 0.2f),
            sides = lines(HelmetEffect.ChaserUp, GREEN, CYAN, GREEN, CYAN).alternating(),
            leftEar = SegmentState(effect = HelmetEffect.ChaserUp, hue = GREEN),
        ),
        entry(
            since = 1,
            id = "the-grid",
            name = "The Grid",
            message = "  DEREZZED  ",
            visor = SegmentState(effect = HelmetEffect.Kitt, hue = CYAN, brightness = 0.2f, speed = 0.5f),
            sides = allLines(SegmentState(hue = CYAN)),
            leftEar = SegmentState(effect = HelmetEffect.ChaserUp, hue = ORANGE),
        ),
        entry(
            since = 1,
            id = "robot-eyes",
            name = "Robot Eyes",
            message = HELLO,
            visor = SegmentState(effect = HelmetEffect.Eyes, hue = RED, brightness = 0.3f),
            sides = allLines(SegmentState(effect = HelmetEffect.Breathe, hue = RED, brightness = 0.4f, speed = 0.25f)),
            leftEar = SegmentState(hue = RED, brightness = 0.3f),
        ),
        entry(
            since = 1,
            id = "arcade",
            name = "Arcade",
            message = "  INSERT COIN  ",
            visor = SegmentState(effect = HelmetEffect.Snake, brightness = 0.3f),
            sides = allLines(SegmentState(effect = HelmetEffect.WipeUp)).alternating(),
            leftEar = SegmentState(effect = HelmetEffect.RainbowBounce),
        ),
        entry(
            since = 1,
            id = "starlight",
            name = "Starlight",
            message = "  STARLIGHT  ",
            visor = SegmentState(effect = HelmetEffect.Glitter, hue = BLUE, saturation = 0.2f, brightness = 0.3f, speed = 0.5f),
            sides = lines(HelmetEffect.Snow, BLUE, 0.66f, 0.69f, VIOLET, speed = 0.5f, brightness = 0.7f),
            leftEar = SegmentState(effect = HelmetEffect.Breathe, hue = VIOLET, speed = 0.25f),
        ),
        entry(
            since = 1,
            id = "standby",
            name = "Standby",
            message = HELLO,
            visor = SegmentState(on = false, effect = HelmetEffect.Scanner, hue = RED, brightness = 0.15f),
            sides = allLines(SegmentState(on = false, hue = RED)),
            leftEar = SegmentState(effect = HelmetEffect.Breathe, hue = RED, brightness = 0.3f, speed = 0.25f),
        ),
    ).mapIndexed { index, entry ->
        Entry(entry.since, entry.preset.copy(updatedAt = BASE_TIME - index))
    }

    /** The built-in presets, newest first. */
    val presets: List<HelmetPreset> = entries.map { it.preset }

    /**
     * Adds the built-in presets that came after [seenVersion] and that [presets] does not have. A
     * deleted built-in preset keeps its tombstone, so it does not come back.
     */
    fun addNew(
        presets: List<HelmetPreset>,
        seenVersion: Int,
        now: Long = System.currentTimeMillis(),
    ): List<HelmetPreset> {
        val known = presets.mapTo(HashSet()) { it.id }
        val added = entries.filter { it.since > seenVersion && it.preset.id !in known }.map { it.preset }
        return HelmetPresets.normalize(presets + added, now)
    }

    private fun entry(
        since: Int,
        id: String,
        name: String,
        message: String,
        visor: SegmentState,
        sides: List<SegmentState>,
        leftEar: SegmentState,
        rightEar: SegmentState = leftEar.mirrored(),
    ): Entry {
        val look = HelmetLook(listOf(visor) + sides + listOf(leftEar, rightEar), message)
        return Entry(since, HelmetPreset("builtin-$id", name, look, updatedAt = BASE_TIME))
    }

    /** The four side lines, from top to bottom: [effect] in one hue for each line. */
    private fun lines(
        effect: HelmetEffect,
        top: Float,
        second: Float,
        third: Float,
        bottom: Float,
        speed: Float = 1f,
        brightness: Float = 1f,
    ): List<SegmentState> = listOf(top, second, third, bottom).map { hue ->
        SegmentState(effect = effect, hue = hue, speed = speed, brightness = brightness)
    }

    /** The four side lines with the same [state]. */
    private fun allLines(state: SegmentState): List<SegmentState> = List(4) { state }

    /** Mirrors the second and fourth lines, so that each line runs the other way from the line above it. */
    private fun List<SegmentState>.alternating(): List<SegmentState> =
        mapIndexed { index, state -> if (index % 2 == 1) state.mirrored() else state }

    /** The same look with the opposite direction, for effects that have an up and a down form. */
    private fun SegmentState.mirrored(): SegmentState = copy(
        effect = when (effect) {
            HelmetEffect.ChaserUp -> HelmetEffect.ChaserDown
            HelmetEffect.ChaserDown -> HelmetEffect.ChaserUp
            HelmetEffect.WipeUp -> HelmetEffect.WipeDown
            HelmetEffect.WipeDown -> HelmetEffect.WipeUp
            HelmetEffect.RainbowUp -> HelmetEffect.RainbowDown
            HelmetEffect.RainbowDown -> HelmetEffect.RainbowUp
            else -> effect
        },
    )
}
