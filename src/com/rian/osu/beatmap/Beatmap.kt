package com.rian.osu.beatmap

import com.rian.osu.beatmap.hitobject.Slider
import com.rian.osu.beatmap.sections.*

/**
 * Represents a beatmap.
 */
class Beatmap : Cloneable {
    /**
     * The format version of this beatmap.
     */
    @JvmField
    var formatVersion = 14

    /**
     * The general section of this beatmap.
     */
    var general = BeatmapGeneral()
        private set

    /**
     * The metadata section of this beatmap.
     */
    var metadata = BeatmapMetadata()
        private set

    /**
     * The difficulty section of this beatmap.
     */
    var difficulty = BeatmapDifficulty()
        private set

    /**
     * The events section of this beatmap.
     */
    var events = BeatmapEvents()
        private set

    /**
     * The colors section of this beatmap.
     */
    var colors = BeatmapColor()
        private set

    /**
     * The control points of this beatmap.
     */
    var controlPoints = BeatmapControlPoints()
        private set

    /**
     * The hit objects of this beatmap.
     */
    var hitObjects = BeatmapHitObjects()
        private set

    /**
     * Raw timing points data.
     */
    var rawTimingPoints = mutableListOf<String>()
        private set

    /**
     * Raw hit objects data.
     */
    var rawHitObjects = mutableListOf<String>()
        private set

    /**
     * The path of parent folder of this beatmap.
     */
    @JvmField
    var folder: String? = null

    /**
     * The name of the `.osu` file of this beatmap.
     */
    @JvmField
    var filename = ""

    /**
     * The MD5 hash of this beatmap.
     */
    @JvmField
    var md5 = ""

    /**
     * Returns a time combined with beatmap-wide time offset.
     *
     * Beatmap version 4 and lower had an incorrect offset. Stable has this set as 24ms off.
     *
     * @param time The time.
     */
    fun getOffsetTime(time: Double) = time + if (formatVersion < 5) 24 else 0

    /**
     * Returns a time combined with beatmap-wide time offset.
     *
     * Beatmap version 4 and lower had an incorrect offset. Stable has this set as 24ms off.
     *
     * @param time The time.
     */
    fun getOffsetTime(time: Int) = time + if (formatVersion < 5) 24 else 0

    /**
     * Gets the max combo of this beatmap.
     */
    val maxCombo: Int
        get() = hitObjects.getObjects().sumOf {
            if (it is Slider) it.nestedHitObjects.size else 1
        }

    public override fun clone() =
        (super.clone() as Beatmap).apply {
            general = this@Beatmap.general.copy()
            metadata = this@Beatmap.metadata.copy()
            difficulty = this@Beatmap.difficulty.clone()
            events = this@Beatmap.events.clone()
            colors = this@Beatmap.colors.clone()
            controlPoints = this@Beatmap.controlPoints.clone()
            hitObjects = this@Beatmap.hitObjects.clone()
        }
}