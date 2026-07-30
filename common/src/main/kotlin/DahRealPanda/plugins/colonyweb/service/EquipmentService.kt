package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.util.MineColoniesReflect.invokeAny
import DahRealPanda.plugins.colonyweb.model.EquipmentInfo
import DahRealPanda.plugins.colonyweb.platform.Platform
import DahRealPanda.plugins.colonyweb.util.ScanCoercion
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

class EquipmentService {
    private val slots: Map<EquipmentSlot, String> = linkedMapOf(
        EquipmentSlot.HEAD to "Head",
        EquipmentSlot.CHEST to "Chest",
        EquipmentSlot.LEGS to "Legs",
        EquipmentSlot.FEET to "Feet",
        EquipmentSlot.MAINHAND to "Main hand",
        EquipmentSlot.OFFHAND to "Off hand"
    )

    fun scan(rawCitizens: List<Any>): Map<Int, List<EquipmentInfo>> {
        val result = mutableMapOf<Int, List<EquipmentInfo>>()
        for (citizen in rawCitizens) {
            val id = ScanCoercion.intOf(invokeAny(citizen, "getId").orElse(null), -1)
            if (id < 0) continue
            result[id] = read(citizen)
        }
        return result
    }

    private fun read(citizen: Any): List<EquipmentInfo> {
        val entity = livingEntity(citizen)
        val inventory = invokeAny(citizen, "getInventory").orElse(null)
        val out = mutableListOf<EquipmentInfo>()
        for ((slot, slotName) in slots) {
            val stack = stackFor(entity, inventory, slot) ?: continue
            out.add(describe(stack, slotName))
        }
        return out
    }

    private fun livingEntity(citizen: Any): LivingEntity? {
        var entity = invokeAny(citizen, "getEntity").orElse(null)
        if (entity is java.util.Optional<*>) entity = entity.orElse(null)
        return if (entity is LivingEntity) entity else null
    }

    private fun stackFor(entity: LivingEntity?, inventory: Any?, slot: EquipmentSlot): ItemStack? {
        if (entity != null) {
            val worn = entity.getItemBySlot(slot)
            if (worn != null && !worn.isEmpty) return worn
        }
        return ScanCoercion.itemStackOf(ScanCoercion.firstNonNull(
            invokeAny(inventory, "getArmorInSlot", slot).orElse(null),
            if (slot == EquipmentSlot.MAINHAND) invokeAny(inventory, "getHeldItemMainhand").orElse(null) else null,
            if (slot == EquipmentSlot.OFFHAND) invokeAny(inventory, "getHeldItemOffhand").orElse(null) else null))
    }

    private fun describe(stack: ItemStack, slotName: String): EquipmentInfo {
        val info = ScanCoercion.fillItem(EquipmentInfo(), stack)
        info.slot = slotName
        info.enchanted = stack.isEnchanted
        info.armorPoints = Platform.get().armorPoints(stack)
        if (stack.isDamageableItem && stack.maxDamage > 0) {
            info.durabilityPct = Math.round(100.0 * (stack.maxDamage - stack.damageValue) / stack.maxDamage).toInt()
        }
        return info
    }
}
