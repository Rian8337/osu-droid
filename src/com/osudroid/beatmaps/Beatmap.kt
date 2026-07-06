package com.osudroid.beatmaps

import com.osudroid.GameMode
import com.osudroid.beatmaps.hitobjects.HitObject
import com.osudroid.beatmaps.hitobjects.Slider
import com.osudroid.beatmaps.hitobjects.sliderobject.SliderHead
import com.osudroid.beatmaps.hitobjects.sliderobject.SliderRepeat
import com.osudroid.beatmaps.hitobjects.sliderobject.SliderTail
import com.osudroid.beatmaps.hitobjects.sliderobject.SliderTick
import com.osudroid.beatmaps.sections.BeatmapColor
import com.osudroid.beatmaps.sections.BeatmapControlPoints
import com.osudroid.beatmaps.sections.BeatmapDifficulty
import com.osudroid.beatmaps.sections.BeatmapEvents
import com.osudroid.beatmaps.sections.BeatmapGeneral
import com.osudroid.beatmaps.sections.BeatmapHitObjects
import com.osudroid.beatmaps.sections.BeatmapMetadata
import com.osudroid.mods.IModApplicableToBeatmap
import com.osudroid.mods.IModApplicableToDifficulty
import com.osudroid.mods.IModApplicableToDifficultyWithMods
import com.osudroid.mods.IModApplicableToHitObject
import com.osudroid.mods.IModApplicableToHitObjectWithMods
import com.osudroid.mods.IModFacilitatesAdjustment
import com.osudroid.mods.IModRequiresOriginalBeatmap
import com.osudroid.mods.Mod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive

/**
 * Represents a beatmap.
 *
 * @param mode The [GameMode] this [Beatmap] was parsed as.
 */
open class Beatmap(mode: GameMode) : IBeatmap, Cloneable {
    /**
     * The [GameMode] this [Beatmap] was parsed as.
     */
    var mode = mode
        private set

    override var formatVersion = 14
    override val general = BeatmapGeneral()
    override val metadata = BeatmapMetadata()
    override var difficulty = BeatmapDifficulty()
    override val events = BeatmapEvents()
    override val colors = BeatmapColor()
    override val controlPoints = BeatmapControlPoints()
    override var hitObjects = BeatmapHitObjects()
    override var filePath = ""
    override var md5 = ""

    override val maxCombo by lazy {
        hitObjects.objects.sumOf {
            if (it is Slider) it.nestedHitObjects.size else 1
        }
    }

    /**
     * The maximum score of this [Beatmap].
     */
    val maxScore by lazy {
        var score = 0
        var combo = 0

        val difficultyMultiplier = 1 + difficulty.od / 10 + difficulty.hp / 10 + (difficulty.gameplayCS - 3) / 4

        for (obj in hitObjects.objects) {
            if (obj !is Slider) {
                score += (300 + (300 * combo * difficultyMultiplier) / 25).toInt()
                combo++
                continue
            }

            for (nested in obj.nestedHitObjects) {
                score += when (nested) {
                    is SliderHead, is SliderRepeat -> 30
                    is SliderTick -> 10
                    is SliderTail -> 300 + (300 * combo * difficultyMultiplier / 25).toInt()
                    else -> 0
                }

                combo++
            }
        }

        score
    }

    /**
     * Constructs a [DroidPlayableBeatmap] from this [Beatmap], where all [HitObject] and [BeatmapDifficulty]
     * [Mod]s have been applied, and [HitObject]s have been fully constructed.
     *
     * @param mods The [Mod]s to apply to the [Beatmap]. Defaults to No Mod.
     * @param scope The [CoroutineScope] to use for coroutines.
     * @return The [DroidPlayableBeatmap].
     */
    @JvmOverloads
    fun createDroidPlayableBeatmap(
        mods: Iterable<Mod>? = null,
        scope: CoroutineScope? = null
    ) = DroidPlayableBeatmap(convert(GameMode.Droid, mods, scope), mods)

    /**
     * Constructs a [StandardPlayableBeatmap] from this [Beatmap], where all [HitObject] and [BeatmapDifficulty]
     * [Mod]s have been applied, and [HitObject]s have been fully constructed.
     *
     * @param mods The [Mod]s to apply to the [Beatmap]. Defaults to No Mod.
     * @param scope The [CoroutineScope] to use for coroutines.
     * @return The [StandardPlayableBeatmap].
     */
    @JvmOverloads
    fun createStandardPlayableBeatmap(
        mods: Iterable<Mod>? = null,
        scope: CoroutineScope? = null
    ) = StandardPlayableBeatmap(convert(GameMode.Standard, mods, scope), mods)

    /**
     * Converts this [Beatmap] to another [Beatmap] for the specified [GameMode], where all [HitObject] and
     * [BeatmapDifficulty] [Mod]s have been applied, and [HitObject]s have been fully constructed.
     *
     * @param mode The [GameMode] to convert the [Beatmap] to.
     * @param mods The [Mod]s to apply to the [Beatmap]. Defaults to No Mod.
     * @param scope The [CoroutineScope] to use for coroutines.
     * @return The converted [Beatmap].
     */
    @JvmOverloads
    fun convert(mode: GameMode, mods: Iterable<Mod>? = null, scope: CoroutineScope? = null): Beatmap {
        if (this.mode == mode && mods?.firstOrNull() == null) {
            // Beatmap is already playable as is.
            return this
        }

        val adjustmentMods = mods?.filterIsInstance<IModFacilitatesAdjustment>() ?: emptyList()

        mods?.filterIsInstance<IModRequiresOriginalBeatmap>()?.forEach {
            scope?.ensureActive()
            it.applyFromBeatmap(this)
        }

        val converter = BeatmapConverter(this, scope)

        // Convert
        val converted = converter.convert()
        converted.mode = mode

        // Apply difficulty mods
        mods?.filterIsInstance<IModApplicableToDifficulty>()?.forEach {
            scope?.ensureActive()
            it.applyToDifficulty(mode, converted.difficulty, adjustmentMods)
        }

        mods?.filterIsInstance<IModApplicableToDifficultyWithMods>()?.forEach {
            scope?.ensureActive()
            it.applyToDifficulty(mode, converted.difficulty, mods)
        }

        val processor = BeatmapProcessor(converted, scope)

        processor.preProcess()

        // Compute default values for hit objects, including creating nested hit objects in-case they're needed
        converted.hitObjects.objects.forEach {
            scope?.ensureActive()
            it.applyDefaults(converted.controlPoints, converted.difficulty, mode, scope)
        }

        mods?.filterIsInstance<IModApplicableToHitObject>()?.forEach {
            for (obj in converted.hitObjects.objects) {
                scope?.ensureActive()
                it.applyToHitObject(mode, obj, adjustmentMods, scope)
            }
        }

        mods?.filterIsInstance<IModApplicableToHitObjectWithMods>()?.forEach {
            for (obj in converted.hitObjects.objects) {
                scope?.ensureActive()
                it.applyToHitObject(mode, obj, mods, scope)
            }
        }

        processor.postProcess()

        mods?.filterIsInstance<IModApplicableToBeatmap>()?.forEach {
            scope?.ensureActive()
            it.applyToBeatmap(converted, scope)
        }

        return converted
    }

    public override fun clone() = super.clone() as Beatmap
}