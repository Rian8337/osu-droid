package ru.nsu.ccfit.zuev.osu;

import com.rian.osu.beatmap.Beatmap;
import com.rian.osu.beatmap.hitobject.HitObjectUtils;
import ru.nsu.ccfit.zuev.osu.game.GameHelper;
import com.rian.osu.difficultycalculator.BeatmapDifficultyCalculator;

import java.io.Serializable;

public class TrackInfo implements Serializable {
    private static final long serialVersionUID = 2049627581836712912L;

    private String filename;

    private String publicName;
    private String mode;
    private String creator;
    private String md5;
    private String background = null;
    private int beatmapID = 0;
    private int beatmapSetID = 0;
    private float difficulty;
    private float hpDrain;
    private float overallDifficulty;
    private float approachRate;
    private float circleSize;
    private float bpmMax = 0;
    private float bpmMin = Float.MAX_VALUE;
    private long musicLength = 0;
    private int hitCircleCount = 0;
    private int sliderCount = 0;
    private int spinnerCount = 0;
    private int totalHitObjectCount = 0;
    private int maxCombo = 0;

    private BeatmapInfo beatmap;

    public TrackInfo(BeatmapInfo beatmap) {
        this.beatmap = beatmap;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(final String filename) {
        this.filename = filename;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(final String mode) {
        this.mode = mode;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(final String creator) {
        this.creator = creator;
    }

    public float getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(final float difficulty) {
        this.difficulty = difficulty;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(final String background) {
        this.background = background;
    }

    public String getPublicName() {
        return publicName;
    }

    public void setPublicName(final String publicName) {
        this.publicName = publicName;
    }

    public BeatmapInfo getBeatmap() {
        return beatmap;
    }

    public void setBeatmap(BeatmapInfo beatmap) {
        this.beatmap = beatmap;
    }

    public float getHpDrain() {
        return hpDrain;
    }

    public void setHpDrain(float hpDrain) {
        this.hpDrain = hpDrain;
    }

    public float getOverallDifficulty() {
        return overallDifficulty;
    }

    public void setOverallDifficulty(float overallDifficulty) {
        this.overallDifficulty = overallDifficulty;
    }

    public float getApproachRate() {
        return approachRate;
    }

    public void setApproachRate(float approachRate) {
        this.approachRate = approachRate;
    }

    public float getCircleSize() {
        return circleSize;
    }

    public void setCircleSize(float circleSize) {
        this.circleSize = circleSize;
    }

    public float getBpmMax() {
        return bpmMax;
    }

    public void setBpmMax(float bpmMax) {
        this.bpmMax = bpmMax;
    }

    public float getBpmMin() {
        return bpmMin;
    }

    public void setBpmMin(float bpmMin) {
        this.bpmMin = bpmMin;
    }

    public long getMusicLength() {
        return musicLength;
    }

    public void setMusicLength(long musicLength) {
        this.musicLength = musicLength;
    }

    public int getHitCircleCount() {
        return hitCircleCount;
    }

    public void setHitCircleCount(int hitCircleCount) {
        this.hitCircleCount = hitCircleCount;
    }

    public int getSliderCount() {
        return sliderCount;
    }

    public void setSliderCount(int sliderCount) {
        this.sliderCount = sliderCount;
    }

    public int getSpinnerCount() {
        return spinnerCount;
    }

    public void setSpinnerCount(int spinnerCount) {
        this.spinnerCount = spinnerCount;
    }

    public int getTotalHitObjectCount() {
        return totalHitObjectCount;
    }

    public void setTotalHitObjectCount(int totalHitObjectCount) {
        this.totalHitObjectCount = totalHitObjectCount;
    }

    public int getBeatmapID() {
        return beatmapID;
    }

    public void setBeatmapID(int beatmapID) {
        this.beatmapID = beatmapID;
    }

    public int getBeatmapSetID() {
        return beatmapSetID;
    }

    public void setBeatmapSetID(int beatmapSetID) {
        this.beatmapSetID = beatmapSetID;
    }

    public int getMaxCombo() {
        return maxCombo;
    }

    public void setMaxCombo(int maxCombo) {
        this.maxCombo = maxCombo;
    }

    public void setMD5(String md5) {
        this.md5 = md5;
    }

    public String getMD5() {
        return md5;
    }

    /**
     * Given a <code>Beatmap</code>, populate the metadata of this <code>TrackInfo</code>
     * with that <code>Beatmap</code>.
     *
     * @param beatmap The <code>Beatmap</code> to populate.
     * @return Whether this <code>TrackInfo</code> was successfully populated.
     */
    public boolean populate(Beatmap beatmap) {
        md5 = beatmap.md5;

        var metadata = beatmap.getMetadata();
        creator = metadata.creator;
        mode = metadata.version;
        publicName = metadata.artist + " - " + metadata.title;
        beatmapID = metadata.beatmapID;
        beatmapSetID = metadata.beatmapSetID;

        // Difficulty
        var difficulty = beatmap.getDifficulty();
        overallDifficulty = difficulty.od;
        approachRate = difficulty.getAr();
        hpDrain = difficulty.hp;
        circleSize = difficulty.cs;

        // Events
        background = beatmap.folder + "/" + beatmap.getEvents().backgroundFilename;

        // Timing points
        for (var point : beatmap.getControlPoints().getTiming().getControlPoints()) {
            var bpm = (float) point.getBPM();

            bpmMin = (bpmMin != Float.MAX_VALUE ? Math.min(bpmMin, bpm) : bpm);
            bpmMax = (bpmMax != 0 ? Math.max(bpmMax, bpm) : bpm);
        }

        // Hit objects
        var hitObjects = beatmap.getHitObjects();
        if (hitObjects.getObjects().isEmpty()) {
            return false;
        }

        totalHitObjectCount = hitObjects.getObjects().size();
        hitCircleCount = hitObjects.getCircleCount();
        sliderCount = hitObjects.getSliderCount();
        spinnerCount = hitObjects.getSpinnerCount();

        var lastObject = hitObjects.getObjects().get(hitObjects.getObjects().size() - 1);

        musicLength = (int) HitObjectUtils.getEndTime(lastObject);
        maxCombo = beatmap.getMaxCombo();

        var attributes = BeatmapDifficultyCalculator.calculateDifficulty(beatmap);

        this.difficulty = GameHelper.Round(attributes.starRating, 2);

        return true;
    }

    // Sometimes when the library is reloaded there can be 2 instances for the same beatmap so checking its MD5 is the
    // proper way to compare
    @Override
    public boolean equals(Object o) {

        if (o == this)
            return true;

        if (o instanceof TrackInfo) {
            var track = (TrackInfo) o;

            return md5 != null
                    && track.md5 != null
                    && track.md5.equals(md5);
        }
        return false;
    }
}
