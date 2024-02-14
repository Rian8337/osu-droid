package com.rian.osu.beatmap.hitobject

import com.rian.osu.beatmap.hitobject.sliderobject.*
import com.rian.osu.beatmap.sections.BeatmapControlPoints
import com.rian.osu.beatmap.sections.BeatmapDifficulty
import com.rian.osu.math.Vector2
import com.rian.osu.utils.Cached
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a slider.
 */
class Slider(
    /**
     * The time at which this [Slider] starts, in milliseconds.
     */
    startTime: Double,

    /**
     * The position of the [Slider] relative to the play field.
     */
    position: Vector2,

    /**
     * The repetition amount of this [Slider]. Note that 1 repetition means no repeats (1 loop).
     */
    repeatCount: Int,

    /**
     * The path of this [Slider].
     */
    @JvmField
    var path: SliderPath,

    /**
     * Whether this [Slider] starts a new combo.
     */
    isNewCombo: Boolean = false,

    /**
     * How many combo colors to skip, if this [Slider] starts a new combo.
     */
    comboColorOffset: Int = 0,

    /**
     * The samples to be played when each node of this [Slider] is hit.
     *
     * - 0: The first node.
     * - 1: The first repeat.
     * - 2: The second repeat.
     * - ...
     * - `n - 1`: The last repeat.
     * - `n`: The last node.
     */
    @JvmField
    var nodeSamples: MutableList<MutableList<HitSampleInfo>>
) : HitObject(startTime, position, isNewCombo, comboColorOffset), IHasDuration {
    override val endTime: Double
        get() = startTime + spanCount * path.expectedDistance / velocity

    override val duration: Double
        get() = endTime - startTime

    private var endPositionCache = Cached(position)

    /**
     * The end position of this [Slider].
     */
    val endPosition: Vector2
        get() {
            if (!endPositionCache.isValid) {
                endPositionCache.value = position + path.positionAt(1.0)
            }

            return endPositionCache.value
        }

    /**
     * The stacked end position of this [Slider].
     */
    val stackedEndPosition: Vector2
        get() = evaluateStackedPosition(endPosition)

    /**
     * The distance of this [Slider].
     */
    val distance: Double
        get() = path.expectedDistance

    /**
     * The amount of times this [Slider] repeats.
     */
    var repeatCount = repeatCount
        set(value) {
            field = value.coerceAtLeast(0)
            updateNestedPositions()
        }

    /**
     * The amount of times the length of this [Slider] spans.
     */
    val spanCount: Int
        get() = repeatCount + 1

    /**
     * The nested hit objects of this [Slider].
     *
     * Consists of a [SliderHead], [SliderTick]s, [SliderRepeat]s, and a [SliderTail].
     */
    var nestedHitObjects = mutableListOf<SliderHitObject>()
        private set

    /**
     * The computed velocity of this [Slider]. This is the amount of path distance travelled in 1 ms.
     */
    var velocity: Double = 0.0
        private set

    /**
     * Spacing between [SliderTick]s of this [Slider].
     */
    var tickDistance: Double = 0.0
        private set

    /**
     * An extra multiplier that affects the number of [SliderTick]s generated by this [Slider].
     * An increase in this value increases [tickDistance], which reduces the number of ticks generated.
     */
    @JvmField
    var tickDistanceMultiplier = 1.0

    /**
     * Whether [SliderTick]s should be generated by this object.
     *
     * This exists for backwards compatibility with maps that abuse NaN slider velocity behavior on osu!stable (e.g. /b/2628991).
     */
    @JvmField
    var generateTicks = true

    /**
     * The head of the [Slider].
     */
    lateinit var head: SliderHead
        private set

    /**
     * The tail of the [Slider].
     */
    lateinit var tail: SliderTail
        private set

    /**
     * The position of the cursor at the point of completion of this [Slider] if it was hit
     * with as few movements as possible. This is set and used by difficulty calculation.
     */
    var lazyEndPosition: Vector2? = null

    /**
     * The distance travelled by the cursor upon completion of this [Slider] if it was hit
     * with as few movements as possible. This is set and used by difficulty calculation.
     */
    var lazyTravelDistance = 0f

    /**
     * The time taken by the cursor upon completion of this [Slider] if it was hit with
     * as few movements as possible. This is set and used by difficulty calculation.
     */
    var lazyTravelTime = 0.0

    /**
     * The duration of one span of this [Slider].
     */
    val spanDuration: Double
        get() = duration / spanCount

    override var scale: Float = super.scale
        set(value) {
            field = value

            for (o in nestedHitObjects) {
                o.scale = value
            }
        }

    init {
        // Create sliding samples
        samples.filterIsInstance<BankHitSampleInfo>().apply {
            find { it.name == BankHitSampleInfo.HIT_NORMAL }?.let {
                auxiliarySamples.add(it.copy(name = "sliderslide"))
            }

            find { it.name == BankHitSampleInfo.HIT_WHISTLE }?.let {
                auxiliarySamples.add(it.copy(name = "sliderwhistle"))
            }
        }
    }

    override fun applyDefaults(controlPoints: BeatmapControlPoints, difficulty: BeatmapDifficulty) {
        super.applyDefaults(controlPoints, difficulty)

        val timingPoint = controlPoints.timing.controlPointAt(startTime)
        val difficultyPoint = controlPoints.difficulty.controlPointAt(startTime)

        val sliderVelocityAsBeatLength = -100 / difficultyPoint.speedMultiplier
        val bpmMultiplier =
            if (sliderVelocityAsBeatLength < 0) (-sliderVelocityAsBeatLength).toFloat().coerceIn(10f, 1000f) / 100.0
            else 1.0

        velocity = BASE_SCORING_DISTANCE * difficulty.sliderMultiplier / (timingPoint.msPerBeat * bpmMultiplier)

        // WARNING: this is intentionally not computed as `BASE_SCORING_DISTANCE * difficulty.sliderMultiplier`
        // for backwards compatibility reasons (intentionally introducing floating point errors to match osu!stable).
        val scoringDistance = velocity * timingPoint.msPerBeat

        generateTicks = difficultyPoint.generateTicks

        tickDistance =
            if (generateTicks) scoringDistance / difficulty.sliderTickRate * tickDistanceMultiplier
            else Double.POSITIVE_INFINITY

        // Invalidate the end position in case there are timing changes.
        endPositionCache.invalidate()

        createNestedHitObjects()

        nestedHitObjects.onEach { it.applyDefaults(controlPoints, difficulty) }
    }

    override fun applySamples(controlPoints: BeatmapControlPoints) {
        super.applySamples(controlPoints)

        nodeSamples.forEachIndexed { i, sampleList ->
            val time = startTime + i * spanDuration + CONTROL_POINT_LENIENCY
            val nodeSamplePoint = controlPoints.sample.controlPointAt(time)

            nodeSamples[i] = sampleList.map { nodeSamplePoint.applyTo(it) }.toMutableList()
        }
    }

    private fun createNestedHitObjects() {
        nestedHitObjects.clear()

        head = SliderHead(startTime, position)
        nestedHitObjects.add(head)

        // A very lenient maximum length of a slider for ticks to be generated.
        // This exists for edge cases such as /b/1573664 where the beatmap has been edited by the user, and should never be reached in normal usage.
        val maxLength = 100000.0
        val length = min(maxLength, path.expectedDistance)
        tickDistance = tickDistance.coerceIn(0.0, length)

        if (tickDistance != 0.0 && generateTicks) {
            val minDistanceFromEnd = velocity * 10

            for (span in 0 until spanCount) {
                val spanStartTime = startTime + span * spanDuration
                val reversed = span % 2 == 1
                val sliderTicks: ArrayList<SliderTick> = ArrayList()

                var d = tickDistance
                while (d <= length) {
                    if (d >= length - minDistanceFromEnd) {
                        break
                    }

                    // Always generate ticks from the start of the path rather than the span to ensure
                    // that ticks in repeat spans are positioned identically to those in non-repeat spans
                    val distanceProgress = d / length
                    val timeProgress = if (reversed) 1 - distanceProgress else distanceProgress
                    val tickPosition = position + path.positionAt(distanceProgress)

                    sliderTicks.add(
                        SliderTick(
                            spanStartTime + timeProgress * spanDuration,
                            tickPosition,
                            span,
                            spanStartTime
                        )
                    )

                    d += tickDistance
                }

                // For repeat spans, ticks are returned in reverse-StartTime order.
                if (reversed) {
                    sliderTicks.reverse()
                }

                nestedHitObjects.addAll(sliderTicks)

                if (span < spanCount - 1) {
                    val repeatPosition = position + path.positionAt(((span + 1) % 2).toDouble())

                    nestedHitObjects.add(
                        SliderRepeat(
                            spanStartTime + spanDuration,
                            repeatPosition,
                            span,
                            spanStartTime
                        )
                    )
                }
            }
        }

        // Okay, I'll level with you. I made a mistake. It was 2007.
        // Times were simpler. osu! was but in its infancy and sliders were a new concept.
        // A hack was made, which has unfortunately lived through until this day.
        //
        // This legacy tick is used for some calculations and judgements where audio output is not required.
        // Generally we are keeping this around just for difficulty compatibility.
        // Optimistically we do not want to ever use this for anything user-facing going forwards.
        // Temporarily set end time to start time. It will be evaluated later.
        val finalSpanIndex = repeatCount
        val finalSpanStartTime = startTime + finalSpanIndex * spanDuration
        val finalSpanEndTime = max(
            startTime + duration / 2,
            finalSpanStartTime + spanDuration - LEGACY_LAST_TICK_OFFSET
        )

        tail = SliderTail(finalSpanEndTime, endPosition, finalSpanIndex, finalSpanStartTime)

        nestedHitObjects.apply {
            add(tail)
            sortBy { it.startTime }
        }

        updateNestedSamples()
    }

    private fun updateNestedPositions() {
        endPositionCache.invalidate()

        head.position = position
        tail.position = endPosition
    }

    private fun updateNestedSamples() {
        val bankSamples = samples.filterIsInstance<BankHitSampleInfo>()
        val normalSample = bankSamples.find { it.name == BankHitSampleInfo.HIT_NORMAL }
        val sampleList = mutableListOf<HitSampleInfo>().apply {
            (normalSample ?: bankSamples.firstOrNull())?.let {
                add(it.copy(name = "slidertick"))
            }
        }

        fun getSample(index: Int) = nodeSamples.getOrNull(index) ?: samples

        nestedHitObjects.onEach {
            it.samples.addAll(
                when (it) {
                    is SliderHead -> getSample(0)
                    is SliderRepeat -> getSample(it.spanIndex + 1)
                    is SliderTail -> getSample(spanCount)
                    else -> sampleList
                }
            )
        }
    }

    companion object {
        const val LEGACY_LAST_TICK_OFFSET = 36.0
        private const val BASE_SCORING_DISTANCE = 100f
    }
}
