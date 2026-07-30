package DahRealPanda.plugins.colonyweb.util

import DahRealPanda.plugins.colonyweb.model.ItemCount
import DahRealPanda.plugins.colonyweb.model.ItemInfo
import DahRealPanda.plugins.colonyweb.service.DomumOrnamentumResolver
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack

object ScanCoercion {
    private const val DO_WORKSTATION = "Architects Cutter"

    fun firstNonNull(vararg values: Any?): Any? {
        for (v in values) {
            if (v != null) return v
        }
        return null
    }

    fun intOf(o: Any?, def: Int): Int {
        return if (o is Number) o.toInt() else def
    }

    fun doubleOf(o: Any?, def: Double): Double {
        return if (o is Number) o.toDouble() else def
    }

    fun boolOf(o: Any?, def: Boolean): Boolean {
        return if (o is Boolean) o else def
    }

    fun stringOf(o: Any?, def: String): String {
        if (o == null) return def
        val s = o.toString()
        return if (s.isEmpty()) def else s
    }

    fun blockPosOf(o: Any?): BlockPos? {
        return if (o is BlockPos) o else null
    }

    fun itemStackOf(o: Any?): ItemStack? {
        return if (o is ItemStack) o else null
    }

    fun <T : ItemInfo> fillItem(target: T, stack: ItemStack): T {
        target.itemKey = DomumOrnamentumResolver.textureKeyFor(stack)
        target.name = stack.hoverName.string
        target.domum = DomumOrnamentumResolver.isDomum(stack)
        if (target.domum) {
            target.material = DomumOrnamentumResolver.materialName(stack)
            target.components = DomumOrnamentumResolver.componentsOf(stack).toMutableList()
            target.craftedIn = DO_WORKSTATION
        }
        return target
    }

    fun itemCount(stack: ItemStack, count: Int, slot: Int): ItemCount {
        val item = fillItem(ItemCount(), stack)
        item.count = count
        item.slot = slot
        return item
    }
}
