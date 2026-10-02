package org.rsmod.content.skills.construction

import jakarta.inject.Inject
import org.rsmod.api.instances.INSTANCE_GRACE_MINUTES
import org.rsmod.api.instances.INSTANCE_TICKS_PER_MINUTE
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid

class PohInstanceFactory @Inject constructor(private val definitions: PohDefinitions) {
    fun spec(house: PohHouseState, exitCoord: CoordGrid): InstanceSpec =
        InstanceSpec(
            fee = 0,
            maxPlayers = 1,
            reclaimTicks = INSTANCE_GRACE_MINUTES * INSTANCE_TICKS_PER_MINUTE,
            graceTicks = INSTANCE_GRACE_MINUTES * INSTANCE_TICKS_PER_MINUTE,
            destroyWhenEmpty = true,
            area = area(house, exitCoord),
            settingsRowId = -1,
            bossNpcs = null,
            bossName = "Player-owned House",
            recommendedCombat = null,
            teamSize = 1,
            lootMultiplier = "x1.0",
            description = "Player-owned house construction workspace.",
            timeLimitTicks = null,
            spawnOnFirstJoin = false,
        )

    private fun area(house: PohHouseState, exitCoord: CoordGrid): InstanceArea.Template =
        InstanceArea.template(
            template = template(house),
            enterCoord = PohConstants.HouseEnter,
            exitCoord = exitCoord,
        )

    private fun template(house: PohHouseState) =
        RegionTemplate.create {
            for (x in 0 until PohConstants.GRID_SIZE) {
                for (z in 0 until PohConstants.GRID_SIZE) {
                    val grid = PohGridCoord(x, z)
                    val room = house.rooms[grid]
                    val source =
                        if (room != null) {
                            definitions.rooms.getValue(room.room).sourceZone
                        } else {
                            PohConstants.EmptySourceZone
                        }
                    this[x, z, 0] = source
                }
            }
        }
}
