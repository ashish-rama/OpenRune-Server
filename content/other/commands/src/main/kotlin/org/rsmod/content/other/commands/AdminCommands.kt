package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.definition.util.CacheVarLiteral
import dev.openrune.literal.CacheVarTypeMap
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcMode
import dev.openrune.types.NpcServerType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import kotlin.math.max
import kotlin.math.min
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub as IfOpenSubProt
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.prepareAdminDieTest
import org.rsmod.api.death.preparePvpDeath
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.mechanics.toxins.impl.PlayerDisease
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.invtx.invClear
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.ironman.setGamemode
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.cheat.adminMaxHit
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.queueDeath
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.player.ui.IfOpenSub as IfOpenSubEvent
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.api.utils.system.SafeServiceExit
import org.rsmod.content.interfaces.bank.openBank
import org.rsmod.events.EventBus
import org.rsmod.game.GameUpdate
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.objtx.TransactionResult
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants
import dev.openrune.types.aconverted.interf.IfSubType
import org.simmetrics.metrics.StringMetrics

class AdminCommands
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val playerList: PlayerList,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val update: GameUpdate,
    private val eventBus: EventBus,
    private val collision: CollisionFlagMap,
    private val areaChecker: AreaChecker,
    private val regions: RegionRegistry,
) : PluginScript() {
    private val logger = InlineLogger()

    private val levenshteinMetric = StringMetrics.levenshtein()

    private var Player.insideWilderness by boolVarBit("varbit.inside_wilderness")

    override fun ScriptContext.startup() {
        onCommand("master", "Max out all stats", ::master)
        onCommand("reset", "Reset all stats", ::reset)
        onCommand("mypos", "Get current coordinates", ::mypos)
        onCommand("tele", "Teleport to coordgrid", ::tele) {
            invalidArgs = "Usage: ::tele mx mz [level](e.g. ::tele 3200 3200 0)"
        }
        onCommand("teleforce", "Teleport to coordgrid and allocate missing collision zone", ::teleForce) {
            invalidArgs = "Usage: ::teleforce mx mz [level](e.g. ::teleforce 3200 3200 0)"
        }
        onCommand("telezone", "Teleport to zone key", ::teleZone) {
            invalidArgs = "Use as ::telezone zoneX zoneY level (ex: 400 400 0)"
        }
        onCommand("up", "Teleport up levels", ::up) {
            invalidArgs = "Use as ::up [amount] (ex: ::up 2)"
        }
        onCommand("down", "Teleport down levels", ::down) {
            invalidArgs = "Use as ::down [amount] (ex: ::down 2)"
        }
        onCommand("anim", "Play animation", ::anim)
        onCommand("spot", "Play spotanim", ::spotanim) {
            invalidArgs = "Use as ::spot spotanimDebugNameOrId (ex: fx_emote_party01_active)"
        }
        onCommand("sound", "Play synth sound", ::sound) {
            invalidArgs = "Use as ::sound synthDebugNameOrId [delay] [loops] (ex: ::sound teleport_all)"
        }
        onCommand("synth", "Play synth sound", ::sound) {
            invalidArgs = "Use as ::synth synthDebugNameOrId [delay] [loops] (ex: ::synth 200)"
        }
        onCommand("object", "Spawn loc", ::locAdd) {
            invalidArgs = "Use as ::object duration locDebugNameOrId (ex: 100 bookcase)"
        }

        onCommand("locadd", "Spawn loc", ::locAdd) {
            invalidArgs = "Use as ::locadd duration locDebugNameOrId (ex: 100 bookcase)"
        }
        onCommand("locdel", "Remove loc", ::locDel) { invalidArgs = "Use as ::locdel duration" }
        onCommand("objectdel", "Remove loc", ::locDel) { invalidArgs = "Use as ::objectdel duration" }

        onCommand("npc", "Spawn npc", ::npcAdd) {
            invalidArgs = "Use as ::npc duration npcDebugNameOrId (ex: 100 prison_pete)"
        }

        onCommand("npcadd", "Spawn npc", ::npcAdd) {
            invalidArgs = "Use as ::npcadd duration npcDebugNameOrId (ex: 100 prison_pete)"
        }

        onCommand("invadd", "Spawn obj into inv", ::invAdd)
        onCommand("item", "Spawn obj into inv", ::invAdd)
        onCommand("itemfind", "Search item cache defs", ::itemFind) {
            invalidArgs = "Use as ::itemfind itemNameOrId (ex: ::itemfind poh_tree)"
        }
        onCommand("locfind", "Search loc cache defs", ::locFind) {
            invalidArgs = "Use as ::locfind locNameOrId (ex: ::locfind portal)"
        }

        onCommand("invclear", "Remove all objs from inv", ::invClear)
        onCommand("bank", "Open your bank", ::bank)
        onCommand("ifopen", "Open interface by id or debug name", ::ifOpen) {
            invalidArgs = "Use as ::ifopen interfaceId|debugName (ex: ::ifopen 71 or ::ifopen poh_options)"
        }
        onCommand("ifdump", "Dump decoded interface components", ::ifDump) {
            invalidArgs = "Use as ::ifdump interfaceId|debugName [query] (ex: ::ifdump 458 build)"
        }
        onCommand("enumdump", "Dump cache enum entries", ::enumDump) {
            invalidArgs = "Use as ::enumdump enumId|debugName [query] (ex: ::enumdump 2157 poh)"
        }
        onCommand("enumfind", "Search all decoded cache enum entries", ::enumFind) {
            invalidArgs = "Use as ::enumfind query [maxRows] (ex: ::enumfind poh_tree 200)"
        }
        onCommand("ifclose", "Close open modal interfaces", ::ifClose)
//        onCommand("openbank", "Open your bank", ::bank)
        onCommand("varp", "Set varp value", ::setVarp) {
            invalidArgs = "Use as ::varp debugNameOrId value (ex: option_run 1)"
        }
        onCommand("varbit", "Set varbit value", ::setVarBit) {
            invalidArgs = "Use as ::varbit debugNameOrId value (ex: emote_hotline_bling 1)"
        }
        onCommand("reboot", "Reboots the game world, applying packed changes", ::reboot)
        onCommand("slowreboot", "Reboots the game world, with a timer", ::slowReboot)
        onCommand("poison", "Test player poison (wiki initial damage, optional raw severity)", ::poisonTest) {
            invalidArgs = "Use as ::poison initialDamage [severity] (e.g. ::poison 8 or ::poison 0 36)"
        }
        onCommand("venom", "Test player venom (escalating damage timer)", ::venomTest)
        onCommand("venomclear", "Clears Venom", ::venomClear)
        onCommand("disease", "Test disease (drain per tick, default 3)", ::diseaseTest) {
            invalidArgs = "Use as ::disease [drainPerTick] (e.g. ::disease 5)"
        }
        onCommand("diseaseclear", "Clears disease timer (stats recover via normal regen)", ::diseaseClear)
        onCommand("die", "Simulate death: ::die pvm|pvp [true=in wildy]", ::dieTest) {
            invalidArgs = "Usage: ::die pvm|pvp [true|false]  (second arg = in Wilderness, default false)"
        }
        onCommand("god", "Toggle god mode (invincibility)", ::god)
        onCommand("maxhit", "Toggle always max hit", ::maxhit)
        onCommand("openbank", "Open the bank", ::bank)
        onCommand("transmog", "Transmog player to NPC appearance (no args to reset)", ::transmog) {
            invalidArgs = "Use as ::transmog npcNameOrId (ex: goblin or 126) or ::transmog to reset"
        }
        onCommand("gamemode", "Set account gamemode (normal|ironman|uim|hcim)", ::gamemode) {
            invalidArgs = "Use as ::gamemode normal|ironman|uim|hcim"
        }
        onCommand("home", "tele to lumbridge", ::home) {
            invalidArgs = "Use as ::home"
        }
    }

    private fun ifOpen(cheat: Cheat) =
        with(cheat) {
            val raw = args.getOrNull(0)
            if (raw == null) {
                player.mes("Use as ::ifopen interfaceId|debugName (ex: ::ifopen 71 or ::ifopen poh_options)")
                return
            }
            val interfaceId = raw.toIntOrNull() ?: resolveInterfaceId(raw)
            if (interfaceId == null || ServerCacheManager.getInterface(interfaceId) == null) {
                player.mes("Could not find interface: $raw")
                return
            }
            player.openMainModalInterface(interfaceId)
            player.mes("Opened interface $interfaceId.")
        }

    private fun ifClose(cheat: Cheat) =
        with(cheat) {
            player.ifClose(eventBus)
            player.mes("Closed modal interfaces.")
        }

    private fun ifDump(cheat: Cheat) =
        with(cheat) {
            val raw = args.getOrNull(0)
            if (raw == null) {
                player.mes("Use as ::ifdump interfaceId|debugName [query] (ex: ::ifdump 458 build)")
                return
            }
            val query = args.drop(1).joinToString(" ").lowercase()
            val interfaceId = raw.toIntOrNull() ?: resolveInterfaceId(raw)
            val interf = interfaceId?.let(ServerCacheManager::getInterface)
            if (interfaceId == null || interf == null) {
                player.mes("Could not find interface: $raw")
                return
            }
            val matches =
                interf.components.entries
                    .asSequence()
                    .sortedBy { it.key }
                    .filter { (_, component) ->
                        if (query.isBlank()) {
                            true
                        } else {
                            listOf(
                                    component.internalName,
                                    component.text,
                                    component.buttonText,
                                    component.opBase,
                                    component.op.joinToString(" "),
                                )
                                .joinToString(" ")
                                .lowercase()
                                .contains(query)
                        }
                    }
                    .toList()
            val header =
                "Interface $interfaceId has ${interf.components.size} components" +
                    if (query.isBlank()) "." else "; ${matches.size} match '$query'."
            player.mes(header)
            logger.info { "[ifdump] ${player.username}: $header" }
            if (matches.isEmpty()) {
                player.mes("No components match '$query'.")
                logger.info { "[ifdump] ${player.username}: No components match '$query'." }
                return@with
            }
            player.mes("Dumped ${matches.size} component rows to the server log.")
            for ((id, component) in matches) {
                val ops = component.op.filterNotNull().filter { it.isNotBlank() }.joinToString("/")
                val scripts =
                    listOfNotNull(
                            "op".takeIf { !component.onOp.isNullOrEmpty() },
                            "click".takeIf { !component.onClick.isNullOrEmpty() },
                            "release".takeIf { !component.onRelease.isNullOrEmpty() },
                            "hold".takeIf { !component.onHold.isNullOrEmpty() },
                            "load".takeIf { !component.onLoad.isNullOrEmpty() },
                        )
                        .joinToString("/")
                val line =
                    "if:$interfaceId comp:$id type=${component.type} button=${component.buttonType} " +
                        "size=${component.width}x${component.height} events=${component.events} " +
                        "text='${component.text}' btn='${component.buttonText}' opbase='${component.opBase}' " +
                        "ops='$ops' scripts='$scripts'"
                logger.info { "[ifdump] $line" }
            }
        }

    private fun enumDump(cheat: Cheat) =
        with(cheat) {
            val raw = args.getOrNull(0)
            if (raw == null) {
                player.mes("Use as ::enumdump enumId|debugName [query] (ex: ::enumdump 2157 poh)")
                return
            }
            val query = args.drop(1).joinToString(" ").lowercase()
            val enumId = raw.toIntOrNull() ?: "enum.$raw".asRSCM(RSCMType.ENUM)
            val enum = ServerCacheManager.getEnum(enumId)
            if (enum == null) {
                player.mes("Could not find enum: $raw")
                return
            }
            val matches =
                enum.values.entries
                    .asSequence()
                    .sortedBy { it.key }
                    .filter { (key, value) ->
                        val decodedKey = decodeEnumSlot(enum.keyType, key)
                        val decodedValue = decodeEnumSlot(enum.valueType, value)
                        if (query.isBlank()) {
                            true
                        } else {
                            listOf(
                                    key.toString(),
                                    value.toString(),
                                    describeEnumSlot(decodedKey),
                                    describeEnumSlot(decodedValue),
                                )
                                .joinToString(" ")
                                .lowercase()
                                .contains(query)
                        }
                    }
                    .toList()
            val header =
                "Enum $enumId size=${enum.values.size} key=${enum.keyType.name} value=${enum.valueType.name} " +
                    "defaultInt=${enum.defaultInt} defaultString='${enum.defaultString}'" +
                    if (query.isBlank()) "" else "; ${matches.size} match '$query'"
            player.mes(header)
            logger.info { "[enumdump] ${player.username}: $header" }
            if (matches.isEmpty()) {
                player.mes("No enum entries match '$query'.")
                logger.info { "[enumdump] ${player.username}: No enum entries match '$query'." }
                return@with
            }
            player.mes("Dumped ${matches.size} enum rows to the server log.")
            for ((key, value) in matches) {
                val decodedKey = decodeEnumSlot(enum.keyType, key)
                val decodedValue = decodeEnumSlot(enum.valueType, value)
                logger.info {
                    "enum:$enumId key=${describeEnumSlot(decodedKey)} rawKey=$key " +
                        "value=${describeEnumSlot(decodedValue)} rawValue=$value"
                }
            }
        }

    private fun enumFind(cheat: Cheat) =
        with(cheat) {
            val query = args.getOrNull(0)?.lowercase()
            if (query.isNullOrBlank()) {
                player.mes("Use as ::enumfind query [maxRows] (ex: ::enumfind poh_tree 200)")
                return
            }
            val maxRows = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 1000) ?: 200
            var logged = 0
            var matchingEnums = 0
            logger.info {
                "[enumfind] ${player.username}: searching ${ServerCacheManager.enumSize()} enums " +
                    "for '$query' maxRows=$maxRows"
            }
            for ((enumId, enum) in ServerCacheManager.getEnums().entries.sortedBy { it.key }) {
                var enumMatched = false
                for ((key, value) in enum.values.entries.sortedBy { it.key }) {
                    val decodedKey = decodeEnumSlot(enum.keyType, key)
                    val decodedValue = decodeEnumSlot(enum.valueType, value)
                    val haystack =
                        listOf(
                                enumId.toString(),
                                key.toString(),
                                value.toString(),
                                describeEnumSlot(decodedKey),
                                describeEnumSlot(decodedValue),
                            )
                            .joinToString(" ")
                            .lowercase()
                    if (query !in haystack) {
                        continue
                    }
                    if (!enumMatched) {
                        matchingEnums++
                        enumMatched = true
                        logger.info {
                            "[enumfind] enum:$enumId size=${enum.values.size} " +
                                "key=${enum.keyType.name} value=${enum.valueType.name} " +
                                "defaultInt=${enum.defaultInt} defaultString='${enum.defaultString}'"
                        }
                    }
                    logger.info {
                        "[enumfind] enum:$enumId key=${describeEnumSlot(decodedKey)} rawKey=$key " +
                            "value=${describeEnumSlot(decodedValue)} rawValue=$value"
                    }
                    logged++
                    if (logged >= maxRows) {
                        player.mes(
                            "Enum search hit maxRows=$maxRows across $matchingEnums enums; see server log."
                        )
                        logger.info {
                            "[enumfind] ${player.username}: stopped at maxRows=$maxRows " +
                                "matchingEnums=$matchingEnums"
                        }
                        return@with
                    }
                }
            }
            player.mes("Enum search logged $logged rows across $matchingEnums enums.")
            logger.info {
                "[enumfind] ${player.username}: complete loggedRows=$logged matchingEnums=$matchingEnums"
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun decodeEnumSlot(type: CacheVarLiteral, raw: Any?): Any? =
        when (raw) {
            null -> null
            is Int ->
                runCatching { CacheVarTypeMap.findCodec<Int, Any>(type).decode(raw) }.getOrNull()
                    ?: raw
            is String ->
                runCatching { CacheVarTypeMap.findCodec<String, Any>(type).decode(raw) }.getOrNull()
                    ?: raw
            else -> raw
        }

    private fun describeEnumSlot(value: Any?): String =
        when (value) {
            null -> "null"
            is ItemServerType -> "obj:${value.id}:${value.internalName}:'${value.name}'"
            is ObjectServerType -> "loc:${value.id}:${value.internalName}:'${value.name}'"
            is NpcServerType -> "npc:${value.id}:${value.internalName}:'${value.name}'"
            else -> value.toString()
        }

    private fun resolveInterfaceId(raw: String): Int? {
        val internal = if (raw.startsWith("interface.")) raw else "interface.$raw"
        val id = internal.asRSCM(RSCMType.INTERFACE)
        return id.takeIf { it != -1 }
    }

    private fun Player.openMainModalInterface(interfaceId: Int) {
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

    private fun home(cheat: Cheat) =
        with(cheat) {
            val coords = CoordGrid(3222, 3222, 0)
            protectedAccess.launch(player) {
                PathingEntityCommon.anim(
                    player,
                    RSCM.getReverseMapping(RSCMType.SEQ, 714),
                    delay = 0,
                    priority = 0,
                )
                player.spotanim(RSCM.getReverseMapping(RSCMType.SPOTANIM, 111), height = 92)
                delay(3)
                telejump(coords, TeleportType.Exempt)
                PathingEntityCommon.anim(
                    player,
                    RSCM.getReverseMapping(RSCMType.SEQ, 715),
                    delay = 0,
                    priority = 0,
                )
                player.mes("Teleported to $coords.")
            }
        }

    private fun gamemode(cheat: Cheat) =
        with(cheat) {
            val mode =
                when (args.getOrNull(0)?.lowercase()) {
                    "normal",
                    "main",
                    "0" -> PlayerGamemode.NORMAL
                    "ironman",
                    "iron",
                    "1" -> PlayerGamemode.IRONMAN
                    "uim",
                    "ultimate",
                    "2" -> PlayerGamemode.ULTIMATE_IRONMAN
                    "hcim",
                    "hardcore",
                    "3" -> PlayerGamemode.HARDCORE_IRONMAN
                    else -> {
                        player.mes("Use as ::gamemode normal|ironman|uim|hcim")
                        return
                    }
                }
            player.setGamemode(mode)
            player.mes("Gamemode set to $mode (varbit.ironman synced; persists on logout).")
        }

    private fun god(cheat: Cheat) =
        with(cheat) {
            player.adminGodMode = !player.adminGodMode
            player.mes("God mode ${if (player.adminGodMode) "enabled" else "disabled"}.")
        }

    private fun maxhit(cheat: Cheat) =
        with(cheat) {
            player.adminMaxHit = !player.adminMaxHit
            player.mes("Max hit ${if (player.adminMaxHit) "enabled" else "disabled"}.")
        }

    private fun poisonTest(cheat: Cheat) =
        with(cheat) {
            val initialDamage = args.getOrNull(0)?.toIntOrNull() ?: 0
            val severity = args.getOrNull(1)?.toIntOrNull() ?: 0
            val ok = PlayerPoison.tryPoison(player, initialDamage = initialDamage, severity = severity)
            player.mes(
                if (ok) {
                    "Poison applied (initialDamage=$initialDamage severityParam=$severity)."
                } else {
                    "Poison not applied (weaker/equal than current, or both inputs zero)."
                },
            )
        }

    private fun venomTest(cheat: Cheat) =
        with(cheat) {
            PlayerVenom.tryVenom(player)
        }

    private fun venomClear(cheat: Cheat) =
        with(cheat) {
            PlayerVenom.clear(player)
        }

    private fun diseaseTest(cheat: Cheat) =
        with(cheat) {
            val drain = args.getOrNull(0)?.toIntOrNull() ?: 3
            val ok = PlayerDisease.tryDisease(player, drain)
            player.mes(
                if (ok) {
                    "Disease applied (drain per tick=$drain)."
                } else {
                    "Disease not applied (no eligible skill)."
                },
            )
        }

    private fun diseaseClear(cheat: Cheat) =
        with(cheat) {
            PlayerDisease.clear(player)
            player.mes("Disease cleared.")
        }

    private fun master(cheat: Cheat) = with(cheat) { player.setStatLevels(level = 99) }

    private fun reset(cheat: Cheat) = with(cheat) { player.setStatLevels(level = 1) }

    private fun mypos(cheat: Cheat) =
        with(cheat) {
            player.mes("${player.coords}:")
            player.mes("  ${ZoneKey.from(player.coords)} - ${ZoneGrid.from(player.coords)}")
            player.mes(
                "  ${MapSquareKey.from(player.coords)} - ${MapSquareGrid.from(player.coords)}"
            )
            player.mes("  BuildArea(${player.buildArea})")
        }

    private fun tele(cheat: Cheat) =
        with(cheat) {
            val args = if (args.size == 1) args[0].split(",") else args
            val x = args[0].toInt()
            val y = args[1].toInt()
            val level = args.getOrNull(2)?.toInt() ?: 0
            val coords = CoordGrid(x,y,level)
            protectedAccess.launch(player) {
                player.mes("Teleported to $coords.")
                telejump(coords, TeleportType.Exempt)
            }
        }

    private fun teleForce(cheat: Cheat) =
        with(cheat) {
            val args = if (args.size == 1) args[0].split(",") else args
            val x = args[0].toInt()
            val y = args[1].toInt()
            val level = args.getOrNull(2)?.toInt() ?: 0
            val coords = CoordGrid(x, y, level)
            collision.allocateIfAbsent(coords.x, coords.z, coords.level)
            protectedAccess.launch(player) {
                player.mes("Force teleported to $coords.")
                telejump(coords, collision, TeleportType.Exempt)
            }
        }

    private fun teleZone(cheat: Cheat) =
        with(cheat) {
            val args = if (args.size == 1) args[0].split(",") else args
            val zoneX = args[0].toInt()
            val zoneZ = args[1].toInt()
            val level = args[2].toInt()
            val coords = ZoneKey(zoneX, zoneZ, level).toCoords()
            protectedAccess.launch(player) {
                player.mes("Teleported to $coords.")
                telejump(coords, TeleportType.Exempt)
            }
        }

    private fun up(cheat: Cheat) {
        teleLevel(cheat, levelDelta = 1)
    }

    private fun down(cheat: Cheat) {
        teleLevel(cheat, levelDelta = -1)
    }

    private fun teleLevel(cheat: Cheat, levelDelta: Int) {
        with(cheat) {
            val amount =
                when {
                    args.isEmpty() -> 1
                    else -> {
                        val parsed = args[0].toIntOrNull()
                        if (parsed == null) {
                            player.mes("Invalid amount: '${args[0]}'")
                            return@with
                        }
                        parsed
                    }
                }
            if (amount <= 0) {
                player.mes("Amount must be positive.")
                return@with
            }
            val current = player.coords
            val destLevel =
                (current.level + levelDelta * amount).coerceIn(0, CoordGrid.LEVEL_BIT_MASK)
            if (destLevel == current.level) {
                val direction = if (levelDelta > 0) "up" else "down"
                player.mes("Cannot go $direction from level ${current.level}.")
                return@with
            }
            val dest = current.copy(level = destLevel)
            protectedAccess.launch(player) {
                player.mes("Teleported to $dest.")
                telejump(dest, TeleportType.Exempt)
            }
        }
    }

    private fun anim(cheat: Cheat) =
        with(cheat) {
            val typeId = RSCM.getRSCM("seq.${args.asTypeName()}")
            if (typeId == -1) {
                player.mes("There is no seq mapped to: '${args.asTypeName()}'")
                return
            }
            val type = ServerCacheManager.getAnim(typeId)
            if (type == null) {
                player.mes("That seq does not exist: $typeId")
                return
            }
            player.anim("seq.${args.asTypeName()}")
            player.mes("Anim: '${args.asTypeName()}' (priority=${type.priority})")
            logger.debug { "Anim: $type" }
        }

    private fun spotanim(cheat: Cheat) =
        with(cheat) {
            val (typeName, heightArg) = args.asTypeNameAndNumber(defaultNumber = 0)
            val typeId = typeName.toIntOrNull() ?: "spotanim.$typeName".asRSCM(RSCMType.SPOTANIM)
            if (typeId == -1) {
                player.mes("There is no spotanim mapped to: '${typeName}'")
                return
            }

            val height = min(heightArg.toInt(), Short.MAX_VALUE.toInt())
            val spotanim = RSCM.getReverseMapping(RSCMType.SPOTANIM, typeId)
            player.spotanim(spotanim, delay = 0, height = height, slot = 0)
            player.mes("Spotanim: '${typeName}' (height=$height)")
            logger.debug { "Spotanim: $typeName" }
        }

    private fun sound(cheat: Cheat) =
        with(cheat) {
            val typeName = args.getOrNull(0)
            if (typeName == null) {
                player.mes("Use as ::sound synthDebugNameOrId [delay] [loops] (ex: ::sound teleport_all)")
                return
            }
            val typeId = typeName.toIntOrNull() ?: RSCM.getRSCM("synth.$typeName")
            if (typeId == -1) {
                player.mes("There is no synth mapped to: '$typeName'")
                return
            }

            val delay = args.getOrNull(1)?.toIntOrNull() ?: 0
            val loops = args.getOrNull(2)?.toIntOrNull() ?: 1
            player.soundSynth(typeId, loops = loops, delay = delay)
            player.mes("Sound: '$typeName' (id=$typeId, delay=$delay, loops=$loops)")
            logger.debug { "Sound: $typeName (id=$typeId, delay=$delay, loops=$loops)" }
        }

    private fun locAdd(cheat: Cheat) =
        with(cheat) {
            val typeId = "loc.${args[1]}".asRSCM()

            val type = ServerCacheManager.getObject(typeId)!!
            if (type == null) {
                player.mes("That loc does not exist: $typeId")
                return
            }
            val duration = args[0].toInt()
            val angle = args.getOrNull(2)?.toInt() ?: LocAngle.West.id
            val shape = args.getOrNull(3)?.toInt() ?: LocShape.CentrepieceStraight.id
            val layer = LocLayerConstants.of(shape)
            val loc = LocInfo(layer, player.coords, LocEntity(type.id, shape, angle))
            locRepo.add(loc, duration)
            player.mes("Spawned loc '${type.internalName}' (duration: $duration cycles)")
            logger.debug { "Spawned loc: loc=$loc, type=$type" }
        }

    private fun locDel(cheat: Cheat) =
        with(cheat) {
            val zone = ZoneKey.from(player.coords)
            val locs = locRepo.findAll(zone).filter { it.coords == player.coords }.toList()
            if (locs.isEmpty()) {
                player.mes("No loc found on ${player.coords}")
                return
            }
            val duration = args[0].toInt()
            val shape = args.getOrNull(1)?.toIntOrNull() ?: LocShape.CentrepieceStraight.id
            val loc = locs.firstOrNull { it.shapeId == shape }
            if (loc == null) {
                player.mes("No loc with shape `${LocShape[shape]}` found on ${player.coords}")
                return
            }
            val type = ServerCacheManager.getObject(loc.id)!!
            locRepo.del(loc, duration)
            player.mes("Deleted loc `${type.internalName}` (duration: $duration cycles)")
            logger.debug { "Deleted loc: loc=$loc, type=$type" }
        }

    private fun npcAdd(cheat: Cheat) =
        with(cheat) {
            val typeName = args[1]
            val typeId = typeName.toIntOrNull() ?: "npc.$typeName".asRSCM(RSCMType.NPC)

            val type = ServerCacheManager.getNpc(typeId)
            if (type == null) {
                player.mes("That npc does not exist: $typeName")
                return
            }
            val duration = args[0].toInt()
            val npc = Npc(type, player.coords)
            npc.mode = NpcMode.None
            npcRepo.add(npc, duration)
            player.mes("Spawned npc `${args[1]}` (duration: $duration cycles)")
        }

    private fun invAdd(cheat: Cheat) =
        with(cheat) {
            val (typeName, countArg) = args.asTypeNameAndNumber(defaultNumber = 1)
            val normalizedName = "obj.$typeName"
            val typeId = typeName.toIntOrNull() ?: normalizedName.asRSCM(RSCMType.OBJ)
            val type = ServerCacheManager.getItem(typeId)
            if (type == null) {
                player.mes("That item does not exist: $typeName")
                return@with
            }

            val count = countArg.toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val objName = type.name.ifEmpty { normalizedName }

            val spawned = player.invAdd(player.inv, typeId, count, strict = false)
            if (spawned.err is TransactionResult.RestrictedDummyitem) {
                player.mes("You can't spawn this item!")
                return
            }
            player.mes("Spawned inv obj `$objName` x ${spawned.completed().formatAmount}")
        }

    private fun itemFind(cheat: Cheat) =
        with(cheat) {
            val query = args.joinToString("_")
            val id = query.toIntOrNull()
            if (id != null) {
                val item = ServerCacheManager.getItem(id)
                if (item == null) {
                    player.mes("No item found for id=$id")
                } else {
                    player.mes("Item $id: ${item.internalName} '${item.name}'")
                }
                return@with
            }

            val normalized = query.lowercase()
            val matches =
                ServerCacheManager.getItems().entries
                    .asSequence()
                    .filter { (_, item) ->
                        item.name.lowercase().contains(normalized) ||
                            item.internalName.lowercase().contains(normalized)
                    }
                    .take(12)
                    .toList()
            if (matches.isEmpty()) {
                player.mes("No item matches for '$query'")
                return@with
            }
            player.mes(
                matches.joinToString(prefix = "Items: ") { (id, item) ->
                    "$id=${item.internalName} '${item.name}'"
                }
            )
        }

    private fun locFind(cheat: Cheat) =
        with(cheat) {
            val query = args.joinToString("_")
            val id = query.toIntOrNull()
            if (id != null) {
                val loc = ServerCacheManager.getObject(id)
                if (loc == null) {
                    player.mes("No loc found for id=$id")
                } else {
                    player.mes("Loc $id: ${loc.internalName} '${loc.name}'")
                }
                return@with
            }

            val normalized = query.lowercase()
            val matches =
                ServerCacheManager.getObjects().entries
                    .asSequence()
                    .filter { (_, loc) ->
                        loc.name.lowercase().contains(normalized) ||
                            loc.internalName.lowercase().contains(normalized)
                    }
                    .take(12)
                    .toList()
            if (matches.isEmpty()) {
                player.mes("No loc matches for '$query'")
                return@with
            }
            player.mes(
                matches.joinToString(prefix = "Locs: ") { (id, loc) ->
                    "$id=${loc.internalName} '${loc.name}'"
                }
            )
        }

    private fun invClear(cheat: Cheat) = with(cheat) { player.invClear(player.inv) }

    private fun bank(cheat: Cheat) = cheat.player.openBank(eventBus)

    private fun setVarp(cheat: Cheat) =
        with(cheat) {
            val typeId = "varp.${args[0]}".asRSCM()

            val type = ServerCacheManager.getVarp(typeId)
            if (type == null) {
                player.mes("That varp does not exist: $typeId")
                return
            }
            val value = args[1].toInt()
            player.vars.backing[type.id] = value
            player.resyncVar(type)
            player.mes("Set varp '${args[0]}' to value: ${player.vars[type]}")
        }

    private fun setVarBit(cheat: Cheat) =
        with(cheat) {
            val typeId = "varbit.${args[0]}".asRSCM()

            val type = ServerCacheManager.getVarbit(typeId)
            if (type == null) {
                player.mes("That varbit does not exist: $typeId")
                return
            }
            val value = args[1].toInt()
            VarPlayerIntMapSetter.set(player, type, value)
            player.mes("Set varbit '${args[0]}' to value: ${player.vars[type]}")
        }

    @OptIn(InternalApi::class)
    private fun Player.setStatLevels(level: Int) {
        val xp = PlayerSkillXPTable.getXPFromLevel(level)
        for (stat in ServerCacheManager.getStats().values) {
            val statInternal = RSCM.getReverseMapping(RSCMType.STAT, stat.id)

            val baseLevel = statMap.getBaseLevel(statInternal)
            val targetLevel = max(stat.minLevel, level)
            if (baseLevel > targetLevel) {
                statRevert(statInternal, targetLevel, xp)
                continue
            }
            val xpDelta = xp - statMap.getXP(statInternal)
            statMap.setCurrentLevel(statInternal, targetLevel.toByte())
            statAdvance(statInternal, xpDelta.toDouble(), rate = 1.0)
        }
    }

    // There is, by design, no helper function to decrease stat xp, as xp reduction is not a
    // standard operation in normal gameplay.
    @OptIn(InternalApi::class)
    private fun Player.statRevert(stat: String, targetLevel: Int, targetXp: Int) {
        statMap.setCurrentLevel(stat, statMap.getBaseLevel(stat))
        val levelDelta = stat(stat) - targetLevel
        require(levelDelta > 0) { "This function can only be used to reduce stat levels." }
        statMap.setXP(stat, targetXp)
        statMap.setBaseLevel(stat, targetLevel.toByte())
        statSub(stat, constant = levelDelta, percent = 0)
        appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(this)
        PlayerInterfaceUpdates.updateCombatLevel(this)
    }

    private fun reboot(cheat: Cheat) {
        logger.info { "Reboot initiated by '${cheat.player.displayName}'." }
        SafeServiceExit.terminate()
    }

    private fun slowReboot(cheat: Cheat) =
        with(cheat) {
            val cycles = min(args[0].toInt(), 65535)
            if (cycles <= 0) {
                update.clear()
                return@with
            }
            update.startCountdown(cycles)
            for (p in playerList) {
                MiscOutput.updateRebootTimer(p, cycles)
            }
        }

    private fun dieTest(cheat: Cheat) = with(cheat) {
        val mode = args.getOrNull(0)?.lowercase()
        val inWildy = args.getOrNull(1)?.lowercase() == "true"
        when (mode) {
            "pvm" -> {
                if (inWildy) player.insideWilderness = true
                player.mes("Simulating PvM death${if (inWildy) " in Wilderness" else ""}.")
                player.prepareAdminDieTest()
                player.queueDeath()
            }
            "pvp" -> {
                player.preparePvpDeath(player)
                if (inWildy) player.insideWilderness = true
                player.mes("Simulating PvP death${if (inWildy) " in Wilderness" else ""}. (self as killer)")
                player.prepareAdminDieTest()
                player.queueDeath()
            }
            else -> player.mes("Usage: ::die pvm|pvp [true|false]  (true = in Wilderness)")
        }
    }

    private fun transmog(cheat: Cheat) = with(cheat) {
        if (args.isEmpty()) {
            protectedAccess.launch(player) { resetTransmog() }
            player.mes("Transmog cleared.")
            return
        }
        val first = args[0]
        val npcName =
            if (first.toIntOrNull() != null) {
                val resolved = RSCM.getReverseMapping(RSCMType.NPC, first.toInt())
                if (resolved.isEmpty()) {
                    player.mes("No NPC mapped to ID: $first")
                    return
                }
                resolved
            } else {
                "npc.${args.asTypeName()}"
            }
        protectedAccess.launch(player) { transmog(npcName) }
        player.mes("Transmog: '$npcName'")
    }

    private fun resolveArgTypeId(arg: String, names: Map<String, Int>): Int? {
        val argAsInt = arg.toIntOrNull()
        if (argAsInt != null) {
            return argAsInt
        }
        val sanitized = arg.replace("-", "_")
        return names[sanitized]
    }

    private fun resolveTypeName(name: String, names: Map<String, Int>): String =
        when {
            name in names -> name
            name.toIntOrNull() != null -> name
            else -> findClosestNameMatch(name, names.keys) ?: name
        }

    private fun List<String>.asTypeNameAndNumber(defaultNumber: Number): Pair<String, String> =
        if (size > 1 && last().toLongOrNull() != null) {
            dropLast(1).joinToString("_") to last()
        } else {
            joinToString("_") to defaultNumber.toString()
        }

    private fun List<String>.asTypeName(): String = joinToString("_")

    private fun findClosestNameMatch(input: String, names: Iterable<String>): String? {
        val normalizedInput = input.replace("_", " ")

        var bestMatchScore = 0.0f
        var bestMatchName: String? = null
        for (name in names) {
            val score = levenshteinMetric.compare(normalizedInput, name.replace("_", " "))
            if (score > bestMatchScore) {
                bestMatchScore = score
                bestMatchName = name
            }
        }

        return if (bestMatchScore >= 0.5) bestMatchName else null
    }
}
