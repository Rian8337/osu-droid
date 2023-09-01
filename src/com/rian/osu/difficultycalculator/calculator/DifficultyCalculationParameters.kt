package com.rian.osu.difficultycalculator.calculator

import ru.nsu.ccfit.zuev.osu.game.mods.GameMod
import java.util.*

/**
 * A class for specifying parameters for difficulty calculation.
 */
data class DifficultyCalculationParameters(
    /**
     * The mods to calculate for.
     */
    @JvmField
    var mods: EnumSet<GameMod> = EnumSet.noneOf(GameMod::class.java),

    /**
     * The custom speed multiplier to calculate for.
     */
    @JvmField
    var customSpeedMultiplier: Float = 1f,

    /**
     * The forced AR setting to calculate for. Set to `null` to disable.
     */
    @JvmField
    var forcedAR: Float? = null
) : Cloneable {
    /**
     * The overall speed multiplier to calculate for.
     */
    val totalSpeedMultiplier: Float
        get() {
            var speedMultiplier = customSpeedMultiplier

            if (mods.contains(GameMod.MOD_DOUBLETIME) || mods.contains(GameMod.MOD_NIGHTCORE)) {
                speedMultiplier *= 1.5f
            }

            if (mods.contains(GameMod.MOD_HALFTIME)) {
                speedMultiplier *= 0.75f
            }

            return speedMultiplier
        }

    /**
     * Whether force AR is active in this parameter.
     */
    val isForceAR: Boolean
        get() = forcedAR != null

    public override fun clone() =
        (super.clone() as DifficultyCalculationParameters).apply {
            mods = this@DifficultyCalculationParameters.mods.clone()
        }
}
