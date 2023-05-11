package com.edlplan.osu.support.timing.controlpoint;

import com.edlplan.osu.support.timing.TimingPoint;
import com.edlplan.osu.support.timing.TimingPoints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ControlPoints {
    private final ArrayList<TimingControlPoint> timingPoints = new ArrayList<>();

    private final ArrayList<DifficultyControlPoint> difficultyPoints = new ArrayList<>();

    private final ArrayList<EffectControlPoint> effectPoints = new ArrayList<>();

    private final ArrayList<SampleControlPoint> samplePoints = new ArrayList<>();

    public void load(TimingPoints points) {
        ArrayList<TimingPoint> res = points.getTimingPointList();
        TimingControlPoint preTp;
        DifficultyControlPoint preDcp;
        EffectControlPoint preEcp;
        SampleControlPoint preScp;
        if (res.size() < 1) {
            throw new IllegalArgumentException("a beatmap must has at least 1 timing point");
        }

        // Get first uninherited timing point
        int tmpIndex = -1;
        for (int i = 0; i < res.size(); i++) {
            if (!res.get(i).isInherited()) {
                tmpIndex = i;
                break;
            }
        }

        TimingPoint tmp;

        if (tmpIndex == -1) {
            tmp = new TimingPoint();
            tmp.setTime(0);
            tmp.setBeatLength(1000);
            tmp.setMeter(4);
        } else {
            tmp = res.get(tmpIndex);
        }

        preTp = new TimingControlPoint();
        preTp.setTime(tmp.getTime());
        preTp.setBeatLength(tmp.getBeatLength());
        preTp.setMeter(tmp.getMeter());
        timingPoints.add(preTp);

        final TimingPoint firstTp = res.get(0);
        preDcp = new DifficultyControlPoint();
        preDcp.setTime(firstTp.getTime());
        preDcp.setSpeedMultiplier(firstTp.getSpeedMultiplier());
        difficultyPoints.add(preDcp);

        preScp = new SampleControlPoint();
        preScp.setTime(firstTp.getTime());
        samplePoints.add(preScp);

        preEcp = new EffectControlPoint();
        preEcp.setTime(firstTp.getTime());
        preEcp.setKiaiModeOn(firstTp.isKiaiMode());
        preEcp.setOmitFirstBarLine(firstTp.isOmitFirstBarSignature());
        effectPoints.add(preEcp);

        for (int i = 0; i < res.size(); i++) {
            tmp = res.get(i);
            if (!tmp.isInherited()) {
                preTp = new TimingControlPoint();
                preTp.setTime(tmp.getTime());
                preTp.setBeatLength(tmp.getBeatLength());
                preTp.setMeter(tmp.getMeter());
                timingPoints.add(preTp);
            }

            if (preDcp.getSpeedMultiplier() != tmp.getSpeedMultiplier()) {
                if (preDcp.getTime() == tmp.getTime()) {
                    //当控制线重合的时候，只保留绿线
                    if (tmp.isInherited()) {
                        difficultyPoints.remove(preDcp);
                        preDcp = new DifficultyControlPoint();
                        preDcp.setTime(tmp.getTime());
                        preDcp.setSpeedMultiplier(tmp.getSpeedMultiplier());
                        preDcp.setAutoGenerated(tmp.isInherited());
                        difficultyPoints.add(preDcp);
                    }
                } else {
                    preDcp = new DifficultyControlPoint();
                    preDcp.setTime(tmp.getTime());
                    preDcp.setSpeedMultiplier(tmp.getSpeedMultiplier());
                    preDcp.setAutoGenerated(tmp.isInherited());

                    difficultyPoints.add(preDcp);
                }

                //handleDifficultyControlPoint(preDcp);
            }

            //添加Sample相关控制点，暂时跳过

            if (tmp.isKiaiMode() != preEcp.isKiaiModeOn() || tmp.isOmitFirstBarSignature() != preEcp.isOmitFirstBarLine()) {
                preEcp = new EffectControlPoint();
                preEcp.setTime(tmp.getTime());
                preEcp.setKiaiModeOn(tmp.isKiaiMode());
                preEcp.setOmitFirstBarLine(tmp.isOmitFirstBarSignature());
                effectPoints.add(preEcp);
            }
        }
        Collections.sort(timingPoints, (a, b) -> Double.compare(a.getTime(), b.getTime()));
        Collections.sort(difficultyPoints, (a, b) -> Double.compare(a.getTime(), b.getTime()));
        Collections.sort(effectPoints, (a, b) -> Double.compare(a.getTime(), b.getTime()));

        /*Log.v("ControlPoints", "t: " + timingPoints.size());
        Log.v("ControlPoints", "d: " + difficultyPoints.size());
        Log.v("ControlPoints", "s: " + samplePoints.size());
        Log.v("ControlPoints", "e: " + effectPoints.size());*/
    }

    private void handleDifficultyControlPoint(DifficultyControlPoint newPoint) {
        DifficultyControlPoint existing = getDifficultyPointAt(newPoint.getTime());

        if (existing.getTime() == newPoint.getTime()) {
            // autogenerated points should not replace non-autogenerated.
            // this allows for incorrectly ordered timing points to still be correctly handled.
            if (newPoint.isAutoGenerated() && !existing.isAutoGenerated())
                return;

            difficultyPoints.remove(existing);
        }

        difficultyPoints.add(newPoint);
        Collections.sort(difficultyPoints, (a, b) -> Double.compare(a.getTime(), b.getTime()));
    }

    public TimingControlPoint getTimingPointAt(double time) {
        if (timingPoints.size() == 0) {
            return null;
        }
        return binarySearch(timingPoints, time, timingPoints.get(0));
    }

    public EffectControlPoint getEffectPointAt(double time) {
        if (effectPoints.size() == 0) {
            return null;
        }
        return binarySearch(effectPoints, time, effectPoints.get(0));
    }

    public SampleControlPoint getSamplePointAt(double time) {
        if (samplePoints.size() == 0) {
            return null;
        }
        return binarySearch(samplePoints, time, samplePoints.get(0));
    }

    public DifficultyControlPoint getDifficultyPointAt(double time) {
        if (difficultyPoints.size() == 0) {
            return null;
        }
        DifficultyControlPoint difficultyControlPoint = binarySearch(difficultyPoints, time, difficultyPoints.get(0));
        if (difficultyControlPoint == null) {
            return new DifficultyControlPoint();
        } else {
            return difficultyControlPoint;
        }
    }

    private <T extends ControlPoint> T binarySearch(List<T> list, double time, T prePoint) {

        if (list.size() == 0)
            return null;

        if (time < list.get(0).getTime())
            return prePoint;

        if (time >= list.get(list.size() - 1).getTime())
            return list.get(list.size() - 1);

        int l = 0;
        int r = list.size() - 2;

        while (l <= r) {
            int pivot = l + ((r - l) >> 1);

            if (list.get(pivot).getTime() < time)
                l = pivot + 1;
            else if (list.get(pivot).getTime() > time)
                r = pivot - 1;
            else
                return list.get(pivot);
        }

        return list.get(l - 1);
    }
}
