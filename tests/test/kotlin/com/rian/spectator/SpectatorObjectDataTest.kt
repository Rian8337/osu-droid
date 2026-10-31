package com.rian.spectator

import com.osudroid.GameMode
import com.osudroid.beatmaps.hitobjects.Slider
import com.osudroid.beatmaps.hitobjects.SliderPath
import com.osudroid.beatmaps.hitobjects.SliderPathType
import com.osudroid.beatmaps.sections.BeatmapControlPoints
import com.osudroid.beatmaps.sections.BeatmapDifficulty
import com.osudroid.beatmaps.timings.TimingControlPoint
import com.osudroid.math.Vector2
import org.junit.Assert
import org.junit.Test
import ru.nsu.ccfit.zuev.osu.scoring.Replay.ReplayObjectData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.BitSet

class SpectatorObjectDataTest {
    @Test
    fun `writes record layout`() {
        val replayData = ReplayObjectData().apply {
            accuracy = -12
            tickSet = BitSet().apply {
                set(0)
                set(2)
            }
            result = 4
        }

        val bytes = SpectatorObjectData(7, 1234.5, replayData, 3).serialize()

        // int + double + short + byte (length) + 1 tickset byte + byte (result) + short
        Assert.assertEquals(4 + 8 + 2 + 1 + 1 + 1 + 2, bytes.size)

        DataInputStream(ByteArrayInputStream(bytes)).use {
            Assert.assertEquals(7, it.readInt())
            Assert.assertEquals(1234.5, it.readDouble(), 0.0)
            Assert.assertEquals((-12).toShort(), it.readShort())
            Assert.assertEquals(1, it.readByte().toInt())
            Assert.assertEquals(0b101, it.readByte().toInt())
            Assert.assertEquals(4, it.readByte().toInt())
            Assert.assertEquals(3.toShort(), it.readShort())
            Assert.assertEquals(0, it.available())
        }
    }

    @Test
    fun `writes empty tickset`() {
        val replayData = ReplayObjectData().apply {
            accuracy = 5
            tickSet = null
            result = 2
        }

        val bytes = SpectatorObjectData(0, 10.0, replayData, 1).serialize()

        // int + double + short + byte (length, 0) + byte (result) + short
        Assert.assertEquals(4 + 8 + 2 + 1 + 1 + 2, bytes.size)

        DataInputStream(ByteArrayInputStream(bytes)).use {
            Assert.assertEquals(0, it.readInt())
            Assert.assertEquals(10.0, it.readDouble(), 0.0)
            Assert.assertEquals(5.toShort(), it.readShort())
            Assert.assertEquals(0, it.readByte().toInt())
            Assert.assertEquals(2, it.readByte().toInt())
            Assert.assertEquals(1.toShort(), it.readShort())
            Assert.assertEquals(0, it.available())
        }
    }

    @Test
    fun `partial slider time uses head offset`() {
        val slider = createSlider()

        Assert.assertEquals(1000.0 + 37, SpectatorObjectData.partialSliderTime(slider, 1, 37), 0.0)
    }

    @Test
    fun `partial slider time uses nested time`() {
        val slider = createSlider()

        // Head, tick, repeat, tick, tail at the very least.
        Assert.assertTrue(slider.nestedHitObjects.size >= 4)

        Assert.assertEquals(
            slider.nestedHitObjects[2].startTime,
            SpectatorObjectData.partialSliderTime(slider, 3, 37),
            0.0
        )
        Assert.assertEquals(
            slider.nestedHitObjects[1].startTime,
            SpectatorObjectData.partialSliderTime(slider, 2, 37),
            0.0
        )
    }

    private fun SpectatorObjectData.serialize(): ByteArray {
        val bytes = ByteArrayOutputStream()

        DataOutputStream(bytes).use {
            writeTo(it)
            it.flush()
        }

        return bytes.toByteArray()
    }

    private fun createSlider(): Slider {
        // Two spans of 200px at 100px / 300ms (1 beat per 100px), which generates a tick in each span and a repeat.
        val slider = Slider(
            1000.0,
            Vector2(0),
            1,
            SliderPath(SliderPathType.Linear, listOf(Vector2(0), Vector2(200, 0)), 200.0),
            false,
            0,
            mutableListOf()
        )

        val controlPoints = BeatmapControlPoints().apply {
            timing.add(TimingControlPoint(0.0, 300.0, 4))
        }

        slider.applyDefaults(controlPoints, BeatmapDifficulty(ar = 9f), GameMode.Droid)

        return slider
    }
}
