package com.rian.difficultycalculator.beatmap

import com.rian.difficultycalculator.beatmap.timings.DifficultyControlPointManager
import com.rian.difficultycalculator.beatmap.timings.TimingControlPointManager

/**
 * A manager for beatmap control points.
 */
class BeatmapControlPointsManager : Cloneable {
    /**
     * The manager for timing control points of this beatmap.
     */
    var timing = TimingControlPointManager()
        private set

    /**
     * The manager for difficulty control points of this beatmap.
     */
    var difficulty = DifficultyControlPointManager()
        private set

    override fun clone() =
        (super.clone() as BeatmapControlPointsManager).apply {
            timing = this@BeatmapControlPointsManager.timing.clone()
            difficulty = this@BeatmapControlPointsManager.difficulty.clone()
        }
}
