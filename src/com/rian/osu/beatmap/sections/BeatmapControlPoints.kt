package com.rian.osu.beatmap.sections

import com.rian.osu.beatmap.timings.DifficultyControlPointManager
import com.rian.osu.beatmap.timings.EffectControlPointManager
import com.rian.osu.beatmap.timings.SampleControlPointManager
import com.rian.osu.beatmap.timings.TimingControlPointManager

/**
* Contains information about the timing (control) points of a beatmap.
*/
class BeatmapControlPoints {
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

    /**
     * The manager for effect control points of this beatmap.
     */
    var effect = EffectControlPointManager()
        private set

    /**
     * The manager for sample control points of this beatmap.
     */
    var sample = SampleControlPointManager()
        private set
}