package main.osu.beatmap.parser.sections;

import com.rian.difficultycalculator.beatmap.constants.HitObjectType;
import com.rian.difficultycalculator.beatmap.hitobject.HitCircle;
import com.rian.difficultycalculator.beatmap.hitobject.HitObject;
import com.rian.difficultycalculator.beatmap.hitobject.Slider;
import com.rian.difficultycalculator.beatmap.hitobject.SliderPath;
import com.rian.difficultycalculator.beatmap.hitobject.SliderPathType;
import com.rian.difficultycalculator.beatmap.hitobject.Spinner;
import com.rian.difficultycalculator.beatmap.timings.DifficultyControlPoint;
import com.rian.difficultycalculator.beatmap.timings.TimingControlPoint;
import com.rian.difficultycalculator.math.Vector2;

import java.util.ArrayList;

import main.osu.Utils;
import main.osu.beatmap.BeatmapData;

/**
 * A parser for parsing a beatmap's hit objects section.
 */
public class BeatmapHitObjectsParser extends BeatmapSectionParser {
    private final boolean parseHitObjects;

    /**
     * @param parseHitObjects Whether to also parse information of hit objects (such as circles,
     *                        slider paths, and spinners).
     *                        <br>
     *                        Parsed hit objects will be added to the
     *                        <code>BeatmapHitObjectsManager</code> of a <code>BeatmapData</code>.
     *
     */
    public BeatmapHitObjectsParser(boolean parseHitObjects) {
        super();

        this.parseHitObjects = parseHitObjects;
    }

    @Override
    public boolean parse(BeatmapData data, String line) {
        final String[] pars = line.split(",");

        if (pars.length < 4) {
            // Malformed hit object
            return false;
        }

        data.rawHitObjects.add(line);

        if (!parseHitObjects) {
            return true;
        }

        int time = data.getOffsetTime(Utils.tryParseInt(pars[2], -1));

        HitObjectType type = HitObjectType.valueOf(Utils.tryParseInt(pars[3], -1) % 16);
        Vector2 position = new Vector2(
            Utils.tryParseFloat(pars[0], Float.NaN),
            Utils.tryParseFloat(pars[1], Float.NaN)
        );

        if (Double.isNaN(position.x) || Double.isNaN(position.y)) {
            return false;
        }

        HitObject object = null;

        if (type == HitObjectType.Normal || type == HitObjectType.NormalNewCombo) {
            object = createCircle(time, position);
        } else if (type == HitObjectType.Slider || type == HitObjectType.SliderNewCombo) {
            object = createSlider(data, time, position, pars);
        } else if (type == HitObjectType.Spinner) {
            object = createSpinner(data, time, pars);
        }

        if (object == null) {
            return false;
        }

        data.hitObjects.add(object);

        return true;
    }

    private HitCircle createCircle(int time, Vector2 position) {
        return new HitCircle(time, position);
    }

    private Slider createSlider(BeatmapData data, int time, Vector2 position, String[] pars) {
        // Handle malformed slider
        if (pars.length < 8) {
            return null;
        }

        String[] curvePointsData = pars[5].split("[|]");
        SliderPathType sliderType = SliderPathType.parse(curvePointsData[0].charAt(0));
        ArrayList<Vector2> curvePoints = new ArrayList<>();
        for (int i = 1; i < curvePointsData.length; i++) {
            String[] curvePointData = curvePointsData[i].split(":");
            Vector2 curvePointPosition = new Vector2(
                    Utils.tryParseFloat(curvePointData[0], Float.NaN),
                    Utils.tryParseFloat(curvePointData[1], Float.NaN)
            );

            if (Double.isNaN(curvePointPosition.x) || Double.isNaN(curvePointPosition.y)) {
                return null;
            }

            curvePoints.add(curvePointPosition);
        }

        int repeat = Utils.tryParseInt(pars[6], -1);
        float rawLength = Utils.tryParseFloat(pars[7], Float.NaN);
        if (repeat < 0 || Float.isNaN(rawLength)) {
            return null;
        }

        SliderPath path = new SliderPath(sliderType, curvePoints, rawLength);
        TimingControlPoint timingControlPoint = data.timingPoints.timing.controlPointAt(time);
        DifficultyControlPoint difficultyControlPoint = data.timingPoints.difficulty.controlPointAt(time);

        return new Slider(
                time,
                position,
                timingControlPoint,
                difficultyControlPoint,
                repeat,
                path,
                data.difficulty.sliderMultiplier,
                data.difficulty.sliderTickRate,
                // Prior to v8, speed multipliers don't adjust for how many ticks are generated over the same distance.
                // this results in more (or less) ticks being generated in <v8 maps for the same time duration.
                data.getFormatVersion() < 8 ? 1 / difficultyControlPoint.speedMultiplier : 1
        );
    }

    private Spinner createSpinner(BeatmapData data, int time, String[] pars) {
        int endTime = Utils.tryParseInt(pars[5], -1);
        if (endTime < 0) {
            return null;
        }
        endTime = data.getOffsetTime(endTime);

        return new Spinner(time, endTime);
    }
}
