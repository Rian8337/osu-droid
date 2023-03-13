package com.rian.difficultycalculator.beatmap.timings;

/**
 * A manager for effect control points.
 */
public class EffectControlPointManager extends ControlPointManager<EffectControlPoint> {
    public EffectControlPointManager() {
        super(new EffectControlPoint(0, false));
    }

    /**
     * Copy constructor.
     *
     * @param source The source to copy from.
     */
    private EffectControlPointManager(EffectControlPointManager source) {
        super(source.defaultControlPoint);

        for (EffectControlPoint point : source.controlPoints) {
            controlPoints.add(point.deepClone());
        }
    }

    @Override
    public EffectControlPoint controlPointAt(double time) {
        return binarySearchWithFallback(time);
    }

    @Override
    public EffectControlPointManager deepClone() {
        return new EffectControlPointManager(this);
    }
}
