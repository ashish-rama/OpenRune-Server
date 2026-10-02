package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub as IfOpenSubProt
import dev.openrune.types.ObjectServerType
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.api.player.ui.IfCloseSub
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.IfModalPauseButton
import org.rsmod.api.player.ui.IfOpenSub as IfOpenSubEvent
import org.rsmod.api.player.ui.IfOverlayScriptTrigger
import org.rsmod.api.player.ui.ifClose
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface
import org.rsmod.events.EventBus
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
    private val protectedAccess: ProtectedAccessLauncher,
    private val eventBus: EventBus,
    private val definitions: PohDefinitions,
    private val instanceFactory: PohInstanceFactory,
    private val renderer: PohRenderer,
    private val runtime: PohRuntime,
    private val buildService: PohBuildService,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onCommand("poh") {
            desc = "Create and enter a WIP player-owned house instance"
            cheat { protectedAccess.launch(player) { createHouseInstance() } }
        }

        onCommand("pohmode") {
            desc = "Toggle WIP player-owned house building mode"
            cheat { protectedAccess.launch(player) { buildService.run { toggleBuildMode() } } }
        }

        onCommand("pohbuild") {
            desc = "Select a WIP POH build interface slot"
            cheat {
                val slot = args.firstOrNull()?.toIntOrNull()?.minus(1) ?: 0
                protectedAccess.launch(player) { buildService.run { selectBuildable(slot) } }
            }
        }

        for (locId in definitions.hotspots.values.flatMap { it.locIds }.distinct()) {
                onOpLoc5(ObjectServerType(locId)) {
                    buildService.run { openHotspotBuildInterface(it) }
                }
        }

        for (component in PohConstants.BUILD_SINGLE_COMPONENTS) {
            onProtectedEvent<IfModalButton>(Component(PohConstants.BUILD_SINGLE_IFACE, component).packed) { event ->
                buildService.run { selectBuildableFromInterface(event) }
            }
            onProtectedEvent<IfModalPauseButton>(Component(PohConstants.BUILD_SINGLE_IFACE, component).packed) { event ->
                buildService.run { selectBuildableFromPauseButton(event) }
            }
        }

        for (component in PohConstants.BUILD_SINGLE_SCRIPT_COMPONENTS) {
            onProtectedEvent<IfOverlayScriptTrigger>(Component(PohConstants.BUILD_SINGLE_IFACE, component).packed) { event ->
                buildService.run { selectBuildableFromScriptTrigger(event) }
            }
        }

        onEvent<IfCloseSub>(PohConstants.BUILD_SINGLE_IFACE) {
            buildService.clearBuildSelection(player)
        }

        onOpLoc1(ObjectServerType(definitions.exitPortal.locIds.first())) {
            buildService.run { leaveHouse() }
        }

        for (locId in definitions.buildables.values.flatMap { it.locIds }.distinct()) {
                onOpLoc5(ObjectServerType(locId)) {
                    buildService.run { removeBuiltFurniture(it) }
                }
        }
    }

    private suspend fun ProtectedAccess.createHouseInstance() {
        if (manager.sessionForPlayer(player) != null) {
            player.mes("You are already inside an instance.")
            return
        }

        val house = definitions.defaultHouse()
        val spec = instanceFactory.spec(house, exitCoord = player.coords)
        when (val result = manager.create(player, PohConstants.INSTANCE_KEY, spec, InstanceAccess.Private, worldClock.cycle)) {
            is InstanceManager.Result.Failed -> player.mes(result.reason)
            is InstanceManager.Result.Created -> {
                runtime.put(result.session.id, house, result.enter)
                player.ifOpenMainModal(PohConstants.CONSTRUCTION_LOADING_IFACE, eventBus)
                delay(1)
                if (!collision.isZoneValid(result.enter)) {
                    collision.allocateIfAbsent(result.enter.x, result.enter.z, result.enter.level)
                }
                renderer.renderHouse(house, result.enter)
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
        runClientScript(2524, -1, -1)
        val target = ServerCacheManager.fromComponent("component.toplevel_osrs_stretch:mainmodal")
        val targetComponent = Component(target.packed)
        val translated = ui.gameframe.getOrNull(targetComponent) ?: targetComponent
        val userInterface =
            ServerCacheManager.getInterface(interfaceId)?.let(::UserInterface)
                ?: error("Could not find interface id $interfaceId")
        ui.modals[targetComponent] = userInterface
        client.write(
            IfOpenSubProt(
                translated.parent,
                translated.child,
                interfaceId,
                IfSubType.Modal.id,
            )
        )
        eventBus.publish(IfOpenSubEvent(this, userInterface, targetComponent, IfSubType.Modal))
    }
}
