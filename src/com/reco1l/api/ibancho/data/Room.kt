package com.reco1l.api.ibancho.data

import com.reco1l.legacy.ui.multiplayer.RoomMods
import com.reco1l.legacy.ui.multiplayer.multiLog

data class Room(
        /**
         * The room ID.
         */
        val id: Long,

        /**
         * The room name.
         */
        var name: String,

        /**
         * Indicates if the room has password.
         */
        var isLocked: Boolean,

        /**
         * The max amount of players.
         */
        val maxPlayers: Int,

        /**
         * The active player count.
         */
        var playerCount: Int,

        /**
         * The active player names concatenated.
         */
        var playerNames: String,

        /**
         * The enabled mods.
         */
        var mods: RoomMods,

        /**
         * Free mods condition
         */
        var isFreeMods: Boolean,

        /**
         * The room versus mode.
         */
        var teamMode: TeamMode,

        /**
         * The room win condition.
         */
        var winCondition: WinCondition,

        /**
         * The room status.
         */
        var status: RoomStatus? = null,

        /**
         * Whether the remove slider lock setting is enabled.
         *
         * This value is not provided when the room is being searched.
         */
        var isRemoveSliderLock: Boolean = false
)
{
    /**
     * The array containing the players, null values are empty slots. The array size correspond to [maxPlayers].
     */
    var players: Array<RoomPlayer?> = arrayOfNulls(maxPlayers)

    /**
     * The host/room owner UID.
     */
    var host: Long = -1
        set(value)
        {
            field = value
            sortPlayers()
        }

    /**
     * The current beatmap.
     */
    var beatmap: RoomBeatmap? = null
        set(value) {
            if (field != null)
                previousBeatmap = field

            field = value
        }

    /**
     * The previous beatmap.
     */
    var previousBeatmap: RoomBeatmap? = null
        private set

    /**
     * Besides [players] it provides an array trimmed with no empty slots.
     */
    val activePlayers
        get() = players.filterNotNull()

    /**
     * Returns an array of all players that are in READY status.
     */
    val readyPlayers
        get() = activePlayers.filter { it.status == PlayerStatus.READY }

    /**
     * Get the host Player instance.
     */
    val hostPlayer
        get() = activePlayers.find { it.id == host }

    /**
     * Get the players list in map format using UIDs as keys.
     */
    val playersMap
        get() = activePlayers.associateBy { it.id }

    /**
     * Get the players list of a team in a map format.
     */
    val teamMap
        get() = activePlayers.groupBy { it.team }

    /**
     * Determines if the room team mode is team vs team.
     */
    val isTeamVersus
        get() = teamMode == TeamMode.TEAM_VS_TEAM


    /**
     * Special handling to add a player in the array.
     *
     * @return `true` if it was successfully added, `false` it if wasn't or if it was already in the array (this can
     * happen due to reconnection).
     */
    fun addPlayer(player: RoomPlayer): Boolean
    {
        /** @see [RoomPlayer.equals] */
        var index = players.indexOf(player)
        val wasAlready = index in players.indices

        if (wasAlready)
            multiLog("WARNING: Tried to add player while it was already in the array.")
        else
            index = players.indexOfFirst { it == null }

        // Handling invalid index
        if (index !in players.indices)
        {
            multiLog("WARNING: Tried to add player with invalid index: $index")
            return false
        }

        players[index] = player
        sortPlayers()
        return !wasAlready
    }

    /**
     * Special handling to remove a player in the array.
     *
     * @return The removed player or `null` if it wasn't on the array.
     */
    fun removePlayer(uid: Long): RoomPlayer?
    {
        val index = players.indexOfFirst { it != null && it.id == uid }
        val removed = players.getOrNull(index)

        if (removed != null)
        {
            players[index] = null
            sortPlayers()
        }
        else multiLog("WARNING: Tried to remove a player with invalid index: $index")

        return removed
    }


    /**
     * Sort players array placing non-null first.
     */
    private fun sortPlayers()
    {
        players = players
                .sortedWith { a, b -> (a == null).compareTo(b == null) }
                .toTypedArray()
    }


    fun modsToReadableString() = mods.toString(this)
}
