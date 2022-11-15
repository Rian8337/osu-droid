package ru.nsu.ccfit.zuev.osu.spectator;

import ru.nsu.ccfit.zuev.osu.scoring.Replay;

/**
 * Emitted whenever a player hits an object.
 */
public class SpectatorObjectData {
    /**
     * The score of the player after hitting this object.
     */
    public final int currentScore;

    /**
     * The combo of the player after hitting this object.
     */
    public final int currentCombo;

    /**
     * The accuracy of the player after hitting this object, from 0 to 1.
     */
    public final float currentAccuracy;

    /**
     * The replay data of the object.
     */
    public final Replay.ReplayObjectData data;

    public SpectatorObjectData(int currentScore, int currentCombo, float currentAccuracy, Replay.ReplayObjectData data) {
        this.currentScore = currentScore;
        this.currentCombo = currentCombo;
        this.currentAccuracy = currentAccuracy;
        this.data = data;
    }
}
