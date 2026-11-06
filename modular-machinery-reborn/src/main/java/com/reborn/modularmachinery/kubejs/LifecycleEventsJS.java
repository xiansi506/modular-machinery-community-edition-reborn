package com.reborn.modularmachinery.kubejs;

import com.reborn.modularmachinery.ModularMachineryReborn;
import com.reborn.modularmachinery.api.event.FactoryRecipeEvent;
import com.reborn.modularmachinery.api.event.FactoryRecipeFailureEvent;
import com.reborn.modularmachinery.api.event.FactoryRecipeFinishEvent;
import com.reborn.modularmachinery.api.event.FactoryRecipeStartEvent;
import com.reborn.modularmachinery.api.event.FactoryRecipeTickEvent;
import com.reborn.modularmachinery.api.event.MachineStructureFormedEvent;
import com.reborn.modularmachinery.api.event.MachineStructureUpdateEvent;
import com.reborn.modularmachinery.api.event.MachineTickEvent;
import com.reborn.modularmachinery.api.event.Phase;
import com.reborn.modularmachinery.api.event.RecipeCheckEvent;
import com.reborn.modularmachinery.api.event.RecipeEvent;
import com.reborn.modularmachinery.api.event.RecipeFailureEvent;
import com.reborn.modularmachinery.api.event.RecipeFinishEvent;
import com.reborn.modularmachinery.api.event.RecipeStartEvent;
import com.reborn.modularmachinery.api.event.RecipeTickEvent;
import dev.latvian.mods.kubejs.event.EventExit;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventJS;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.ScriptTypeHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;

/**
 * C3（0.90.0）：把 C2 的 Forge 生命周期事件面接到 KubeJS 服务端脚本——旧版 CraftTweaker
 * {@code mods.modularmachinery.MMEvents.onXxx(handler)} 那一族的对应物，KubeJS 化后是事件组：
 *
 * <pre>{@code
 * // server_scripts/callbacks.js
 * MachineLifecycleEvents.recipeCheck(event => {
 *   if (event.recipe == 'modular_machinery_reborn:too_hot') {
 *     event.setFailed('太烫了，不给他开')   // 否决开工，理由会显示在机器界面上
 *   } else {
 *     event.parallelism = Math.min(event.parallelism, 8)
 *   }
 * })
 * MachineLifecycleEvents.recipeFailure(event => {
 *   event.destructRecipe = true   // 按次改写：这次失败直接清合成
 * })
 * }</pre>
 *
 * <p><b>桥的形式</b>：Forge 总线 → 这里的包装 {@code EventJS} → KubeJS 事件组。可取消的语义
 * **原样转发**：{@code setFailed} 写回 {@code RecipeCheckEvent}（理由经 0.88.0 的字符串通道显示）、
 * {@code cancel()} 写回对应 Forge 事件的取消位、{@code destructRecipe} 写回失败事件。
 *
 * <p><b>性能提醒</b>（旧版 CT 桥同样如此）：{@code machineTick}/{@code recipeTick} 每服务端 tick
 * 都会过一遍脚本事件分发；没有监听器时分发只是一次查表，但监听器里不要做重活。
 * 与旧版不同处：KubeJS 6 移除了泛型 {@code onEvent}，所以必须走事件组绑定（见
 * {@link MachineEvents} 的同款说明）。
 */
public final class LifecycleEventsJS {

    /** 脚本绑定名与事件组 id。 */
    public static final String GROUP_NAME = "MachineLifecycleEvents";

    private static final EventGroup GROUP = EventGroup.of(GROUP_NAME);
    private static boolean bridgeRegistered;
    private static final EventHandler RESULT_CHANCE = GROUP.server("resultChance", () -> ResultChanceEventJS.class);
    private static final EventHandler SMART_INTERFACE = GROUP.server("smartInterfaceUpdate", () -> SmartInterfaceEventJS.class);

    private static final EventHandler STRUCTURE_FORMED = GROUP.server("structureFormed",
            () -> ControllerEventJS.class);
    private static final EventHandler STRUCTURE_UPDATED = GROUP.server("structureUpdated",
            () -> ControllerEventJS.class);
    private static final EventHandler MACHINE_TICK = GROUP.server("machineTick",
            () -> MachineTickEventJS.class);
    private static final EventHandler RECIPE_CHECK = GROUP.server("recipeCheck",
            () -> RecipeCheckEventJS.class);
    private static final EventHandler RECIPE_START = GROUP.server("recipeStart",
            () -> RecipeEventJS.class);
    private static final EventHandler RECIPE_TICK = GROUP.server("recipeTick",
            () -> RecipeTickEventJS.class);
    private static final EventHandler RECIPE_FINISH = GROUP.server("recipeFinish",
            () -> RecipeEventJS.class);
    private static final EventHandler RECIPE_FAILURE = GROUP.server("recipeFailure",
            () -> RecipeFailureEventJS.class);
    private static final EventHandler FACTORY_RECIPE_START = GROUP.server("factoryRecipeStart",
            () -> FactoryRecipeEventJS.class);
    private static final EventHandler FACTORY_RECIPE_TICK = GROUP.server("factoryRecipeTick",
            () -> FactoryRecipeTickEventJS.class);
    private static final EventHandler FACTORY_RECIPE_FINISH = GROUP.server("factoryRecipeFinish",
            () -> FactoryRecipeEventJS.class);
    private static final EventHandler FACTORY_RECIPE_FAILURE = GROUP.server("factoryRecipeFailure",
            () -> FactoryFailureEventJS.class);

    private LifecycleEventsJS() {
    }

    /** Called from the plugin's {@code registerEvents()}: KubeJS refuses an unregistered group's events. */
    public static void register() {
        GROUP.register();
    }

    /** The group object the plugin binds as {@code MachineLifecycleEvents}. */
    public static EventGroup group() {
        return GROUP;
    }

    /**
     * The Forge-bus bridge. Registered by the plugin (so it only exists when KubeJS does), server events only.
     */
    public static void registerBridge() {
        if (bridgeRegistered) return;
        bridgeRegistered = true;
        MinecraftForge.EVENT_BUS.addListener((com.reborn.modularmachinery.api.event.ResultChanceCreateEvent event) ->
                post(RESULT_CHANCE, new ResultChanceEventJS(event)));
        MinecraftForge.EVENT_BUS.addListener((com.reborn.modularmachinery.api.event.SmartInterfaceUpdateEvent event) ->
                post(SMART_INTERFACE, new SmartInterfaceEventJS(event)));
        MinecraftForge.EVENT_BUS.addListener((MachineStructureFormedEvent event) ->
                post(STRUCTURE_FORMED, new ControllerEventJS(event)));
        MinecraftForge.EVENT_BUS.addListener((MachineStructureUpdateEvent event) ->
                post(STRUCTURE_UPDATED, new ControllerEventJS(event)));
        MinecraftForge.EVENT_BUS.addListener((MachineTickEvent event) ->
                post(MACHINE_TICK, new MachineTickEventJS(event)));
        MinecraftForge.EVENT_BUS.addListener((RecipeCheckEvent event) ->
                post(RECIPE_CHECK, new RecipeCheckEventJS(event)));
        MinecraftForge.EVENT_BUS.addListener((RecipeStartEvent event) ->
                post(RECIPE_START, new RecipeEventJS(event)));
        MinecraftForge.EVENT_BUS.addListener((RecipeTickEvent event) ->
                post(RECIPE_TICK, new RecipeTickEventJS(event)));
        MinecraftForge.EVENT_BUS.addListener((RecipeFinishEvent event) ->
                post(RECIPE_FINISH, new RecipeEventJS(event)));
        MinecraftForge.EVENT_BUS.addListener((RecipeFailureEvent event) ->
                post(RECIPE_FAILURE, new RecipeFailureEventJS(event)));
        MinecraftForge.EVENT_BUS.addListener((FactoryRecipeStartEvent event) ->
                post(FACTORY_RECIPE_START, new FactoryRecipeEventJS(event)));
        MinecraftForge.EVENT_BUS.addListener((FactoryRecipeTickEvent event) ->
                post(FACTORY_RECIPE_TICK, new FactoryRecipeTickEventJS(event)));
        MinecraftForge.EVENT_BUS.addListener((FactoryRecipeFinishEvent event) ->
                post(FACTORY_RECIPE_FINISH, new FactoryRecipeEventJS(event)));
        MinecraftForge.EVENT_BUS.addListener((FactoryRecipeFailureEvent event) ->
                post(FACTORY_RECIPE_FAILURE, new FactoryFailureEventJS(event)));
    }

    private static void post(EventHandler handler, ControllerEventJS event) {
        boolean[] failed = {false};
        // KubeJS may log a callback exception and still return PASS. Observe its error callback too,
        // then fail the native gate after the entire global listener batch has returned.
        EventResult result = handler.post((ScriptTypeHolder) ScriptType.SERVER, null, event,
                (js, container, error) -> { failed[0] = true; return error; });
        if (!failed[0] && !result.error()) return;
        if (event.backing instanceof RecipeCheckEvent check) {
            check.setFailed("Global script callback failed: " + handler);
        } else if (event.backing instanceof RecipeTickEvent tick) {
            tick.preventProgressing("Global script callback failed: " + handler);
        } else if (event.backing.isCancelable()) {
            event.backing.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ script-facing wrappers

    /** 结构事件的载荷：控制器位置与机器 id。 */
    public static class ControllerEventJS extends EventJS {
        protected final com.reborn.modularmachinery.api.event.MachineEvent backing;

        public ControllerEventJS(com.reborn.modularmachinery.api.event.MachineEvent backing) {
            this.backing = backing;
        }

        public BlockPos getPos() {
            return this.backing.controller().getBlockPos();
        }

        public com.reborn.modularmachinery.script.MachineControllerAccess getController() {
            return new com.reborn.modularmachinery.script.MachineControllerAccess(backing.controller());
        }
        public net.minecraft.world.level.Level getWorld() { return backing.controller().getLevel(); }

        /** Safe for scoped callbacks too: mark the underlying event without throwing EventExit. */
        public void prevent() {
            if (backing instanceof RecipeTickEvent tick) { tick.preventProgressing("Script prevented progress"); return; }
            if (!backing.isCancelable()) throw new IllegalStateException("This machine event cannot be canceled");
            backing.setCanceled(true);
        }
        @Override public Object cancel() throws EventExit {
            prevent();
            return super.cancel();
        }

        @Nullable
        public ResourceLocation getMachine() {
            return this.backing.machineId();
        }
    }

    /** 机器 tick 的载荷。 */
    public static class MachineTickEventJS extends ControllerEventJS {
        public MachineTickEventJS(MachineTickEvent backing) {
            super(backing);
        }

        public Phase getPhase() {
            return ((MachineTickEvent) this.backing).phase();
        }
    }

    /** 配方类事件共有的载荷。 */
    public static class RecipeEventJS extends ControllerEventJS {
        public RecipeEventJS(RecipeEvent backing) {
            super(backing);
        }

        public ResourceLocation getRecipe() {
            return ((RecipeEvent) this.backing).recipe().getId();
        }

        public int getProgress() {
            return ((RecipeEvent) this.backing).progress();
        }

        public int getDuration() {
            return ((RecipeEvent) this.backing).maxProgress();
        }

        public int getParallelism() {
            return ((RecipeEvent) this.backing).parallelism();
        }
    }

    /** 开工检查：可下调并行、可否决开工。 */
    public static class RecipeCheckEventJS extends RecipeEventJS {
        public RecipeCheckEventJS(RecipeCheckEvent backing) {
            super(backing);
        }

        public Phase getPhase() {
            return ((RecipeCheckEvent) this.backing).phase();
        }
        @Override public int getParallelism() { return ((RecipeCheckEvent) backing).requestedParallelism(); }
        public int getThread() { return ((RecipeCheckEvent) backing).threadOrdinal(); }

        /** 脚本属性写法：{@code event.parallelism = n}（控制器仍会夹到 1..上限）。 */
        public void setParallelism(int parallelism) {
            ((RecipeCheckEvent) this.backing).setParallelism(parallelism);
        }

        /** 否决这次开工；理由（键或明文）会显示在机器界面上。 */
        public void setFailed(@Nullable String reason) {
            ((RecipeCheckEvent) this.backing).setFailed(reason);
        }
    }

    /** 配方逐 tick：取消 = 按 C2 语义（START 整 tick 跳过；END 资源留扣、进度不加）。 */
    public static class RecipeTickEventJS extends RecipeEventJS {
        public RecipeTickEventJS(RecipeTickEvent backing) {
            super(backing);
        }

        public Phase getPhase() {
            return ((RecipeTickEvent) this.backing).phase();
        }

        /**
         * KubeJS 的 {@code EventJS#cancel()} 返回 {@code Object} 并抛 {@code EventExit} 中断后续脚本监听器。
         * 顺序必须「先转发到 Forge 事件的取消位，再 super.cancel()」——super 抛出后后面的语句不再执行。
         */
        @Override
        public Object cancel() throws EventExit {
            ((RecipeTickEvent) this.backing).preventProgressing("Script canceled progress");
            super.cancel();
            return null;
        }
    }

    /** 配方失败：可按次改写 destruct。 */
    public static class RecipeFailureEventJS extends RecipeEventJS {
        public RecipeFailureEventJS(RecipeFailureEvent backing) {
            super(backing);
        }

        @Nullable
        public String getCause() {
            return ((RecipeFailureEvent) this.backing).cause();
        }

        public boolean isDestructRecipe() {
            return ((RecipeFailureEvent) this.backing).destructRecipe();
        }

        /** 脚本属性写法：{@code event.destructRecipe = true/false}。 */
        public void setDestructRecipe(boolean destruct) {
            ((RecipeFailureEvent) this.backing).setDestructRecipe(destruct);
        }
    }

    /** 工厂配方事件共有的载荷：再加槽位序号。 */
    public static class FactoryRecipeEventJS extends RecipeEventJS {
        public FactoryRecipeEventJS(FactoryRecipeEvent backing) {
            super(backing);
        }

        /** 槽位序号（核心/普通线程共用，与工厂界面对应）。 */
        public int getThread() {
            return ((FactoryRecipeEvent) this.backing).threadOrdinal();
        }
    }

    /** 工厂配方逐 tick：两段取消语义与单控制器相同。 */
    public static class FactoryRecipeTickEventJS extends FactoryRecipeEventJS {
        public FactoryRecipeTickEventJS(FactoryRecipeTickEvent backing) {
            super(backing);
        }

        public Phase getPhase() {
            return ((FactoryRecipeTickEvent) this.backing).phase();
        }

        /** 同 {@link RecipeTickEventJS#cancel()}：先转发再 super（super 抛 {@code EventExit}）。 */
        @Override
        public Object cancel() throws EventExit {
            ((FactoryRecipeTickEvent) this.backing).setCanceled(true);
            super.cancel();
            return null;
        }
    }

    /** 工厂配方失败：可按次改写 destruct。 */
    public static class FactoryFailureEventJS extends FactoryRecipeEventJS {
        public FactoryFailureEventJS(FactoryRecipeFailureEvent backing) {
            super(backing);
        }

        @Nullable
        public String getCause() {
            return ((FactoryRecipeFailureEvent) this.backing).cause();
        }

        public boolean isDestructRecipe() {
            return ((FactoryRecipeFailureEvent) this.backing).destructRecipe();
        }

        public void setDestructRecipe(boolean destruct) {
            ((FactoryRecipeFailureEvent) this.backing).setDestructRecipe(destruct);
        }
    }

    public static class ResultChanceEventJS extends RecipeEventJS {
        public ResultChanceEventJS(com.reborn.modularmachinery.api.event.ResultChanceCreateEvent backing) { super(backing); }
        public int getThread() { return ((com.reborn.modularmachinery.api.event.ResultChanceCreateEvent) backing).threadOrdinal(); }
        public void setSeed(long seed) {
            ((com.reborn.modularmachinery.api.event.ResultChanceCreateEvent) backing).setRandom(net.minecraft.util.RandomSource.create(seed));
        }
        public net.minecraft.util.RandomSource getRandom() {
            return ((com.reborn.modularmachinery.api.event.ResultChanceCreateEvent) backing).random();
        }
        public void setRandom(net.minecraft.util.RandomSource random) {
            ((com.reborn.modularmachinery.api.event.ResultChanceCreateEvent) backing).setRandom(random);
        }
    }
    public static class SmartInterfaceEventJS extends ControllerEventJS {
        public SmartInterfaceEventJS(com.reborn.modularmachinery.api.event.SmartInterfaceUpdateEvent backing) { super(backing); }
        public String getType() { return ((com.reborn.modularmachinery.api.event.SmartInterfaceUpdateEvent) backing).type(); }
        public float getValue() { return ((com.reborn.modularmachinery.api.event.SmartInterfaceUpdateEvent) backing).newValue(); }
    }
}
