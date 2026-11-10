package cn.academy.ability.vanilla.meltdowner.skill;

import cn.academy.data.Balance;

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
    private final boolean lucky,silk;
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
        for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=Balance.i("mine_ray_context", "mine_ray_context_threshold_1"))audience.add(viewer);
        float proficiency=state.experience(skill.id());
        boolean upgraded=MeltdownerSkillRules.upgraded(state,skill);
        float exp=proficiency;
        silk=!basic && !lucky && upgraded;
        range=range(state,variant);harvest=basic?(upgraded?MeltdownerSkillRules.integer(skill,"upgraded_harvest_level"):2):5;
        cp=lerp(basic?Balance.i("mine_ray_context", "mine_ray_context_lerp_1"):lucky?Balance.i("mine_ray_context", "mine_ray_context_lerp_2"):Balance.i("mine_ray_context", "mine_ray_context_lerp_3"),basic?Balance.i("mine_ray_context", "mine_ray_context_lerp_4"):lucky?Balance.i("mine_ray_context", "mine_ray_context_lerp_5"):Balance.i("mine_ray_context", "mine_ray_context_lerp_6"),exp);
        overload=lerp(basic?Balance.i("mine_ray_context", "mine_ray_context_lerp_7"):lucky?Balance.i("mine_ray_context", "mine_ray_context_lerp_8"):Balance.i("mine_ray_context", "mine_ray_context_lerp_9"),basic?Balance.i("mine_ray_context", "mine_ray_context_lerp_10"):lucky?Balance.i("mine_ray_context", "mine_ray_context_lerp_11"):Balance.i("mine_ray_context", "mine_ray_context_lerp_12"),exp);
        speed=upgraded?MeltdownerSkillRules.curve(skill,"upgraded_speed",proficiency):lucky?MeltdownerSkillRules.curve(AbilityRegistry.skill("meltdowner.mine_ray_expert"),"upgraded_speed",proficiency):lerp(basic?Balance.f("mine_ray_context", "mine_ray_context_lerp_13"):Balance.f("mine_ray_context", "mine_ray_context_lerp_14"),basic?Balance.f("mine_ray_context", "mine_ray_context_lerp_15"):Balance.i("mine_ray_context", "mine_ray_context_lerp_16"),exp);
        cooldown=(int)lerp(basic?Balance.i("mine_ray_context", "mine_ray_context_lerp_17"):Balance.i("mine_ray_context", "mine_ray_context_lerp_18"),basic?Balance.i("mine_ray_context", "mine_ray_context_lerp_19"):Balance.i("mine_ray_context", "mine_ray_context_lerp_20"),exp);
        gain=basic?Balance.f("mine_ray_context", "mine_ray_context_gain_1"):Balance.f("mine_ray_context", "mine_ray_context_gain_2");
    }
    public void start(AbilityState state) {
        send(true);
    }
    /** A failed payment disposes the context, but the original listener still executes this tick's digging. */
    public boolean tick(AbilityState state) {
        boolean paid=state.consumeRaw(skill,cp,overload/20,player.isCreative());
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
                if(lucky)tool.enchant(level.registryAccess().holderOrThrow(Enchantments.FORTUNE),MeltdownerSkillRules.integer(skill,"fortune_level"));
                if(silk)tool.enchant(level.registryAccess().holderOrThrow(Enchantments.SILK_TOUCH),1);
                // dropBlockAsItemWithChance did not call harvestBlock/spawnAfterBreak (no mining XP).
                if(level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)) {
                    // Enchanted beams use the block loot table so Fortune and Silk Touch apply.
                    var drops=!lucky && !silk && block.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy", "mine_ray_preserve_ore")))
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
        var payload=new cn.academy.network.MineRayEffectPayload(player.getId(),slot,id,request,variant,range,start);
        for(var viewer:audience)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer,payload);
    }
    public static int range(AbilityState state,int variant) {
        var expert=AbilityRegistry.skill("meltdowner.mine_ray_expert");
        if(variant==2 || variant==1 && MeltdownerSkillRules.upgraded(state,expert))
            return MeltdownerSkillRules.integer(expert,"upgraded_range");
        return Balance.i("mine_ray_context",variant==0?"mine_ray_context_range_1":"mine_ray_context_range_2");
    }
    private static float lerp(float a,float b,float exp){return a+(b-a)*exp;}
}
