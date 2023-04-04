package ru.nsu.ccfit.zuev.osu.helper;

import com.rian.difficultycalculator.attributes.DifficultyAttributes;
import com.rian.difficultycalculator.attributes.PerformanceAttributes;
import com.rian.difficultycalculator.attributes.TimedDifficultyAttributes;
import com.rian.difficultycalculator.beatmap.BeatmapDifficultyManager;
import com.rian.difficultycalculator.beatmap.DifficultyBeatmap;
import com.rian.difficultycalculator.calculator.DifficultyCalculationParameters;
import com.rian.difficultycalculator.calculator.DifficultyCalculator;
import com.rian.difficultycalculator.calculator.PerformanceCalculationParameters;
import com.rian.difficultycalculator.calculator.PerformanceCalculator;

import java.util.List;

import ru.nsu.ccfit.zuev.osu.beatmap.BeatmapData;
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2;

/**
 * A helper class for operations relating to difficulty and performance calculation.
 */
public final class BeatmapDifficultyCalculator {
    private static final DifficultyCalculator difficultyCalculator = new DifficultyCalculator();

    /**
     * Constructs a <code>DifficultyBeatmap</code> from a <code>BeatmapData</code>.
     *
     * @param data The <code>BeatmapData</code> to construct the <code>DifficultyBeatmap</code> from.
     * @return The constructed <code>DifficultyBeatmap</code>.
     */
    public static DifficultyBeatmap constructDifficultyBeatmap(final BeatmapData data) {
        BeatmapDifficultyManager difficultyManager = new BeatmapDifficultyManager();
        difficultyManager.setCS(data.difficulty.cs);
        difficultyManager.setAR(data.difficulty.ar);
        difficultyManager.setOD(data.difficulty.od);
        difficultyManager.setHP(data.difficulty.hp);
        difficultyManager.setSliderMultiplier(data.difficulty.sliderMultiplier);
        difficultyManager.setSliderTickRate(data.difficulty.sliderTickRate);

        DifficultyBeatmap beatmap = new DifficultyBeatmap(difficultyManager, data.hitObjects);
        beatmap.setFormatVersion(data.getFormatVersion());
        beatmap.setStackLeniency(data.general.stackLeniency);

        return beatmap;
    }

    /**
     * Constructs a <code>DifficultyCalculationParameters</code> from a <code>StatisticV2</code>.
     *
     * @param stat The <code>StatisticV2</code> to construct the <code>DifficultyCalculationParameters</code> from.
     * @return The <code>DifficultyCalculationParameters</code> representing the <code>StatisticV2</code>,
     * <code>null</code> if the <code>StatisticV2</code> instance is <code>null</code>.
     */
    public static DifficultyCalculationParameters constructDifficultyParameters(final StatisticV2 stat) {
        if (stat == null) {
            return null;
        }

        DifficultyCalculationParameters parameters = new DifficultyCalculationParameters();

        parameters.mods = stat.getMod().clone();
        parameters.customSpeedMultiplier = stat.getChangeSpeed();

        if (stat.isEnableForceAR()) {
            parameters.forcedAR = stat.getForceAR();
        }

        return parameters;
    }

    /**
     * Constructs a <code>PerformanceCalculationParameters</code> from a <code>StatisticV2</code>.
     *
     * @param stat The <code>StatisticV2</code> to construct the <code>PerformanceCalculationParameters</code> from.
     * @return The <code>PerformanceCalculationParameters</code> representing the <code>StatisticV2</code>,
     * <code>null</code> if the <code>StatisticV2</code> instance is <code>null</code>.
     */
    public static PerformanceCalculationParameters constructPerformanceParameters(final StatisticV2 stat) {
        if (stat == null) {
            return null;
        }

        PerformanceCalculationParameters parameters = new PerformanceCalculationParameters();

        parameters.maxCombo = stat.getMaxCombo();
        parameters.countGreat = stat.getHit300();
        parameters.countOk = stat.getHit100();
        parameters.countMeh = stat.getHit50();
        parameters.countMiss = stat.getMisses();

        return parameters;
    }

    /**
     * Calculates the difficulty of a <code>DifficultyBeatmap</code>.
     *
     * @param beatmap The <code>DifficultyBeatmap</code> to calculate.
     * @return A structure describing the difficulty of the <code>DifficultyBeatmap</code>.
     */
    public static DifficultyAttributes calculateDifficulty(final DifficultyBeatmap beatmap) {
        return calculateDifficulty(beatmap, (DifficultyCalculationParameters) null);
    }

    /**
     * Calculates the difficulty of a <code>DifficultyBeatmap</code> given a <code>StatisticV2</code>.
     *
     * @param beatmap The <code>DifficultyBeatmap</code> to calculate.
     * @param stat The <code>StatisticV2</code> to calculate.
     * @return A structure describing the difficulty of the <code>DifficultyBeatmap</code>
     * relating to the <code>StatisticV2</code>.
     */
    public static DifficultyAttributes calculateDifficulty(
            final DifficultyBeatmap beatmap, final StatisticV2 stat) {
        return calculateDifficulty(beatmap, constructDifficultyParameters(stat));
    }

    /**
     * Calculates the difficulty of a <code>DifficultyBeatmap</code>.
     *
     * @param beatmap The <code>DifficultyBeatmap</code> to calculate.
     * @param parameters The parameters of the calculation. Can be <code>null</code>.
     * @return A structure describing the difficulty of the <code>DifficultyBeatmap</code>
     * relating to the calculation parameters.
     */
    public static DifficultyAttributes calculateDifficulty(
            final DifficultyBeatmap beatmap, final DifficultyCalculationParameters parameters) {
        return difficultyCalculator.calculate(beatmap, parameters);
    }

    /**
     * Calculates the difficulty of a <code>DifficultyBeatmap</code>, returning a set of
     * <code>TimedDifficultyAttributes</code> representing the difficulty of the beatmap
     * at any relevant time.
     *
     * @param beatmap The <code>DifficultyBeatmap</code> to calculate.
     * @return A set of <code>TimedDifficultyAttributes</code> describing the difficulty of
     * the <code>DifficultyBeatmap</code> at any relevant time.
     */
    public static List<TimedDifficultyAttributes> calculateTimedDifficulty(
            final DifficultyBeatmap beatmap) {
        return calculateTimedDifficulty(beatmap, (DifficultyCalculationParameters) null);
    }

    /**
     * Calculates the difficulty of a <code>DifficultyBeatmap</code>, returning a set of
     * <code>TimedDifficultyAttributes</code> representing the difficulty of the beatmap
     * at any relevant time.
     *
     * @param beatmap The <code>DifficultyBeatmap</code> to calculate.
     * @param stat The <code>StatisticV2</code> to calculate.
     * @return A set of <code>TimedDifficultyAttributes</code> describing the difficulty of
     * the <code>DifficultyBeatmap</code> at any relevant time relating to the <code>StatisticV2</code>.
     */
    public static List<TimedDifficultyAttributes> calculateTimedDifficulty(
            final DifficultyBeatmap beatmap, final StatisticV2 stat) {
        return calculateTimedDifficulty(beatmap, constructDifficultyParameters(stat));
    }

    /**
     * Calculates the difficulty of a <code>DifficultyBeatmap</code> given a <code>StatisticV2</code>,
     * returning a set of <code>TimedDifficultyAttributes</code> representing the difficulty of the
     * beatmap at any relevant time.
     *
     * @param beatmap The <code>DifficultyBeatmap</code> to calculate.
     * @param parameters The parameters of the calculation. Can be <code>null</code>.
     * @return A set of <code>TimedDifficultyAttributes</code> describing the difficulty of
     * the <code>DifficultyBeatmap</code> at any relevant time relating to the calculation parameters.
     */
    public static List<TimedDifficultyAttributes> calculateTimedDifficulty(
            final DifficultyBeatmap beatmap, final DifficultyCalculationParameters parameters) {
        return difficultyCalculator.calculateTimed(beatmap, parameters);
    }

    /**
     * Calculates the performance of a <code>DifficultyAttributes</code>.
     *
     * @param attributes The <code>DifficultyAttributes</code> to calculate.
     * @return A structure describing the performance of the <code>DifficultyAttributes</code>.
     */
    public static PerformanceAttributes calculatePerformance(
            final DifficultyAttributes attributes) {
        return calculatePerformance(attributes, (PerformanceCalculationParameters) null);
    }

    /**
     * Calculates the performance of a <code>DifficultyAttributes</code> given a <code>StatisticV2</code>.
     *
     * @param attributes The <code>DifficultyAttributes</code> to calculate.
     * @param stat The <code>StatisticV2</code> to calculate.
     * @return A structure describing the performance of the <code>DifficultyAttributes</code>
     * relating to the <code>StatisticV2</code>.
     */
    public static PerformanceAttributes calculatePerformance(
            final DifficultyAttributes attributes, final StatisticV2 stat) {
        return calculatePerformance(attributes, constructPerformanceParameters(stat));
    }

    /**
     * Calculates the performance of a <code>DifficultyAttributes</code>.
     *
     * @param attributes The <code>DifficultyAttributes</code> to calculate.
     * @param parameters The parameters of the calculation. Can be <code>null</code>.
     * @return A structure describing the performance of the <code>DifficultyAttributes</code>
     * relating to the calculation parameters.
     */
    public static PerformanceAttributes calculatePerformance(
            final DifficultyAttributes attributes, final PerformanceCalculationParameters parameters) {
        return new PerformanceCalculator(attributes).calculate(parameters);
    }
}