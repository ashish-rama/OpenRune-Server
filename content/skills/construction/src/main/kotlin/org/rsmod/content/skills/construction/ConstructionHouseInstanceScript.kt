package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub as IfOpenSubProt
import org.rsmod.api.instances.INSTANCE_GRACE_MINUTES
import org.rsmod.api.instances.INSTANCE_TICKS_PER_MINUTE
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.script.onCommand
import org.rsmod.api.player.ui.IfOpenSub as IfOpenSubEvent
import org.rsmod.api.player.ui.ifClose
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface
import org.rsmod.events.EventBus
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import dev.openrune.types.aconverted.interf.IfSubType

class ConstructionHouseInstanceScript
@Inject
constructor(
    private val manager: InstanceManager,
    private val worldClock: MapClock,
    private val collision: CollisionFlagMap,
    private val locRepo: LocRepository,
    private val protectedAccess: ProtectedAccessLauncher,
    private val eventBus: EventBus,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onCommand("poh") {
            desc = "Create and enter a WIP player-owned house instance"
            cheat { protectedAccess.launch(player) { createHouseInstance() } }
        }
    }

    private suspend fun ProtectedAccess.createHouseInstance() {
        if (manager.sessionForPlayer(player) != null) {
            player.mes("You are already inside an instance.")
            return
        }

        val spec = houseInstanceSpec(exitCoord = player.coords)
        when (val result = manager.create(player, HOUSE_INSTANCE_KEY, spec, InstanceAccess.Private, worldClock.cycle)) {
            is InstanceManager.Result.Failed -> player.mes(result.reason)
            is InstanceManager.Result.Created -> {
                player.ifOpenMainModal(CONSTRUCTION_LOADING_IFACE, eventBus)
                delay(1)
                if (!collision.isZoneValid(result.enter)) {
                    collision.allocateIfAbsent(result.enter.x, result.enter.z, result.enter.level)
                }
                renderInitialGardenState(result.enter)
                delay(1)
                PathingEntityCommon.telejump(player, collision, result.enter)
                manager.finalizeEntry(player, result.session, worldClock.cycle)
                delay(1)
                player.ifClose(eventBus)
                player.mes("Created player-owned house instance.")
            }
            is InstanceManager.Result.Joined -> {
                if (!collision.isZoneValid(result.enter)) {
                    collision.allocateIfAbsent(result.enter.x, result.enter.z, result.enter.level)
                }
                PathingEntityCommon.telejump(player, collision, result.enter)
                manager.finalizeEntry(player, result.session, worldClock.cycle)
            }
        }
    }

    private fun org.rsmod.game.entity.Player.ifOpenMainModal(interfaceId: Int, eventBus: EventBus) {
        val target = ServerCacheManager.fromComponent("component.toplevel_osrs_stretch:mainmodal")
        val targetComponent = Component(target.packed)
        val userInterface =
            ServerCacheManager.getInterface(interfaceId)?.let(::UserInterface)
                ?: error("Could not find interface id $interfaceId")
        ui.modals[targetComponent] = userInterface
        client.write(
            IfOpenSubProt(
                target.interfaceId,
                target.component,
                interfaceId,
                IfSubType.Modal.id,
            )
        )
        eventBus.publish(IfOpenSubEvent(this, userInterface, targetComponent, IfSubType.Modal))
    }

    @Suppress("DEPRECATION")
    private fun renderInitialGardenState(enter: CoordGrid) {
        val hotspot =
            locRepo.findAll(ZoneKey.from(enter)).firstOrNull {
                it.id == GARDEN_CENTERPIECE_HOTSPOT_LOC
            }
        locRepo.add(
            coords = hotspot?.coords ?: enter,
            type = ObjectServerType(EXIT_PORTAL_LOC),
            duration = Int.MAX_VALUE,
            angle = hotspot?.angle ?: LocAngle.West,
            shape = hotspot?.shape ?: LocShape.CentrepieceStraight,
        )
    }

    private fun houseInstanceSpec(exitCoord: CoordGrid): InstanceSpec =
        InstanceSpec(
            fee = 0,
            maxPlayers = 1,
            reclaimTicks = INSTANCE_GRACE_MINUTES * INSTANCE_TICKS_PER_MINUTE,
            graceTicks = INSTANCE_GRACE_MINUTES * INSTANCE_TICKS_PER_MINUTE,
            destroyWhenEmpty = true,
            area = HOUSE_AREA.copy(exitCoord = exitCoord),
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

    private companion object {
        private const val HOUSE_INSTANCE_KEY = "poh"
        private const val HOUSE_GRID_SIZE = 9
        private const val HOUSE_GARDEN_ZONE = 4
        private const val CONSTRUCTION_LOADING_IFACE = 71
        private const val GARDEN_CENTERPIECE_HOTSPOT_LOC = 15361
        private const val EXIT_PORTAL_LOC = 4525

        /*
         * The POH construction hotspot atlas begins at tile 1856,7040:
         * chunk 232,880. A new house starts in the garden chunk directly north
         * of it: chunk 232,881. The surrounding 9x9 layout is filled with the
         * empty source chunk directly east of the start: chunk 233,880.
         */
        private val HOUSE_ENTER = RegionLocal(
            level = 0,
            regionZoneX = 29,
            regionZoneZ = 110,
            localX = 4,
            localZ = 12,
        )

        private val HOUSE_AREA =
            InstanceArea.template(
                template = RegionTemplate.create {
                    val empty = ZoneKey(233, 880, 0)
                    for (x in 0 until HOUSE_GRID_SIZE) {
                        for (z in 0 until HOUSE_GRID_SIZE) {
                            if (x == HOUSE_GARDEN_ZONE && z == HOUSE_GARDEN_ZONE) {
                                continue
                            }
                            this[x, z, 0] = empty
                        }
                    }
                    this[HOUSE_GARDEN_ZONE, HOUSE_GARDEN_ZONE, 0] = ZoneKey(232, 881, 0)
                },
                enterCoord = HOUSE_ENTER,
                exitCoord = CoordGrid.ZERO,
            )
    }
}
