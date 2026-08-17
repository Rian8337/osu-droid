package com.rian.spectator

/**
 * Represents a spectator event.
 */
data class SpectatorEvent(
    /**
     * The time at which the event occurred, in milliseconds.
     */
    @JvmField
    val time: Float,

    /**
     * The score of the player after this event.
     */
    @JvmField
    val score: Long,

    /**
     * The combo of the player after this event.
     */
    @JvmField
    val combo: Int,

    /**
     * The amount of 300s achieved after this event.
     */
    @JvmField
    val hit300: Int,

    /**
     * The amount of 100s achieved after this event.
     */
    @JvmField
    val hit100: Int,

    /**
     * The amount of 50s achieved after this event.
     */
    @JvmField
    val hit50: Int,

    /**
     * The number of misses after this event.
     */
    @JvmField
    val misses: Int
)