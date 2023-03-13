package main.osu.beatmap.sections;

import java.util.ArrayList;

import main.osu.RGBColor;
import main.osu.game.BreakPeriod;

/**
 * Contains beatmap events.
 */
public class BeatmapEvents {
    /**
     * The file name of this beatmap's background.
     */
    public String backgroundFilename;

    /**
     * The breaks this beatmap has.
     */
    public ArrayList<BreakPeriod> breaks = new ArrayList<>();

    /**
     * The background color of this beatmap.
     */
    public RGBColor backgroundColor;

    public BeatmapEvents() {}

    /**
     * Copy constructor.
     *
     * @param source The source to copy from.
     */
    private BeatmapEvents(BeatmapEvents source) {
        backgroundFilename = source.backgroundFilename;

        for (BreakPeriod breakPeriod : source.breaks) {
            breaks.add(new BreakPeriod(breakPeriod.getStart(), breakPeriod.getEndTime()));
        }

        backgroundColor = source.backgroundColor != null ? new RGBColor(backgroundColor) : null;
    }

    /**
     * Deep clones this instance.
     *
     * @return The deep cloned instance.
     */
    public BeatmapEvents deepClone() {
        return new BeatmapEvents(this);
    }
}
