package com.rian.spectator

import com.osudroid.beatmaps.hitobjects.Slider
import ru.nsu.ccfit.zuev.osu.scoring.Replay.ReplayObjectData
import java.io.DataOutputStream

/**
 * Represents the replay data of an object.
 */
data class SpectatorObjectData(
    /**
     * The index of the object in the beatmap's hit objects.
     */
    @JvmField
    val index: Int,

    /**
     * The time at which the object was hit, in milliseconds.
     */
    @JvmField
    val time: Double,

    /**
     * The replay data of the object.
     */
    @JvmField
    val data: ReplayObjectData,

    /**
     * The number of parts of the object that have been judged so far, including the slider head.
     *
     * This is always 1 for circles and spinners. For sliders, this ranges from 0 to the amount of nested hit objects
     * of the slider. The record is final once this equals the amount of parts of the object.
     */
    @JvmField
    val judgedCount: Int
) {
    /**
     * Writes this [SpectatorObjectData] to a [DataOutputStream].
     *
     * Layout: `int index, double time, short accuracy, byte ticksetLength + bytes, byte result, short judgedCount`.
     *
     * @param out The [DataOutputStream] to write to.
     */
    fun writeTo(out: DataOutputStream) {
        out.writeInt(index)
        out.writeDouble(time)
        out.writeShort(data.accuracy.toInt())

        val tickSet = data.tickSet

        if (tickSet == null || tickSet.length() == 0) {
            out.writeByte(0)
        } else {
            val bytes = ByteArray((tickSet.length() + 7) / 8)
            for (j in 0 until tickSet.length()) {
                if (tickSet[j]) {
                    bytes[bytes.size - j / 8 - 1] =
                        (bytes[bytes.size - j / 8 - 1].toInt() or (1 shl j % 8)).toByte()
                }
            }
            out.writeByte(bytes.size)
            out.write(bytes)
        }

        out.writeByte(data.result.toInt())
        out.writeShort(judgedCount)
    }

    companion object {
        /**
         * Obtains the nominal time of a partial slider record.
         *
         * @param slider The [Slider].
         * @param judgedCount The number of parts of the [Slider] that have been judged, including the head.
         * @param firstHitAccuracy The accuracy of the slider head's hit, in milliseconds.
         * @return The nominal time of the record. For the head, this is the start time of the [Slider] offset by
         * [firstHitAccuracy]. Otherwise, this is the start time of the nested hit object that was judged last.
         */
        @JvmStatic
        fun partialSliderTime(slider: Slider, judgedCount: Int, firstHitAccuracy: Int) =
            if (judgedCount == 1) slider.startTime + firstHitAccuracy
            else slider.nestedHitObjects[judgedCount - 1].startTime
    }
}
