package main.osu.beatmap.sections;

import com.rian.difficultycalculator.beatmap.constants.SampleBank;

import main.osu.beatmap.constants.BeatmapCountdown;

/**
 * Contains general information about a beatmap.
 */
public class BeatmapGeneral {
    /**
     * The location of the audio file relative to the beatmapset file.
     */
    public String audioFilename = "";

    /**
     * The amount of milliseconds of silence before the audio starts playing.
     */
    public int audioLeadIn;

    /**
     * The time in milliseconds when the audio preview should start.
     *
     * If -1, the audio should begin playing at 40% of its length.
     */
    public int previewTime = -1;

    /**
     * The speed of the countdown before the first hit object.
     */
    public BeatmapCountdown countdown = BeatmapCountdown.normal;

    /**
     * The sample bank that will be used if timing points do not override it.
     */
    public SampleBank sampleBank = SampleBank.normal;

    /**
     * The sample volume that will be used if timing points do not override it.
     */
    public int sampleVolume = 100;

    /**
     * The multiplier for the threshold in time where hit objects
     * placed close together stack, ranging from 0 to 1.
     */
    public float stackLeniency = 0.7f;

    public BeatmapGeneral() {}

    /**
     * Copy constructor.
     *
     * @param source The source to copy from.
     */
    private BeatmapGeneral(BeatmapGeneral source) {
        audioFilename = source.audioFilename;
        audioLeadIn = source.audioLeadIn;
        previewTime = source.previewTime;
        countdown = source.countdown;
        sampleBank = source.sampleBank;
        sampleVolume = source.sampleVolume;
        stackLeniency = source.stackLeniency;
    }

    /**
     * Deep clones this instance.
     *
     * @return The deep cloned instance.
     */
    public BeatmapGeneral deepClone() {
        return new BeatmapGeneral(this);
    }
}