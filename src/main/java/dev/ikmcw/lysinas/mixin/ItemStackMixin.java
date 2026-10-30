package dev.ikmcw.lysinas.mixin;

import dev.ikmcw.lysinas.Main;
import dev.ikmcw.lysinas.config.ConfigManager;
import dev.ikmcw.lysinas.config.ExtraConfig;
import dev.ikmcw.lysinas.gradient.Gradient;
import dev.ikmcw.lysinas.tooltip.EnchantmentData;
import dev.ikmcw.lysinas.tooltip.TooltipColourizer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@ModifyVariable(method = "addToTooltip", at = @At("STORE"))
	private TooltipProvider lysinas$colourEnchantments(TooltipProvider itemComponent) {
		if (!(itemComponent instanceof ItemLore lore) || !Main.isActive()) return itemComponent;

		ExtraConfig config = ConfigManager.get();
		CompoundTag customData = ((ItemStack) (Object) this)
				.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		CompoundTag appliedEnchantments = customData.getCompoundOrEmpty("enchantments");
		ItemStack stack = (ItemStack) (Object) this;
		String registryItemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		String customItemId = customData.getStringOr("id",
				customData.getCompoundOrEmpty("ExtraAttributes").getStringOr("id", ""));
		Set<String> maxEnchantments = new LinkedHashSet<>();
		Set<String> goodEnchantments = new LinkedHashSet<>();
		Set<String> presentEnchantments = new LinkedHashSet<>();

		for (String id : appliedEnchantments.keySet()) {
			EnchantmentData.Entry enchantment = EnchantmentData.find(id);
			int level = appliedEnchantments.getIntOr(id, 0);
			if (level <= 0) continue;
			presentEnchantments.add(id);
			if (enchantment == null) continue;
			if (enchantment.isMax(level)) {
				maxEnchantments.add(enchantment.displayName(level));
			} else if (config.showGoodEnchantments && enchantment.isGood(level)) {
				goodEnchantments.add(enchantment.displayName(level));
			}
		}

		int profileSlot = config.activeProfile == null || config.activeProfile.isBlank()
				? config.findProfileIndex(registryItemId, customItemId, presentEnchantments)
				: config.findProfileIndexByName(config.activeProfile);
		if (maxEnchantments.isEmpty() && goodEnchantments.isEmpty()) return itemComponent;
		int goodColour = config.showGoodEnchantments ? Gradient.parseColor(config.goodEnchantmentColor) : 0;
		List<Component> recolouredLines = null;
		List<Component> originalLines = lore.lines();
		for (int i = 0; i < originalLines.size(); i++) {
			Component line = originalLines.get(i);
			Component recoloured = TooltipColourizer.apply(line, maxEnchantments, goodEnchantments,
					config, goodColour, profileSlot);
			if (recoloured != line) {
				if (recolouredLines == null) recolouredLines = new ArrayList<>(originalLines);
				recolouredLines.set(i, recoloured);
			}
		}
		return recolouredLines == null ? itemComponent : new ItemLore(recolouredLines);
	}
}
