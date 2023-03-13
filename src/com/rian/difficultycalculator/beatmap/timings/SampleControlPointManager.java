package com.rian.difficultycalculator.beatmap.timings;

import com.rian.difficultycalculator.beatmap.constants.SampleBank;

/**
 * A manager for sample control points.
 */
public class SampleControlPointManager extends ControlPointManager<SampleControlPoint> {
    public SampleControlPointManager() {
        super(new SampleControlPoint(0, SampleBank.normal, 100, 0));
    }

    /**
     * Copy constructor.
     *
     * @param source The source to copy from.
     */
    private SampleControlPointManager(SampleControlPointManager source) {
        super(source.defaultControlPoint);

        for (SampleControlPoint point : source.controlPoints) {
            controlPoints.add(point.deepClone());
        }
    }

    @Override
    public SampleControlPoint controlPointAt(double time) {
        return binarySearchWithFallback(time);
    }

    @Override
    public SampleControlPointManager deepClone() {
        return new SampleControlPointManager(this);
    }
}
