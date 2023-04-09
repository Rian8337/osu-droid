package ru.nsu.ccfit.zuev.osu.spectator;

import android.util.Log;

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
    public static final int spectatorDataVersion = 1;

    private final ArrayList<SpectatorObjectData> objectData = new ArrayList<>();

    private int[] beginningCursorMoveIndexes = new int[GameScene.CursorCount];
    private final int[] endCursorMoveIndexes = new int[GameScene.CursorCount];

    private String roomId = "";
    private int beginningObjectDataIndex;
    private int endObjectDataIndex;

    private final GameScene gameScene;
    private final Replay replay;
    private final StatisticV2 stat;

    private final Timer submissionTimer = new Timer();
    private final long submissionPeriod = 5000;
    private boolean isPaused;
    private boolean gameEnded;

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
                ds.writeInt(stat.getHit300());
                ds.writeInt(stat.getHit100());
                ds.writeInt(stat.getHit50());
                ds.writeInt(stat.getMisses());

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
                    roomId,
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
                Log.e("SpectatorDataManager", "IOException: " + e.getMessage(), e);
                postDataSend(false);
            } catch (OnlineManager.OnlineManagerException e) {
                Log.e("SpectatorDataManager", "OnlineManagerException: " + e.getMessage(), e);
                postDataSend(false);
            } finally {
                try {
                    byteArrayOutputStream.flush();
                    byteArrayOutputStream.close();
                } catch (IOException e) {
                    Log.e("SpectatorDataManager", "IOException: " + e.getMessage(), e);
                }
            }
        }

        private void postDataSend(final boolean success) {
            if (!success) {
                return;
            }

            if (gameEnded) {
                cancel();
                gameScene.stopSpectatorDataSubmission();
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

        submissionTimer.scheduleAtFixedRate(
            task,
            submissionPeriod,
            submissionPeriod
        );
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

    /**
     * Sets the room ID of this manager.
     *
     * @param id The room ID.
     */
    public void setRoomId(final String id) {
        roomId = id;
    }

    public void setGameEnded(boolean gameEnded) {
        this.gameEnded = gameEnded;
    }

    @Override
    protected void finalize() throws Throwable {
        pauseTimer();
        super.finalize();
    }
}
