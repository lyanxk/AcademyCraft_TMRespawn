package cn.academy.ability.vanilla.meltdowner.skill;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.ability.vanilla.electromaster.skill.ElectromasterOreRules;
import cn.academy.event.BlockDestroyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;

/** Original MRContext server lifecycle: snapshot parameters, select once, then reduce hardness. */
public final class MineRayContext {
    private static final BlockPos RESET=new BlockPos(-1,-1,-1);
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final float cp,overload,speed,gain;
    private final int range,harvest,cooldown;
    private final boolean lucky;
    private BlockPos selected=RESET;
    private float hardness=Float.MAX_VALUE;
    private boolean ended;
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_ID=new java.util.concurrent.atomic.AtomicInteger();
    private final int id=NEXT_ID.incrementAndGet(),slot,request,variant;
    private final java.util.ArrayList<ServerPlayer> audience=new java.util.ArrayList<>();

    public static boolean handles(AbilitySkill skill) {
        return switch(skill.name()){case "mine_ray_basic","mine_ray_expert","mine_ray_luck"->true;default->false;};
    }
    public MineRayContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;
        this.slot=slot;this.request=request;
        boolean basic=skill.name().equals("mine_ray_basic");lucky=skill.name().equals("mine_ray_luck");
        variant=basic?0:lucky?2:1;
        audience.add(player);
        for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=625)audience.add(viewer);
        float exp=state.experience(skill.id());
        range=basic?10:20;harvest=basic?2:5;
        cp=lerp(basic?12:lucky?50:25,basic?7:lucky?35:15,exp);
        overload=lerp(basic?200:lucky?350:300,basic?150:lucky?300:200,exp);
        speed=lerp(basic?.2f:.5f,basic?.4f:1,exp);
        cooldown=(int)lerp(basic?40:60,basic?20:30,exp);
        gain=basic?.0005f:.0003f;
    }
    public void start(AbilityState state) {
        state.consumeRaw(skill,0,overload,player.isCreative()); // Original ignores startup failure.
        send(true);
    }
    /** A failed payment disposes the context, but the original listener still executes this tick's digging. */
    public boolean tick(AbilityState state) {
        boolean paid=state.consumeRaw(skill,cp,0,player.isCreative());
        var level=player.serverLevel();
        // LambdaLib2 supplies a MISS result with truncated end coordinates; MRContext never checks its type.
        BlockPos pos=ElectricAttackTrace.traceBlocks(player,range).getBlockPos();
        if(!pos.equals(selected)) {
            var block=level.getBlockState(pos);
            var event=new BlockDestroyEvent(level,player,pos,skill);
            NeoForge.EVENT_BUS.post(event);
            if(!event.isCanceled() && AbilityContext.of(player,skill).canBreakBlock(level)
                    && ElectromasterOreRules.harvestLevel(block)<=harvest) {
                selected=pos.immutable();hardness=block.getDestroySpeed(level,pos);
                if(hardness<0)hardness=Float.MAX_VALUE;
            }else selected=RESET;
        }else {
            hardness-=speed;
            if(hardness<=0) {
                var block=level.getBlockState(pos);
                level.playSound(null,pos,block.getSoundType(level,pos,player).getBreakSound(),SoundSource.BLOCKS,.5f,1);
                var tool=new ItemStack(Items.NETHERITE_PICKAXE);
                if(lucky)tool.enchant(level.registryAccess().holderOrThrow(Enchantments.FORTUNE),3);
                // dropBlockAsItemWithChance did not call harvestBlock/spawnAfterBreak (no mining XP).
                if(level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)) {
                    // Pre-raw-metal vanilla ores dropped the block, unaffected by Fortune.
                    var drops=block.is(Blocks.IRON_ORE)||block.is(Blocks.GOLD_ORE)
                            ? java.util.List.of(new ItemStack(block.getBlock()))
                            : Block.getDrops(block,level,pos,level.getBlockEntity(pos),player,tool);
                    for(var drop:drops)
                        if(level.random.nextFloat()<=1.0f)Block.popResource(level,pos,drop);
                }
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                state.addExperience(skill,gain);
                selected=RESET;
            }
            var particles=new cn.academy.network.MineRayParticlesPayload(id,selected);
            for(var viewer:audience)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer,particles);
        }
        return paid;
    }
    public void end(AbilityState state) {
        if(!ended){ended=true;state.finishContext(skill,cooldown,0);send(false);audience.clear();}
    }
    private void send(boolean start) {
        var payload=new cn.academy.network.MineRayEffectPayload(player.getId(),slot,id,request,variant,start);
        for(var viewer:audience)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer,payload);
    }
    private static float lerp(float a,float b,float exp){return a+(b-a)*exp;}
}
