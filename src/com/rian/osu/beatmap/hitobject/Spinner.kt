package com.rian.osu.beatmap.hitobject

import com.rian.osu.GameMode
import com.rian.osu.math.Vector2

/**
 * Represents a spinner.
 */
class Spinner(
    /**
     * The time at which this [Spinner] starts, in milliseconds.
     */
    startTime: Double,

    override var endTime: Double
) : HitObject(startTime, Vector2(256f, 192f)), IHasDuration {
    init {
        auxiliarySamples.apply {
            samples.filterIsInstance<BankHitSampleInfo>().firstOrNull()?.let { add(it.copy(name = "spinnerspin")) }

            add(createHitSampleInfo("spinnerbonus"))
        }
    }

    override fun getStackedPosition(mode: GameMode) = position

    override val duration: Double
        get() = endTime - startTime
}
