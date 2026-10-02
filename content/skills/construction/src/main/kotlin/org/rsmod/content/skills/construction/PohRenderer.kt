package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

class PohRenderer
@Inject
constructor(
    private val definitions: PohDefinitions,
    private val locRepo: LocRepository,
) {
    fun renderHouse(house: PohHouseState, enter: CoordGrid) {
        val spawnZone = ZoneKey.from(enter)
        for ((grid, room) in house.rooms) {
            val zone = instanceZone(spawnZone, house.spawnRoom, grid)
            renderRoom(house, room, zone)
        }
    }

    private fun renderRoom(house: PohHouseState, room: PohRoomState, zone: ZoneKey) {
        renderDoorHotspots(room, zone, house.buildMode)
        for ((hotspotKey, hotspot) in definitions.hotspots) {
            val built = room.builtFurniture[hotspotKey]
            val buildable = built?.let { definitions.buildables.getValue(it) }
            renderHotspot(room, zone, hotspot, buildable, house.buildMode)
        }
    }

    private fun renderDoorHotspots(room: PohRoomState, zone: ZoneKey, buildMode: Boolean) {
        val doorHotspots = findDoorHotspots(zone)
        for (door in doorHotspots) {
            val anchor = door.toDoorHotspotAnchor()
            if (room.doorHotspotAnchors.none { it.locId == anchor.locId && it.coords == anchor.coords }) {
                room.doorHotspotAnchors += anchor
            }
        }

        if (buildMode) {
            for (anchor in room.doorHotspotAnchors) {
                val exists =
                    locRepo.findAll(anchor.coords).any {
                        it.id == anchor.locId && it.shape == anchor.shape
                    }
                if (!exists) {
                    addDoorHotspot(anchor)
                }
            }
            return
        }

        for (door in doorHotspots) {
            locRepo.del(door, duration = Int.MAX_VALUE)
        }
    }

    private fun renderHotspot(
        room: PohRoomState,
        zone: ZoneKey,
        hotspot: PohHotspotDefinition,
        buildable: PohBuildableDefinition?,
        buildMode: Boolean,
    ) {
        val anchors = findHotspotAnchors(zone, hotspot)
        anchors.firstOrNull()?.let { room.hotspotAnchors[hotspot.key] = it.toPohAnchor() }

        if (buildable == null && !buildMode) {
            hideHotspot(room, hotspot, anchors)
            return
        }

        if (anchors.isEmpty()) {
            when {
                buildable != null && findBuildableLocs(zone, buildable).isEmpty() -> {
                    val anchor = room.hotspotAnchors[hotspot.key] ?: return
                    addBuiltLoc(anchor, buildable, hotspot.buildableLocSlot)
                }
                buildable == null && buildMode -> {
                    val anchor = room.hotspotAnchors[hotspot.key] ?: return
                    addHotspot(anchor, hotspot)
                }
            }
            return
        }

        for (anchor in anchors) {
            val slot = hotspot.slotFor(anchor.id).coerceAtLeast(0)
            if (buildable != null) {
                addBuiltLoc(anchor.toPohAnchor(), buildable, hotspot.buildableLocSlot + slot)
            }
        }
    }

    private fun hideHotspot(
        room: PohRoomState,
        hotspot: PohHotspotDefinition,
        anchors: List<LocInfo>,
    ) {
        for (anchor in anchors) {
            locRepo.del(anchor, duration = Int.MAX_VALUE)
        }
        val stored = room.hotspotAnchors[hotspot.key] ?: return
        val locIds = hotspot.locIds.toSet()
        for (loc in locRepo.findAll(stored.coords)) {
            if (loc.id in locIds) {
                locRepo.del(loc, duration = Int.MAX_VALUE)
            }
        }
    }

    private fun findHotspotAnchors(zone: ZoneKey, hotspot: PohHotspotDefinition): List<LocInfo> {
        val locIds = hotspot.locIds.toSet()
        return locRepo.findAll(zone).filter { it.id in locIds }.toList()
    }

    private fun findBuildableLocs(zone: ZoneKey, buildable: PohBuildableDefinition): List<LocInfo> {
        val locIds = buildable.locIds.toSet()
        return locRepo.findAll(zone).filter { it.id in locIds }.toList()
    }

    private fun findDoorHotspots(zone: ZoneKey): List<LocInfo> =
        locRepo.findAll(zone)
            .filter { ServerCacheManager.getObject(it.id)?.name.equals("Door hotspot", ignoreCase = true) }
            .toList()

    @Suppress("DEPRECATION")
    fun addHotspot(anchor: PohLocAnchor, hotspot: PohHotspotDefinition): LocInfo =
        locRepo.add(
            coords = anchor.coords,
            type = ObjectServerType(hotspot.locIds.first()),
            duration = Int.MAX_VALUE,
            angle = anchor.angle,
            shape = anchor.shape,
        )

    @Suppress("DEPRECATION")
    fun addBuiltLoc(
        anchor: PohLocAnchor,
        buildable: PohBuildableDefinition,
        slot: Int,
    ): LocInfo =
        locRepo.add(
            coords = anchor.coords,
            type = ObjectServerType(buildable.locIdForSlot(slot)),
            duration = Int.MAX_VALUE,
            angle = anchor.angle,
            shape = anchor.shape,
        )

    @Suppress("DEPRECATION")
    private fun addDoorHotspot(anchor: PohDoorHotspotAnchor): LocInfo =
        locRepo.add(
            coords = anchor.coords,
            type = ObjectServerType(anchor.locId),
            duration = Int.MAX_VALUE,
            angle = anchor.angle,
            shape = anchor.shape,
        )

    private fun instanceZone(
        spawnZone: ZoneKey,
        spawnRoom: PohGridCoord,
        room: PohGridCoord,
    ): ZoneKey {
        val offset = room.offsetFrom(spawnRoom)
        return spawnZone.translate(offset.x, offset.z)
    }

    private fun LocInfo.toPohAnchor(): PohLocAnchor = PohLocAnchor(coords, angle, shape)

    private fun LocInfo.toDoorHotspotAnchor(): PohDoorHotspotAnchor =
        PohDoorHotspotAnchor(id, coords, angle, shape)
}
