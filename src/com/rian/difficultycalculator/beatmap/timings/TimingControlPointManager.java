package com.rian.difficultycalculator.beatmap.timings;

/**
 * A manager for timing control points.
 */
public class TimingControlPointManager extends ControlPointManager<TimingControlPoint> {
    public TimingControlPointManager() {
        super(new TimingControlPoint(0, 1000, 4));
    }

    @Override
    public TimingControlPoint controlPointAt(double time) {
        return binarySearchWithFallback(time, controlPoints.size() > 0 ? controlPoints.get(0) : defaultControlPoint);
    }
}
