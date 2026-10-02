package dev.openrune.tables.skills

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object Construction {
    private const val COL_MODEL_OBJ = 0
    private const val COL_FURNITURE_NAME = 1
    private const val COL_BUILT_LOCS = 2
    private const val COL_XP = 3

    fun pohBuildables() = dbTable("dbtable.construction_poh_buildables", serverOnly = true) {
        column("model_obj", COL_MODEL_OBJ, VarType.OBJ)
        column("furniture_name", COL_FURNITURE_NAME, VarType.STRING)
        column("built_locs", COL_BUILT_LOCS, VarType.LOC)
        column("xp", COL_XP, VarType.INT)

        row("dbrow.poh_exit_portal") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_garden_centrepiece_1")
            column(COL_FURNITURE_NAME, "Exit portal")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_exit_portal")
            column(COL_XP, 100)
        }
        row("dbrow.poh_decorative_rock") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_garden_centrepiece_2")
            column(COL_FURNITURE_NAME, "Decorative Rock")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_crude_garden_centrepiece2")
            column(COL_XP, 100)
        }
        row("dbrow.poh_pond") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_garden_centrepiece_3")
            column(COL_FURNITURE_NAME, "Pond")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_crude_garden_centrepiece3")
            column(COL_XP, 100)
        }
        row("dbrow.poh_imp_statue") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_garden_centrepiece_4")
            column(COL_FURNITURE_NAME, "Imp Statue")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_crude_garden_centrepiece4")
            column(COL_XP, 150)
        }
        row("dbrow.poh_dungeon_entrance") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_garden_centrepiece_5")
            column(COL_FURNITURE_NAME, "Dungeon entrance")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_crude_garden_centrepiece5")
            column(COL_XP, 500)
        }

        row("dbrow.poh_tree_dead") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_tree_1")
            column(COL_FURNITURE_NAME, "Dead tree")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_big_tree1_4", "loc.poh_small_tree1_5")
            column(COL_XP, 31)
        }
        row("dbrow.poh_tree_nice") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_tree_2")
            column(COL_FURNITURE_NAME, "Nice tree")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_big_tree2_4", "loc.poh_small_tree2_5")
            column(COL_XP, 44)
        }
        row("dbrow.poh_tree_oak") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_tree_3")
            column(COL_FURNITURE_NAME, "Oak tree")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_big_tree3_4", "loc.poh_small_tree3_5")
            column(COL_XP, 70)
        }
        row("dbrow.poh_tree_willow") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_tree_4")
            column(COL_FURNITURE_NAME, "Willow tree")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_big_tree4_4", "loc.poh_small_tree4_5")
            column(COL_XP, 100)
        }
        row("dbrow.poh_tree_maple") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_tree_5")
            column(COL_FURNITURE_NAME, "Maple tree")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_big_tree5_4", "loc.poh_small_tree5_5")
            column(COL_XP, 122)
        }
        row("dbrow.poh_tree_yew") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_tree_6")
            column(COL_FURNITURE_NAME, "Yew tree")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_big_tree6_4", "loc.poh_small_tree6_5")
            column(COL_XP, 141)
        }
        row("dbrow.poh_tree_magic") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_tree_7")
            column(COL_FURNITURE_NAME, "Magic tree")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_big_tree7_4", "loc.poh_small_tree7_5")
            column(COL_XP, 223)
        }

        row("dbrow.poh_big_plant_fern") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_plantbig1a")
            column(COL_FURNITURE_NAME, "Fern")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_plantbig1a")
            column(COL_XP, 31)
        }
        row("dbrow.poh_big_plant_bush") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_plantbig1b")
            column(COL_FURNITURE_NAME, "Bush")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_plantbig1b")
            column(COL_XP, 70)
        }
        row("dbrow.poh_big_plant_tall") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_plantbig1c")
            column(COL_FURNITURE_NAME, "Tall plant")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_plantbig1c")
            column(COL_XP, 100)
        }
        row("dbrow.poh_big_plant_short") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_plantbig2a")
            column(COL_FURNITURE_NAME, "Short plant")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_plantbig2a")
            column(COL_XP, 31)
        }
        row("dbrow.poh_big_plant_large_leaf") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_plantbig2b")
            column(COL_FURNITURE_NAME, "Large-leaf plant")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_plantbig2b")
            column(COL_XP, 70)
        }
        row("dbrow.poh_big_plant_huge") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_plantbig2c")
            column(COL_FURNITURE_NAME, "Huge plant")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_plantbig2c")
            column(COL_XP, 100)
        }

        row("dbrow.poh_small_plant") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_plantsmall1a")
            column(COL_FURNITURE_NAME, "Plant")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_plantbsmall1a")
            column(COL_XP, 31)
        }
        row("dbrow.poh_small_plant_smaller_fern") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_plantsmall1b")
            column(COL_FURNITURE_NAME, "Small Fern")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_plantbsmall1b")
            column(COL_XP, 70)
        }
        row("dbrow.poh_small_plant_fern") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_plantsmall1c")
            column(COL_FURNITURE_NAME, "Fern")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_plantbsmall1c")
            column(COL_XP, 100)
        }
        row("dbrow.poh_small_plant_dock_leaf") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_plantsmall2a")
            column(COL_FURNITURE_NAME, "Dock leaf")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_plantbsmall2a")
            column(COL_XP, 31)
        }
        row("dbrow.poh_small_plant_thistle") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_plantsmall2b")
            column(COL_FURNITURE_NAME, "Thistle")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_plantbsmall2b")
            column(COL_XP, 70)
        }
        row("dbrow.poh_small_plant_reeds") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_plantsmall2c")
            column(COL_FURNITURE_NAME, "Reeds")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_plantbsmall2c")
            column(COL_XP, 100)
        }

        row("dbrow.poh_tip_jar") {
            columnRSCM(COL_MODEL_OBJ, "obj.poh_tipjar")
            column(COL_FURNITURE_NAME, "Tip jar")
            columnRSCM(COL_BUILT_LOCS, "loc.poh_tipjar")
            column(COL_XP, 651)
        }
    }
}
