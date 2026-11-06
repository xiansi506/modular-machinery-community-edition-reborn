package com.reborn.modularmachinery.client;

import com.google.gson.*;
import com.reborn.modularmachinery.authoring.*;
import com.reborn.modularmachinery.network.*;
import com.reborn.modularmachinery.recipe.LiteralNbt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Local draft forms; a server validation ticket is needed before any file can be written. */
public final class RecipeEditorScreen extends Screen {
    private JsonObject root, state;
    private final UUID token;
    private final String dimension;
    private final List<String> machines = new ArrayList<>();
    private final Set<String> absent = new HashSet<>();
    private final Map<String,EditBox> fields = new LinkedHashMap<>();
    private final Map<String,MultiLineEditBox> multiline = new LinkedHashMap<>();
    private String recipeId="custom:recipe", message="", checkedSource="", checkedId="";
    /**
     * P01-01（0.121.41 返工，0.121.42 续修）：原文页的逐字草稿。原文页的内容只有在语法与
     * 表单结构都通过后才发布成 {@link #root}；失败时保留这里的原文与旧模型，重建（缩窗、
     * 服务端回执刷新）也从草稿还原，不用格式化 JSON 冒充逐字保留。
     *
     * <p>0.121.42：{@link #sourceDraftModel} 记录草稿被接受时的 root 文本。草稿只在它仍是
     * 当前 root 的最后一次原文提交时有效——表单侧任何修改入口（删除/添加/类型与资源选择/
     * 拖入等）改变 root 后，旧原文立即失效，切回原文页显示当前模型而不是复活旧条目。
     */
    private String sourceDraft, sourceDraftModel;
    /**
     * P01-02（0.121.47，0.121.49 返工）：本地编辑代次。控件的每一次真实改动（含格式按钮等
     * 直接修改模型的非文本入口）让 {@link #localEdits} 单调前进一格；collect／切页／缩窗重建
     * 只搬运值、不推进它，因此"发送之后出现过本地修改"这个事实不会被收集动作抹掉。
     * {@link #sentEdits} 是 {@link #send} 在发包前记录的请求基线：回包到达时两者不等，就说明
     * 这份回复针对的不是当前草稿——只能更新服务端 revision 与提示，不得重建表单、切预览页
     * 或恢复导出。新请求发包时基线追平 {@link #localEdits}，其相符回复照常生效（界面不会
     * 永远"脏"）。程序化回填（建立控件、回包后逐字还原、缩窗还原）由 {@link #programmatic}
     * 屏蔽——否则每次 rebuild 都会把自己算成一次编辑。
     */
    private long localEdits, sentEdits;
    private boolean programmatic;
    /** 回包时被保留的未收集控件内容，rebuild 后逐字回填（不触发 {@link #edited()}）。 */
    private Map<String,String> pendingValues;
    private RecipeDocument.Format format=RecipeDocument.Format.JSON, checkedFormat;
    private int tab, part, selected, left, panel, label, waiting, scroll;
    private boolean previewSource;
    private boolean advanced;
    private boolean jeiLayout;
    private int inputPage,outputPage;
    public record GhostSlot(boolean input,int index,Rect2i area) {}
    private final List<GhostSlot> canvasSlots=new ArrayList<>();
    private List<FormattedCharSequence> lines=List.of();
    private Button exportButton;
    private final List<ItemStack> previewItems=new ArrayList<>();
    public static Component tr(String key,Object... args) { return Component.translatable("editor.modular_machinery_reborn."+key,args); }
    public RecipeEditorScreen(JsonObject state) {
        super(tr("title")); this.state=state; token=UUID.fromString(state.get("token").getAsString()); dimension=state.get("dimension").getAsString();
        root=RecipeDocument.read(state.get("source").getAsString());
        if(state.has("suggestedId"))recipeId=state.get("suggestedId").getAsString();
        state.getAsJsonArray("machines").forEach(v->machines.add(v.getAsString())); state.getAsJsonArray("absent").forEach(v->absent.add(v.getAsString()));
    }
    public static void accept(JsonObject state, boolean open) {
        Minecraft mc=Minecraft.getInstance();
        if (mc.player==null || mc.level==null) return;
        if (open) { if (mc.level.dimension().location().toString().equals(state.get("dimension").getAsString())) mc.setScreen(new RecipeEditorScreen(state)); }
        else if (mc.screen instanceof RecipeEditorScreen screen && screen.token.toString().equals(state.get("token").getAsString())) screen.update(state);
    }
    private void update(JsonObject next) {
        // P01-02（0.121.49 返工）：判定依据是“发送之后有没有出现过任何本地修改”。collect／
        // 切页／缩窗把值并进模型并不能消除这个事实——那份回包针对的仍是发送时的旧草稿，
        // 只能更新服务端 revision 与提示，不能重建表单/切预览，也不能把新草稿标成 ready。
        // 没有新修改时行为与 0.121.47 前完全一致（valid 且内容相符 → 切预览并开导出）。
        boolean stale = localEdits != sentEdits;
        if (stale) pendingValues = values();
        state=next; waiting=0;
        String kind=state.get("kind").getAsString();
        if (kind.equals("valid") && !stale && reviewMatches(state,recipeId,root.toString(),format)) {
            checkedSource=state.get("reviewedSource").getAsString(); checkedId=recipeId; checkedFormat=format; tab=3; scroll=0; message=tr("valid").getString();previewItems.clear();
            if(root.has("requirements")&&root.get("requirements").isJsonArray())for(JsonElement element:root.getAsJsonArray("requirements"))try{
                JsonObject req=element.getAsJsonObject();if(RecipeDocument.kind(req).equals("item")){
                    var parsed=(com.reborn.modularmachinery.recipe.ItemRequirement)com.reborn.modularmachinery.recipe.MachineRequirementParser.parse(req,RecipeDocument.id(recipeId));
                    ItemStack stack=parsed.ioType()==com.reborn.modularmachinery.recipe.IOType.OUTPUT?parsed.outputStack().copy():com.reborn.modularmachinery.recipe.LegacyIngredients.firstStack(parsed.ingredient()).copy();
                    if(parsed.displayTag()!=null)stack.setTag(parsed.displayTag());else if(parsed.nbt()!=null)stack.setTag(parsed.nbt().toTag());stack.setCount(parsed.minAmount());previewItems.add(stack);
                }
            }catch(RuntimeException ignored){/* Server source/summary remains available if local tags lag behind a reload. */}
        }
        else if (kind.equals("saved")) { checkedSource=""; message=tr("saved",state.get("message").getAsString()).getString(); }
        else if(kind.equals("valid")){dirty();message=tr("needsValidation").getString();}
        else message=state.get("message").getAsString();
        // 屏幕上的草稿未被任何一方验证过：导出必须保持关闭，提示也按“待校验”给。
        if (stale) dirty();
        rebuild();
    }
    public static boolean reviewMatches(JsonObject state,String id,String source,RecipeDocument.Format format){return state.has("reviewedId")&&id.equals(state.get("reviewedId").getAsString())&&source.equals(state.get("reviewedSource").getAsString())&&format.name().equals(state.get("reviewedFormat").getAsString());}
    private JsonArray requirements() {
        if (!root.has("requirements")) root.add("requirements",new JsonArray());
        return root.getAsJsonArray("requirements");
    }
    private JsonObject current() { JsonArray array=requirements(); selected=Math.max(0,Math.min(selected,array.size()-1)); return array.isEmpty()?null:array.get(selected).getAsJsonObject(); }
    private static String text(JsonObject json,String key) { if (!json.has(key)) return ""; JsonElement v=json.get(key); return v.isJsonPrimitive()?v.getAsString():v.toString(); }
    private static String editable(JsonElement value){String pretty=RecipeDocument.JSON.toJson(value);return pretty.length()<=RecipeDocument.MAX_SOURCE?pretty:value.toString();}
    private Map<String,String> values() { Map<String,String> values=new LinkedHashMap<>(); fields.forEach((key,box)->values.put(key,box.getValue())); multiline.forEach((key,box)->values.put(key,box.getValue())); return values; }
    /**
     * 0.121.42：原文草稿的身份校验。草稿只在它是当前 root 的最后一次原文提交时有效；
     * 表单侧任何入口改了 root 后，旧原文立即作废，不能再覆盖新模型（删除被撤销的回归）。
     * 在 collect 与 init 前调用，覆盖删除/添加/类型与资源选择/拖入/切页等全部修改路径。
     */
    private void syncDraft() {
        if (sourceDraft!=null && (sourceDraftModel==null || !sourceDraftModel.equals(root.toString()))) {
            sourceDraft=null; sourceDraftModel=null;
        }
    }
    private boolean collect() {
        try {
            syncDraft();
            Map<String,String> values=values();
            if (tab==0 && !advanced && fields.containsKey("seconds")) {
                try{root=RecipeQuickEdit.overview(root,values.get("seconds"),values.get("quickEnergy"));}
                catch(RuntimeException error){message=tr(String.valueOf(error.getMessage()).contains("64 requirements")?"tooManyRequirements":"invalidQuick").getString();return false;}
            } else if (tab==0 && fields.containsKey("id")) {
                String id=values.remove("id").trim(); RecipeDocument.id(id);
                JsonObject patched=RecipeForm.root(root,values); root=patched; recipeId=id; sourceDraft=null; sourceDraftModel=null;
            } else if (tab==1 && current()!=null && RecipeDocument.TYPES.contains(RecipeDocument.kind(current()))) {
                JsonObject patched=RecipeForm.requirement(current(),values); requirements().set(selected,patched); sourceDraft=null; sourceDraftModel=null;
            } else if (tab==2 && multiline.containsKey("source")) {
                // P01-01（0.121.41 返工）：先在临时变量里解析并做表单结构校验，通过后才发布
                // root。失败时 root（旧表单模型）和原文框内容都不动，消息给出可定位的错误；
                // 校验只覆盖表单真正依赖的结构，不要求满足完整服务端语义。
                String source=multiline.get("source").getValue();
                JsonObject parsed=RecipeDocument.read(source);
                String structureError=RecipeDocument.formStructureError(parsed);
                if (structureError!=null) { message=structureError; return false; }
                root=parsed; sourceDraft=source; sourceDraftModel=parsed.toString();
            }
            return true;
        } catch (RuntimeException error) { message=String.valueOf(error.getMessage()); return false; }
    }
    private void change(Runnable action) { if (waiting==0 && collect()) { action.run(); scroll=0; rebuild(); } }
    private void rebuild() { clearWidgets(); init(); }
    @Override public void resize(Minecraft mc,int width,int height) { if (!collect()) { /* Preserve malformed source while resizing. */ savedValues=values(); } super.resize(mc,width,height); }
    private Map<String,String> savedValues;
    public static int top(int height) { return Math.min(58,Math.max(44,height-142)); }
    public static int rowHeight(int height) { return Math.max(20,Math.min(24,(height-top(height)-68)/6)); }
    private int y(int row) { return top(height)+row*rowHeight(height); }
    /**
     * P01-02：建立控件期间的一次 {@code setValue} 不是作者在打字。整段 init 在
     * {@link #programmatic} 下运行，异常时也一定复位，避免一次失败的重建让编辑器永远“脏”。
     */
    @Override protected void init() {
        boolean prior=programmatic; programmatic=true;
        try { initWidgets(); } finally { programmatic=prior; }
    }
    private void initWidgets() {
        // 重建前先把原文页当前内容收进草稿：init 会被缩窗、回执刷新和切页复用，若从 root
        // 重新格式化会把作者刚敲的逐字文本冲掉（失败切页后尤其不能发生）。收进后立即做身份
        // 校验：表单侧已改过 root 的旧草稿在这里失效，不会随重建复活。
        if (tab==2 && multiline.containsKey("source")) sourceDraft=multiline.get("source").getValue();
        syncDraft();
        fields.clear(); multiline.clear();
        int sidebar=jeiLayout?Math.min(180,Math.max(104,width/4)):0;
        panel=Math.min(Math.max(0,width-16),Math.max(200,Math.min(620,width-sidebar-24)));
        left=jeiLayout?Math.max(8,(width-sidebar-panel)/2):(width-panel)/2;
        label=Math.min(140,panel/3);
        canvasSlots.clear();
        if(advanced){
            int fifth=panel/5;
            for(int i=0;i<4;i++){int page=i;button(i*fifth,25,fifth-2,tr("tab."+i),()->change(()->tab=page));}
            button(fifth*4,25,panel-fifth*4,tr("simple"),()->change(()->{advanced=false;tab=0;}));
        }else{
            button(0,25,panel/2-2,tr(tab==0?"canvas":"backCanvas"),()->change(()->tab=0));
            button(panel/2+2,25,panel/2-2,tr("advanced"),()->change(()->{advanced=true;tab=0;}));
        }
        if (tab==0&&!advanced) {
            initCanvas();
        } else if (tab==0) {
            field("id",recipeId,0); field("machine",text(root,"machine"),1);
            button(0,y(2),panel,tr("machineNext",machines.size()),()->pick(machines,text(root,"machine"),value->root.addProperty("machine",value)));
            field("recipeTime",root.has("recipeTime")?text(root,"recipeTime"):"100",3); field("max-parallelism",text(root,"max-parallelism"),4);
            button(0,y(5),panel,tr("format",format.name()),()->change(()->{format=format==RecipeDocument.Format.JSON?RecipeDocument.Format.KUBEJS:RecipeDocument.Format.JSON;edited();}));
        } else if (tab==1) {
            // P01-01（0.121.39，0.121.41 返工）：切到表单页前校验表单控件真正依赖的结构
            // （requirements 数组、条目对象、type 非空字符串、已知需求的布尔字段）。原文页
            // 正常路径已在 collect() 发布 root 之前拦截（失败保留原文草稿与旧模型）；这里
            // 是进入表单模型前的最后一道防线，覆盖非原文来源的 root（如服务端初始草稿）。
            // 结构不符就留在原文页显示可定位错误，错误不走未捕获异常路径。
            String structureError = RecipeDocument.formStructureError(root);
            if (structureError != null) { tab = 2; message = structureError; }
        }
        if (tab==1) {
            int fifth=panel/5;
            if(!advanced){
                button(0,y(0),panel/2-2,tr("backCanvas"),()->change(()->tab=0));
                button(panel/2+2,y(0),panel/2-2,tr("remove"),()->change(()->{if(current()!=null)requirements().remove(selected);tab=0;edited();}));
            }else{
            button(0,y(0),fifth-2,tr("previous"),()->change(()->selected=Math.max(0,selected-1)));
            button(fifth,y(0),fifth-2,tr("next"),()->change(()->selected=Math.min(requirements().size()-1,selected+1)));
            button(2*fifth,y(0),fifth-2,tr("add"),()->change(()-> { if(requirements().size()<RecipeDocument.MAX_REQUIREMENTS) {requirements().add(RecipeDocument.requirement("item")); selected=requirements().size()-1;} }));
            button(3*fifth,y(0),fifth-2,tr("remove"),()->change(()-> { if(current()!=null)requirements().remove(selected); }));
            button(4*fifth,y(0),panel-4*fifth,tr("part",part+1),()->change(()->part=(part+1)%3));
            }
            JsonObject req=current();
            if (req!=null && RecipeDocument.TYPES.contains(RecipeDocument.kind(req))) {
                String kind=RecipeDocument.kind(req);
                if (part==0) {
                    button(0,y(1),panel/2-2,tr("kind",tr("resource."+kind).getString(),absent.contains(kind)?"*":""),()->change(()-> {
                        minecraft.setScreen(new RecipeResourcePicker(this,RecipeDocument.TYPES,"",value->{JsonObject next=RecipeDocument.requirement(value);next.add("io-type",current().get("io-type").deepCopy());requirements().set(selected,next);edited();},value->tr("resource."+value)));
                    })).setTooltip(Tooltip.create(tr("kindHelp")));
                    button(panel/2+2,y(1),panel/2-2,tr("io",tr("direction."+text(req,"io-type")).getString()),()->change(()->current().addProperty("io-type",text(current(),"io-type").equals("input")?"output":"input")));
                    String key=RecipeDocument.resourceKey(kind);
                    if(!key.isEmpty())field(key,text(req,key),2); else if(kind.equals("compressed_air"))field("pressure",text(req,"pressure"),2);
                    String amountKey=RecipeDocument.amountKey(kind), value=text(req,amountKey);
                    if(value.isEmpty()) value=kind.equals("ember")?text(req,"ember"):kind.equals("compressed_air")?text(req,"amount"):"1";
                    field(amountKey,value,3);
                    button(0,y(4),panel/2-2,tr("perTick",RecipeDocument.perTick(req)?"✓":"–"),()->change(()->RecipeDocument.perTick(current(),!RecipeDocument.perTick(current())))).active=waiting==0&&!List.of("energy","item").contains(kind);
                    button(panel/2+2,y(4),panel/2-2,tr("search"),()->search(kind));
                    if(advanced)button(0,y(5),panel,tr("hand"),()->change(()-> {if(minecraft.player!=null)dropItem(heldIngredient());}));
                    else button(0,y(5),panel,tr("moreOptions"),()->change(()->{advanced=true;part=1;}));
                } else if(part==1) {
                    if(List.of("item","fluid","gas").contains(kind))field("chance",text(req,"chance"),1);
                    field("selector-tag",text(req,"selector-tag"),2);
                    if(kind.equals("item") || List.of("aura","will","mana","lifeessence","ember").contains(kind)) {
                        String min=kind.equals("item")?"minAmount":kind.equals("aura")?"min-aura":"min";
                        String max=kind.equals("item")?"maxAmount":kind.equals("aura")?"max-aura":"max";
                        field(min,text(req,min),3); field(max,text(req,max),4);
                    }
                    button(0,y(5),panel,tr("fixed",req.has("parallelize-unaffected")&&req.get("parallelize-unaffected").getAsBoolean()?"✓":"–"),()->change(()->current().addProperty("parallelize-unaffected",!(current().has("parallelize-unaffected")&&current().get("parallelize-unaffected").getAsBoolean()))));
                } else if(kind.equals("item")) {
                    multi("nbt",req.has("nbt")?editable(req.get("nbt")):"",y(1)+12,Math.max(24,rowHeight(height)*2-14));
                    multi("nbt-display",req.has("nbt-display")?editable(req.get("nbt-display")):"",y(3)+12,Math.max(24,rowHeight(height)*2-14));
                }
            } else if(req!=null) button(0,y(2),panel,tr("rawHelp"),()->change(()->tab=2));
        }
        if(tab==2) {
            // 草稿失效后从当前 root 重新生成，并把这份文本记为新的身份基线：之后的编辑
            // 只改草稿不改 root，直到下一次成功提交。
            if(sourceDraft==null) sourceDraftModel=root.toString();
            multi("source",sourceDraft!=null?sourceDraft:editable(root),top(height)+12,Math.max(35,height-top(height)-64));
            // 原文页每次编辑都留在草稿里，供失败切页与重建（缩窗/回执刷新）逐字还原。
            multiline.get("source").setValueListener(value->{if(!programmatic){sourceDraft=value;edited();}});
            button(0,height-49,panel/2-2,tr("paste"),()->{try{String source=minecraft.keyboardHandler.getClipboard();RecipeDocument.read(source);multiline.get("source").setValue(source);}catch(RuntimeException error){message=String.valueOf(error.getMessage());}});
            button(panel/2+2,height-49,panel/2-2,tr("copy"),()->minecraft.keyboardHandler.setClipboard(multiline.get("source").getValue()));
        }
        if(tab==3) {
            String body=previewSource?state.get("preview").getAsString():tr("baseHelp").getString()+"\n";
            if(!previewSource)for(JsonElement line:state.getAsJsonArray("summary"))body+=line.getAsString()+"\n";
            lines=font.split(Component.literal(body),panel-12);
            button(0,height-49,panel/2-2,tr(previewSource?"summary":"source"),()->{previewSource=!previewSource;scroll=0;rebuild();});
            button(panel/2+2,height-49,panel/2-2,tr("copy"),()->minecraft.keyboardHandler.setClipboard(state.get("preview").getAsString()));
        }
        button(0,height-25,panel/2-2,tr("validate"),()->send(RecipeEditorService.Action.VALIDATE));
        exportButton=button(panel/2+2,height-25,panel/2-2,tr("export"),()->send(RecipeEditorService.Action.EXPORT));exportButton.active=waiting==0&&state.get("ready").getAsBoolean()&&checkedId.equals(recipeId)&&checkedSource.equals(root.toString())&&checkedFormat==format;
        if(savedValues!=null) { restore(savedValues); savedValues=null; }
        // P01-02：迟到回包期间作者新输入的内容，逐字放回刚建好的控件里；控件模型仍是旧 root，
        // 所以这些值仍旧“未收集”，下一次真正的操作（切页/校验/导出）才会把它们并进模型。
        if(pendingValues!=null) { restore(pendingValues); pendingValues=null; }
    }
    /** 逐字回填控件；调用点都在 init 的 {@link #programmatic} 区间内，不会算成新的本地编辑。 */
    private void restore(Map<String,String> saved) {
        saved.forEach((key,value)-> {
            EditBox box=fields.get(key); if(box!=null)box.setValue(value);
            MultiLineEditBox source=multiline.get(key); if(source!=null)source.setValue(value);
        });
        if(multiline.containsKey("source")&&saved.containsKey("source"))sourceDraft=saved.get("source");
    }
    private void initCanvas() {
        button(0,48,panel,tr("chooseMachine",choiceLabel(text(root,"machine")).getString()),
                ()->pick(machines,"",value->root.addProperty("machine",value)));
        String duration;
        try{duration=RecipeQuickEdit.seconds(root);}catch(RuntimeException error){duration=text(root,"recipeTime");}
        canvasField("seconds",duration,0,panel/2-4,73);
        String energy=RecipeQuickEdit.energy(root);
        if(energy!=null)canvasField("quickEnergy",energy,panel/2+4,panel/2-4,73);
        else button(panel/2+4,72,panel/2-4,tr("advancedEnergy"),()->change(()->{advanced=true;tab=1;part=0;
            selected=RecipeQuickEdit.indices(root,true).stream().filter(i->RecipeDocument.kind(requirements().get(i).getAsJsonObject()).equals("energy")).findFirst().orElse(0);}));
        int columns=Math.max(2,Math.min(6,(panel/2-8)/22)),rows=Math.max(1,Math.min(3,(height-160)/22)),capacity=columns*rows;
        for(boolean input:new boolean[]{true,false}){
            int offset=input?0:panel/2+4,half=panel/2-4;
            List<Integer> indices=RecipeQuickEdit.visibleIndices(root,input);
            int last=indices.size()/capacity;
            int page=Math.min(input?inputPage:outputPage,last);
            if(input)inputPage=page;else outputPage=page;
            button(offset+half-66,94,32,tr("other"),()->addCanvasResource(input)).setHeight(14);
            Button previous=button(offset+half-32,94,15,Component.literal("<"),()->change(()->{if(input)inputPage=Math.max(0,inputPage-1);else outputPage=Math.max(0,outputPage-1);}));previous.setHeight(14);previous.active=waiting==0&&page>0;
            Button next=button(offset+half-15,94,15,Component.literal(">"),()->change(()->{if(input)inputPage=Math.min(last,inputPage+1);else outputPage=Math.min(last,outputPage+1);}));next.setHeight(14);next.active=waiting==0&&page<last;
            for(int i=0;i<capacity;i++){
                int position=page*capacity+i,index=position<indices.size()?indices.get(position):-1;
                canvasSlots.add(new GhostSlot(input,index,new Rect2i(left+offset+(i%columns)*22,110+(i/columns)*22,20,20)));
            }
        }
        if(machines.isEmpty()&&message.isEmpty())message=tr("noMachines").getString();
    }
    private void canvasField(String key,String value,int x,int width,int y){
        int labelWidth=Math.min(48,width/2);
        EditBox box=addRenderableWidget(new EditBox(font,left+x+labelWidth,y,width-labelWidth,18,tr(key)));
        box.setMaxLength(40);box.setValue(value);box.setResponder(v->edited());box.active=waiting==0;
        box.setTooltip(Tooltip.create(tr(key+"Help")));fields.put(key,box);
    }
    private Component choiceLabel(String value){
        ResourceLocation id=ResourceLocation.tryParse(value);
        if(id==null)return Component.literal(value);
        if(machines.contains(value))return com.reborn.modularmachinery.machine.MachineRegistry.byId(id,true)
                .map(com.reborn.modularmachinery.machine.MachineDefinition::displayName).orElse(Component.literal(value));
        if(ForgeRegistries.ITEMS.containsKey(id))return ForgeRegistries.ITEMS.getValue(id).getDescription();
        if(ForgeRegistries.FLUIDS.containsKey(id))return new FluidStack(ForgeRegistries.FLUIDS.getValue(id),1).getDisplayName();
        return Component.literal(value);
    }
    private void addCanvasResource(boolean input){
        if(waiting!=0||!collect())return;
        minecraft.setScreen(new RecipeResourcePicker(this,RecipeDocument.TYPES.stream().filter(kind->!absent.contains(kind)).toList(),"",kind->{
            try{selected=RecipeQuickEdit.add(root,kind,input);part=0;tab=1;edited();}
            catch(RuntimeException error){message=String.valueOf(error.getMessage());}
        },kind->tr("resource."+kind)));
    }
    private void chooseCanvasItem(GhostSlot slot){
        List<String> ids=ForgeRegistries.ITEMS.getKeys().stream().filter(id->!id.equals(new ResourceLocation("minecraft:air"))).map(Object::toString).toList();
        pick(ids,"",id->{try{selected=RecipeQuickEdit.add(root,"item",slot.input());requirements().get(selected).getAsJsonObject().addProperty("item",id);}
            catch(RuntimeException error){message=String.valueOf(error.getMessage());}});
    }
    private GhostSlot slotAt(double x,double y){return canvasSlots.stream().filter(slot->{Rect2i a=slot.area();return x>=a.getX()&&y>=a.getY()&&x<a.getX()+a.getWidth()&&y<a.getY()+a.getHeight();}).findFirst().orElse(null);}
    private ItemStack heldIngredient(){
        if(minecraft.player==null)return ItemStack.EMPTY;
        ItemStack main=minecraft.player.getMainHandItem();
        return main.is(com.reborn.modularmachinery.item.ModItems.RECIPE_EDITOR.get())?minecraft.player.getOffhandItem():main;
    }
    private ItemStack canvasIcon(JsonObject req){
        try{
            String kind=RecipeDocument.kind(req);
            if(kind.equals("item")){
                var parsed=(com.reborn.modularmachinery.recipe.ItemRequirement)com.reborn.modularmachinery.recipe.MachineRequirementParser.parse(req,RecipeDocument.id(recipeId));
                ItemStack stack=parsed.ioType()==com.reborn.modularmachinery.recipe.IOType.OUTPUT?parsed.outputStack().copy():com.reborn.modularmachinery.recipe.LegacyIngredients.firstStack(parsed.ingredient()).copy();
                if(parsed.displayTag()!=null)stack.setTag(parsed.displayTag());else if(parsed.nbt()!=null)stack.setTag(parsed.nbt().toTag());stack.setCount(1);return stack;
            }
            if(kind.equals("fluid")){var fluid=ForgeRegistries.FLUIDS.getValue(ResourceLocation.tryParse(text(req,"fluid")));if(fluid!=null)return new ItemStack(fluid.getBucket());}
            return new ItemStack(switch(kind){case "energy"->net.minecraft.world.item.Items.REDSTONE;case "mana"->net.minecraft.world.item.Items.LAPIS_LAZULI;case "aura"->net.minecraft.world.item.Items.OAK_SAPLING;case "lifeessence"->net.minecraft.world.item.Items.RED_DYE;case "will"->net.minecraft.world.item.Items.AMETHYST_SHARD;case "ember"->net.minecraft.world.item.Items.BLAZE_POWDER;default->net.minecraft.world.item.Items.GLASS_BOTTLE;});
        }catch(RuntimeException error){return ItemStack.EMPTY;}
    }
    private void renderCanvas(GuiGraphics g,int mx,int my){
        g.drawString(font,tr("seconds"),left,78,0xdddddd,false);if(fields.containsKey("quickEnergy"))g.drawString(font,tr("quickEnergy"),left+panel/2+4,78,0xdddddd,false);
        g.drawString(font,tr("inputs"),left,97,0x99ddff,false);g.drawString(font,tr("outputs"),left+panel/2+4,97,0xffcc88,false);
        for(GhostSlot slot:canvasSlots){
            Rect2i a=slot.area();g.fill(a.getX(),a.getY(),a.getX()+20,a.getY()+20,slot.input()?0xff6699aa:0xffaa8855);g.fill(a.getX()+1,a.getY()+1,a.getX()+19,a.getY()+19,0xff202020);
            if(slot.index()<0){g.drawString(font,"+",a.getX()+7,a.getY()+6,0x999999,false);continue;}
            JsonObject req=requirements().get(slot.index()).getAsJsonObject();ItemStack icon=canvasIcon(req);
            if(!icon.isEmpty())g.renderItem(icon,a.getX()+2,a.getY()+2);
            else g.drawString(font,"?",a.getX()+7,a.getY()+6,0xffffff,false);
            String amount=text(req,RecipeDocument.amountKey(RecipeDocument.kind(req)));
            if(amount.length()>4)amount=amount.substring(0,3)+"…";
            if(!amount.equals("1"))g.drawString(font,amount,a.getX()+19-font.width(amount),a.getY()+12,0xffffff,true);
        }
        if(message.isEmpty())g.drawString(font,font.plainSubstrByWidth(tr("canvasHelp").getString(),panel),left,height-49,0xaaaaaa,false);
    }
    private void renderCanvasTooltip(GuiGraphics g,int mx,int my){
        GhostSlot slot=slotAt(mx,my);if(slot==null)return;
        List<Component> tooltip=new ArrayList<>();
        if(slot.index()<0)tooltip.add(tr("emptySlot"));else{
            JsonObject req=requirements().get(slot.index()).getAsJsonObject();String kind=RecipeDocument.kind(req);ItemStack icon=canvasIcon(req);
            tooltip.add(kind.equals("item")&&!icon.isEmpty()?icon.getHoverName():tr("resource."+(RecipeDocument.TYPES.contains(kind)?kind:"unknown")));
            tooltip.add(Component.literal(text(req,RecipeDocument.resourceKey(kind))));
            tooltip.add(Component.literal(text(req,RecipeDocument.amountKey(kind))+" "+RecipeDocument.unit(kind)));
            tooltip.add(tr("slotHelp"));
        }
        g.renderComponentTooltip(font,tooltip,mx,my);
    }
    private Button button(int x,int y,int w,Component name,Runnable action) { Button b=addRenderableWidget(Button.builder(name,ignored->action.run()).bounds(left+x,y,w,20).build()); b.active=waiting==0; return b; }
    private void dirty(){checkedSource="";if(exportButton!=null)exportButton.active=false;}
    /** 作者真的改了控件：记一代本地编辑并让导出失效；程序化回填不计数。 */
    private void edited(){if(!programmatic){localEdits++;dirty();}}
    private void field(String key,String value,int row) { EditBox box=addRenderableWidget(new EditBox(font,left+label,y(row)+1,panel-label,18,tr(key)));box.setMaxLength(key.equals("id")?256:1024);box.setValue(value);box.setResponder(v->edited());box.active=waiting==0;fields.put(key,box);box.setTooltip(Tooltip.create(tr(key+"Help"))); }
    private void multi(String key,String value,int y,int height) { MultiLineEditBox box=addRenderableWidget(new MultiLineEditBox(font,left,y,panel,height,Component.empty(),tr(key)));box.setCharacterLimit(RecipeDocument.MAX_SOURCE);box.setValue(value);box.setValueListener(v->edited());box.active=waiting==0;multiline.put(key,box); }
    private void pick(Collection<String> choices,String query,java.util.function.Consumer<String> choose){if(waiting==0&&collect())minecraft.setScreen(new RecipeResourcePicker(this,choices,query,value->{choose.accept(value);edited();},this::choiceLabel));}
    private void search(String kind) {
        String key=RecipeDocument.resourceKey(kind); EditBox field=fields.get(key); if(field==null)return;
        String query=field.getValue(); List<String> ids=new ArrayList<>();
        if(kind.equals("item")){ForgeRegistries.ITEMS.getKeys().forEach(id->ids.add(id.toString()));net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagNames().forEach(tag->ids.add("#"+tag.location()));}
        else if(kind.equals("fluid"))ForgeRegistries.FLUIDS.getKeys().forEach(id->ids.add(id.toString()));
        else if(kind.equals("aura"))ids.addAll(List.of("naturesaura:overworld","naturesaura:nether","naturesaura:end"));
        else if(kind.equals("will"))ids.addAll(List.of("default","corrosive","destructive","vengeful","steadfast"));
        if(ids.isEmpty()){message=tr("searchHelp").getString();return;}
        pick(ids,query,value->current().addProperty(key,value));
    }
    public Rect2i dropArea() { EditBox box=fields.get(current()==null?"":RecipeDocument.resourceKey(RecipeDocument.kind(current()))); return tab==1&&part==0&&waiting==0&&box!=null?new Rect2i(box.getX(),box.getY(),box.getWidth(),box.getHeight()):new Rect2i(0,0,0,0); }
    public boolean accepts(String kind) { return tab==1&&part==0&&waiting==0&&current()!=null&&RecipeDocument.kind(current()).equals(kind); }
    public List<GhostSlot> ghostSlots(String kind) {
        if(advanced||tab!=0||waiting!=0||absent.contains(kind)||!List.of("item","fluid","gas").contains(kind))return List.of();
        return canvasSlots.stream().filter(slot->slot.index()<0||RecipeDocument.kind(requirements().get(slot.index()).getAsJsonObject()).equals(kind)).toList();
    }
    public void dropGhost(GhostSlot slot,String kind,Object value) {
        if(kind.equals("item")&&(!(value instanceof ItemStack item)||item.isEmpty())
                ||kind.equals("fluid")&&(!(value instanceof FluidStack fluid)||fluid.isEmpty()))return;
        if(!ghostSlots(kind).contains(slot)||!collect())return;
        try{
            String id=value instanceof ItemStack item?ForgeRegistries.ITEMS.getKey(item.getItem()).toString()
                    :value instanceof FluidStack fluid?ForgeRegistries.FLUIDS.getKey(fluid.getFluid()).toString():String.valueOf(value);
            JsonObject original=slot.index()<0?null:requirements().get(slot.index()).getAsJsonObject();
            JsonObject next=RecipeQuickEdit.resource(original,kind,id);next.addProperty("io-type",slot.input()?"input":"output");
            if(value instanceof ItemStack item){next.remove("nbt");next.remove("nbt-display");if(item.hasTag())next.add("nbt",LiteralNbt.encode(item.getTag()));}
            if(slot.index()<0){RecipeQuickEdit.checkSpace(requirements());requirements().add(next);}else requirements().set(slot.index(),next);
            edited();rebuild();
        }catch(RuntimeException error){message=String.valueOf(error.getMessage());}
    }
    public Rect2i editorArea(){return new Rect2i(left,0,panel,height);}
    /** Called by the actual JEI screen handler before it queries bounds. No JEI classes leak here. */
    public void prepareJeiLayout(){
        if(jeiLayout)return;
        if(!collect())savedValues=values();
        jeiLayout=true;
        if(minecraft!=null&&font!=null)rebuild();
    }
    public void dropItem(ItemStack stack) {
        if(!accepts("item")||stack.isEmpty()||!collect())return;
        JsonObject req=current(); req.addProperty("item",ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        req.remove("nbt");req.remove("nbt-display"); if(stack.hasTag())req.add("nbt",LiteralNbt.encode(stack.getTag()));
        if(!fields.isEmpty()) { EditBox item=fields.get("item"); if(item!=null)item.setValue(text(req,"item")); }
        rebuild();
    }
    public void dropFluid(FluidStack stack) { if(accepts("fluid")&&!stack.isEmpty()) {fields.get("fluid").setValue(ForgeRegistries.FLUIDS.getKey(stack.getFluid()).toString());} }
    public void dropGas(String id) { if(accepts("gas"))fields.get("gas").setValue(id); }
    /**
     * P01-02（0.121.49 返工）：请求基线，由真实发送入口在发包前调用。离线夹具没有 Netty
     * 通道，在受控发送边界调用这同一个方法记录基线——与 {@link #send} 的发包点前状态一致，
     * 而不是反射把保护字段写成恰好通过的值。
     */
    void sentBaseline() { sentEdits = localEdits; }
    private void send(RecipeEditorService.Action action) {
        if(waiting>0 || !collect())return;
        if(action==RecipeEditorService.Action.EXPORT&&(!checkedId.equals(recipeId)||!checkedSource.equals(root.toString())||checkedFormat!=format)){message=tr("needsValidation").getString();dirty();return;}
        try {
            RecipeDocument.read(root.toString()); RecipeDocument.id(recipeId);
            sentBaseline();
            ModNetwork.CHANNEL.sendToServer(new RecipeEditorActionPacket(token,state.get("revision").getAsLong(),action,recipeId,root.toString(),format));waiting=1;message=tr("waiting").getString();rebuild();
        } catch(RuntimeException error) {message=String.valueOf(error.getMessage());}
    }
    @Override public void tick() {fields.values().forEach(EditBox::tick);multiline.values().forEach(MultiLineEditBox::tick);if(minecraft.player==null||minecraft.level==null||!dimension.equals(minecraft.level.dimension().location().toString()))onClose();if(waiting>0&&++waiting>100){waiting=0;message=tr("timeout").getString();rebuild();} }
    @Override public void onClose() {if(minecraft.getConnection()!=null)ModNetwork.CHANNEL.sendToServer(new RecipeEditorActionPacket(token,0,RecipeEditorService.Action.CLOSE,"","",format));super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
    public boolean scrollSlot(double x,double y,double delta,boolean fast){
        if(!advanced&&tab==0&&waiting==0&&delta!=0){
            GhostSlot slot=slotAt(x,y);
            if(slot!=null&&slot.index()>=0&&collect())try{
                if(RecipeQuickEdit.adjust(requirements().get(slot.index()).getAsJsonObject(),(delta>0?1:-1)*(fast?10:1))){edited();rebuild();}
                else message=tr("amountAdvanced").getString();return true;
            }catch(RuntimeException error){message=String.valueOf(error.getMessage());return true;}
        }
        return false;
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(!advanced&&tab==0&&waiting==0&&delta!=0&&slotAt(x,y)!=null&&scrollSlot(x,y,delta,hasShiftDown()))return true;
        if(tab==3){scroll=Math.max(0,Math.min(Math.max(0,lines.size()-(height-top(height)-64)/10),scroll-(int)(delta*3)));return true;}return super.mouseScrolled(x,y,delta);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(!advanced&&tab==0&&waiting==0){
            GhostSlot slot=slotAt(x,y);
            if(slot!=null){
                if(button==1&&slot.index()>=0)change(()->{requirements().remove(slot.index());edited();});
                else if(button==0&&slot.index()>=0)change(()->{selected=slot.index();part=0;tab=1;});
                else if(button==0)chooseCanvasItem(slot);
                return true;
            }
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public void render(GuiGraphics graphics,int mx,int my,float partial) {
        renderBackground(graphics);graphics.drawCenteredString(font,title,left+panel/2,5,0xFFFFFF);
        graphics.drawString(font,font.plainSubstrByWidth(message,panel),left,!advanced&&tab==0?height-49:height-62,0xFFCC77,false);
        if(!advanced&&tab==0)renderCanvas(graphics,mx,my);
        if(tab==0&&advanced) {for(var e:fields.entrySet())graphics.drawString(font,tr(e.getKey()),left,e.getValue().getY()+4,0xEEEEEE,false);}
        if(tab==1) {
            for(var e:fields.entrySet())graphics.drawString(font,tr(e.getKey()),left,e.getValue().getY()+4,0xEEEEEE,false);
            graphics.drawString(font,tr("position",requirements().isEmpty()?0:selected+1,requirements().size()),left,top(height)-11,0xAAAAAA,false);
            if(part==2)for(var e:multiline.entrySet())graphics.drawString(font,tr(e.getKey()),left,e.getValue().getY()-11,0xDDDDDD,false);
        }
        if(tab==2)graphics.drawString(font,font.plainSubstrByWidth(tr("rawHelp").getString(),panel),left,top(height),0xBBBBBB,false);
        if(tab==3) {
            int offset=!previewSource&&!previewItems.isEmpty()?24:0;
            if(offset>0)for(int i=0;i<Math.min(previewItems.size(),panel/22);i++) {int x=left+i*22;graphics.renderItem(previewItems.get(i),x,top(height));graphics.renderItemDecorations(font,previewItems.get(i),x,top(height));}
            graphics.enableScissor(left,top(height)+offset,left+panel,height-64);try{int visible=Math.max(0,(height-top(height)-offset-64)/10);for(int i=0;i<visible&&scroll+i<lines.size();i++)graphics.drawString(font,lines.get(scroll+i),left+4,top(height)+offset+i*10,0xDDDDDD,false);}finally{graphics.disableScissor();}
            if(offset>0&&my>=top(height)&&my<top(height)+18){int index=(mx-left)/22;if(mx>=left&&index<Math.min(previewItems.size(),panel/22))graphics.renderTooltip(font,previewItems.get(index),mx,my);}
        }
        super.render(graphics,mx,my,partial);
        if(!advanced&&tab==0)renderCanvasTooltip(graphics,mx,my);
        int noticeY=!advanced&&tab==0?height-49:height-62;
        if(!message.isBlank()&&mx>=left&&mx<left+panel&&my>=noticeY&&my<noticeY+10)
            graphics.renderTooltip(font,Component.literal(message),mx,my);
    }
}
