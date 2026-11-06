package com.reborn.modularmachinery.authoring;

import com.google.gson.*;
import com.reborn.modularmachinery.machine.*;
import com.reborn.modularmachinery.recipe.*;
import com.reborn.modularmachinery.network.RecipeEditorStatePacket;
import com.reborn.modularmachinery.selection.MachineFragmentWriter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/** Permission checks, validation tickets and files all belong to the logical server. */
public final class RecipeEditorService {
    public enum Action { VALIDATE, EXPORT, CLOSE }
    public static final Map<String,String> MODS = Map.of("gas","mekanism", "mana","botania", "aura","naturesaura", "lifeessence","bloodmagic", "will","bloodmagic", "compressed_air","pneumaticcraft", "ember","embers");
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    public static final class Session {
        public final UUID token = UUID.randomUUID();
        public long revision, nextRequest;
        public Draft draft;
        public void validate(String id,String source,RecipeDocument.Format format) {
            draft=null;revision++;draft=prepare(id,source,format);
        }
        public Path export(String id,String source,RecipeDocument.Format format,Path directory,String playerName) throws java.io.IOException {
            if(draft==null || !draft.matches(id,source,format))throw new IllegalArgumentException("The draft changed; validate it before exporting");
            Draft checked=prepare(id,source,format);
            Path saved=save(checked,directory,playerName);draft=null;revision++;return saved;
        }
    }
    public record Draft(String id, String source, RecipeDocument.Format format, String content, List<String> summary) {
        public boolean matches(String id, String source, RecipeDocument.Format format) { return this.id.equals(id) && this.source.equals(source) && this.format == format; }
    }
    private RecipeEditorService() {}
    public static boolean allowed(ServerPlayer player) { return player != null && player.isCreative() && player.hasPermissions(2); }
    public static void open(ServerPlayer player, String machine) {
        if (!allowed(player)) { player.displayClientMessage(net.minecraft.network.chat.Component.translatable("editor.modular_machinery_reborn.permission"), false); return; }
        Session session = new Session(); SESSIONS.put(player.getUUID(),session);
        JsonObject state = state(session, "", "");
        state.addProperty("suggestedId", RecipeQuickEdit.suggestedId());
        JsonArray machines = new JsonArray(); MachineRegistry.all().stream().map(m->m.id().toString()).sorted().limit(512).forEach(machines::add); state.add("machines", machines);
        JsonArray absent = new JsonArray(); MODS.forEach((kind,mod)-> { if (!ModList.get().isLoaded(mod)) absent.add(kind); }); state.add("absent", absent);
        JsonObject initial=RecipeDocument.create(machine.isBlank() ? (machines.isEmpty() ? "custom:machine" : machines.get(0).getAsString()) : machine);
        initial.add("requirements",new JsonArray());
        state.addProperty("source", initial.toString());
        state.addProperty("dimension", player.level().dimension().location().toString());
        RecipeEditorStatePacket.sendTo(player,state,true);
    }
    public static Draft prepare(String id, String source, RecipeDocument.Format format) {
        ResourceLocation recipeId = RecipeDocument.id(id); JsonObject root = RecipeDocument.read(source);
        if (root.has("requirements")) for (JsonElement element : root.getAsJsonArray("requirements")) {
            String kind = RecipeDocument.kind(element.getAsJsonObject()), mod = MODS.get(kind);
            if (mod != null && !ModList.get().isLoaded(mod)) throw new IllegalArgumentException("Requires mod: " + mod);
        }
        MachineRecipe recipe = RecipeDocument.validate(recipeId,root);
        for (ResourceLocation machine : recipe.machineIds()) if (MachineRegistry.byId(machine).isEmpty()) throw new IllegalArgumentException("Unknown machine: " + machine);
        String content = RecipeDocument.content(recipeId,root,format);
        if (content.length() > 65536) throw new IllegalArgumentException("Formatted export exceeds 65536 characters; reduce the draft");
        List<String> summary=new ArrayList<>(RecipeDocument.summary(root));
        recipe.machineIds().forEach(machine -> MachineRegistry.byId(machine).ifPresent(def -> summary.addAll(RecipeDocument.portWarnings(root,def))));
        summary.add(format == RecipeDocument.Format.JSON ? "Install as data/" + recipeId.getNamespace() + "/recipes/" + recipeId.getPath() + ".json" : "Install in kubejs/server_scripts/; keep the .id() value.");
        return new Draft(id,source,format,content,List.copyOf(summary));
    }
    public static Path save(Draft draft, Path directory, String playerName) throws java.io.IOException {
        String name = MachineFragmentWriter.fileNameFor(playerName,LocalDateTime.now()).replaceFirst("^machine-", "recipe-");
        if (draft.format() == RecipeDocument.Format.KUBEJS) name = name.substring(0,name.length()-5) + ".js";
        return MachineFragmentWriter.write(directory,name,draft.content());
    }
    public static void handle(ServerPlayer player, UUID token, long revision, Action action, String id, String source, RecipeDocument.Format format) {
        if (!allowed(player)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.token.equals(token)) return;
        if (action == Action.CLOSE) { SESSIONS.remove(player.getUUID()); return; }
        if (revision != session.revision) { RecipeEditorStatePacket.sendTo(player,state(session,"error","Editor state changed; validate again"),false); return; }
        long now = player.server.getTickCount();
        if (now < session.nextRequest) { RecipeEditorStatePacket.sendTo(player,state(session,"error","Please wait before another request"),false); return; }
        session.nextRequest = now + 5;
        try {
            if (action == Action.VALIDATE) {
                session.validate(id,source,format);
                RecipeEditorStatePacket.sendTo(player,state(session,"valid",""),false);
            } else {
                // A reload may have removed a machine, resource or requirement handler after preview.
                Path saved = session.export(id,source,format,MachineDirectory.directory().resolveSibling("exports").resolve("recipes"),player.getGameProfile().getName());
                RecipeEditorStatePacket.sendTo(player,state(session,"saved",saved.getFileName().toString()),false);
            }
        } catch (Exception error) {
            RecipeEditorStatePacket.sendTo(player,state(session,"error",String.valueOf(error.getMessage())),false);
        }
    }
    public static JsonObject state(Session session, String kind, String message) {
        JsonObject state = new JsonObject(); state.addProperty("token",session.token.toString()); state.addProperty("revision",session.revision);
        state.addProperty("kind",kind); state.addProperty("message",message.substring(0,Math.min(1500,message.length())));
        Draft draft = session.draft; state.addProperty("ready",draft != null);
        state.addProperty("preview",draft == null ? "" : draft.content());
        state.addProperty("reviewedId",draft == null ? "" : draft.id());
        state.addProperty("reviewedSource",draft == null ? "" : draft.source());
        state.addProperty("reviewedFormat",draft == null ? "" : draft.format().name());
        JsonArray lines = new JsonArray();
        if (draft != null) {
            draft.summary().forEach(lines::add);
        }
        state.add("summary",lines); return state;
    }
    public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) { SESSIONS.remove(e.getEntity().getUUID()); }
    public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e) { SESSIONS.remove(e.getEntity().getUUID()); }
    public static void respawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent e) { SESSIONS.remove(e.getEntity().getUUID()); }
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e) { SESSIONS.clear(); }
}
