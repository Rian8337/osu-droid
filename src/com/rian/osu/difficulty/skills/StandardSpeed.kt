package com.rian.osu.difficulty.skills

import com.rian.osu.difficulty.StandardDifficultyHitObject
import com.rian.osu.difficulty.evaluators.StandardRhythmEvaluator
import com.rian.osu.difficulty.evaluators.StandardSpeedEvaluator
import com.rian.osu.mods.Mod
import kotlin.math.exp
import kotlin.math.pow

/**
 * Represents the skill required to press keys or tap with regards to keeping up with the speed at which objects need to be hit.
 */
class StandardSpeed(
    /**
     * The [Mod]s that this skill processes.
     */
    mods: List<Mod>,

    /**
     * The 300 hit window.
     */
    private val greatWindow: Double
) : StandardStrainSkill(mods) {
    override val difficultyMultiplier = 1.04
    override val reducedSectionCount = 5

    private var currentStrain = 0.0
    private var currentRhythm = 0.0
    private val skillMultiplier = 1375.0
    private val strainDecayBase = 0.3

    private val objectStrains = mutableListOf<Double>()

    /**
     * Calculates the number of clickable objects weighted by difficulty.
     */
    fun relevantNoteCount(): Double = objectStrains.run {
        if (isEmpty()) {
            return 0.0
        }

        val maxStrain = max()
        if (maxStrain == 0.0) {
            return 0.0
        }

        fold(0.0) { acc, d -> acc + 1 / (1 + exp(-(d / maxStrain * 12 - 6))) }
    }

    override fun strainValueAt(current: StandardDifficultyHitObject): Double {
        currentStrain *= strainDecay(current.strainTime)
        currentStrain += StandardSpeedEvaluator.evaluateDifficultyOf(current, greatWindow) * skillMultiplier

        currentRhythm = StandardRhythmEvaluator.evaluateDifficultyOf(current, greatWindow)
        val totalStrain = currentStrain * currentRhythm

        objectStrains.add(totalStrain)
        return totalStrain
    }

    override fun calculateInitialStrain(time: Double, current: StandardDifficultyHitObject) =
        currentStrain * currentRhythm * strainDecay(time - current.previous(0)!!.startTime)

    private fun strainDecay(ms: Double) = strainDecayBase.pow(ms / 1000)
}
