package com.rian.difficultycalculator.calculator

import com.rian.difficultycalculator.attributes.DifficultyAttributes
import com.rian.difficultycalculator.attributes.PerformanceAttributes
import ru.nsu.ccfit.zuev.osu.game.mods.GameMod
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A performance calculator for calculating performance points.
 */
class PerformanceCalculator(
    /**
     * The difficulty attributes being calculated.
     */
    val difficultyAttributes: DifficultyAttributes
) {
    private var scoreMaxCombo = 0
    private var countGreat = 0
    private var countOk = 0
    private var countMeh = 0
    private var countMiss = 0
    private var effectiveMissCount = 0.0

    init {
        processParameters(null)
    }

    /**
     * Calculates the performance value of the difficulty attributes with the specified parameters.
     *
     * @param parameters The parameters to create the attributes for. If omitted, the beatmap was assumed to be SS.
     * @return The performance attributes for the beatmap relating to the parameters.
     */
    @JvmOverloads
    fun calculate(parameters: PerformanceCalculationParameters? = null) = run {
        processParameters(parameters)
        createPerformanceAttributes()
    }

    /**
     * Creates the performance attributes of the difficulty attributes.
     *
     * @return The performance attributes for the beatmap relating to the parameters.
     */
    private fun createPerformanceAttributes() = PerformanceAttributes().also {
        var multiplier = FINAL_MULTIPLIER

        difficultyAttributes.mods.apply {
            if (GameMod.MOD_NOFAIL in this) {
                multiplier *= max(0.9, 1 - 0.02 * this@PerformanceCalculator.effectiveMissCount)
            }

            if (GameMod.MOD_RELAX in this) {
                // Graph: https://www.desmos.com/calculator/bc9eybdthb
                // We use OD13.3 as maximum since it's the value at which great hit window becomes 0.
                val okMultiplier = max(
                    0.0,
                    if (difficultyAttributes.overallDifficulty > 0)
                        1 - (difficultyAttributes.overallDifficulty / 13.33).pow(1.8)
                    else 1.0
                )
                val mehMultiplier = max(
                    0.0,
                    if (difficultyAttributes.overallDifficulty > 0)
                        1 - (difficultyAttributes.overallDifficulty / 13.33).pow(5.0)
                    else 1.0
                )

                // As we're adding 100s and 50s to an approximated number of combo breaks, the result can be higher
                // than total hits in specific scenarios (which breaks some calculations),  so we need to clamp it.
                effectiveMissCount =
                    min(effectiveMissCount + countOk * okMultiplier + countMeh * mehMultiplier, totalHits.toDouble())
            }
        }

        it.effectiveMissCount = effectiveMissCount
        it.aim = calculateAimValue()
        it.speed = calculateSpeedValue()
        it.accuracy = calculateAccuracyValue()
        it.flashlight = calculateFlashlightValue()
        it.total = (
            it.aim.pow(1.1) +
            it.speed.pow(1.1) +
            it.accuracy.pow(1.1) +
            it.flashlight.pow(1.1)
        ).pow(1 / 1.1) * multiplier
    }

    private fun processParameters(parameters: PerformanceCalculationParameters?) = parameters?.apply {
        this@PerformanceCalculator.scoreMaxCombo = maxCombo
        this@PerformanceCalculator.countGreat = countGreat
        this@PerformanceCalculator.countOk = countOk
        this@PerformanceCalculator.countMeh = countMeh
        this@PerformanceCalculator.countMiss = countMiss
        this@PerformanceCalculator.effectiveMissCount = calculateEffectiveMissCount()
    } ?: run { resetDefaults() }

    /**
     * The accuracy of the parameters.
     */
    private val accuracy: Double
        get() = (countGreat * 6.0 + countOk * 2 + countMeh) / (totalHits * 6)

    /**
     * The total hits that can be done in the beatmap.
     */
    private val totalHits: Int
        get() = difficultyAttributes.hitCircleCount + difficultyAttributes.sliderCount + difficultyAttributes.spinnerCount

    /**
     * The amount of hits that were successfully done.
     */
    private val totalSuccessfulHits: Int
        get() = countGreat + countOk + countMeh

    /**
     * Resets this calculator to its original state.
     */
    private fun resetDefaults() {
        scoreMaxCombo = difficultyAttributes.maxCombo
        countGreat = totalHits
        countOk = 0
        countMeh = 0
        countMiss = 0
        effectiveMissCount = 0.0
    }

    private fun calculateAimValue(): Double {
        var aimValue = (5 * max(1.0, difficultyAttributes.aimDifficulty / 0.0675) - 4).pow(3.0) / 100000

        // Longer maps are worth more
        val lengthBonus = 0.95 + 0.4 * min(1.0, totalHits / 2000.0) +
                if (totalHits > 2000) log10(totalHits / 2000.0) * 0.5 else 0.0

        aimValue *= lengthBonus

        if (effectiveMissCount > 0) {
            // Penalize misses by assessing # of misses relative to the total # of objects. Default a 3% reduction for any # of misses.
            aimValue *= 0.97 * (1 - (effectiveMissCount / totalHits).pow(0.775)).pow(effectiveMissCount)
        }

        aimValue *= comboScalingFactor

        difficultyAttributes.apply {
            if (GameMod.MOD_RELAX !in mods) {
                // AR scaling
                var approachRateFactor = 0.0
                if (approachRate > 10.33) {
                    approachRateFactor += 0.3 * (approachRate - 10.33)
                } else if (approachRate < 8) {
                    approachRateFactor += 0.05 * (8 - approachRate)
                }

                // Buff for longer maps with high AR.
                aimValue *= 1 + approachRateFactor * lengthBonus
            }

            // We want to give more reward for lower AR when it comes to aim and HD. This nerfs high AR and buffs lower AR.
            if (GameMod.MOD_HIDDEN in mods) {
                aimValue *= 1 + 0.04 * (12 - approachRate)
            }

            // We assume 15% of sliders in a map are difficult since there's no way to tell from the performance calculator.
            val estimateDifficultSliders = sliderCount * 0.15
            if (estimateDifficultSliders > 0) {
                val estimateSliderEndsDropped =
                    min(
                        (countOk + countMeh + countMiss),
                        (maxCombo - scoreMaxCombo)
                    ).toDouble().coerceIn(0.0, estimateDifficultSliders)

                val sliderNerfFactor =
                    (1 - aimSliderFactor) *
                    (1 - estimateSliderEndsDropped / estimateDifficultSliders).pow(3.0) + aimSliderFactor

                aimValue *= sliderNerfFactor
            }
        }

        // Scale the aim value with accuracy.
        aimValue *= accuracy

        // It is also important to consider accuracy difficulty when doing that.
        aimValue *= 0.98 + difficultyAttributes.overallDifficulty.pow(2.0) / 2500
        return aimValue
    }

    private fun calculateSpeedValue(): Double {
        if (GameMod.MOD_RELAX in difficultyAttributes.mods) {
            return 0.0
        }

        var speedValue = (5 * max(1.0, difficultyAttributes.speedDifficulty / 0.0675) - 4).pow(3.0) / 100000

        // Longer maps are worth more
        val lengthBonus = 0.95 + 0.4 * min(1.0, totalHits / 2000.0) +
                if (totalHits > 2000) log10(totalHits / 2000.0) * 0.5 else 0.0

        speedValue *= lengthBonus

        if (effectiveMissCount > 0) {
            // Penalize misses by assessing # of misses relative to the total # of objects. Default a 3% reduction for any # of misses.
            speedValue *= 0.97 * (1 - (effectiveMissCount / totalHits).pow(0.775)).pow(effectiveMissCount.pow(0.875))
        }

        speedValue *= comboScalingFactor

        difficultyAttributes.apply {
            // AR scaling
            if (approachRate > 10.33) {
                // Buff for longer maps with high AR.
                speedValue *= 1 + 0.3 * (approachRate - 10.33) * lengthBonus
            }
            if (GameMod.MOD_HIDDEN in mods) {
                speedValue *= 1 + 0.04 * (12 - approachRate)
            }

            // Calculate accuracy assuming the worst case scenario.
            val relevantTotalDiff = totalHits - speedNoteCount
            val relevantCountGreat = max(0.0, countGreat - relevantTotalDiff)
            val relevantCountOk = max(0.0, countOk - max(0.0, relevantTotalDiff - countGreat))
            val relevantCountMeh = max(0.0, countMeh - max(0.0, relevantTotalDiff - countGreat - countOk))
            val relevantAccuracy =
                if (speedNoteCount == 0.0) 0.0
                else (relevantCountGreat * 6 + relevantCountOk * 2 + relevantCountMeh) / (speedNoteCount * 6)

            // Scale the speed value with accuracy and OD.
            speedValue *=
                (0.95 + overallDifficulty.pow(2.0) / 750) *
                ((accuracy + relevantAccuracy) / 2).pow((14.5 - max(overallDifficulty, 8.0)) / 2
            )
        }

        // Scale the speed value with # of 50s to punish double-tapping.
        speedValue *= 0.99.pow(max(0.0, countMeh - totalHits / 500.0))

        return speedValue
    }

    private fun calculateAccuracyValue(): Double {
        if (GameMod.MOD_RELAX in difficultyAttributes.mods) {
            return 0.0
        }

        // This percentage only considers HitCircles of any value - in this part of the calculation we focus on hitting the timing hit window.
        val circleCount = difficultyAttributes.hitCircleCount
        val betterAccuracyPercentage =
            if (circleCount > 0) max(
                0.0,
                ((countGreat - (totalHits - circleCount)) * 6.0 + countOk * 2 + countMeh) / (circleCount * 6)
            )
            else 0.0

        return difficultyAttributes.run {
            // Lots of arbitrary values from testing.
            // Considering to use derivation from perfect accuracy in a probabilistic manner - assume normal distribution
            var accuracyValue =
                1.52163.pow(overallDifficulty) * betterAccuracyPercentage.pow(24.0) * 2.83

            // Bonus for many hit circles - it's harder to keep good accuracy up for longer
            accuracyValue *= min(1.15, (circleCount / 1000.0).pow(0.3))

            if (GameMod.MOD_HIDDEN in mods) {
                accuracyValue *= 1.08
            }
            if (GameMod.MOD_FLASHLIGHT in mods) {
                accuracyValue *= 1.02
            }

            accuracyValue
        }
    }

    private fun calculateFlashlightValue(): Double {
        if (GameMod.MOD_FLASHLIGHT !in difficultyAttributes.mods) {
            return 0.0
        }

        var flashlightValue = difficultyAttributes.flashlightDifficulty.pow(2.0) * 25

        if (effectiveMissCount > 0) {
            // Penalize misses by assessing # of misses relative to the total # of objects. Default a 3% reduction for any # of misses.
            flashlightValue *= 0.97 * (1 - (effectiveMissCount / totalHits).pow(0.775)).pow(effectiveMissCount.pow(0.875))
        }

        flashlightValue *= comboScalingFactor

        // Account for shorter maps having a higher ratio of 0 combo/100 combo flashlight radius.
        flashlightValue *=
            0.7 + 0.1 * min(1.0, totalHits / 200.0) +
                if (totalHits > 200)
                    0.2 * min(1.0, (totalHits - 200.0) / 200)
                else 0.0

        // Scale the flashlight value with accuracy slightly.
        flashlightValue *= 0.5 + accuracy / 2

        // It is also important to consider accuracy difficulty when doing that.
        flashlightValue *= 0.98 + difficultyAttributes.overallDifficulty.pow(2.0) / 2500

        return flashlightValue
    }

    private fun calculateEffectiveMissCount() = difficultyAttributes.run {
        // Guess the number of misses + slider breaks from combo
        var comboBasedMissCount = 0.0

        if (sliderCount > 0) {
            val fullComboThreshold: Double = maxCombo - 0.1 * sliderCount
            if (scoreMaxCombo < fullComboThreshold) {
                // Clamp miss count to maximum amount of possible breaks.
                comboBasedMissCount = min(
                    fullComboThreshold / max(1, scoreMaxCombo),
                    (countOk + countMeh + countMiss).toDouble()
                )
            }
        }

        max(countMiss.toDouble(), comboBasedMissCount)
    }

    private val comboScalingFactor: Double
        get() =
            if (difficultyAttributes.maxCombo <= 0) 0.0
            else min((scoreMaxCombo.toDouble() / difficultyAttributes.maxCombo).pow(0.8), 1.0)

    companion object {
        const val FINAL_MULTIPLIER = 1.14
    }
}
