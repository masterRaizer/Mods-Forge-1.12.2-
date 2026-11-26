package com.shatteredcitadels.herobrine;

import com.shatteredcitadels.Reference;
import net.minecraft.block.Block;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Lighting the netherrack of the altar summons the boss. A lair altar works once; a player-built
 * altar works only with {@code playerBuiltAltar}. Either way the netherrack is spent (turns to
 * obsidian).
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class AltarHandler {

    /** No second summon while a Herobrine lives this close. */
    private static final double BUSY_RANGE = 64.0D;

    private AltarHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || !HerobrineSettings.enabled() || world.provider.getDimension() != 0) {
            return;
        }
        Item held = event.getItemStack().getItem();
        BlockPos pos = event.getPos();
        if (held != Items.FLINT_AND_STEEL && held != Items.FIRE_CHARGE || world.getBlockState(pos).getBlock() != Blocks.NETHERRACK) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        HerobrineSavedData data = HerobrineSavedData.get(world);
        HerobrineSavedData.Lair lair = data.lairAt(pos);
        if (lair == null && !HerobrineSettings.playerBuiltAltar()) {
            return;
        }
        if (!AltarPattern.matches(cells(world, pos))) {
            if (lair != null) {
                tell(player, "broken");
            }
            return;
        }
        if (lair != null && !lair.isActive()) {
            tell(player, "dormant");
            return;
        }
        if (world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            tell(player, "peaceful");
            return;
        }
        AxisAlignedBB near = new AxisAlignedBB(pos).grow(BUSY_RANGE);
        if (!world.getEntitiesWithinAABB(EntityHerobrine.class, near).isEmpty()) {
            tell(player, "busy");
            return;
        }
        world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
        if (lair != null) {
            data.advance(pos, HerobrineSavedData.SUMMONED);
        }
        summon(world, pos.up(), lair != null ? pos : null);
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
    }

    /** What stands around a netherrack block, in the terms of {@link AltarPattern}. */
    static AltarPattern.Cells cells(World world, BlockPos netherrack) {
        return (dx, dy, dz) -> {
            BlockPos at = netherrack.add(dx, dy, dz);
            if (!world.isBlockLoaded(at)) {
                return AltarPattern.Cell.OTHER;
            }
            Block block = world.getBlockState(at).getBlock();
            if (block == Blocks.GOLD_BLOCK) {
                return AltarPattern.Cell.GOLD;
            }
            if (block == Blocks.NETHERRACK) {
                return AltarPattern.Cell.NETHERRACK;
            }
            return block == Blocks.REDSTONE_TORCH || block == Blocks.UNLIT_REDSTONE_TORCH
                    ? AltarPattern.Cell.TORCH : AltarPattern.Cell.OTHER;
        };
    }

    /**
     * Spawns the boss with thunder (a harmless bolt) and tells the players around.
     *
     * @param altar the lair altar that called him, or null (command, player-built altar)
     */
    public static EntityHerobrine summon(World world, BlockPos at, BlockPos altar) {
        EntityHerobrine boss = new EntityHerobrine(world);
        boss.setLocationAndAngles(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, world.rand.nextFloat() * 360.0F, 0.0F);
        boss.onInitialSpawn(world.getDifficultyForLocation(at), null);
        boss.setAltar(altar);
        world.addWeatherEffect(new EntityLightningBolt(world, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, true));
        world.spawnEntity(boss);
        world.playSound(null, at, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.0F, 0.6F);
        TextComponentTranslation message = new TextComponentTranslation("shatteredcitadels.herobrine.summoned");
        message.getStyle().setColor(TextFormatting.DARK_RED);
        for (EntityPlayer player : world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(at).grow(BUSY_RANGE))) {
            player.sendMessage(message);
        }
        return boss;
    }

    private static void tell(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation("shatteredcitadels.herobrine.altar." + key), true);
    }
}
