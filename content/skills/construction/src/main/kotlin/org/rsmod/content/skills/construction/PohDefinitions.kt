package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.table.construction.ConstructionPohBuildablesRow
import org.rsmod.api.table.FurnitureRow
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.zone.ZoneKey

class PohDefinitions @Inject constructor() {
    val gardenRoom =
        PohRoomDefinition(
            key = PohRoomKey("garden"),
            name = "Garden",
            sourceZone = PohConstants.GardenSourceZone,
            defaultBuiltFurniture =
                mapOf(PohHotspotKey("centrepiece") to PohBuildableKey("exit_portal")),
        )

    val centrepieceHotspot =
        hotspot(
            key = "centrepiece",
            name = "Centrepiece",
            locId = 15361,
            buildables =
                listOf("exit_portal", "decorative_rock", "pond", "imp_statue", "dungeon_entrance"),
        )

    val bigTreeHotspot =
        hotspot(
            key = "big_tree",
            name = "Big tree",
            locId = 15362,
            buildables =
                listOf(
                    "dead_tree",
                    "nice_tree",
                    "oak_tree",
                    "willow_tree",
                    "maple_tree",
                    "yew_tree",
                    "magic_tree",
                ),
        )

    val smallTreeHotspot =
        hotspot(
            key = "small_tree",
            name = "Small tree",
            locId = 15363,
            buildables =
                listOf(
                    "dead_tree",
                    "nice_tree",
                    "oak_tree",
                    "willow_tree",
                    "maple_tree",
                    "yew_tree",
                    "magic_tree",
                ),
            buildableLocSlot = 1,
        )

    val bigPlant1Hotspot =
        hotspot("big_plant_1", "Big plant 1", 15364, listOf("fern", "bush", "tall_plant"))

    val bigPlant2Hotspot =
        hotspot(
            "big_plant_2",
            "Big plant 2",
            15365,
            listOf("short_plant", "large_leaf_bush", "huge_plant"),
        )

    val smallPlant1Hotspot =
        hotspot(
            "small_plant_1",
            "Small plant 1",
            15366,
            listOf("plant", "smaller_fern", "small_fern"),
        )

    val smallPlant2Hotspot =
        hotspot("small_plant_2", "Small plant 2", 15367, listOf("dock_leaf", "thistle", "reeds"))

    val tipJarHotspot = hotspot("tip_jar", "Tip jar", 29119, listOf("tip_jar"))

    val exitPortal = buildable("exit_portal", "dbrow.poh_exit_portal")
    val decorativeRock = buildable("decorative_rock", "dbrow.poh_decorative_rock")
    val pond = buildable("pond", "dbrow.poh_pond")
    val impStatue = buildable("imp_statue", "dbrow.poh_imp_statue")
    val dungeonEntrance = buildable("dungeon_entrance", "dbrow.poh_dungeon_entrance")

    val deadTree = buildable("dead_tree", "dbrow.poh_tree_dead")
    val niceTree = buildable("nice_tree", "dbrow.poh_tree_nice")
    val oakTree = buildable("oak_tree", "dbrow.poh_tree_oak")
    val willowTree = buildable("willow_tree", "dbrow.poh_tree_willow")
    val mapleTree = buildable("maple_tree", "dbrow.poh_tree_maple")
    val yewTree = buildable("yew_tree", "dbrow.poh_tree_yew")
    val magicTree = buildable("magic_tree", "dbrow.poh_tree_magic")

    val fern = buildable("fern", "dbrow.poh_big_plant_fern")
    val bush = buildable("bush", "dbrow.poh_big_plant_bush")
    val tallPlant = buildable("tall_plant", "dbrow.poh_big_plant_tall")
    val shortPlant = buildable("short_plant", "dbrow.poh_big_plant_short")
    val largeLeafBush = buildable("large_leaf_bush", "dbrow.poh_big_plant_large_leaf")
    val hugePlant = buildable("huge_plant", "dbrow.poh_big_plant_huge")
    val plant = buildable("plant", "dbrow.poh_small_plant")
    val smallerFern = buildable("smaller_fern", "dbrow.poh_small_plant_smaller_fern")
    val smallFern = buildable("small_fern", "dbrow.poh_small_plant_fern")
    val dockLeaf = buildable("dock_leaf", "dbrow.poh_small_plant_dock_leaf")
    val thistle = buildable("thistle", "dbrow.poh_small_plant_thistle")
    val reeds = buildable("reeds", "dbrow.poh_small_plant_reeds")

    val tipJar = buildable("tip_jar", "dbrow.poh_tip_jar")

    val rooms: Map<PohRoomKey, PohRoomDefinition> = mapOf(gardenRoom.key to gardenRoom)

    val hotspots: Map<PohHotspotKey, PohHotspotDefinition> =
        listOf(
                centrepieceHotspot,
                bigTreeHotspot,
                smallTreeHotspot,
                bigPlant1Hotspot,
                bigPlant2Hotspot,
                smallPlant1Hotspot,
                smallPlant2Hotspot,
                tipJarHotspot,
            )
            .associateBy { it.key }

    val buildables: Map<PohBuildableKey, PohBuildableDefinition> =
        listOf(
                exitPortal,
                decorativeRock,
                pond,
                impStatue,
                dungeonEntrance,
                deadTree,
                niceTree,
                oakTree,
                willowTree,
                mapleTree,
                yewTree,
                magicTree,
                fern,
                bush,
                tallPlant,
                shortPlant,
                largeLeafBush,
                hugePlant,
                plant,
                smallerFern,
                smallFern,
                dockLeaf,
                thistle,
                reeds,
                tipJar,
            )
            .associateBy { it.key }

    fun defaultHouse(): PohHouseState =
        PohHouseState(
            buildMode = true,
            rooms = mutableMapOf(PohConstants.CenterGrid to PohRoomState.fromDefinition(gardenRoom)),
        )

    private fun hotspot(
        key: String,
        name: String,
        locId: Int,
        buildables: List<String>,
        buildableLocSlot: Int = 0,
    ): PohHotspotDefinition =
        PohHotspotDefinition(
            key = PohHotspotKey(key),
            name = name,
            locIds = listOf(locId),
            buildables = buildables.map(::PohBuildableKey),
            buildableLocSlot = buildableLocSlot,
            fallbackAngle = LocAngle.West,
            fallbackShape = LocShape.CentrepieceStraight,
        )

    private fun buildable(
        key: String,
        row: String,
    ): PohBuildableDefinition =
        PohBuildableDefinition(
            key = PohBuildableKey(key),
            row = ConstructionPohBuildablesRow.getRow(row),
        )
}

data class PohRoomDefinition(
    val key: PohRoomKey,
    val name: String,
    val sourceZone: ZoneKey,
    val defaultBuiltFurniture: Map<PohHotspotKey, PohBuildableKey> = emptyMap(),
    val hasRoof: Boolean = false,
)

data class PohHotspotDefinition(
    val key: PohHotspotKey,
    val name: String,
    val locIds: List<Int>,
    val buildables: List<PohBuildableKey>,
    val buildableLocSlot: Int,
    val fallbackAngle: LocAngle,
    val fallbackShape: LocShape,
) {
    init {
        require(locIds.isNotEmpty()) { "POH hotspot must define at least one loc id." }
    }

    fun slotFor(locId: Int): Int = locIds.indexOf(locId)
}

data class PohBuildableDefinition(
    val key: PohBuildableKey,
    val row: ConstructionPohBuildablesRow,
) {
    val locIds: List<Int> = row.builtLocs.map { it.id }

    val xp: Double = row.xp.toDouble()

    init {
        require(locIds.isNotEmpty()) { "POH buildable must define at least one loc id." }
    }

    private val cacheFurniture: FurnitureRow by lazy { resolveFurnitureRow() }

    fun locIdForSlot(slot: Int): Int = locIds.getOrElse(slot) { locIds.first() }

    fun menuFurnitureId(): Int = cacheFurniture.rowId

    fun constructionLevel(): Int =
        cacheFurniture.levelRequirement
            .firstOrNull { requirement ->
                requirement.t0.displayName.equals("construction", ignoreCase = true)
            }
            ?.t1
            ?: 1

    fun resolvedMaterials(): List<PohMaterial> =
        cacheFurniture.materialCost.map { material ->
            PohMaterial(
                itemId = material.t0.id,
                name = material.t0.name.meaningfulName() ?: material.t0.internalName,
                count = material.t1,
            )
        }

    fun displayName(): String =
        cacheFurniture.name.meaningfulName() ?: cacheFurniture.modelObj.name

    fun creationMenuString(): String =
        buildString {
            append(displayName())
            append("|")
            for (index in 0 until 4) {
                val material = resolvedMaterials().getOrNull(index)
                if (material != null) {
                    append(material.displayName())
                    append(": ")
                    append(material.count)
                }
                append("<br>")
            }
        }

    private fun resolveFurnitureRow(): FurnitureRow {
        val modelMatches =
            FurnitureRow.all().filter { furniture ->
                furniture.hiddenInBuildMenu == null &&
                    furniture.modelObj.id == row.modelObj.id
            }
        if (modelMatches.size == 1) {
            return modelMatches.single()
        }

        val matches =
            modelMatches.filter { furniture ->
                furniture.name.equals(row.furnitureName, ignoreCase = true)
            }
        return when (matches.size) {
            0 ->
                error(
                    "No visible furniture row found for ${row.rowId}: " +
                        "${row.modelObj.internalName} / ${row.furnitureName}. " +
                        "Model matches: ${modelMatches.joinToString { "${it.rowId}:${it.name}" }}"
                )
            1 -> matches.single()
            else ->
                error(
                    "Ambiguous furniture rows for ${row.rowId}: " +
                        "${row.modelObj.internalName} / ${row.furnitureName} -> " +
                        matches.joinToString { it.rowId.toString() }
                )
        }
    }
}

data class PohMaterial(val itemId: Int, val name: String, val count: Int) {
    fun displayName(): String =
        ServerCacheManager.getItem(itemId)?.name?.meaningfulName() ?: name
}

private fun String.meaningfulName(): String? =
    trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

@JvmInline
value class PohRoomKey(val id: String)

@JvmInline
value class PohHotspotKey(val id: String)

@JvmInline
value class PohBuildableKey(val id: String)
