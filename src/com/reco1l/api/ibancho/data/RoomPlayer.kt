package com.reco1l.api.ibancho.data

import com.reco1l.legacy.ui.multiplayer.RoomMods

data class RoomPlayer
(
        /**
         * The user ID.
         */
        val id: Long,

        /**
         * The username.
         */
        val name: String,

        /**
         * The player status.
         */
        var status: PlayerStatus,

        /**
         * The player team if the mode is set to team versus.
         */
        var team: RoomTeam?,

        /**
         * The player mods.
         */
        var mods: RoomMods
)
{
        /**
         * Locally used to indicate the player is muted.
         */
        var isMuted = false


        override fun equals(other: Any?) = other === this || other is RoomPlayer && other.id == id
}
