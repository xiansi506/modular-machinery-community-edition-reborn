package com.reborn.modularmachinery.client;

import com.google.gson.*;
import com.reborn.modularmachinery.network.*;
import com.reborn.modularmachinery.selection.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

/** Selection settings, export settings and paged read-only source preview. No file path comes from the client. */
public final class ConstructToolScreen extends Screen {
    private JsonObject state,options;
    private int tab,left,panel,row,scroll,waiting;
    private final Map<String,EditBox> fields=new LinkedHashMap<>();
    private String from="",to="";
    private boolean advanced,previewSource;
    /**
     * P01-02（0.121.47，0.121.49 返工）：与配方编辑器同一口径的本地编辑代次。控件改动与
     * 格式/模式/忽略空气等直接修改 options 的非文本入口都让 {@link #localEdits} 单调前进；
     * collect／切页／缩窗重建只搬运值、不推进它，因此"发送之后出现过本地修改"不会被收集
     * 动作抹掉。{@link #sentEdits} 是 {@link #send} 发包前记录的请求基线：回包到达时两者
     * 不等，这一包就只能更新 state（revision、选区计数等），不得用服务端旧 options 覆盖
     * 表单、也不得替作者换页。新请求发包时基线追平 {@link #localEdits}，其相符回复照常
     * 生效（界面不会永远"脏"）。建立控件与逐字回填由 {@link #programmatic} 屏蔽，不会把
     * 自己算成编辑。
     */
    private long localEdits, sentEdits;
    private boolean programmatic;
    private Map<String,String> pendingValues;
    private List<FormattedCharSequence> lines=List.of();
    private static Component tr(String key,Object... args){return Component.translatable("tool.modular_machinery_reborn."+key,args);}
    public ConstructToolScreen(JsonObject state,int tab){super(tr("title"));this.state=state;this.options=state.getAsJsonObject("options").deepCopy();this.tab=tab;}
    @Override public void resize(net.minecraft.client.Minecraft mc,int width,int height){collect();super.resize(mc,width,height);}
    public void update(JsonObject next,int nextTab){
        // P01-02（0.121.49 返工）：判定依据是“发送之后有没有出现过任何本地修改”——collect、
        // 切页、缩窗都不能消除这个事实。有的话这一包只更新 state（revision、选区计数等），
        // 不拿服务端旧 options 覆盖表单，也不替作者换页——否则超时后敲的新显示名称（哪怕已
        // 被收集进模型）会被旧值顶回去。没有新修改时行为完全不变（服务端 options/页面照旧生效）。
        boolean stale=localEdits!=sentEdits;
        if(stale)pendingValues=values(); else collect();
        boolean error="error".equals(next.get("kind").getAsString());state=next;
        if(!error&&!stale)options=next.getAsJsonObject("options").deepCopy();
        if(!error&&!stale)tab=Math.max(0,Math.min(2,nextTab));waiting=0;
        if(!stale)scroll=0;
        clearWidgets();init();
    }
    private Map<String,String> values(){
        Map<String,String> values=new LinkedHashMap<>();fields.forEach((key,box)->values.put(key,box.getValue()));return values;
    }
    private void collect(){
        fields.forEach((key,box)->{if(key.equals("from"))from=box.getValue();else if(key.equals("to"))to=box.getValue();else options.addProperty(key,box.getValue());});
    }
    public static int formTop(int height){return Math.min(72,Math.max(48,height-120));}
    public static int rowHeight(int height){return Math.max(20,Math.min(24,(height-formTop(height))/6));}
    /** 建立控件与逐字回填期间不算作者编辑；异常也要复位，免得一次失败的重建让界面永远“脏”。 */
    @Override protected void init(){
        boolean prior=programmatic;programmatic=true;
        try{initWidgets();}finally{programmatic=prior;}
    }
    private void initWidgets(){
        fields.clear();panel=Math.min(600,width-16);left=(width-panel)/2;row=rowHeight(height);
        int third=panel/3;
        button(0,24,third-2,tr("selection"),()->page(0));button(third,24,third-2,tr("exportSettings"),()->page(1));
        button(third*2,24,panel-third*2,tr(advanced?"simple":"advanced"),()->{collect();advanced=!advanced;clearWidgets();init();});
        if(tab==0){
            button(0,y(0),panel/2-2,tr("mode",tr("mode."+options.get("mode").getAsString())),()->cycle("mode",ConstructOptions.Mode.values()));
            button(panel/2+2,y(0),panel/2-2,tr("operation",tr("operation."+options.get("operation").getAsString())),()->cycle("operation",StructureSelection.Operation.values()));
            button(0,y(1),panel,tr("ignoreAir",tr(options.get("ignoreAir").getAsBoolean()?"on":"off")),()->{collect();options.addProperty("ignoreAir",!options.get("ignoreAir").getAsBoolean());edited();send(ConstructToolService.Action.APPLY);});
            button(0,y(2),third-2,tr("undo"),()->send(ConstructToolService.Action.UNDO)).active=waiting==0&&state.get("undo").getAsBoolean();
            button(third,y(2),third-2,tr("redo"),()->send(ConstructToolService.Action.REDO)).active=waiting==0&&state.get("redo").getAsBoolean();
            button(third*2,y(2),panel-third*2,tr("clear"),()->send(ConstructToolService.Action.CLEAR));
            field("from",from,y(3),128);field("to",to,y(4),256);
            button(0,y(5),panel/2-2,tr("replace"),()->{collect();options.getAsJsonObject("replacements").addProperty(from.trim(),to.trim());edited();send(ConstructToolService.Action.APPLY);});
            button(panel/2+2,y(5),panel/2-2,tr("resetReplacements",options.getAsJsonObject("replacements").size()),()->{collect();options.add("replacements",new JsonObject());edited();send(ConstructToolService.Action.APPLY);});
        }else if(tab==1&&!advanced){
            field("machineId",options.get("machineId").getAsString(),y(0),128);field("displayName",options.get("displayName").getAsString(),y(1),128);
            button(0,y(2),panel,tr("format",tr("format."+options.get("format").getAsString())),()->{collect();var values=ConstructOptions.Format.values();int next=(ConstructOptions.Format.valueOf(options.get("format").getAsString()).ordinal()+1)%values.length;options.addProperty("format",values[next].name());edited();clearWidgets();init();});
            button(0,y(3),panel,tr("preview"),()->send(ConstructToolService.Action.PREVIEW));
        }else if(tab==1){
            field("machineId",options.get("machineId").getAsString(),y(0),128);field("displayName",options.get("displayName").getAsString(),y(1),128);
            field("states",options.get("states").getAsString(),y(2),512);field("matchingNbt",options.get("matchingNbt").getAsString(),y(3),1024);field("previewNbt",options.get("previewNbt").getAsString(),y(4),1024);
            button(0,y(5),panel/2-2,tr("format",tr("format."+options.get("format").getAsString())),()->{collect();var values=ConstructOptions.Format.values();int next=(ConstructOptions.Format.valueOf(options.get("format").getAsString()).ordinal()+1)%values.length;options.addProperty("format",values[next].name());edited();clearWidgets();init();});
            button(panel/2+2,y(5),panel/2-2,tr("preview"),()->send(ConstructToolService.Action.PREVIEW));
        }else{
            String body=advanced||previewSource?state.get("preview").getAsString():tr("quickReview",state.get("count").getAsInt(),state.get("controller").getAsString(),options.get("displayName").getAsString(),options.get("machineId").getAsString()).getString();
            lines=font.split(Component.literal(body),panel-12);
            if(!advanced&&!previewSource){
                button(0,height-26,panel/2-2,tr("showCode"),()->{previewSource=true;clearWidgets();init();});
                button(panel/2+2,height-26,panel/2-2,tr("save"),()->send(ConstructToolService.Action.EXPORT)).active=waiting==0&&state.get("pages").getAsInt()>0;
            }else{
            int quarter=panel/4;
            button(0,height-26,quarter-2,tr("previous"),()->send(ConstructToolService.Action.PREVIOUS_PAGE));
            button(quarter,height-26,quarter-2,tr("next"),()->send(ConstructToolService.Action.NEXT_PAGE));
            button(quarter*2,height-26,quarter-2,tr("copyPage"),()->minecraft.keyboardHandler.setClipboard(state.get("preview").getAsString()));
            button(quarter*3,height-26,panel-quarter*3,tr("save"),()->send(ConstructToolService.Action.EXPORT)).active=waiting==0&&state.get("pages").getAsInt()>0;
            }
        }
        // P01-02：迟到回包期间敲进去的内容，逐字放回刚建好的控件；模型仍是旧 options，
        // 所以它们依旧“未收集”，下一次真实操作才会并进去。
        if(pendingValues!=null){restore(pendingValues);pendingValues=null;}
    }
    /** 逐字回填；调用点在 init 的 programmatic 区间内，不会被记成新的本地编辑。 */
    private void restore(Map<String,String> saved){
        saved.forEach((key,value)->{EditBox box=fields.get(key);if(box!=null)box.setValue(value);});
        if(saved.containsKey("from"))from=saved.get("from");
        if(saved.containsKey("to"))to=saved.get("to");
    }
    /** 作者真的改了控件：记一代本地编辑；程序化回填不计数。 */
    private void edited(){if(!programmatic)localEdits++;}
    private int y(int r){return formTop(height)+r*row;}
    private void page(int next){collect();tab=next;scroll=0;clearWidgets();init();}
    private <E extends Enum<E>> void cycle(String key,E[] values){collect();int index=0;for(int i=0;i<values.length;i++)if(values[i].name().equals(options.get(key).getAsString()))index=i;options.addProperty(key,values[(index+1)%values.length].name());edited();send(ConstructToolService.Action.APPLY);}
    private Button button(int x,int y,int w,Component label,Runnable action){Button b=addRenderableWidget(Button.builder(label,ignored->action.run()).bounds(left+x,y,w,20).build());b.active=waiting==0;return b;}
    private void field(String key,String value,int y,int max){int label=Math.min(150,panel/3);EditBox box=addRenderableWidget(new EditBox(font,left+label,y+1,panel-label,18,tr(key)));box.setMaxLength(max);box.setValue(value);box.setResponder(v->edited());box.active=waiting==0;
        if(key.equals("states")||key.endsWith("Nbt"))box.setTooltip(Tooltip.create(tr("fieldsHelp")));fields.put(key,box);}
    /**
     * P01-02（0.121.49 返工）：请求基线，由真实发送入口在发包前调用。离线夹具没有 Netty
     * 通道，在受控发送边界调用这同一个方法记录基线——与 {@link #send} 的发包点前状态一致，
     * 而不是反射把保护字段写成恰好通过的值。
     */
    void sentBaseline(){sentEdits=localEdits;}
    private void send(ConstructToolService.Action action){
        if(waiting>0)return;collect();sentBaseline();ModNetwork.CHANNEL.sendToServer(new ConstructToolActionPacket(new net.minecraft.resources.ResourceLocation(state.get("dimension").getAsString()),action,state.get("revision").getAsLong(),options.toString()));waiting=1;clearWidgets();init();
    }
    @Override public void tick(){
        fields.values().forEach(EditBox::tick);
        if(minecraft.player==null||minecraft.level==null||!minecraft.level.dimension().location().toString().equals(state.get("dimension").getAsString())){onClose();return;}
        if(waiting>0&&++waiting>100){waiting=0;state.addProperty("kind","error");state.addProperty("message","Request timed out; reopen the tool panel");clearWidgets();init();}
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        renderBackground(g);g.drawCenteredString(font,title,width/2,7,0xffffff);
        String notice=state.get("message").getAsString();
        Component header=notice.isBlank()?tr("summary",state.get("count").getAsInt(),state.get("bounds").getAsString()):tr(state.get("kind").getAsString(),notice);
        g.drawString(font,font.plainSubstrByWidth(header.getString(),panel),left,49,notice.isBlank()?0xffffff:0xffcc88);
        if(tab==2){
            g.drawString(font,font.plainSubstrByWidth(tr("target",state.get("filename").getAsString(),state.get("page").getAsInt()+1,state.get("pages").getAsInt()).getString(),panel),left,62,0xcccccc);
            int visible=Math.max(1,(height-110)/10);scroll=Math.max(0,Math.min(scroll,Math.max(0,lines.size()-visible)));
            g.fill(left,77,left+panel,height-32,0xc0000000);
            for(int i=0;i<visible&&i+scroll<lines.size();i++)g.drawString(font,lines.get(i+scroll),left+4,80+i*10,0xe0e0e0);
        }else{
            if(height>=200)g.drawString(font,font.plainSubstrByWidth(tr("origin",state.get("controller").getAsString()).getString(),panel),left,61,0xaaaaaa);
            for(var e:fields.entrySet())g.drawString(font,font.plainSubstrByWidth(tr(e.getKey()).getString(),Math.min(145,panel/3)-4),left,e.getValue().getY()+5,0xdddddd);
            if(height>=230)g.drawString(font,font.plainSubstrByWidth(tr(!advanced?(tab==0?"quickSelectionHelp":"quickExportHelp"):(tab==0?"selectionHelp":"fieldsHelp")).getString(),panel),left,height-12,0x999999);
        }
        super.render(g,mouseX,mouseY,partial);
        if(!notice.isBlank()&&mouseX>=left&&mouseX<left+panel&&mouseY>=48&&mouseY<60)g.renderTooltip(font,header,mouseX,mouseY);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){if(tab==2){scroll-=delta*3;return true;}return super.mouseScrolled(x,y,delta);}
}
