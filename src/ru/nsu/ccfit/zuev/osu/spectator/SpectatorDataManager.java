package ru.nsu.ccfit.zuev.osu.spectator;

import org.anddev.andengine.util.Debug;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.game.GameScene;
import ru.nsu.ccfit.zuev.osu.online.OnlineManager;
import ru.nsu.ccfit.zuev.osu.scoring.Replay;
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2;
import ru.nsu.ccfit.zuev.osu.scoring.TouchType;

/**
 * Holds spectator data that will be sent to the server periodically.
 */
public class SpectatorDataManager {
    private final ArrayList<SpectatorObjectData> objectData = new ArrayList<>();

    private int[] beginningCursorMoveIndexes = new int[GameScene.CursorCount];
    private final int[] endCursorMoveIndexes = new int[GameScene.CursorCount];

    private int beginningObjectDataIndex;
    private int endObjectDataIndex;

    private final GameScene gameScene;
    private final Replay replay;
    private final StatisticV2 stat;

    private final Timer submissionTimer = new Timer();
    private final long submissionPeriod = 5000;
    private boolean isPaused = false;
    private final TimerTask task = new TimerTask() {
        @Override
        public void run() {
            float secPassed = gameScene.getSecPassed();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            DataOutputStream ds;

            try {
                ds = new DataOutputStream(byteArrayOutputStream);

                for (int i = 0; i < endCursorMoveIndexes.length; ++i) {
                    endCursorMoveIndexes[i] = replay.cursorMoves.get(i).size;
                }

                endObjectDataIndex = objectData.size();

                ds.writeFloat(secPassed);
                ds.writeInt(stat.getModifiedTotalScore());
                ds.writeInt(stat.getCombo());
                ds.writeFloat(stat.getAccuracy());

                ds.writeInt(replay.cursorMoves.size());
                for (int i = 0; i < beginningCursorMoveIndexes.length; ++i) {
                    Replay.MoveArray move = replay.cursorMoves.get(i);

                    int beginningIndex = beginningCursorMoveIndexes[i];
                    int endIndex = endCursorMoveIndexes[i];

                    ds.writeInt(endIndex - beginningIndex);
                    for (int j = beginningIndex; j < endIndex; ++j) {
                        Replay.ReplayMovement movement = move.movements[j];
                        ds.writeInt((movement.getTime() << 2) + movement.getTouchType().getId());

                        if (movement.getTouchType() != TouchType.UP) {
                            ds.writeFloat(movement.getPoint().x * Config.getTextureQuality());
                            ds.writeFloat(movement.getPoint().y * Config.getTextureQuality());
                        }
                    }
                }

                ds.writeInt(endObjectDataIndex - beginningObjectDataIndex);
                for (int i = beginningObjectDataIndex; i < endObjectDataIndex; ++i) {
                    SpectatorObjectData specData = objectData.get(i);
                    Replay.ReplayObjectData data = specData.data;

                    ds.writeInt(i);
                    ds.writeInt(specData.currentScore);
                    ds.writeInt(specData.currentCombo);
                    ds.writeFloat(specData.currentAccuracy);
                    ds.writeShort(data.accuracy);
                    if (data.tickSet == null || data.tickSet.length() == 0) {
                        ds.writeByte(0);
                    } else {
                        byte[] bytes = new byte[(data.tickSet.length() + 7) / 8];
                        for (int j = 0; j < data.tickSet.length(); ++j) {
                            if (data.tickSet.get(j)) {
                                bytes[bytes.length - j / 8 - 1] |= 1 << (j % 8);
                            }
                        }
                        ds.writeByte(bytes.length);
                        ds.write(bytes);
                    }
                    ds.writeByte(data.result);
                }

                ds.flush();
                byteArrayOutputStream.flush();

                String message = OnlineManager.getInstance().sendSpectatorData(
                    byteArrayOutputStream.toByteArray()
                );

                ds.close();
                byteArrayOutputStream.close();

                if (message.equals("FAILED")) {
                    gameScene.stopSpectatorDataSubmission();
                    return;
                }

                postDataSend(message.equals("SUCCESS"));
            } catch (final IOException e) {
                Debug.e("IOException: " + e.getMessage(), e);
                postDataSend(false);
            } catch (OnlineManager.OnlineManagerException e) {
                Debug.e("OnlineManagerException: " + e.getMessage(), e);
                postDataSend(false);
            } finally {
                try {
                    byteArrayOutputStream.flush();
                    byteArrayOutputStream.close();
                } catch (IOException e) {
                    Debug.e("IOException: " + e.getMessage(), e);
                }
            }
        }

        private void postDataSend(final boolean success) {
            if (!success) {
                return;
            }

            if (endObjectDataIndex == replay.objectData.length - 1) {
                // End the submission after the latest object data has been submitted.
                cancel();
                return;
            }

            beginningCursorMoveIndexes = endCursorMoveIndexes.clone();
            beginningObjectDataIndex = endObjectDataIndex;
        }
    };

    public SpectatorDataManager(final GameScene gameScene, final Replay replay, final StatisticV2 stat) {
        this.gameScene = gameScene;
        this.replay = replay;
        this.stat = stat;

        submissionTimer.scheduleAtFixedRate(task, submissionPeriod, submissionPeriod);
    }

    /**
     * Adds a hit object data.
     *
     * @param objectId The ID of the object.
     */
    public void addObjectData(int objectId) {
        objectData.add(
            new SpectatorObjectData(
                stat.getModifiedTotalScore(),
                stat.getCombo(),
                stat.getAccuracy(),
                replay.objectData[objectId]
            )
        );
    }

    /**
     * Resumes the timer after a specified delay.
     *
     * @param delay The delay.
     */
    public void resumeTimer(final long delay) {
        if (!isPaused) {
            return;
        }

        submissionTimer.scheduleAtFixedRate(task, delay, submissionPeriod);
        isPaused = false;
    }

    /**
     * Pauses the timer.
     */
    public void pauseTimer() {
        if (isPaused) {
            return;
        }

        submissionTimer.cancel();
        isPaused = true;
    }

    @Override
    protected void finalize() throws Throwable {
        pauseTimer();
        super.finalize();
    }
}
