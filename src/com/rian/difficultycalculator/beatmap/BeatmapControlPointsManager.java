package com.rian.difficultycalculator.beatmap;

import com.rian.difficultycalculator.beatmap.timings.DifficultyControlPointManager;
import com.rian.difficultycalculator.beatmap.timings.EffectControlPointManager;
import com.rian.difficultycalculator.beatmap.timings.SampleControlPointManager;
import com.rian.difficultycalculator.beatmap.timings.TimingControlPointManager;

/**
 * A manager for beatmap control points.
 */
public class BeatmapControlPointsManager {
    /**
     * The manager for timing control points of this beatmap.
     */
    public final TimingControlPointManager timing;

    /**
     * The manager for difficulty control points of this beatmap.
     */
    public final DifficultyControlPointManager difficulty;

    /**
     * The manager for effect control points of this beatmap.
     */
    public final EffectControlPointManager effect;

    /**
     * The manager for sample control points of this beatmap.
     */
    public final SampleControlPointManager sample;

    public BeatmapControlPointsManager() {
        timing = new TimingControlPointManager();
        difficulty = new DifficultyControlPointManager();
        effect = new EffectControlPointManager();
        sample = new SampleControlPointManager();
    }

    /**
     * Copy constructor.
     *
     * @param source The source to copy from.
     */
    private BeatmapControlPointsManager(BeatmapControlPointsManager source) {
        timing = source.timing.deepClone();
        difficulty = source.difficulty.deepClone();
        effect = source.effect.deepClone();
        sample = source.sample.deepClone();
    }

    /**
     * Deep clones this manager.
     *
     * @return The deep cloned manager.
     */
    public BeatmapControlPointsManager deepClone() {
        return new BeatmapControlPointsManager(this);
    }
}
