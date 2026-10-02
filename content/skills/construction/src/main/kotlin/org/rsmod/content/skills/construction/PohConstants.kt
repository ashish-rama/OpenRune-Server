package org.rsmod.content.skills.construction

import org.rsmod.api.instances.RegionLocal
import org.rsmod.map.zone.ZoneKey

object PohConstants {
    const val INSTANCE_KEY = "poh"
    const val GRID_SIZE = 9
    const val CONSTRUCTION_LOADING_IFACE = 71
    const val BUILD_SINGLE_IFACE = 458
    const val BUILD_SINGLE_LIST_COMPONENT = 2
    const val BUILD_SINGLE_FIRST_ROW_COMPONENT = 4
    const val BUILD_SINGLE_LAST_ROW_COMPONENT = 11
    val BUILD_SINGLE_SCRIPT_COMPONENTS = 0..35
    val BUILD_SINGLE_COMPONENTS =
        BUILD_SINGLE_SCRIPT_COMPONENTS.toList()

    val CenterGrid = PohGridCoord(4, 4)

    /*
     * The POH construction hotspot atlas begins at tile 1856,7040:
     * chunk 232,880. A new house starts in the garden chunk directly north
     * of it: chunk 232,881. The surrounding 9x9 layout is filled with the
     * empty source chunk directly east of the atlas start: chunk 233,880.
     */
    val EmptySourceZone = ZoneKey(233, 880, 0)
    val GardenSourceZone = ZoneKey(232, 881, 0)

    val HouseEnter =
        RegionLocal(
            level = 0,
            regionZoneX = 29,
            regionZoneZ = 110,
            localX = 4,
            localZ = 12,
        )
}
