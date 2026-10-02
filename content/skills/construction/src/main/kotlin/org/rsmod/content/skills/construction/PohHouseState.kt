package org.rsmod.content.skills.construction

data class PohHouseState(
    var buildMode: Boolean,
    val rooms: MutableMap<PohGridCoord, PohRoomState>,
) {
    val spawnRoom: PohGridCoord = PohConstants.CenterGrid
}

data class PohRoomState(
    val room: PohRoomKey,
    val rotation: Int,
    val builtFurniture: MutableMap<PohHotspotKey, PohBuildableKey>,
    val hotspotAnchors: MutableMap<PohHotspotKey, PohLocAnchor> = mutableMapOf(),
    val doorHotspotAnchors: MutableList<PohDoorHotspotAnchor> = mutableListOf(),
) {
    companion object {
        fun fromDefinition(definition: PohRoomDefinition): PohRoomState =
            PohRoomState(
                room = definition.key,
                rotation = 0,
                builtFurniture = definition.defaultBuiltFurniture.toMutableMap(),
            )
    }
}

data class PohLocAnchor(
    val coords: org.rsmod.map.CoordGrid,
    val angle: org.rsmod.game.loc.LocAngle,
    val shape: org.rsmod.game.loc.LocShape,
)

data class PohDoorHotspotAnchor(
    val locId: Int,
    val coords: org.rsmod.map.CoordGrid,
    val angle: org.rsmod.game.loc.LocAngle,
    val shape: org.rsmod.game.loc.LocShape,
)

data class PohGridCoord(val x: Int, val z: Int) {
    init {
        require(x in 0 until PohConstants.GRID_SIZE) {
            "`x` must be within the POH grid."
        }
        require(z in 0 until PohConstants.GRID_SIZE) {
            "`z` must be within the POH grid."
        }
    }

    fun offsetFrom(other: PohGridCoord): PohGridOffset = PohGridOffset(x - other.x, z - other.z)
}

data class PohGridOffset(val x: Int, val z: Int)
