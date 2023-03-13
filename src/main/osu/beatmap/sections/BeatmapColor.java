package main.osu.beatmap.sections;

import java.util.ArrayList;

import main.osu.RGBColor;
import main.osu.beatmap.ComboColor;

/**
 * Contains information about combo and skin colors of a beatmap.
 */
public class BeatmapColor {
    /**
     * The combo colors of this beatmap.
     */
    public ArrayList<ComboColor> comboColors = new ArrayList<>();

    /**
     * The color of the slider border.
     */
    public RGBColor sliderBorderColor;

    public BeatmapColor() {}

    /**
     * Copy constructor.
     *
     * @param source The source to copy from.
     */
    private BeatmapColor(BeatmapColor source) {
        for (ComboColor color : source.comboColors) {
            comboColors.add(color.deepClone());
        }

        sliderBorderColor = source.sliderBorderColor != null ? new RGBColor(source.sliderBorderColor) : null;
    }

    /**
     * Deep clones this instance.
     *
     * @return The deep cloned instance.
     */
    public BeatmapColor deepClone() {
        return new BeatmapColor(this);
    }
}
