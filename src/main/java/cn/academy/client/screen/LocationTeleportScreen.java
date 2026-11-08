package cn.academy.client.screen;

import cn.academy.client.render.ACGuiTextures;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.ability.vanilla.teleporter.LocationTeleportRules;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.network.LocationActionPayload;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Native recreation of LocationTeleport's 1.12.2 loctele_new.xml overlay. */
public final class LocationTeleportScreen extends Screen {
    private static final float SCALE = .24f;
    private static final float MENU_X = 20;
    private static final float MENU_Y = -179.1667f;
    private static final float MENU_WIDTH = 442;
    private static final float MENU_HEIGHT = 530;
    private static final float LIST_Y = MENU_Y + 18;
    private static final float ROW_HEIGHT = 80;
    private static final float ROW_STEP = 82;
    private static final int VISIBLE_ROWS = 6;
    private static final net.minecraft.client.renderer.RenderType OUTLINE=net.minecraft.client.renderer.RenderType.create("academy_location_outline",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_LINES,256,false,false,
            net.minecraft.client.renderer.RenderType.CompositeState.builder()
                    .setShaderState(new net.minecraft.client.renderer.RenderStateShard.ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getPositionColorShader))
                    .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setLineState(new net.minecraft.client.renderer.RenderStateShard.LineStateShard(java.util.OptionalDouble.of(2)))
                    .setDepthTestState(net.minecraft.client.renderer.RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(net.minecraft.client.renderer.RenderStateShard.COLOR_WRITE).createCompositeState(false));
    private static final ResourceLocation TELEPORT_ICON = texture("guis/icons/icon_location_on.png");
    private static final ResourceLocation REMOVE_ICON = texture("guis/icons/icon_clear.png");
    private static final ResourceLocation CONFIRM_ICON = texture("guis/check.png");

    private final long openedAt = Util.getMillis();
    private int scrollRow;
    private int hoveredRow = -1;
    private List<AbilityState.TeleportLocation> cachedLocations=List.of();
    private List<Row> rows=List.of();
    private List<String> addHint=List.of();
    private long listCreatedAt;
    private int caret,displayOffset;
    private boolean editingName,inputStarted;
    private String newName = "";

    public LocationTeleportScreen() {
        super(Component.translatable("ac.ability.teleporter.location_teleport.name"));
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/" + path);
    }

    private float originX() { return width * .5f; }
    private float originY() { return height * .5f; }
    private float virtualX(double mouseX) { return (float) ((mouseX - originX()) / SCALE); }
    private float virtualY(double mouseY) { return (float) ((mouseY - originY()) / SCALE); }

    private List<AbilityState.TeleportLocation> locations() {
        if (minecraft == null || minecraft.player == null) return List.of();
        return AbilityState.load(minecraft.player).teleportLocations();
    }

    @Override protected void init(){rebuild();}
    private void rebuild() {
        cachedLocations=locations();listCreatedAt=Util.getMillis();scrollRow=0;hoveredRow=-1;
        editingName=inputStarted=false;newName="";caret=displayOffset=0;
        var list=new ArrayList<Row>();
        for(var location:cachedLocations) {
            var stat=availability(location);var hint=new ArrayList<String>();
            hint.add(dimensionLabel(location.dimension()));
            hint.add(String.format("(%.0f, %.0f, %.0f)",location.x(),location.y(),location.z()));
            hint.add(String.format("%.0f CP",stat.cpCost));if(stat.reason!=null)hint.add(stat.reason);
            list.add(new Row(location,stat,List.copyOf(hint)));
        }
        rows=List.copyOf(list);
        if(minecraft!=null && minecraft.player!=null)addHint=List.of(dimensionLabel(minecraft.player.level().dimension().location().toString()),
                String.format("(%.0f, %.0f, %.0f)",minecraft.player.getX(),minecraft.player.getY(),minecraft.player.getZ()));
    }
    @Override public void tick(){super.tick();if(!locations().equals(cachedLocations))rebuild();}
    private float blend(int row,float offset,float length){return Mth.clamp((Util.getMillis()-listCreatedAt-row*60f-offset)/length,0,1);}
    @Override public void renderBackground(GuiGraphics gui,int mouseX,int mouseY,float partialTick) {
        // GuiScreen.drawDefaultBackground's in-world gradient, without the modern menu blur.
        gui.fillGradient(0,0,width,height,0xC0101010,0xD0101010);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        float vx = virtualX(mouseX);
        float vy = virtualY(mouseY);
        List<AbilityState.TeleportLocation> locations = cachedLocations;
        int totalRows = locations.size() + 1;
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll(locations.size()));
        hoveredRow = rowAt(vx, vy, totalRows);
        float opening = Mth.clamp((Util.getMillis() - openedAt) / 400f, 0, 1);
        float shownHeight = MENU_HEIGHT * opening;

        gui.pose().pushPose();
        gui.pose().translate(originX(), originY(), 0);
        gui.pose().scale(SCALE, SCALE, 1);
        drawPanel(gui, MENU_X, MENU_Y, MENU_WIDTH, shownHeight, 0x80353535, 0x9E7A929C);

        int displayed = Math.min(VISIBLE_ROWS, totalRows - scrollRow);
        for (int visible = 0; visible < displayed; visible++) {
            int rowIndex = scrollRow + visible;
            float rowY = LIST_Y + visible * ROW_STEP;
            float alpha = blend(rowIndex,0,200);
            boolean hovered = hoveredRow == rowIndex;
            int panelAlpha = (int)(255 * alpha * (hovered ? .4f : .1f));
            rect(gui,MENU_X,rowY,MENU_WIDTH,ROW_HEIGHT,panelAlpha<<24|0xffffff);
            if (rowIndex < locations.size()) drawLocationRow(gui, locations.get(rowIndex), rowIndex,
                    rowY, vx, vy);
            else drawAddRow(gui, rowIndex, rowY, vx, vy);
        }

        if (hoveredRow >= 0) drawHint(gui, hoveredRow, locations, vx, vy);
        gui.pose().popPose();
        for (var renderable : renderables) renderable.render(gui, mouseX, mouseY, partialTick);
    }

    private void drawLocationRow(GuiGraphics gui,AbilityState.TeleportLocation location,int index,float y,float mouseX,float mouseY) {
        var stat=rows.get(index).stat;
        int textColor=withAlpha(stat.enabled?0xFFC1CFD5:0xFFA2A2A2,blend(index,100,100));
        drawText(gui,location.name(),MENU_X+45.27778f,y+22.404762f,377.77778f,43,textColor,Align.LEFT);
        if(stat.enabled)drawIcon(gui,TELEPORT_ICON,MENU_X+335,y+29.916667f,blend(index,30,100),mouseX,mouseY);
        drawIcon(gui,REMOVE_ICON,MENU_X+375,y+29.916667f,blend(index,50,100),mouseX,mouseY);
    }
    private void drawAddRow(GuiGraphics gui,int index,float y,float mouseX,float mouseY) {
        updateCaret();String text=inputStarted?newName.substring(displayOffset):"Add...";
        int color=withAlpha(0xFFA4D4E9,blend(index,0,200)*(editingName?.8f:.4f));
        drawText(gui,text,MENU_X+45.27778f,y+25.97619f,280.55556f,43,color,Align.LEFT);
        if(editingName && Util.getMillis()%2000<1000) {
            float offset=ACLegacyFont.width(Component.literal(newName.substring(displayOffset,caret)),43,false);
            drawText(gui,"|",MENU_X+45.27778f+offset,y+24.97619f,0,43,color,Align.LEFT);
        }
        drawIcon(gui,CONFIRM_ICON,MENU_X+375.83333f,y+29.916667f,blend(index,0,100),mouseX,mouseY);
    }
    private void drawIcon(GuiGraphics gui,ResourceLocation texture,float x,float y,float alpha,float mouseX,float mouseY) {
        boolean hovered=inside(mouseX,mouseY,x,y,33.333333f,33.333333f);
        ACGuiTextures.blit(gui,texture,x,y,33.333333f,33.333333f,0,0,64,64,64,64,
                193/255f,207/255f,213/255f,alpha*(hovered?1:.7f));
    }
    private void drawHint(GuiGraphics gui,int index,List<AbilityState.TeleportLocation> locations,float mouseX,float mouseY) {
        List<String> lines=index<rows.size()?rows.get(index).hint:addHint;if(lines.isEmpty())return;
        float width=0;for(String line:lines)width=Math.max(width,ACLegacyFont.width(Component.literal(line),40,false));width+=40;
        float height=lines.size()*42+40,hintY=LIST_Y+(index-scrollRow)*ROW_STEP,hintLeft=-23.333333f-width;
        drawPanel(gui,hintLeft,hintY,width,height,0x80353535,0x9E7A929C);
        for(int line=0;line<lines.size();line++)drawText(gui,lines.get(line),hintLeft+20,hintY+20+line*42,width-40,40,0xFFC1CFD5,Align.RIGHT);
    }
    private String dimensionLabel(String id) {
        // Original dimensionNameMap returns the numeric provider ID, not a localized display name.
        return switch(id){case "minecraft:overworld"->"0 (#0)";case "minecraft:the_nether"->"-1 (#-1)";case "minecraft:the_end"->"1 (#1)";default->id;};
    }

    private Availability availability(AbilityState.TeleportLocation location) {
        if (minecraft == null || minecraft.player == null) return new Availability(false, 0, "");
        AbilityState state = AbilityState.load(minecraft.player);
        AbilitySkill skill = AbilityRegistry.skill("teleporter.location_teleport");
        boolean cross = !minecraft.player.level().dimension().location().toString().equals(location.dimension());
        double distance = minecraft.player.position().distanceTo(
                new net.minecraft.world.phys.Vec3(location.x(), location.y(), location.z()));
        float exp = skill == null ? 0 : state.experience(skill.id());
        float cp = LocationTeleportRules.cpCost(exp, distance, cross);
        String reason = null;
        if (cross && !LocationTeleportRules.canCrossDimension(exp)) reason = Component.translatable("ac.gui.loctele.err_exp").getString();
        else if (!minecraft.player.isCreative() && state.cp() < cp) reason = Component.translatable("ac.gui.loctele.err_cp").getString();
        return new Availability(reason == null, cp, reason);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        float vx = virtualX(mouseX), vy = virtualY(mouseY);
        List<AbilityState.TeleportLocation> locations = cachedLocations;
        int row = rowAt(vx, vy, locations.size() + 1);
        if (row < 0) {
            editingName = false;
            return super.mouseClicked(mouseX, mouseY, button);
        }
        float rowY = LIST_Y + (row - scrollRow) * ROW_STEP;
        if (row < locations.size()) {
            editingName=false;
            if (inside(vx, vy, MENU_X + 375, rowY + 29.916667f, 33.333333f, 33.333333f)) {
                PacketDistributor.sendToServer(LocationActionPayload.remove(row));
                return true;
            }
            if (inside(vx, vy, MENU_X + 335, rowY + 29.916667f, 33.333333f, 33.333333f) && rows.get(row).stat.enabled) {
                minecraft.setScreen(null);
                cn.academy.client.sound.ACContextSounds.once(minecraft.player.getUUID(),"tp.tp",.5f,net.minecraft.sounds.SoundSource.AMBIENT,-1000000);
                PacketDistributor.sendToServer(LocationActionPayload.teleport(row));
                return true;
            }
        } else {
            if (inside(vx, vy, MENU_X, rowY, MENU_WIDTH, ROW_HEIGHT)) {
                if (inside(vx,vy,MENU_X+375.83333f,rowY+29.916667f,33.333333f,33.333333f)) confirmAdd();
                else if(!editingName){editingName=inputStarted=true;newName="";caret=displayOffset=0;}
                return true;
            }
        }
        return true;
    }

    private void confirmAdd() {
        if (minecraft == null || minecraft.player == null) return;
        String input=inputStarted?newName:"Add...";
        PacketDistributor.sendToServer(LocationActionPayload.add(input.substring(0,Math.min(LocationTeleportRules.NAME_LENGTH,input.length()))));
        newName = "";
        editingName = inputStarted = false;caret=displayOffset=0;
    }

    @Override public boolean charTyped(char codePoint,int modifiers) {
        if(editingName && !Character.isISOControl(codePoint)){insert(String.valueOf(codePoint));return true;}
        return super.charTyped(codePoint,modifiers);
    }
    private void insert(String value){newName=newName.substring(0,caret)+value+newName.substring(caret);caret+=value.length();updateCaret();}
    private void updateCaret() {
        caret=Mth.clamp(caret,0,newName.length());displayOffset=Mth.clamp(displayOffset,0,caret);
        while(displayOffset<caret && ACLegacyFont.width(Component.literal(newName.substring(displayOffset,caret)),43,false)>280.55556f)
            displayOffset=newName.offsetByCodePoints(displayOffset,1);
    }
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers) {
        if(editingName) {
            if(keyCode==GLFW.GLFW_KEY_ENTER || keyCode==GLFW.GLFW_KEY_KP_ENTER){confirmAdd();return true;}
            if(keyCode==GLFW.GLFW_KEY_LEFT){if(caret>0)caret=newName.offsetByCodePoints(caret,-1);updateCaret();return true;}
            if(keyCode==GLFW.GLFW_KEY_RIGHT){if(caret<newName.length())caret=newName.offsetByCodePoints(caret,1);updateCaret();return true;}
            if(keyCode==GLFW.GLFW_KEY_BACKSPACE){if(caret>0){int previous=newName.offsetByCodePoints(caret,-1);newName=newName.substring(0,previous)+newName.substring(caret);caret=previous;displayOffset=Math.min(displayOffset,caret);}return true;}
            if(keyCode==GLFW.GLFW_KEY_DELETE){if(caret<newName.length())newName=newName.substring(0,caret)+newName.substring(newName.offsetByCodePoints(caret,1));return true;}
            if(Screen.hasControlDown() && keyCode==GLFW.GLFW_KEY_C){minecraft.keyboardHandler.setClipboard(newName);return true;}
            if(Screen.hasControlDown() && keyCode==GLFW.GLFW_KEY_V){insert(minecraft.keyboardHandler.getClipboard().replaceAll("[\\p{Cntrl}]",""));return true;}
        }
        return super.keyPressed(keyCode,scanCode,modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float vx = virtualX(mouseX), vy = virtualY(mouseY);
        if (inside(vx, vy, MENU_X, MENU_Y, MENU_WIDTH, MENU_HEIGHT)) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll(locations().size()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int rowAt(float x, float y, int totalRows) {
        if (x < MENU_X || x > MENU_X + MENU_WIDTH || y < LIST_Y) return -1;
        int visible = (int) ((y - LIST_Y) / ROW_STEP);
        if (visible < 0 || visible >= VISIBLE_ROWS || y > LIST_Y + visible * ROW_STEP + ROW_HEIGHT) return -1;
        int row = scrollRow + visible;
        return row < totalRows ? row : -1;
    }

    private static int maxScroll(int locations) { return Math.max(0, locations + 1 - VISIBLE_ROWS); }
    private static int withAlpha(int color, float alpha) {
        return (Mth.clamp((int)(alpha * 255), 0, 255) << 24) | (color & 0xFFFFFF);
    }

    private void drawPanel(GuiGraphics gui,float x,float y,float width,float height,int fill,int outline) {
        gui.flush();var out=gui.bufferSource().getBuffer(OUTLINE);float l=x-.4f,t=y-.4f,r=x+width+.4f,b=y+height+.4f;
        float[][] corners={{l,t},{l,b},{r,b},{r,t}};
        for(int i=0;i<4;i++){var a=corners[i];var next=corners[(i+1)%4];out.addVertex(gui.pose().last(),a[0],a[1],0).setColor(outline);out.addVertex(gui.pose().last(),next[0],next[1],0).setColor(outline);}
        gui.flush();rect(gui,x,y,width,height,fill);
    }
    private static void rect(GuiGraphics gui,float x,float y,float width,float height,int color) {
        gui.pose().pushPose();gui.pose().translate(x,y,0);gui.pose().scale(width,height,1);gui.fill(0,0,1,1,color);gui.pose().popPose();
    }

    private void drawText(GuiGraphics gui, String text, float x, float y, float maxWidth,
                          float size, int color, Align align) {
        int alignment = align == Align.RIGHT ? ACLegacyFont.RIGHT
                : align == Align.CENTER ? ACLegacyFont.CENTER : ACLegacyFont.LEFT;
        float anchor = align == Align.RIGHT ? x + maxWidth : align == Align.CENTER ? x + maxWidth / 2 : x;
        if(maxWidth>0){int end=0;while(end<text.length() && ACLegacyFont.width(Component.literal(text.substring(0,end)),size,false)<maxWidth)end=text.offsetByCodePoints(end,1);text=text.substring(0,end);}
        ACLegacyFont.draw(gui,Component.literal(text),anchor,y,size,color,alignment,0,false);
    }

    private static boolean inside(float x, float y, float l, float t, float w, float h) {
        return x >= l && x <= l + w && y >= t && y <= t + h;
    }

    @Override public boolean isPauseScreen() { return false; }

    private enum Align { LEFT, CENTER, RIGHT }
    private record Availability(boolean enabled, float cpCost, String reason) {}
    private record Row(AbilityState.TeleportLocation location,Availability stat,List<String> hint) {}
}
