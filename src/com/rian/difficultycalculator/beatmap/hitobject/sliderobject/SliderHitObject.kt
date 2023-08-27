package com.rian.difficultycalculator.beatmap.hitobject.sliderobject

import com.rian.difficultycalculator.beatmap.hitobject.HitObject
import com.rian.difficultycalculator.math.Vector2

/**
 * Represents a hit object that can be nested into a slider.
 */
abstract class SliderHitObject(
    /**
     * The time at which this hit object starts, in milliseconds.
     */
    startTime: Double,

    /**
     * The position of the hit object relative to the play field.
     */
    position: Vector2,

    /**
     * The index of the span at which this slider hit object lies.
     */
    spanIndex: Int,

    /**
     * The start time of the span at which this slider hit object lies, in milliseconds.
     */
    spanStartTime: Double
) : HitObject(startTime, position) {
    /**
     * The index of the span at which this slider hit object lies.
     */
    var spanIndex = spanIndex
        private set

    /**
     * The start time of the span at which this slider hit object lies, in milliseconds.
     */
    var spanStartTime = spanStartTime
        private set

    override fun clone() = super.clone() as SliderHitObject
}
