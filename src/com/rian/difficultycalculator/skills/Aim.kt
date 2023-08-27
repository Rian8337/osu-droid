package com.rian.difficultycalculator.skills

import com.rian.difficultycalculator.beatmap.hitobject.DifficultyHitObject
import com.rian.difficultycalculator.evaluators.AimEvaluator.evaluateDifficultyOf
import ru.nsu.ccfit.zuev.osu.game.mods.GameMod
import java.util.*
import kotlin.math.pow

/**
 * Represents the skill required to correctly aim at every object in the map with a uniform circle size and normalized distances.
 */
class Aim(
    /**
     * The mods that this skill processes.
     */
    mods: EnumSet<GameMod>,

    /**
     * Whether to consider sliders in the calculation.
     */
    private val withSliders: Boolean
) : StrainSkill(mods) {
    private var currentStrain = 0.0
    private val skillMultiplier = 23.55
    private val strainDecayBase = 0.15

    override fun strainValueAt(current: DifficultyHitObject): Double {
        currentStrain *= strainDecay(current.deltaTime)
        currentStrain += evaluateDifficultyOf(current, withSliders) * skillMultiplier

        return currentStrain
    }

    override fun calculateInitialStrain(time: Double, current: DifficultyHitObject) =
        currentStrain * strainDecay(time - current.previous(0)!!.startTime)

    override fun saveToHitObject(current: DifficultyHitObject) {
        if (withSliders) {
            current.aimStrainWithSliders = currentStrain
        } else {
            current.aimStrainWithoutSliders = currentStrain
        }
    }

    private fun strainDecay(ms: Double) = strainDecayBase.pow(ms / 1000)
}
