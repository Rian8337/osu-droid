package com.rian.difficultycalculator.calculator

import com.rian.difficultycalculator.attributes.DifficultyAttributes
import com.rian.difficultycalculator.attributes.TimedDifficultyAttributes
import com.rian.difficultycalculator.beatmap.BeatmapDifficultyManager
import com.rian.difficultycalculator.beatmap.DifficultyBeatmap
import com.rian.difficultycalculator.beatmap.hitobject.DifficultyHitObject
import com.rian.difficultycalculator.skills.Aim
import com.rian.difficultycalculator.skills.Flashlight
import com.rian.difficultycalculator.skills.Skill
import com.rian.difficultycalculator.skills.Speed
import com.rian.difficultycalculator.utils.HitObjectStackEvaluator.applyStacking
import com.rian.difficultycalculator.utils.HitWindowConverter.hitWindow300ToOD
import com.rian.difficultycalculator.utils.HitWindowConverter.odToHitWindow300
import ru.nsu.ccfit.zuev.osu.game.mods.GameMod
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A difficulty calculator for calculating star rating.
 */
class DifficultyCalculator {
    /**
     * Mods that can alter the star rating when they are used in calculation with one or more mods.
     */
    @JvmField
    val difficultyAdjustmentMods: EnumSet<GameMod> = EnumSet.of(
        GameMod.MOD_DOUBLETIME, GameMod.MOD_HALFTIME, GameMod.MOD_NIGHTCORE,
        GameMod.MOD_SMALLCIRCLE, GameMod.MOD_RELAX, GameMod.MOD_EASY,
        GameMod.MOD_REALLYEASY, GameMod.MOD_HARDROCK, GameMod.MOD_HIDDEN,
        GameMod.MOD_FLASHLIGHT, GameMod.MOD_SPEEDUP
    )

    /**
     * Calculates the difficulty of the beatmap with specific parameters.
     *
     * @param beatmap The beatmap whose difficulty is to be calculated.
     * @param parameters The calculation parameters that should be applied to the beatmap.
     * @return A structure describing the difficulty of the beatmap.
     */
    @JvmOverloads
    fun calculate(
        beatmap: DifficultyBeatmap,
        parameters: DifficultyCalculationParameters? = null
    ): DifficultyAttributes {
        // Always operate on a clone of the original beatmap if parameters are present, to not modify it game-wide
        val beatmapToCalculate = if (parameters != null) beatmap.clone() else beatmap

        if (parameters != null) {
            applyParameters(beatmapToCalculate, parameters)
        }

        val skills = createSkills(beatmapToCalculate, parameters)

        for (obj in createDifficultyHitObjects(beatmapToCalculate, parameters)) {
            for (skill in skills) {
                skill.process(obj)
            }
        }

        return createDifficultyAttributes(beatmapToCalculate, skills, parameters)
    }

    /**
     * Calculates the difficulty of a beatmap with specific parameters and returns a set of
     * `TimedDifficultyAttributes` representing the difficulty at every relevant time
     * value in the beatmap.
     *
     * @param beatmap The beatmap whose difficulty is to be calculated.
     * @param parameters The calculation parameters that should be applied to the beatmap.
     * @return The set of `TimedDifficultyAttributes`.
     */
    @JvmOverloads
    fun calculateTimed(
        beatmap: DifficultyBeatmap,
        parameters: DifficultyCalculationParameters? = null
    ): List<TimedDifficultyAttributes> {
        // Always operate on a clone of the original beatmap if parameters are present, to not modify it game-wide
        val beatmapToCalculate = if (parameters != null) beatmap.clone() else beatmap

        if (parameters != null) {
            applyParameters(beatmapToCalculate, parameters)
        }

        val skills = createSkills(beatmapToCalculate, parameters)
        val attributes = ArrayList<TimedDifficultyAttributes>()

        if (beatmapToCalculate.hitObjectsManager.getObjects().isEmpty()) {
            return attributes
        }

        val progressiveBeatmap = DifficultyBeatmap(beatmapToCalculate.difficultyManager)

        // Add the first object in the beatmap, otherwise it will be ignored.
        progressiveBeatmap.hitObjectsManager.add(beatmapToCalculate.hitObjectsManager.getObjects()[0])

        for (obj in createDifficultyHitObjects(beatmapToCalculate, parameters)) {
            progressiveBeatmap.hitObjectsManager.add(obj.obj)

            for (skill in skills) {
                skill.process(obj)
            }

            attributes.add(
                TimedDifficultyAttributes(
                    obj.endTime * (parameters?.totalSpeedMultiplier?.toDouble() ?: 1.0),
                    createDifficultyAttributes(progressiveBeatmap, skills, parameters)
                )
            )
        }
        return attributes
    }

    /**
     * Creates difficulty attributes to describe a beatmap's difficulty.
     *
     * @param beatmap The beatmap whose difficulty was calculated.
     * @param skills The skills which processed the beatmap.
     * @param parameters The difficulty calculation parameters used.
     * @return Difficulty attributes describing the beatmap's difficulty.
     */
    private fun createDifficultyAttributes(
        beatmap: DifficultyBeatmap, skills: Array<Skill>,
        parameters: DifficultyCalculationParameters?
    ) = DifficultyAttributes().apply {
            mods = parameters?.mods?.clone() ?: mods

            aimDifficulty = calculateRating(skills[0])
            speedDifficulty = calculateRating(skills[2])
            speedNoteCount = (skills[2] as Speed).relevantNoteCount()
            flashlightDifficulty = calculateRating(skills[3])

            aimSliderFactor = if (aimDifficulty > 0) calculateRating(skills[1]) / aimDifficulty else 1.0

            if (parameters?.mods?.contains(GameMod.MOD_RELAX) == true) {
                aimDifficulty *= 0.9
                speedDifficulty = 0.0
                flashlightDifficulty *= 0.7
            }

            val baseAimPerformance: Double = (5 * max(1.0, aimDifficulty / 0.0675) - 4).pow(3.0) / 100000
            val baseSpeedPerformance: Double = (5 * max(1.0, speedDifficulty / 0.0675) - 4).pow(3.0) / 100000
            var baseFlashlightPerformance = 0.0
            if (parameters?.mods?.contains(GameMod.MOD_FLASHLIGHT) == true) {
                baseFlashlightPerformance = flashlightDifficulty.pow(2.0) * 25.0
            }

            val basePerformance =
                (baseAimPerformance.pow(1.1) + baseSpeedPerformance.pow(1.1) + baseFlashlightPerformance.pow(1.1)).pow(
                    1.0 / 1.1
                )

            // Document for formula derivation:
            // https://docs.google.com/document/d/10DZGYYSsT_yjz2Mtp6yIJld0Rqx4E-vVHupCqiM4TNI/edit
            starRating =
                if (basePerformance > 1e-5)
                    Math.cbrt(PerformanceCalculator.FINAL_MULTIPLIER) * 0.027 *
                    (Math.cbrt(100000 / 2.0.pow(1 / 1.1) * basePerformance) + 4)
                else 0.0

            val ar = beatmap.difficultyManager.ar
            var preempt = (if (ar <= 5) 1800 - 120 * ar else 1950 - 150 * ar).toDouble()

            if (parameters?.isForceAR == false) {
                preempt /= parameters.totalSpeedMultiplier.toDouble()
            }

            approachRate = if (preempt > 1200) (1800 - preempt) / 120 else (1200 - preempt) / 150 + 5

            val greatWindow =
                odToHitWindow300(beatmap.difficultyManager.od) /
                (parameters?.totalSpeedMultiplier?.toDouble() ?: 1.0)

            overallDifficulty = hitWindow300ToOD(greatWindow).toDouble()
            maxCombo = beatmap.maxCombo
            hitCircleCount = beatmap.hitObjectsManager.circleCount
            sliderCount = beatmap.hitObjectsManager.sliderCount
            spinnerCount = beatmap.hitObjectsManager.spinnerCount
        }

    /**
     * Applies difficulty calculation parameters to the given beatmap.
     *
     * @param beatmap The beatmap.
     * @param parameters The difficulty calculation parameters.
     */
    private fun applyParameters(beatmap: DifficultyBeatmap, parameters: DifficultyCalculationParameters) {
        val manager = beatmap.difficultyManager
        val initialAR = manager.ar

        processCS(manager, parameters)
        processAR(manager, parameters)
        processOD(manager, parameters)
        processHP(manager, parameters)

        if (initialAR != manager.ar) {
            beatmap.hitObjectsManager.resetStacking()

            applyStacking(
                beatmap.formatVersion,
                beatmap.hitObjectsManager.getObjects(),
                manager.ar,
                beatmap.stackLeniency
            )
        }
    }

    /**
     * Creates the skills to calculate the difficulty of a beatmap.
     *
     * @param beatmap The beatmap whose difficulty will be calculated.
     * @param parameters The difficulty calculation parameter being used.
     * @return The skills.
     */
    private fun createSkills(beatmap: DifficultyBeatmap, parameters: DifficultyCalculationParameters?): Array<Skill> {
        val mods = parameters?.mods ?: EnumSet.noneOf(GameMod::class.java)
        val greatWindow =
            odToHitWindow300(beatmap.difficultyManager.od) /
            (parameters?.totalSpeedMultiplier?.toDouble() ?: 1.0)

        return arrayOf(
            Aim(mods, true),
            Aim(mods, false),
            Speed(mods, greatWindow),
            Flashlight(mods)
        )
    }

    private fun calculateRating(skill: Skill) = sqrt(skill.difficultyValue()) * DIFFICULTY_MULTIPLIER

    private fun processCS(manager: BeatmapDifficultyManager, parameters: DifficultyCalculationParameters?) {
        var cs = manager.cs

        parameters?.mods?.apply {
            when {
                GameMod.MOD_HARDROCK in this -> ++cs
                GameMod.MOD_EASY in this -> --cs
                GameMod.MOD_REALLYEASY in this -> --cs
                GameMod.MOD_SMALLCIRCLE in this -> cs += 4
            }
        }

        // 12.14 is the point at which the object radius approaches 0. Use the _very_ minimum value.
        manager.cs = min(cs, 12.13f)
    }

    private fun processAR(manager: BeatmapDifficultyManager, parameters: DifficultyCalculationParameters?) {
        var ar = manager.ar

        if (parameters == null) {
            manager.ar = min(ar, 10f)
            return
        }

        parameters.apply {
            manager.ar = forcedAR ?: run {
                if (GameMod.MOD_HARDROCK in mods) {
                    ar *= 1.4f
                }

                if (GameMod.MOD_EASY in mods) {
                    ar /= 2f
                }

                if (GameMod.MOD_REALLYEASY in mods) {
                    if (GameMod.MOD_EASY in mods) {
                        ar *= 2f
                        ar -= 0.5f
                    }

                    ar -= 0.5f
                    ar -= customSpeedMultiplier - 1
                }

                min(ar, 10f)
            }
        }
    }

    private fun processOD(manager: BeatmapDifficultyManager, parameters: DifficultyCalculationParameters?) {
        var od = manager.od

        parameters?.mods?.apply {
            if (GameMod.MOD_HARDROCK in this) {
                od *= 1.4f
            }

            if (GameMod.MOD_EASY in this) {
                od /= 2f
            }

            if (GameMod.MOD_REALLYEASY in this) {
                od /= 2f
            }
        }

        manager.od = min(od, 10f)
    }

    private fun processHP(manager: BeatmapDifficultyManager, parameters: DifficultyCalculationParameters?) {
        var hp = manager.hp

        parameters?.mods?.apply {
            if (GameMod.MOD_HARDROCK in this) {
                hp *= 1.4f
            }

            if (GameMod.MOD_EASY in this) {
                hp /= 2f
            }

            if (GameMod.MOD_REALLYEASY in this) {
                hp /= 2f
            }
        }

        manager.hp = min(hp, 10f)
    }

    /**
     * Retrieves the difficulty hit objects to calculate against.
     *
     * @param beatmap The beatmap providing the hit objects to generate from.
     * @param parameters The difficulty calculation parameter being used.
     * @return The generated difficulty hit objects.
     */
    private fun createDifficultyHitObjects(
        beatmap: DifficultyBeatmap, parameters: DifficultyCalculationParameters?
    ): List<DifficultyHitObject> {
        val objects = mutableListOf<DifficultyHitObject>()
        val rawObjects = beatmap.hitObjectsManager.getObjects()

        val ar = beatmap.difficultyManager.ar
        val timePreempt = (if (ar <= 5) 1800 - 120 * ar else 1950 - 150 * ar).toDouble()

        val objectScale = (1 - 0.7f * (beatmap.difficultyManager.cs - 5) / 5) / 2

        for (i in 1 until rawObjects.size) {
            rawObjects[i].scale = objectScale
            rawObjects[i - 1].scale = objectScale
            val lastLast = rawObjects.getOrNull(i - 2)

            objects.add(
                DifficultyHitObject(
                    rawObjects[i],
                    rawObjects[i - 1],
                    lastLast,
                    parameters?.totalSpeedMultiplier?.toDouble() ?: 1.0,
                    objects,
                    objects.size,
                    timePreempt,
                    parameters?.isForceAR == true
                )
            )
        }
        return objects
    }

    companion object {
        private const val DIFFICULTY_MULTIPLIER = 0.0675
    }
}
