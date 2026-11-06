package com.reborn.modularmachinery.selection;

import com.google.gson.*;
import com.reborn.modularmachinery.item.ModItems;
import com.reborn.modularmachinery.machine.MachineDirectory;
import com.reborn.modularmachinery.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.Path;

/** All authoring actions are checked on the server. Packets never supply world positions or file paths. */
public final class ConstructToolService {
    public enum Action { APPLY, UNDO, REDO, CLEAR, PREVIEW, EXPORT, PREVIOUS_PAGE, NEXT_PAGE }
    private ConstructToolService(){}
    public static boolean allowed(ServerPlayer player){
        return player!=null&&player.isCreative()&&player.hasPermissions(2)
            &&(player.getMainHandItem().is(ModItems.CONSTRUCT_TOOL.get())||player.getOffhandItem().is(ModItems.CONSTRUCT_TOOL.get()));
    }
    public static void open(ServerPlayer player,BlockPos controller){
        if(!allowed(player))return;ConstructSession session=ServerSelections.session(player);
        if(controller!=null){
            session.controller(controller);
            ConstructOptions previous=session.options();
            if(session.needsIdentitySuggestion(controller)) {
                var entity=player.level().getBlockEntity(controller);
                var id=entity instanceof com.reborn.modularmachinery.block.MachineControllerBlockEntity machine
                        ? machine.previewMachineId() : null;
                String suggested=id==null ? "modular_machinery_reborn:machine_"+java.util.UUID.randomUUID().toString().replace("-","") : id.toString();
                String name=id==null ? previous.displayName() : com.reborn.modularmachinery.machine.MachineRegistry.byId(id)
                        .map(com.reborn.modularmachinery.machine.MachineDefinition::localizedName).orElse(previous.displayName());
                session.suggestMachineIdentity(controller,suggested,name);
            }
        }
        sync(player,true,controller==null?0:1,"","");
    }
    public static void click(ServerPlayer player,BlockPos pos){
        if(!allowed(player))return;
        try{ServerSelections.session(player).click(player.level(),pos);sync(player,false,0,"","");}
        catch(RuntimeException error){player.displayClientMessage(Component.translatable("tool.modular_machinery_reborn.error",error.getMessage()),false);sync(player,false,0,"","");}
    }
    public static void handle(ServerPlayer player,Action action,long revision,String json){
        if(!allowed(player))return;
        ConstructSession session=ServerSelections.session(player);
        if(revision!=session.revision()){sync(player,true,0,"error","Tool state changed; reopen the panel");return;}
        try{
            int tab=0;
            switch(action){
                case APPLY -> session.options(ConstructOptions.read(JsonParser.parseString(json).getAsJsonObject()));
                case UNDO -> session.undo();
                case REDO -> session.redo();
                case CLEAR -> session.clear();
                case PREVIEW -> {
                    long now=player.level().getGameTime();if(now<session.nextExpensiveTick)throw new IllegalArgumentException("Please wait before another preview");
                    session.nextExpensiveTick=now+5;checkControllerRange(player,session);
                    session.options(ConstructOptions.read(JsonParser.parseString(json).getAsJsonObject()));
                    session.draft=ConstructExport.prepare(player.level(),session,player.getGameProfile().getName());tab=2;
                }
                case EXPORT -> {
                    checkControllerRange(player,session);
                    Path saved=ConstructExport.save(player.level(),session,MachineDirectory.directory().resolveSibling("exports"));
                    sync(player,true,1,"saved",saved.getFileName().toString());return;
                }
                case PREVIOUS_PAGE -> {session.previewPage=Math.max(0,session.previewPage-1);tab=2;}
                case NEXT_PAGE -> {if(session.draft!=null)session.previewPage=Math.min(session.draft.pages()-1,session.previewPage+1);tab=2;}
            }
            // P01-06（0.121.39）：成功的翻页不改变选区，省略全量坐标包（32768 个方块位置
            // 未压缩编码约 393240 字节），只发送实际页状态。初次 open、选区变更（APPLY/UNDO/
            // REDO/CLEAR 与点击）、PREVIEW/EXPORT、生命周期清理与过期 revision 纠正仍走
            // 完整同步；失败/拒绝路径保留既有纠正语义，不因操作名是翻页就跳过同步。
            sync(player,true,tab,"","",action==Action.PREVIOUS_PAGE||action==Action.NEXT_PAGE);
        }catch(Exception error){sync(player,true,action==Action.EXPORT?2:1,"error",String.valueOf(error.getMessage()));}
    }
    private static void checkControllerRange(ServerPlayer player,ConstructSession session){
        BlockPos origin=session.controller();
        if(origin==null||player.distanceToSqr(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5)>64*64)throw new IllegalArgumentException("Click a controller within 64 blocks");
    }
    public static JsonObject snapshot(ConstructSession session,String dimension,String kind,String message){
        JsonObject state=new JsonObject();state.addProperty("dimension",dimension);state.addProperty("revision",session.revision());state.add("options",session.options().json());
        state.addProperty("count",session.selection.size());state.addProperty("bounds",session.selection.boundsText());
        state.addProperty("undo",session.corner()!=null||session.selection.canUndo());state.addProperty("redo",session.selection.canRedo());
        state.addProperty("controller",session.controller()==null?"-":session.controller().toShortString());
        if(session.corner()!=null){JsonArray corner=new JsonArray();corner.add(session.corner().getX());corner.add(session.corner().getY());corner.add(session.corner().getZ());state.add("corner",corner);}
        state.addProperty("kind",kind);state.addProperty("message",message.substring(0,Math.min(message.length(),512)));
        state.addProperty("page",session.previewPage);state.addProperty("pages",session.draft==null?0:session.draft.pages());
        state.addProperty("filename",session.draft==null?"":session.draft.filename());state.addProperty("preview",session.draft==null?"":session.draft.page(session.previewPage));return state;
    }
    public static void sync(ServerPlayer player,boolean open,int tab,String kind,String message){
        sync(player,open,tab,kind,message,false);
    }

    /**
     * 发送工具状态；{@code unchangedSelection} 为 true 时跳过全量选区坐标包，只发送
     * 状态快照（P01-06：仅供不改变选区的成功翻页使用——页码已在状态包内）。选区坐标
     * 包的编码格式不变（见 {@code SelectionSyncPacket.encode}），没有新增增量格式或缓存。
     */
    public static void sync(ServerPlayer player,boolean open,int tab,String kind,String message,boolean unchangedSelection){
        ConstructSession session=ServerSelections.session(player);
        if(!unchangedSelection) SelectionSyncPacket.sendTo(player,session.selection.positions());
        ConstructToolStatePacket.sendTo(player,snapshot(session,player.level().dimension().location().toString(),kind,message),open,tab);
    }
}
