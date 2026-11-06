package com.reborn.modularmachinery.script;

import com.google.gson.JsonObject;
import com.reborn.modularmachinery.block.MachineControllerBlockEntity;
import com.reborn.modularmachinery.factory.FactoryThread;
import com.reborn.modularmachinery.machine.HatchCollection;
import com.reborn.modularmachinery.machine.MachineRegistry;
import com.reborn.modularmachinery.recipe.RecipeModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.UUID;

/** Engine-independent server script view. All mutable state belongs to the addressed controller/slot. */
public final class MachineControllerAccess {
    private final MachineControllerBlockEntity controller;
    public MachineControllerAccess(MachineControllerBlockEntity controller) { this.controller = controller; }
    public static MachineControllerAccess from(HatchCollection ports) {
        return ports.smartInterfaces() instanceof MachineControllerBlockEntity be ? new MachineControllerAccess(be) : null;
    }
    private void write() {
        Level world = controller.getLevel();
        if (world != null && (world.isClientSide || !world.getServer().isSameThread()))
            throw new IllegalStateException("Machine state may only be changed on the server thread");
        controller.setChanged();
    }
    public BlockPos getPos() { return controller.getBlockPos(); }
    public Level getWorld() { return controller.getLevel(); }
    public BlockState getBlockState() { return controller.getBlockState(); }
    public ResourceLocation getMachine() { return controller.machineId(); }
    public String getFormedMachineName() { return getMachine() == null ? "" : getMachine().toString(); }
    public boolean isFormed() { return controller.isFormed(); }
    public boolean isWorking() { return controller.isWorking(); }
    public UUID getOwnerUUID() { return controller.getOwner(); }
    public net.minecraft.world.entity.player.Player getOwnerPlayer() {
        return getWorld() == null || getOwnerUUID() == null ? null : getWorld().getPlayerByUUID(getOwnerUUID());
    }
    public String getOwnerName() { return getOwnerPlayer() == null ? "" : getOwnerPlayer().getGameProfile().getName(); }
    public int getProgress() { return controller.progress(); }
    public void setProgress(int ticks) { write(); controller.setScriptProgress(ticks); }
    public int getDuration() { return controller.maxProgress(); }
    public int getParallelism() { return controller.activeParallelism(); }
    public ResourceLocation getActiveRecipe() { return controller.scriptActiveRecipeId(); }
    public int getExtraThreadCount() { return controller.extraThreadCount(); }
    public void setExtraThreadCount(int count) { write(); controller.setExtraThreadCount(Math.min(32767, count)); }
    /** Mutable NBT, matching KubeJS's normal block-entity persistentData convention. */
    public CompoundTag getCustomData() { write(); return controller.scriptState().data(); }
    public void setCustomData(CompoundTag data) { write(); controller.scriptState().data(data); }
    public String getStatusMessage() { return controller.scriptState().status(); }
    public void setStatusMessage(String message) { write(); controller.scriptState().status(message); controller.syncScriptState(); }
    public Float getSmartInterfaceData(String type) { return controller.valueOf(type); }
    public Float setSmartInterfaceData(String type, float value) { write(); return controller.setSmartInterfaceValue(type, value); }
    public List<com.reborn.modularmachinery.machine.DynamicPattern.Status> getDynamicPatterns() { return controller.dynamicPatterns(); }
    public List<com.reborn.modularmachinery.machine.MachineModifier> getFoundModifiers() { return controller.activeModifiers(); }
    public List<com.reborn.modularmachinery.machine.MachineMultiBlockModifier> getFoundMultiBlockModifiers() {
        return controller.activeMultiBlockModifiers();
    }
    public List<BlockPos> getBlockPosInPattern() {
        return controller.scriptPatternPositions();
    }
    public List<BlockState> getBlocksInPattern() {
        List<BlockPos> positions = getBlockPosInPattern();
        Level world = readableWorld(positions);
        var states = new java.util.ArrayList<BlockState>(positions.size());
        for (BlockPos pos : positions) {
            if (world != getWorld() || readableWorld(List.of(pos)) != world) throw new IllegalStateException("Machine structure became unavailable");
            states.add(world.getBlockState(pos));
        }
        return List.copyOf(states);
    }

    private Level readableWorld(List<BlockPos> positions) {
        Level world = getWorld();
        BlockPos own = getPos();
        if (world == null || world.isClientSide || controller.isRemoved() || !world.isLoaded(own)
                || world.getBlockEntity(own) != controller)
            throw new IllegalStateException("Machine structure query requires the current loaded server controller");
        for (BlockPos pos : positions) {
            if (world.isOutsideBuildHeight(pos) || !world.isLoaded(pos))
                throw new IllegalStateException("Machine structure position is unavailable: " + pos);
        }
        return world;
    }
    public BlockPos rotateWithControllerFacing(BlockPos offset) {
        for (int i = 0; i < controller.rotationSteps(); i++) offset = new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
        return offset;
    }
    public void addModifier(String name, String target, String io, int operation, float value, boolean chance) {
        add(name, target, io, operation, value, chance, false);
    }
    public void addPermanentModifier(String name, String target, String io, int operation, float value, boolean chance) {
        add(name, target, io, operation, value, chance, true);
    }
    private void add(String name, String target, String io, int operation, float value, boolean chance, boolean permanent) {
        write(); controller.scriptState().add(name, modifier(target, io, operation, value, chance), permanent);
    }
    public static RecipeModifier modifier(String target, String io, int operation, float value, boolean chance) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Modifier value must be finite");
        JsonObject json = new JsonObject();
        json.addProperty("target", target); json.addProperty("io", io); json.addProperty("operation", operation);
        json.addProperty("multiplier", value); json.addProperty("affectChance", chance);
        return RecipeModifier.parse(json, "runtime script modifier");
    }
    public void removeModifier(String name) { write(); controller.scriptState().remove(name); }
    public void removePermanentModifier(String name) { removeModifier(name); }
    public boolean hasModifier(String name) { return controller.scriptState().has(name); }
    public List<UpgradeAccess> getMachineUpgrades() {
        var found = new java.util.ArrayList<UpgradeAccess>();
        for (var bus : controller.upgradeBuses()) for (var stack : bus.upgrades().compatibleWith(getMachine()))
            found.add(new UpgradeAccess(bus, stack));
        return List.copyOf(found);
    }
    public UpgradeAccess getMachineUpgrade(String name) {
        ResourceLocation id = ScriptUpgradeDefinitions.id(name);
        return getMachineUpgrades().stream().filter(u -> u.getId().equals(id)).findFirst().orElse(null);
    }
    public boolean hasMachineUpgrade(String name) { return getMachineUpgrade(name) != null; }
    public boolean hasModifierReplacement(String name) {
        return controller.activeMultiBlockModifiers().stream().anyMatch(m -> m.description().equals(name));
    }
    public final class UpgradeAccess {
        private final com.reborn.modularmachinery.upgrade.UpgradeBusUtility bus;
        private final com.reborn.modularmachinery.upgrade.UpgradeStack stack;
        UpgradeAccess(com.reborn.modularmachinery.upgrade.UpgradeBusUtility bus, com.reborn.modularmachinery.upgrade.UpgradeStack stack) {
            this.bus = bus; this.stack = stack;
        }
        public ResourceLocation getId() { return stack.type().id(); }
        public int getCount() { return stack.count(); }
        public CompoundTag getItemData() { return stack.customDataCopy(); }
        public CompoundTag getCustomData() {
            return bus instanceof com.reborn.modularmachinery.block.UpgradeBusBlockEntity be ? be.upgradeCustomData(getId()).copy() : new CompoundTag();
        }
        public void setCustomData(CompoundTag data) {
            write();
            if (bus instanceof com.reborn.modularmachinery.block.UpgradeBusBlockEntity be) be.setUpgradeCustomData(getId(), data.copy());
        }
    }
    public List<ThreadAccess> getRecipeThreadList() {
        return controller.factoryEngine().allThreads().stream().map(t -> new ThreadAccess(t)).toList();
    }
    public ThreadAccess getRecipeThread(int ordinal) { return getRecipeThreadList().stream().filter(t -> t.getOrdinal() == ordinal).findFirst().orElse(null); }
    public ThreadAccess getCoreThread(String name) { return getRecipeThreadList().stream().filter(t -> name.equals(t.getName())).findFirst().orElse(null); }
    public void createCoreThread(String name, String... recipes) {
        write(); controller.scriptState().coreThread(name, java.util.Arrays.stream(recipes).map(ScriptUpgradeDefinitions::id).toList());
    }
    public void removeCoreThread(String name) {
        write(); var current = getCoreThread(name);
        if (current != null && current.isWorking()) throw new IllegalStateException("Wait for the core thread to become idle before removing it");
        controller.scriptState().removeCoreThread(name);
    }
    public com.reborn.modularmachinery.machine.DynamicPattern.Status getDynamicPattern(String name) {
        return getDynamicPatterns().stream().filter(p -> p.patternName().equals(name)).findFirst().orElse(null);
    }
    public long getTicksExisted() { return controller.scriptTicksExisted(); }
    public net.minecraft.core.Direction getFacing() { return getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING); }
    public List<BlockPos> findBlocks(java.util.function.Predicate<BlockState> predicate) {
        List<BlockPos> positions = getBlockPosInPattern();
        Level world = readableWorld(positions);
        var found = new java.util.ArrayList<BlockPos>();
        for (BlockPos pos : positions) {
            if (world != getWorld() || readableWorld(List.of(pos)) != world) throw new IllegalStateException("Machine structure became unavailable");
            if (predicate.test(world.getBlockState(pos))) found.add(pos);
        }
        return List.copyOf(found);
    }
    public final class ThreadAccess {
        private final FactoryThread thread;
        private ThreadAccess(FactoryThread thread) { this.thread = thread; }
        public int getOrdinal() { return thread.ordinal(); }
        public String getName() { return thread.threadName(); }
        public boolean isCoreThread() { return thread.isCoreThread(); }
        public boolean isWorking() { return thread.isWorking(); }
        public ResourceLocation getRecipe() { return thread.recipeId(); }
        public int getProgress() { return thread.progress(); }
        public int getDuration() { return thread.duration(); }
        public int getParallelism() { return thread.parallelism(); }
        public void setProgress(int ticks) { write(); thread.setScriptProgress(ticks); }
        public CompoundTag getCustomData() { write(); return thread.scriptState().data(); }
        public String getStatusMessage() { return thread.scriptState().status(); }
        public void setStatusMessage(String text) { write(); thread.scriptState().status(text); controller.syncScriptState(); }
        public void addModifier(String name, String target, String io, int operation, float value, boolean chance) {
            write(); thread.scriptState().add(name, modifier(target, io, operation, value, chance), false);
        }
        public void addPermanentModifier(String name, String target, String io, int operation, float value, boolean chance) {
            write(); thread.scriptState().add(name, modifier(target, io, operation, value, chance), true);
        }
        public void removeModifier(String name) { write(); thread.scriptState().remove(name); }
        public boolean hasModifier(String name) { return thread.scriptState().has(name); }
        public List<ResourceLocation> getRecipes() { return List.copyOf(thread.recipeSet()); }
        public void setRecipes(List<ResourceLocation> recipes) { write(); thread.setScriptRecipes(recipes); }
    }
}
