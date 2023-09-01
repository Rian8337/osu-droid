package com.rian.osu.difficultycalculator

import com.rian.osu.beatmap.Beatmap
import com.rian.osu.difficultycalculator.attributes.DifficultyAttributes
import com.rian.osu.difficultycalculator.attributes.TimedDifficultyAttributes
import com.rian.osu.difficultycalculator.calculator.DifficultyCalculationParameters
import com.rian.osu.difficultycalculator.calculator.DifficultyCalculator
import com.rian.osu.difficultycalculator.calculator.PerformanceCalculationParameters
import com.rian.osu.difficultycalculator.calculator.PerformanceCalculator
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2

/**
 * A helper class for operations relating to difficulty and performance calculation.
 */
object BeatmapDifficultyCalculator {
    /**
     * Cache of difficulty calculations, mapped by MD5 hash.
     */
    private val difficultyCache = HashMap<String, BeatmapDifficultyCache>()

    /**
     * Constructs a `DifficultyCalculationParameters` from a `StatisticV2`.
     *
     * @param stat The `StatisticV2` to construct the `DifficultyCalculationParameters` from.
     * @return The `DifficultyCalculationParameters` representing the `StatisticV2`,
     * `null` if the `StatisticV2` instance is `null`.
     */
    @JvmStatic
    fun constructDifficultyParameters(stat: StatisticV2?) = stat?.run {
        DifficultyCalculationParameters().also {
            it.mods = mod.clone()
            it.customSpeedMultiplier = changeSpeed

            if (isEnableForceAR) {
                it.forcedAR = forceAR
            }
        }
    }

    /**
     * Constructs a `PerformanceCalculationParameters` from a `StatisticV2`.
     *
     * @param stat The `StatisticV2` to construct the `PerformanceCalculationParameters` from.
     * @return The `PerformanceCalculationParameters` representing the `StatisticV2`,
     * `null` if the `StatisticV2` instance is `null`.
     */
    @JvmStatic
    fun constructPerformanceParameters(stat: StatisticV2?) = stat?.run {
        PerformanceCalculationParameters().also {
            it.maxCombo = getMaxCombo()
            it.countGreat = hit300
            it.countOk = hit100
            it.countMeh = hit50
            it.countMiss = misses
        }
    }

    /**
     * Calculates the difficulty of a `BeatmapData`.
     *
     * @param beatmap The `BeatmapData` to calculate.
     * @param parameters The parameters of the calculation. Can be `null`.
     * @return A structure describing the difficulty of the `BeatmapData`
     * relating to the calculation parameters.
     */
    @JvmStatic
    @JvmOverloads
    fun calculateDifficulty(
        beatmap: Beatmap, parameters: DifficultyCalculationParameters? = null
    ) = difficultyCache[beatmap.md5]?.getDifficultyCache(parameters)
        ?:
        DifficultyCalculator.calculate(beatmap, parameters).also { addCache(beatmap.md5, parameters, it) }

    /**
     * Calculates the difficulty of a `BeatmapData` given a `StatisticV2`,
     * returning a set of `TimedDifficultyAttributes` representing the difficulty of the
     * beatmap at any relevant time.
     *
     * @param beatmap The `BeatmapData` to calculate.
     * @param parameters The parameters of the calculation. Can be `null`.
     * @return A set of `TimedDifficultyAttributes` describing the difficulty of
     * the `BeatmapData` at any relevant time relating to the calculation parameters.
     */
    @JvmStatic
    @JvmOverloads
    fun calculateTimedDifficulty(
        beatmap: Beatmap, parameters: DifficultyCalculationParameters? = null
    ) = difficultyCache[beatmap.md5]?.getTimedDifficultyCache(parameters)
        ?:
        DifficultyCalculator.calculateTimed(beatmap, parameters).also { addCache(beatmap.md5, parameters, it) }

    /**
     * Calculates the performance of a `DifficultyAttributes`.
     *
     * @param attributes The `DifficultyAttributes` to calculate.
     * @param parameters The parameters of the calculation. Can be `null`.
     * @return A structure describing the performance of the `DifficultyAttributes`
     * relating to the calculation parameters.
     */
    @JvmStatic
    @JvmOverloads
    fun calculatePerformance(
        attributes: DifficultyAttributes,
        parameters: PerformanceCalculationParameters? = null
    ) = PerformanceCalculator(attributes).calculate(parameters)

    /**
     * Adds a cache to the difficulty cache.
     *
     * @param md5 The MD5 hash of the beatmap to cache.
     * @param parameters The difficulty calculation parameters to cache.
     * @param attributes The difficulty attributes to cache.
     */
    private fun addCache(
        md5: String, parameters: DifficultyCalculationParameters?,
        attributes: DifficultyAttributes
    ) = difficultyCache[md5, { BeatmapDifficultyCache() }].run { addCache(parameters, attributes) }

    /**
     * Adds a cache to the difficulty cache.
     *
     * @param md5 The MD5 hash of the beatmap to cache.
     * @param parameters The difficulty calculation parameters to cache.
     * @param attributes The timed difficulty attributes to cache.
     */
    private fun addCache(
        md5: String, parameters: DifficultyCalculationParameters?,
        attributes: List<TimedDifficultyAttributes>
    ) = difficultyCache[md5, { BeatmapDifficultyCache() }].run { addCache(parameters, attributes) }

    /**
     * A cache holder for a beatmap.
     */
    private class BeatmapDifficultyCache {
        private val attributeCache = HashMap<DifficultyCalculationParameters, DifficultyAttributes>()
        private val timedAttributeCache = HashMap<DifficultyCalculationParameters, List<TimedDifficultyAttributes>>()

        /**
         * Adds a difficulty attributes cache.
         *
         * @param parameters The difficulty parameters of the difficulty attributes.
         * @param attributes The difficulty attributes to cache.
         */
        fun addCache(parameters: DifficultyCalculationParameters?, attributes: DifficultyAttributes) =
            addCache(parameters, attributes, attributeCache)

        /**
         * Adds a difficulty attributes cache.
         *
         * @param parameters The difficulty parameters of the difficulty attributes.
         * @param attributes The timed difficulty attributes to cache.
         */
        fun addCache(parameters: DifficultyCalculationParameters?, attributes: List<TimedDifficultyAttributes>) =
            addCache(parameters, attributes, timedAttributeCache)

        /**
         * Retrieves the difficulty attributes cache of a calculation parameter.
         *
         * @param parameters The difficulty calculation parameter to retrieve.
         * @return The difficulty attributes, `null` if not found.
         */
        fun getDifficultyCache(parameters: DifficultyCalculationParameters?) =
            getCache(parameters, attributeCache)

        /**
         * Retrieves the timed difficulty attributes cache of a calculation parameter.
         *
         * @param parameters The difficulty calculation parameter to retrieve.
         * @return The timed difficulty attributes, `null` if not found.
         */
        fun getTimedDifficultyCache(parameters: DifficultyCalculationParameters?) =
            getCache(parameters, timedAttributeCache)

        /**
         * Adds a difficulty attributes cache to a cache map.
         *
         * @param parameters The difficulty calculation parameter to cache.
         * @param cache The difficulty attributes cache to add.
         * @param cacheMap The map to add the cache to.
         * @param <T> The difficulty attributes cache type.
        </T> */
        private fun <T> addCache(
            parameters: DifficultyCalculationParameters?, cache: T,
            cacheMap: HashMap<DifficultyCalculationParameters, T>
        ) =
            // Copy the parameter for caching.
            (parameters?.clone() ?: DifficultyCalculationParameters()).run {
                mods.retainAll(DifficultyCalculator.difficultyAdjustmentMods)

                cacheMap[this] = cache
            }

        /**
         * Gets the cache of difficulty attributes of a calculation parameter.
         *
         * @param parameters The difficulty calculation parameter to retrieve.
         * @param cacheMap The map containing the cache to lookup for.
         * @return The difficulty attributes, `null` if not found.
         */
        private fun <T> getCache(
            parameters: DifficultyCalculationParameters?,
            cacheMap: HashMap<DifficultyCalculationParameters, T>
        ) = (parameters?.clone() ?: DifficultyCalculationParameters()).run {
            mods.retainAll(DifficultyCalculator.difficultyAdjustmentMods)

            cacheMap[this]
        }
    }
}

operator fun <K: Any, V: Any> HashMap<K, V>.get(key: K, fallback: () -> V) = this[key] ?: fallback().also { this[key] = it }