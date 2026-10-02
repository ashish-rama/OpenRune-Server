package org.rsmod.tools.wiki.dumping

import dev.openrune.ServerCacheManager
import dev.openrune.definition.util.CacheVarLiteral
import dev.openrune.literal.CacheVarTypeMap
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import dev.openrune.types.ObjectServerType
import org.rsmod.api.table.FurnitureRow

fun main(args: Array<String>) {
    val parsed = parseArgs(args)
    if (parsed == null) {
        println("Usage: cacheSearch <query> [maxRows] [revision]")
        println("Example: ./gradlew :tools:wiki-dumping:cacheSearch --args=\"poh_tree 500 239\"")
        return
    }
    val (query, maxRows, revision) = parsed

    println("Initializing cache revision $revision...")
    ServerCacheManager.init(revision)
    println(
        "Cache loaded: items=${ServerCacheManager.getItems().size} " +
            "locs=${ServerCacheManager.getObjects().size} " +
            "dbrows=${ServerCacheManager.getRows().size} enums=${ServerCacheManager.enumSize()}"
    )
    println("Searching for '$query' maxRows=$maxRows")

    var rows = 0
    rows += searchItems(query, maxRows - rows)
    rows += searchLocs(query, maxRows - rows)
    rows += searchFurnitureRows(query, maxRows - rows)
    rows += searchDbRows(query, maxRows - rows)
    rows += searchEnums(query, maxRows - rows)
    println("Search complete. rows=$rows")
}

private data class CacheSearchArgs(val query: String, val maxRows: Int, val revision: Int)

private fun parseArgs(args: Array<String>): CacheSearchArgs? {
    if (args.isEmpty()) {
        return null
    }
    val mutable = args.toMutableList()
    val hasRevision = mutable.size >= 3 && mutable.last().toIntOrNull() != null && mutable[mutable.lastIndex - 1].toIntOrNull() != null
    val revision = if (hasRevision) mutable.removeLast().toInt() else 239
    val maxRows =
        if (mutable.size >= 2) {
            mutable.last().toIntOrNull()?.let {
                mutable.removeLast()
                it.coerceIn(1, 10_000)
            } ?: 500
        } else {
            500
        }
    val query = mutable.joinToString(" ").lowercase().trim()
    return query.takeIf { it.isNotBlank() }?.let { CacheSearchArgs(it, maxRows, revision) }
}

private fun searchItems(query: String, limit: Int): Int {
    if (limit <= 0) {
        return 0
    }
    var rows = 0
    for ((id, item) in ServerCacheManager.getItems().entries.sortedBy { it.key }) {
        val haystack =
            listOf(
                    id.toString(),
                    item.internalName,
                    item.name,
                    item.examine,
                    item.interfaceOptions.filterNotNull().joinToString(" "),
                )
                .joinToString(" ")
                .lowercase()
        if (query !in haystack) {
            continue
        }
        println(
            "item:$id internal='${item.internalName}' name='${item.name}' " +
                "iops='${item.interfaceOptions.filterNotNull().joinToString("/")}'"
        )
        rows++
        if (rows >= limit) {
            return rows
        }
    }
    return rows
}

private fun searchLocs(query: String, limit: Int): Int {
    if (limit <= 0) {
        return 0
    }
    var rows = 0
    for ((id, loc) in ServerCacheManager.getObjects().entries.sortedBy { it.key }) {
        val actions = loc.actions.toString()
        val transforms = loc.transforms?.joinToString(" ").orEmpty()
        val haystack =
            listOf(
                    id.toString(),
                    loc.internalName,
                    loc.name,
                    loc.desc,
                    actions,
                    transforms,
                    loc.multiVarBit.toString(),
                    loc.multiVarp.toString(),
                )
                .joinToString(" ")
                .lowercase()
        if (query !in haystack) {
            continue
        }
        println(
            "loc:$id internal='${loc.internalName}' name='${loc.name}' " +
                "size=${loc.width}x${loc.length} actions='${loc.actions}' " +
                "multiVarBit=${loc.multiVarBit} multiVarp=${loc.multiVarp} " +
                "transforms=${loc.transforms?.joinToString(",") ?: ""}"
        )
        rows++
        if (rows >= limit) {
            return rows
        }
    }
    return rows
}

private fun searchFurnitureRows(query: String, limit: Int): Int {
    if (limit <= 0) {
        return 0
    }
    var rows = 0
    for (furniture in FurnitureRow.all().sortedBy { it.rowId }) {
        val materials =
            furniture.materialCost.joinToString(" ") { material ->
                val item = material.t0
                "${item.id} ${item.internalName} ${item.name} ${material.t1}"
            }
        val requirements =
            furniture.levelRequirement.joinToString(" ") { requirement ->
                "${requirement.t0.id} ${requirement.t0.displayName} ${requirement.t1}"
            }
        val haystack =
            listOf(
                    furniture.rowId.toString(),
                    furniture.name,
                    furniture.modelObj.id.toString(),
                    furniture.modelObj.internalName,
                    furniture.modelObj.name,
                    materials,
                    requirements,
                )
                .joinToString(" ")
                .lowercase()
        if (query !in haystack) {
            continue
        }
        println(
                "furniture:${furniture.rowId} name='${furniture.name}' " +
                "modelObj=${furniture.modelObj.id}:${furniture.modelObj.internalName}:'${furniture.modelObj.name}' " +
                "levels='${furniture.levelRequirement.joinToString { "${it.t0.displayName}:${it.t1}" }}' " +
                "materials='${furniture.materialCost.joinToString { "${it.t0.id}:${it.t0.internalName}:${it.t0.name} x${it.t1}" }}' " +
                "hidden=${furniture.hiddenInBuildMenu}"
        )
        rows++
        if (rows >= limit) {
            return rows
        }
    }
    return rows
}

private fun searchDbRows(query: String, limit: Int): Int {
    if (limit <= 0) {
        return 0
    }
    var rows = 0
    for ((id, row) in ServerCacheManager.getRows().entries.sortedBy { it.key }) {
        val haystack = row.toString().lowercase()
        if (query !in haystack) {
            continue
        }
        println("dbrow:$id table=${row.tableId} data=$row")
        rows++
        if (rows >= limit) {
            return rows
        }
    }
    return rows
}

private fun searchEnums(query: String, limit: Int): Int {
    if (limit <= 0) {
        return 0
    }
    var rows = 0
    for ((enumId, enum) in ServerCacheManager.getEnums().entries.sortedBy { it.key }) {
        var printedHeader = false
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
            if (!printedHeader) {
                println(
                    "enum:$enumId size=${enum.values.size} key=${enum.keyType.name} " +
                        "value=${enum.valueType.name} defaultInt=${enum.defaultInt} " +
                        "defaultString='${enum.defaultString}'"
                )
                printedHeader = true
            }
            println(
                "enum:$enumId key=${describeEnumSlot(decodedKey)} rawKey=$key " +
                    "value=${describeEnumSlot(decodedValue)} rawValue=$value"
            )
            rows++
            if (rows >= limit) {
                return rows
            }
        }
    }
    return rows
}

@Suppress("UNCHECKED_CAST")
private fun decodeEnumSlot(type: CacheVarLiteral, raw: Any?): Any? =
    when (raw) {
        null -> null
        is Int ->
            runCatching { CacheVarTypeMap.findCodec<Int, Any>(type).decode(raw) }.getOrNull() ?: raw
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
