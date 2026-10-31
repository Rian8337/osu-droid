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

    private val beginningCursorMoveIndexes = IntArray(GameScene.CursorCount)
    private val endCursorMoveIndexes = IntArray(GameScene.CursorCount)

    private val submissionTimer = Timer()
    private val submissionPeriod = 5000L
    private var gameEnded = false

    // Reused by every submission, so the buffer keeps its capacity between chunks. Both streams only hold memory, so
    // there is nothing to release by closing them.
    private val chunkBuffer = ByteArrayOutputStream(8192)
    private val chunkOutput = DataOutputStream(chunkBuffer)

    private val task = object : TimerTask() {
        override fun run() {
            val secPassed = gameScene.elapsedTime
            val gameHasEnded = gameEnded

            try {
                chunkBuffer.reset()

                chunkOutput.writeFloat(secPassed)
                chunkOutput.writeInt(endCursorMoveIndexes.size)

                // Each slice is written while holding its lock rather than copied out first. The game thread only
                // waits for that one slice's writes into memory.
                for (i in endCursorMoveIndexes.indices) {
                    val move = replay.cursorMoves[i]

                    synchronized(move) {
                        val begin = beginningCursorMoveIndexes[i]
                        val end = move.size

                        endCursorMoveIndexes[i] = end
                        chunkOutput.writeInt(end - begin)

                        for (j in begin until end) {
                            val movement = move.movements[j]

                            chunkOutput.writeInt((movement.time shl 2) + movement.touchType.id)

                            if (movement.touchType != TouchType.UP) {
                                chunkOutput.writeFloat(movement.x)
                                chunkOutput.writeFloat(movement.y)
                            }
                        }
                    }
                }

                synchronized(objectData) {
                    endObjectDataIndex = objectData.size
                    chunkOutput.writeInt(endObjectDataIndex - beginningObjectDataIndex)

                    for (j in beginningObjectDataIndex until endObjectDataIndex) {
                        objectData[j].writeTo(chunkOutput)
                    }
                }

                synchronized(events) {
                    endEventIndex = events.size
                    chunkOutput.writeInt(endEventIndex - beginningEventIndex)

                    for (j in beginningEventIndex until endEventIndex) {
                        val event = events[j]

                        chunkOutput.writeFloat(event.time)
                        chunkOutput.writeLong(event.score)
                        chunkOutput.writeInt(event.combo)
                        chunkOutput.writeInt(event.hit300)
                        chunkOutput.writeInt(event.hit100)
                        chunkOutput.writeInt(event.hit50)
                        chunkOutput.writeInt(event.misses)
                    }
                }

                chunkOutput.flush()

                // The socket keeps the payload until it is sent, so it gets its own copy rather than the reused buffer.
                val submitted = RoomAPI.submitSpectatorData(chunkBuffer.toByteArray())

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
                endCursorMoveIndexes.copyInto(beginningCursorMoveIndexes)
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