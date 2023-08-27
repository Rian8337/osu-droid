package com.rian.difficultycalculator.beatmap

/**
 * A beatmap difficulty manager.
 */
class BeatmapDifficultyManager : Cloneable {
    /**
     * The circle size of this beatmap.
     */
    @JvmField
    var cs = 5f

    /**
     * The approach rate of this beatmap.
     */
    var ar = Float.NaN
        get() = if (field.isNaN()) od else field

    /**
     * The overall difficulty of this beatmap.
     */
    @JvmField
    var od = 5f

    /**
     * The health drain rate of this beatmap.
     */
    @JvmField
    var hp = 5f

    /**
     * The base slider velocity in hundreds of osu! pixels per beat.
     */
    @JvmField
    var sliderMultiplier = 1.0

    /**
     * The amount of slider ticks per beat.
     */
    @JvmField
    var sliderTickRate = 1.0

    public override fun clone() = super.clone() as BeatmapDifficultyManager
}
