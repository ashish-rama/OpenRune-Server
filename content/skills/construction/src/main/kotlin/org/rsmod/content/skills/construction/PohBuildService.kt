package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.ItemServerType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.aconverted.interf.IfSubType
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub as IfOpenSubProt
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.invtx.invDelAll
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.ui.IfOpenSub as IfOpenSubEvent
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.IfModalPauseButton
import org.rsmod.api.player.ui.IfOverlayScriptTrigger
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.inv.InvObj
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface
import org.rsmod.routefinder.collision.CollisionFlagMap

class PohBuildService
@Inject
constructor(
    private val manager: InstanceManager,
    private val worldClock: MapClock,
    private val collision: CollisionFlagMap,
    private val definitions: PohDefinitions,
    private val runtime: PohRuntime,
    private val renderer: PohRenderer,
    private val locRepo: LocRepository,
    private val eventBus: EventBus,
) {
    suspend fun ProtectedAccess.toggleBuildMode() {
        val state = currentState() ?: return
        state.house.buildMode = !state.house.buildMode
        renderer.renderHouse(state.house, state.enter)
        player.mes("Building mode is now ${if (state.house.buildMode) "on" else "off"}.")
    }

    fun ProtectedAccess.openHotspotBuildInterface(event: LocEvents.Op5) {
        val state = currentState() ?: return
        if (!state.house.buildMode) {
            player.mes("You can only build furniture in building mode.")
            return
        }

        val room = state.house.rooms[PohConstants.CenterGrid] ?: return
        val hotspot = definitions.hotspots.values.firstOrNull { event.vis.id in it.locIds }
        if (hotspot == null) {
            player.mes("No POH hotspot definition exists for loc ${event.vis.id}.")
            return
        }
        room.hotspotAnchors[hotspot.key] = PohLocAnchor(event.vis.coords, event.vis.angle, event.vis.shape)
        state.buildSelection = PohBuildSelection(PohConstants.CenterGrid, hotspot.key)

        val buildables = hotspot.buildables.map { definitions.buildables.getValue(it) }
        player.ifOpenMainModal(PohConstants.BUILD_SINGLE_IFACE, eventBus)
        player.sendFurnitureCreationMenu(buildables)
        player.mes("Selected ${hotspot.name}. Use ::pohbuild <slot> if the interface buttons do not respond yet.")
    }

    fun ProtectedAccess.selectBuildable(slot: Int) {
        val state = currentState() ?: return
        val selection = state.buildSelection
        if (selection == null) {
            player.mes("No build hotspot is selected.")
            return
        }

        val room = state.house.rooms[selection.room] ?: return
        val hotspot = definitions.hotspots.getValue(selection.hotspot)
        val buildable = buildableFor(hotspot, slot)
        if (buildable == null) {
            player.mes("Nothing is available in that build slot.")
            return
        }
        val unmetRequirement = player.firstUnmetRequirement(buildable)
        if (unmetRequirement != null) {
            player.mes(unmetRequirement)
            return
        }

        val anchor = room.hotspotAnchors[hotspot.key]
        if (anchor == null) {
            player.mes("The selected build hotspot is no longer available.")
            return
        }
        if (!player.consumeMaterials(buildable)) {
            player.mes("You do not have the materials needed to build this.")
            return
        }

        room.builtFurniture[hotspot.key] = buildable.key
        renderer.addBuiltLoc(
            anchor,
            buildable,
            slot = hotspot.buildableLocSlot,
        )
        state.buildSelection = null
        player.ifClose(eventBus)
        player.runClientScript(2158)
        statAdvance("stat.construction", buildable.xp)
        player.mes("You build ${buildable.displayName().lowercase()}.")
    }

    fun ProtectedAccess.selectBuildableFromInterface(event: IfModalButton) {
        val slot =
            buildSlotFrom(component = event.component.component, comsub = event.comsub)
                .takeIf { it >= 0 }
                ?: slotFromClientIndex(event.op.slot)
                ?: -1
        if (slot < 0) {
            player.mes(
                "IF458 click did not include a furniture slot: " +
                    "component=${event.component.component} sub=${event.comsub} op=${event.op.slot}."
            )
            return
        }
        selectBuildable(slot)
    }

    fun ProtectedAccess.selectBuildableFromScriptTrigger(event: IfOverlayScriptTrigger) {
        val slot =
            buildSlotFrom(component = event.component.component, comsub = event.comsub)
                .takeIf { it >= 0 }
                ?: event.args.asSequence().filterIsInstance<Int>().mapNotNull(::slotFromClientIndex).firstOrNull()
                ?: return
        selectBuildable(slot)
    }

    fun ProtectedAccess.selectBuildableFromPauseButton(event: IfModalPauseButton) {
        val slot = buildSlotFrom(component = event.component.component, comsub = event.comsub)
        if (slot < 0) {
            player.mes(
                "IF458 pause button did not include a furniture slot: " +
                    "component=${event.component.component} sub=${event.comsub}."
            )
            return
        }
        selectBuildable(slot)
    }

    fun clearBuildSelection(player: Player) {
        val session = manager.sessionForPlayer(player) ?: return
        if (session.key != PohConstants.INSTANCE_KEY) {
            return
        }
        runtime.get(session.id)?.buildSelection = null
        player.runClientScript(2158)
    }

    suspend fun ProtectedAccess.removeBuiltFurniture(event: LocEvents.Op5) {
        val state = currentState() ?: return
        if (!state.house.buildMode) {
            player.mes("You can only remove furniture in building mode.")
            return
        }

        val confirmed =
            choice2(
                "Remove exit portal.",
                true,
                "Cancel.",
                false,
                title = "Really remove it?",
            )
        if (!confirmed) {
            return
        }

        val room = state.house.rooms[PohConstants.CenterGrid] ?: return
        val hotspotKey =
            room.builtFurniture.entries.firstOrNull { (_, buildableKey) ->
                val buildable = definitions.buildables.getValue(buildableKey)
                event.vis.id in buildable.locIds
            }?.key
        if (hotspotKey == null) {
            player.mes("No built furniture state exists for this object.")
            return
        }
        room.builtFurniture.remove(hotspotKey)
        locRepo.del(event.vis, duration = Int.MAX_VALUE)
        renderer.renderHouse(state.house, state.enter)
        player.mes("You remove the furniture.")
    }

    fun ProtectedAccess.leaveHouse() {
        val session = manager.sessionForPlayer(player) ?: return
        if (session.key != PohConstants.INSTANCE_KEY) {
            return
        }
        val exit = manager.leave(player, session, worldClock.cycle)
        runtime.remove(session.id)
        PathingEntityCommon.telejump(player, collision, exit)
    }

    private fun ProtectedAccess.currentState(): PohInstanceState? {
        val session = manager.sessionForPlayer(player)
        if (session == null || session.key != PohConstants.INSTANCE_KEY) {
            player.mes("You are not inside a player-owned house.")
            return null
        }
        val state = runtime.get(session.id)
        if (state == null) {
            player.mes("No house state is available for this instance.")
            return null
        }
        return state
    }

    private fun buildableFor(
        hotspot: PohHotspotDefinition,
        slot: Int,
    ): PohBuildableDefinition? =
        when {
            slot in hotspot.buildables.indices -> definitions.buildables.getValue(hotspot.buildables[slot])
            else -> null
        }

    private fun buildSlotFrom(component: Int, comsub: Int): Int =
        when {
            component in PohConstants.BUILD_SINGLE_FIRST_ROW_COMPONENT..PohConstants.BUILD_SINGLE_LAST_ROW_COMPONENT ->
                component - PohConstants.BUILD_SINGLE_FIRST_ROW_COMPONENT
            component == PohConstants.BUILD_SINGLE_LIST_COMPONENT && comsub > 0 -> comsub - 1
            comsub in 1..8 -> comsub - 1
            else -> -1
        }

    private fun slotFromClientIndex(value: Int): Int? =
        when (value) {
            in 1..8 -> value - 1
            in 0..7 -> value
            else -> null
        }

    private fun Player.sendFurnitureCreationMenu(buildables: List<PohBuildableDefinition>) {
        var slot = 1
        for (buildable in buildables) {
            val enabled = if (canBuild(buildable)) 1 else 0
            runClientScript(
                1404,
                slot++,
                buildable.menuFurnitureId(),
                buildable.constructionLevel(),
                buildable.creationMenuString(),
                enabled,
            )
        }
        runClientScript(1406, buildables.size, 0)
        sendFurnitureCreationEvents(buildables.size)
        runClientScript(2157)
    }

    private fun Player.canBuild(buildable: PohBuildableDefinition): Boolean =
        firstUnmetRequirement(buildable) == null

    private fun Player.firstUnmetRequirement(buildable: PohBuildableDefinition): String? {
        val constructionLevel = stat("stat.construction")
        val requiredLevel = buildable.constructionLevel()
        if (constructionLevel < requiredLevel) {
            return "You need a Construction level of $requiredLevel to build this."
        }
        val missingMaterial =
            buildable.resolvedMaterials().firstOrNull { material ->
                invCount(material.itemId) < material.count
            }
        if (missingMaterial != null) {
            return "You need ${missingMaterial.count} x ${missingMaterial.displayName()} to build this."
        }
        return null
    }

    private fun Player.invCount(itemId: Int): Int {
        var count = 0
        for (obj in inv) {
            if (obj?.id == itemId) {
                count += obj.count
            }
        }
        return count
    }

    private fun Player.consumeMaterials(buildable: PohBuildableDefinition): Boolean {
        val materials = buildable.resolvedMaterials()
        if (materials.isEmpty()) {
            return true
        }
        val consume = materials.map { InvObj(ItemServerType(it.itemId), it.count) }
        return invDelAll(inv, consume, strict = true).success
    }

    private fun Player.sendFurnitureCreationEvents(size: Int) {
        val events = IfEvent.Op1.bitmask or IfEvent.PauseButton.bitmask
        val target = Component(PohConstants.BUILD_SINGLE_IFACE, PohConstants.BUILD_SINGLE_LIST_COMPONENT)
        ui.events.add(target, 1..size, events)
        client.write(
            IfSetEventsV2(
                interfaceId = target.parent,
                componentId = target.child,
                start = 1,
                end = size,
                events1 = events.toInt(),
                events2 = (events shr 32).toInt(),
            )
        )
        for (component in PohConstants.BUILD_SINGLE_FIRST_ROW_COMPONENT..PohConstants.BUILD_SINGLE_LAST_ROW_COMPONENT) {
            val row = Component(PohConstants.BUILD_SINGLE_IFACE, component)
            ui.events.add(row, -1..-1, events)
            client.write(
                IfSetEventsV2(
                    interfaceId = row.parent,
                    componentId = row.child,
                    start = -1,
                    end = -1,
                    events1 = events.toInt(),
                    events2 = (events shr 32).toInt(),
                )
            )
        }
    }

    private fun Player.ifOpenMainModal(interfaceId: Int, eventBus: EventBus) {
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
