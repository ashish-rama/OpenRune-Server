package org.rsmod.content.skills.construction

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceId
import org.rsmod.map.CoordGrid

@Singleton
class PohRuntime @Inject constructor() {
    private val states = HashMap<InstanceId, PohInstanceState>()

    fun put(instanceId: InstanceId, house: PohHouseState, enter: CoordGrid) {
        states[instanceId] = PohInstanceState(house, enter)
    }

    fun get(instanceId: InstanceId): PohInstanceState? = states[instanceId]

    fun remove(instanceId: InstanceId) {
        states.remove(instanceId)
    }
}

data class PohInstanceState(
    val house: PohHouseState,
    val enter: CoordGrid,
    var buildSelection: PohBuildSelection? = null,
)

data class PohBuildSelection(
    val room: PohGridCoord,
    val hotspot: PohHotspotKey,
)
