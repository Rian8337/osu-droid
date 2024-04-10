package com.rian.osu.difficulty.calculator

import com.rian.osu.GameMode
import com.rian.osu.beatmap.Beatmap
import com.rian.osu.beatmap.BeatmapConverter
import com.rian.osu.beatmap.BeatmapProcessor
import com.rian.osu.difficulty.DifficultyHitObject
import com.rian.osu.difficulty.attributes.DifficultyAttributes
import com.rian.osu.difficulty.attributes.TimedDifficultyAttributes
import com.rian.osu.difficulty.skills.Skill
import com.rian.osu.mods.*
import kotlin.math.sqrt

/**
 * A difficulty calculator for calculating star rating.
 */
abstract class DifficultyCalculator<TObject : DifficultyHitObject, TAttributes : DifficultyAttributes> {
    protected abstract val mode: GameMode
    protected abstract val difficultyMultiplier: Double

    /**
     * [Mod]s that can alter the star rating when they are used in calculation with one or more [Mod]s.
     */
    protected open val difficultyAdjustmentMods = setOf(
        ModDoubleTime(), ModHalfTime(), ModNightCore(),
        ModRelax(), ModEasy(), ModReallyEasy(),
        ModHardRock(), ModHidden(), ModFlashlight(),
        ModDifficultyAdjust()
    )

    /**
     * Retains [Mod]s that change star rating.
     *
     * This is used rather than [MutableCollection.retainAll] as some [Mod]s need a special treatment.
     */
    fun retainDifficultyAdjustmentMods(parameters: DifficultyCalculationParameters) =
        parameters.mods.iterator().run {
            for (mod in this) {
                // ModDifficultyAdjust always changes difficulty.
                if (mod is ModDifficultyAdjust) {
                    continue
                }

                if (!difficultyAdjustmentMods.contains(mod)) {
                    remove()
                }
            }
        }

    /**
     * Calculates the difficulty of a [Beatmap] with specific parameters.
     *
     * @param beatmap The [Beatmap] whose difficulty is to be calculated.
     * @param parameters The calculation parameters that should be applied to the [Beatmap].
     * @return A structure describing the difficulty of the [Beatmap].
     */
    @JvmOverloads
    fun calculate(
        beatmap: Beatmap,
        parameters: DifficultyCalculationParameters? = null
    ): TAttributes {
        // Always operate on a clone of the original beatmap when needed, to not modify it game-wide
        val beatmapToCalculate = convertBeatmap(beatmap, parameters)
        val skills = createSkills(beatmapToCalculate, parameters)
        val objects = createDifficultyHitObjects(beatmapToCalculate, parameters)

        for (obj in objects) {
            for (skill in skills) {
                skill.process(obj)
            }
        }

        return createDifficultyAttributes(beatmapToCalculate, skills, objects, parameters)
    }

    /**
     * Calculates the difficulty of a [Beatmap] with specific parameters and returns a set of
     * [TimedDifficultyAttributes] representing the difficulty at every relevant time value in the [Beatmap].
     *
     * @param beatmap The [Beatmap] whose difficulty is to be calculated.
     * @param parameters The calculation parameters that should be applied to the [Beatmap].
     * @return The set of [TimedDifficultyAttributes].
     */
    @JvmOverloads
    fun calculateTimed(
        beatmap: Beatmap,
        parameters: DifficultyCalculationParameters? = null
    ): List<TimedDifficultyAttributes<TAttributes>> {
        // Always operate on a clone of the original beatmap when needed, to not modify it game-wide
        val beatmapToCalculate = convertBeatmap(beatmap, parameters)
        val skills = createSkills(beatmapToCalculate, parameters)
        val attributes = mutableListOf<TimedDifficultyAttributes<TAttributes>>()

        if (beatmapToCalculate.hitObjects.objects.isEmpty()) {
            return attributes
        }

        val progressiveBeatmap = Beatmap().apply {
            difficulty.apply(beatmapToCalculate.difficulty)
        }

        // Add the first object in the beatmap, otherwise it will be ignored.
        progressiveBeatmap.hitObjects.add(beatmapToCalculate.hitObjects.objects.first())

        val objects = createDifficultyHitObjects(beatmapToCalculate, parameters)

        for (i in objects.indices) {
            val obj = objects[i]

            progressiveBeatmap.hitObjects.add(obj.obj)

            for (skill in skills) {
                skill.process(obj)
            }

            attributes.add(
                TimedDifficultyAttributes(
                    obj.endTime * (parameters?.totalSpeedMultiplier?.toDouble() ?: 1.0),
                    createDifficultyAttributes(progressiveBeatmap, skills, objects.subList(0, i + 1), parameters)
                )
            )
        }

        return attributes
    }

    /**
     * Creates the [Skill]s to calculate the difficulty of a [Beatmap].
     *
     * @param beatmap The [Beatmap] whose difficulty will be calculated.
     * @param parameters The difficulty calculation parameter being used.
     * @return The [Skill]s.
     */
    protected abstract fun createSkills(beatmap: Beatmap, parameters: DifficultyCalculationParameters?): Array<Skill<TObject>>

    /**
     * Retrieves the [DifficultyHitObject]s to calculate against.
     *
     * @param beatmap The [Beatmap] providing the hit objects to generate from.
     * @param parameters The difficulty calculation parameter being used.
     * @return The generated [DifficultyHitObject]s.
     */
    protected abstract fun createDifficultyHitObjects(
        beatmap: Beatmap,
        parameters: DifficultyCalculationParameters?
    ): List<TObject>

    protected fun calculateRating(skill: Skill<TObject>) = sqrt(skill.difficultyValue()) * difficultyMultiplier

    /**
     * Creates a [TAttributes] to describe a beatmap's difficulty.
     *
     * @param beatmap The [Beatmap] whose difficulty was calculated.
     * @param skills The [Skill]s which processed the beatmap.
     * @param objects The [TObject]s that were generated.
     * @param parameters The difficulty calculation parameters used.
     * @return [TAttributes] describing the beatmap's difficulty.
     */
    protected abstract fun createDifficultyAttributes(
        beatmap: Beatmap,
        skills: Array<Skill<TObject>>,
        objects: List<TObject>,
        parameters: DifficultyCalculationParameters?
    ): TAttributes

    private fun convertBeatmap(beatmap: Beatmap, parameters: DifficultyCalculationParameters?): Beatmap {
        val converter = BeatmapConverter(beatmap)

        // Convert
        val converted = converter.convert()

        // Apply difficulty mods
        parameters?.mods?.filterIsInstance<IApplicableToDifficulty>()?.forEach {
            it.applyToDifficulty(mode, converted.difficulty)
        }

        parameters?.mods?.filterIsInstance<IApplicableToDifficultyWithSettings>()?.forEach {
            it.applyToDifficulty(mode, converted.difficulty, parameters.mods, parameters.customSpeedMultiplier)
        }

        val processor = BeatmapProcessor(converted)

        processor.preProcess()

        // Compute default values for hit objects, including creating nested hit objects in-case they're needed
        converted.hitObjects.objects.forEach {
            it.applyDefaults(converted.controlPoints, converted.difficulty, mode)
        }

        parameters?.mods?.filterIsInstance<IApplicableToHitObject>()?.forEach {
            for (obj in converted.hitObjects.objects) {
                it.applyToHitObject(mode, obj)
            }
        }

        processor.postProcess(mode)

        parameters?.mods?.filterIsInstance<IApplicableToBeatmap>()?.forEach {
            it.applyToBeatmap(converted)
        }

        return converted
    }
}
