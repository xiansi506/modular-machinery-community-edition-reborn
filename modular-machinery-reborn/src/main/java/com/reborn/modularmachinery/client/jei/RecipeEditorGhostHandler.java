package com.reborn.modularmachinery.client.jei;

import com.reborn.modularmachinery.client.RecipeEditorScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import java.util.List;

/** Only JEI loads this class. No optional ingredient types enter the editor's common/client API. */
public final class RecipeEditorGhostHandler implements IGhostIngredientHandler<RecipeEditorScreen> {
    @Override public <I> List<Target<I>> getTargetsTyped(RecipeEditorScreen screen, ITypedIngredient<I> ingredient, boolean start) {
        Object value=ingredient.getIngredient();
        String canvasKind=value instanceof ItemStack?"item":value instanceof FluidStack?"fluid":"gas";
        String canvasGas=canvasKind.equals("gas")&&net.minecraftforge.fml.ModList.get().isLoaded("mekanism")?com.reborn.modularmachinery.compat.mekanism.MekanismEditorIngredient.id(value):null;
        var slots=canvasKind.equals("gas")&&canvasGas==null?List.<RecipeEditorScreen.GhostSlot>of():screen.ghostSlots(canvasKind);
        if(!slots.isEmpty())return slots.stream().<Target<I>>map(slot->new Target<I>(){
            public Rect2i getArea(){return slot.area();}
            public void accept(I dropped){screen.dropGhost(slot,canvasKind,canvasGas==null?dropped:canvasGas);}
        }).toList();
        boolean accepted=value instanceof ItemStack&&screen.accepts("item") || value instanceof FluidStack&&screen.accepts("fluid");
        String gas=net.minecraftforge.fml.ModList.get().isLoaded("mekanism") ? com.reborn.modularmachinery.compat.mekanism.MekanismEditorIngredient.id(value) : null;
        if(!accepted && !(gas!=null&&screen.accepts("gas")))return List.of();
        Rect2i area=screen.dropArea();
        return List.of(new Target<I>() {
            public Rect2i getArea(){return area;}
            public void accept(I dropped){if(dropped instanceof ItemStack stack)screen.dropItem(stack);else if(dropped instanceof FluidStack fluid)screen.dropFluid(fluid);else if(gas!=null)screen.dropGas(gas);}
        });
    }
    @Override public void onComplete(){}
}
