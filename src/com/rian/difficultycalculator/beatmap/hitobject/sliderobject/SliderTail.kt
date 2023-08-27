package com.rian.difficultycalculator.beatmap.hitobject.sliderobject

import com.rian.difficultycalculator.math.Vector2

/**
 * Represents a slider tail.
 */
class SliderTail(
    /**
     * The time at which this slider repeat starts, in milliseconds.
     */
    startTime: Double,

    /**
     * The position of the slider repeat relative to the play field.
     */
    position: Vector2,

    /**
     * The index of the span at which this slider repeat lies.
     */
    spanIndex: Int,

    /**
     * The start time of the span at which this slider repeat lies, in milliseconds.
     */
    spanStartTime: Double
) : SliderHitObject(startTime, position, spanIndex, spanStartTime) {
    override fun clone() = super.clone() as SliderTail
}
