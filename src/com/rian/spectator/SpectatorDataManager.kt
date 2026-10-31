package com.rian.spectator

import android.util.Log
import com.osudroid.multiplayer.api.RoomAPI
import com.osudroid.beatmaps.hitobjects.HitCircle
import com.osudroid.beatmaps.hitobjects.Slider
import ru.nsu.ccfit.zuev.osu.game.GameScene
import ru.nsu.ccfit.zuev.osu.online.OnlineManager.OnlineManagerException
import ru.nsu.ccfit.zuev.osu.scoring.Replay
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2
import ru.nsu.ccfit.zuev.osu.scoring.TouchType
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.*

/**
 * Holds spectator data that will be sent to the server periodically.
 */
class SpectatorDataManager(
    private val gameScene: GameScene,
    private val replay: Replay,
    private val stat: StatisticV2
) {
    // Appended to on the game thread and read on the submission timer's thread: guard every access with
    // synchronized(objectData) / synchronized(events). Cursor moves are guarded the same way, by their MoveArray.
    private val objectData = mutableListOf<SpectatorObjectData>()
    private var beginningObjectDataIndex = 0
    private var endObjectDataIndex = 0

    private val events = mutableListOf<SpectatorEvent>()
    private var beginningEventIndex = 0
    private var endEventIndex = 0

    private var beginningCursorMoveIndexes = IntArray(GameScene.CursorCount)
    private val endCursorMoveIndexes = IntArray(GameScene.CursorCount)

    private val submissionTimer = Timer()
    private val submissionPeriod = 5000L
    private var gameEnded = false

    private val task = object : TimerTask() {
        override fun run() {
            val secPassed = gameScene.elapsedTime
            val gameHasEnded = gameEnded

            // Copy this chunk's slices under their locks, so the game thread can keep appending while they are written.
            val cursorMoveChunks = Array(endCursorMoveIndexes.size) { i ->
                val move = replay.cursorMoves[i]

                synchronized(move) {
                    endCursorMoveIndexes[i] = move.size
                    move.movements.copyOfRange(beginningCursorMoveIndexes[i], endCursorMoveIndexes[i])
                }
            }

            val objectDataChunk = synchronized(objectData) {
                endObjectDataIndex = objectData.size
                objectData.subList(beginningObjectDataIndex, endObjectDataIndex).toList()
            }

            val eventChunk = synchronized(events) {
                endEventIndex = events.size
                events.subList(beginningEventIndex, endEventIndex).toList()
            }

            try {
                val byteArrayOutputStream = ByteArrayOutputStream()

                DataOutputStream(byteArrayOutputStream).use {
                    it.writeFloat(secPassed)
                    it.writeInt(cursorMoveChunks.size)

                    for (movements in cursorMoveChunks) {
                        it.writeInt(movements.size)

                        for (movement in movements) {
                            it.writeInt((movement.time shl 2) + movement.touchType.id)

                            if (movement.touchType != TouchType.UP) {
                                it.writeFloat(movement.x)
                                it.writeFloat(movement.y)
                            }
                        }
                    }

                    it.writeInt(objectDataChunk.size)
                    for (data in objectDataChunk) {
                        data.writeTo(it)
                    }

                    it.writeInt(eventChunk.size)
                    for (event in eventChunk) {
                        it.writeFloat(event.time)
                        it.writeLong(event.score)
                        it.writeInt(event.combo)
                        it.writeInt(event.hit300)
                        it.writeInt(event.hit100)
                        it.writeInt(event.hit50)
                        it.writeInt(event.misses)
                    }
                }

                // Closing the DataOutputStream flushes it into (and closes) the ByteArrayOutputStream, whose bytes stay
                // readable after closing.
                val submitted = RoomAPI.submitSpectatorData(byteArrayOutputStream.toByteArray())

                postDataSend(submitted, gameHasEnded)
            } catch (e: IOException) {
                Log.e("SpectatorDataManager", "IOException: " + e.message, e)
                postDataSend(false)
            } catch (e: OnlineManagerException) {
                Log.e("SpectatorDataManager", "OnlineManagerException: " + e.message, e)
                postDataSend(false)
            }
        }

        private fun postDataSend(submitSuccess: Boolean, gameHasEnded: Boolean = false) {
            if (gameHasEnded) {
                cancel()
                submissionTimer.cancel()
                return
            }

            if (submitSuccess) {
                beginningCursorMoveIndexes = endCursorMoveIndexes.clone()
                beginningObjectDataIndex = endObjectDataIndex
                beginningEventIndex = endEventIndex
            }
        }
    }

    init {
        submissionTimer.scheduleAtFixedRate(
            task,
            submissionPeriod,
            submissionPeriod
        )
    }

    /**
     * Adds a hit object data.
     *
     * @param objectId The ID of the object.
     */
    fun addObjectData(objectId: Int) {
        val replayObjectData = replay.objectData ?: return

        if (objectId < 0 || objectId >= replayObjectData.size) {
            return
        }

        val obj = gameScene.playableBeatmap!!.hitObjects.objects[objectId]
        val replayData = replayObjectData[objectId]
        var time = obj.endTime
        val hitWindow = if (obj is Slider) obj.head.hitWindow else obj.hitWindow

        if (hitWindow == null) {
            return
        }

        if (obj is HitCircle) {
            // Special handling for circles that are not tapped.
            time += (
                if (replayData.accuracy == 10000.toShort()) hitWindow.mehWindow
                else replayData.accuracy
            ).toDouble()
        }

        val data = SpectatorObjectData(
            objectId,
            time,
            replayData,
            (obj as? Slider)?.nestedHitObjects?.size ?: 1
        )

        synchronized(objectData) { objectData.add(data) }
    }

    /**
     * Adds a snapshot of a slider that has been partially judged.
     *
     * The snapshot stands alone: it copies [tickSet] as the slider keeps mutating it, and it never touches the
     * replay's object data. Nothing is stored when [judgedCount] reaches the amount of nested hit objects of the
     * slider, as that judgement is carried by the final record added via [addObjectData].
     *
     * @param objectId The ID of the slider.
     * @param accuracy The accuracy of the slider head's hit, in milliseconds.
     * @param tickSet The tick set of the slider. Bit `i - 1` represents whether nested hit object `i` was hit.
     * @param judgedCount The number of parts of the slider that have been judged, including the head.
     */
    fun addPartialObjectData(objectId: Int, accuracy: Int, tickSet: BitSet, judgedCount: Int) {
        val objects = gameScene.playableBeatmap?.hitObjects?.objects ?: return

        if (objectId < 0 || objectId >= objects.size) {
            return
        }

        val slider = objects[objectId] as? Slider ?: return

        if (judgedCount < 1 || judgedCount >= slider.nestedHitObjects.size) {
            return
        }

        val replayData = Replay.ReplayObjectData().also {
            it.accuracy = accuracy.toShort()
            it.tickSet = tickSet.clone() as BitSet
        }

        val data = SpectatorObjectData(
            objectId,
            SpectatorObjectData.partialSliderTime(slider, judgedCount, accuracy),
            replayData,
            judgedCount
        )

        synchronized(objectData) { objectData.add(data) }
    }

    /**
     * Adds a spectator event.
     */
    fun addEvent() {
        val event = SpectatorEvent(
            gameScene.elapsedTime * 1000,
            stat.totalScoreWithMultiplier,
            stat.combo,
            stat.hit300,
            stat.hit100,
            stat.hit50,
            stat.misses
        )

        synchronized(events) { events.add(event) }
    }

    /**
     * Stops submitting spectator data for good. The underlying [Timer] cannot be rescheduled once cancelled.
     */
    fun stopSubmission() = submissionTimer.cancel()

    fun setGameEnded(gameEnded: Boolean) {
        this.gameEnded = gameEnded
    }

    @Throws(Throwable::class)
    protected fun finalize() = stopSubmission()
}