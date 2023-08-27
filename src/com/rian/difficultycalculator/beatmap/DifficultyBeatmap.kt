package com.rian.difficultycalculator.beatmap

import com.rian.difficultycalculator.beatmap.hitobject.Slider

/**
 * A beatmap structure containing necessary information for difficulty and performance calculation.
 */
class DifficultyBeatmap(
    /**
     * The manager for difficulty settings of this beatmap. If provided, the manager will be cloned.
     */
    difficultyManager: BeatmapDifficultyManager? = null,

    /**
     * The manager for hit objects of this beatmap. If provided, the manager will be cloned.
     */
    hitObjectsManager: BeatmapHitObjectsManager? = null
) : Cloneable {
    /**
     * The format version of this beatmap.
     */
    @JvmField
    var formatVersion = 14

    /**
     * The multiplier for the threshold in time where hit objects placed close together stack, ranging from 0 to 1.
     */
    @JvmField
    var stackLeniency = 0.7f

    /**
     * The manager for difficulty settings of this beatmap.
     */
    var difficultyManager = difficultyManager?.clone() ?: BeatmapDifficultyManager()
        private set

    /**
     * The manager for hit objects of this beatmap.
     */
    var hitObjectsManager = hitObjectsManager?.clone() ?: BeatmapHitObjectsManager()
        private set

    /**
     * Returns a time combined with beatmap-wide time offset.
     *
     * Beatmap version 4 and lower had an incorrect offset. Stable has this set as 24ms off.
     *
     * @param time The time.
     */
    fun getOffsetTime(time: Double) = time + if (formatVersion < 5) 24 else 0

    /**
     * The max combo of this beatmap.
     */
    val maxCombo: Int
        get() {
            var combo = 0

            for (obj in hitObjectsManager.getObjects()) {
                ++combo
                if (obj is Slider) {
                    combo += obj.nestedHitObjects.size - 1
                }
            }

            return combo
        }

    public override fun clone() =
        (super.clone() as DifficultyBeatmap).apply {
            difficultyManager = this@DifficultyBeatmap.difficultyManager.clone()
            hitObjectsManager = this@DifficultyBeatmap.hitObjectsManager.clone()
        }
}
