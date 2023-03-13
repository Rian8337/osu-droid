package main.osu.helper;

import com.rian.difficultycalculator.attributes.ExtendedRimuDifficultyAttributes;
import com.rian.difficultycalculator.attributes.RimuDifficultyAttributes;
import com.rian.difficultycalculator.attributes.RimuPerformanceAttributes;
import com.rian.difficultycalculator.attributes.StandardDifficultyAttributes;
import com.rian.difficultycalculator.attributes.StandardPerformanceAttributes;
import com.rian.difficultycalculator.beatmap.BeatmapDifficultyManager;
import com.rian.difficultycalculator.beatmap.DifficultyBeatmap;
import com.rian.difficultycalculator.calculator.DifficultyCalculationParameters;
import com.rian.difficultycalculator.calculator.PerformanceCalculationParameters;
import com.rian.difficultycalculator.calculator.RimuDifficultyCalculator;
import com.rian.difficultycalculator.calculator.RimuPerformanceCalculator;
import com.rian.difficultycalculator.calculator.StandardDifficultyCalculator;
import com.rian.difficultycalculator.calculator.StandardPerformanceCalculator;

import java.util.EnumSet;

import main.osu.beatmap.BeatmapData;
import main.osu.beatmap.parser.sections.BeatmapHitObjectsParser;
import main.osu.scoring.StatisticV2;

/**
 * A helper class for operations relating to difficulty and performance calculation.
 */
public final class BeatmapDifficultyCalculator {
    private static final RimuDifficultyCalculator rimuDifficultyCalculator = new RimuDifficultyCalculator();
    private static final StandardDifficultyCalculator standardDifficultyCalculator = new StandardDifficultyCalculator();

    /**
     * Constructs a <code>DifficultyBeatmap</code> from a <code>BeatmapData</code>.
     *
     * @param data The <code>BeatmapData</code> to construct the <code>DifficultyBeatmap</code> from.
     * @return The constructed <code>DifficultyBeatmap</code>.
     */
    public static DifficultyBeatmap constructDifficultyBeatmap(final BeatmapData data) {
        if (data.hitObjects.getObjects().isEmpty() && !data.rawHitObjects.isEmpty()) {
            BeatmapHitObjectsParser parser = new BeatmapHitObjectsParser(true);

            for (String s : data.rawHitObjects) {
                parser.parse(data, s);

                // Remove the last object in raw hit object to undo the process done by the parser.
                data.rawHitObjects.remove(data.rawHitObjects.size() - 1);
            }
        }

        BeatmapDifficultyManager difficultyManager = new BeatmapDifficultyManager();
        difficultyManager.setCS(data.difficulty.cs);
        difficultyManager.setAR(data.difficulty.ar);
        difficultyManager.setOD(data.difficulty.od);
        difficultyManager.setHP(data.difficulty.hp);
        difficultyManager.setSliderMultiplier(data.difficulty.sliderMultiplier);
        difficultyManager.setSliderTickRate(data.difficulty.sliderTickRate);

        return new DifficultyBeatmap(difficultyManager, data.hitObjects);
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

        parameters.mods = EnumSet.copyOf(stat.getMod());
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
     * Calculates the rimu! difficulty of a <code>DifficultyBeatmap</code>.
     *
     * @param beatmap The <code>DifficultyBeatmap</code> to calculate.
     * @return A structure describing the rimu! difficulty of the <code>DifficultyBeatmap</code>.
     */
    public static ExtendedRimuDifficultyAttributes calculateRimuDifficulty(final DifficultyBeatmap beatmap) {
        return calculateRimuDifficulty(beatmap, (DifficultyCalculationParameters) null);
    }

    /**
     * Calculates the rimu! difficulty of a <code>DifficultyBeatmap</code> given a <code>StatisticV2</code>.
     *
     * @param beatmap The <code>DifficultyBeatmap</code> to calculate.
     * @param stat The <code>StatisticV2</code> to calculate.
     * @return A structure describing the rimu! difficulty of the <code>DifficultyBeatmap</code>
     * relating to the <code>StatisticV2</code>.
     */
    public static ExtendedRimuDifficultyAttributes calculateRimuDifficulty(
            final DifficultyBeatmap beatmap, final StatisticV2 stat) {
        return calculateRimuDifficulty(beatmap, constructDifficultyParameters(stat));
    }

    /**
     * Calculates the rimu! difficulty of a <code>DifficultyBeatmap</code>.
     *
     * @param beatmap The <code>DifficultyBeatmap</code> to calculate.
     * @param parameters The parameters of the calculation. Can be <code>null</code>.
     * @return A structure describing the rimu! difficulty of the <code>DifficultyBeatmap</code>.
     */
    public static ExtendedRimuDifficultyAttributes calculateRimuDifficulty(
            final DifficultyBeatmap beatmap, final DifficultyCalculationParameters parameters) {
        return (ExtendedRimuDifficultyAttributes) rimuDifficultyCalculator.calculate(beatmap, parameters);
    }

    /**
     * Calculates the osu!standard difficulty of a <code>DifficultyBeatmap</code>.
     *
     * @param beatmap The <code>DifficultyBeatmap</code> to calculate.
     * @return A structure describing the osu!standard difficulty of the <code>DifficultyBeatmap</code>.
     */
    public static StandardDifficultyAttributes calculateStandardDifficulty(final DifficultyBeatmap beatmap) {
        return calculateStandardDifficulty(beatmap, (DifficultyCalculationParameters) null);
    }

    /**
     * Calculates the osu!standard difficulty of a <code>DifficultyBeatmap</code> given a <code>StatisticV2</code>.
     *
     * @param beatmap The <code>DifficultyBeatmap</code> to calculate.
     * @param stat The <code>StatisticV2</code> to calculate.
     * @return A structure describing the osu!standard difficulty of the <code>DifficultyBeatmap</code>
     * relating to the <code>StatisticV2</code>.
     */
    public static StandardDifficultyAttributes calculateStandardDifficulty(
            final DifficultyBeatmap beatmap, final StatisticV2 stat) {
        return calculateStandardDifficulty(beatmap, constructDifficultyParameters(stat));
    }

    /**
     * Calculates the osu!standard difficulty of a <code>DifficultyBeatmap</code>.
     *
     * @param beatmap The <code>DifficultyBeatmap</code> to calculate.
     * @param parameters The parameters of the calculation. Can be <code>null</code>.
     * @return A structure describing the osu!standard difficulty of the <code>DifficultyBeatmap</code>
     * relating to the calculation parameters.
     */
    public static StandardDifficultyAttributes calculateStandardDifficulty(
            final DifficultyBeatmap beatmap, final DifficultyCalculationParameters parameters) {
        return (StandardDifficultyAttributes) standardDifficultyCalculator.calculate(beatmap, parameters);
    }

    /**
     * Calculates the performance of a <code>RimuDifficultyAttributes</code>.
     *
     * @param attributes The <code>RimuDifficultyAttributes</code> to calculate.
     * @return A structure describing the performance of the <code>RimuDifficultyAttributes</code>.
     */
    public static RimuPerformanceAttributes calculateRimuPerformance(final RimuDifficultyAttributes attributes) {
        return calculateRimuPerformance(attributes, (PerformanceCalculationParameters) null);
    }

    /**
     * Calculates the performance of a <code>RimuDifficultyAttributes</code> given a <code>StatisticV2</code>.
     *
     * @param attributes The <code>RimuDifficultyAttributes</code> to calculate.
     * @param stat The <code>StatisticV2</code> to calculate.
     * @return A structure describing the performance of the <code>RimuDifficultyAttributes</code>
     * relating to the <code>StatisticV2</code>.
     */
    public static RimuPerformanceAttributes calculateRimuPerformance(
            final RimuDifficultyAttributes attributes, final StatisticV2 stat) {
        return calculateRimuPerformance(attributes, constructPerformanceParameters(stat));
    }

    /**
     * Calculates the performance of a <code>RimuDifficultyAttributes</code>.
     *
     * @param attributes The <code>RimuDifficultyAttributes</code> to calculate.
     * @param parameters The parameters of the calculation. Can be <code>null</code>.
     * @return A structure describing the performance of the <code>RimuDifficultyAttributes</code>
     * relating to the calculation parameters.
     */
    public static RimuPerformanceAttributes calculateRimuPerformance(
            final RimuDifficultyAttributes attributes, final PerformanceCalculationParameters parameters) {
        return (RimuPerformanceAttributes) new RimuPerformanceCalculator(attributes).calculate(parameters);
    }

    /**
     * Calculates the performance of a <code>StandardDifficultyAttributes</code>.
     *
     * @param attributes The <code>StandardDifficultyAttributes</code> to calculate.
     * @return A structure describing the performance of the <code>StandardDifficultyAttributes</code>.
     */
    public static StandardPerformanceAttributes calculateStandardPerformance(
            final StandardDifficultyAttributes attributes) {
        return calculateStandardPerformance(attributes, (PerformanceCalculationParameters) null);
    }

    /**
     * Calculates the performance of a <code>StandardDifficultyAttributes</code> given a <code>StatisticV2</code>.
     *
     * @param attributes The <code>StandardDifficultyAttributes</code> to calculate.
     * @param stat The <code>StatisticV2</code> to calculate.
     * @return A structure describing the performance of the <code>StandardDifficultyAttributes</code>
     * relating to the <code>StatisticV2</code>.
     */
    public static StandardPerformanceAttributes calculateStandardPerformance(
            final StandardDifficultyAttributes attributes, final StatisticV2 stat) {
        return calculateStandardPerformance(attributes, constructPerformanceParameters(stat));
    }

    /**
     * Calculates the performance of a <code>StandardDifficultyAttributes</code>.
     *
     * @param attributes The <code>StandardDifficultyAttributes</code> to calculate.
     * @param parameters The parameters of the calculation. Can be <code>null</code>.
     * @return A structure describing the performance of the <code>StandardDifficultyAttributes</code>
     * relating to the calculation parameters.
     */
    public static StandardPerformanceAttributes calculateStandardPerformance(
            final StandardDifficultyAttributes attributes, final PerformanceCalculationParameters parameters) {
        return (StandardPerformanceAttributes) new StandardPerformanceCalculator(attributes).calculate(parameters);
    }
}
