package com.shatteredcitadels.structure;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.config.ConfigCache;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Unbreakable citadels (config {@code unbreakableCitadels}): until a structure's boss is defeated,
 * its walls, floors, ceilings, windows and stairs cannot be broken by players, explosions or
 * griefing mobs, so the sealed gates cannot be dug around. Chests, furniture and other tile entity
 * blocks, vases, cobwebs and anything a player placed stay breakable; creative ignores it.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class CitadelProtectionHandler {

    private static final long MESSAGE_COOLDOWN_MS = 2000L;
    private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

    private CitadelProtectionHandler() {
    }

    /** @return true if the block at {@code pos} belongs to an unconquered structure and must not break. */
    public static boolean isProtected(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null || !ConfigCache.get().isUnbreakableCitadels()) {
            return false;
        }
        CitadelInstance citadel = CitadelWorldSavedData.get(world).getCitadelAt(pos);
        if (citadel == null || citadel.isConquered() || citadel.isPlayerPlaced(pos)) {
            return false;
        }
        return !isAlwaysBreakable(world.getBlockState(pos));
    }

    /** Loot containers, furniture, vases and cobwebs stay breakable; our own machinery never does. */
    static boolean isAlwaysBreakable(IBlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.AIR || block == ModBlocks.VASE || block == Blocks.WEB) {
            return true;
        }
        if (block == ModBlocks.TRIAL_SPAWNER || block == ModBlocks.BOSS_ALTAR || block == ModBlocks.SEALED_GATE) {
            return false;
        }
        return block.hasTileEntity(state);
    }

    private static boolean bypasses(EntityPlayer player) {
        return player != null && (player.capabilities.isCreativeMode || player.isSpectator());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        EntityPlayer player = event.getEntityPlayer();
        if (!bypasses(player) && isProtected(player.world, event.getPos())) {
            event.setNewSpeed(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        EntityPlayer player = event.getPlayer();
        if (world.isRemote || bypasses(player)) {
            if (!world.isRemote) {
                forgetPlaced(world, event.getPos());
            }
            return;
        }
        if (isProtected(world, event.getPos())) {
            event.setCanceled(true);
            notify(player);
            return;
        }
        forgetPlaced(world, event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        World world = event.getWorld();
        if (world.isRemote || event.isCanceled() || !(event.getEntity() instanceof EntityPlayer)
                || !ConfigCache.get().isUnbreakableCitadels()) {
            return;
        }
        CitadelInstance citadel = CitadelWorldSavedData.get(world).getCitadelAt(event.getPos());
        if (citadel != null && !citadel.isConquered()) {
            citadel.markPlayerPlaced(event.getPos());
            CitadelWorldSavedData.get(world).markDirty();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        World world = event.getWorld();
        if (!world.isRemote) {
            event.getAffectedBlocks().removeIf(pos -> isProtected(world, pos));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (isProtected(event.getEntityLiving().world, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.world.isRemote || entity instanceof EntityPlayer) {
            return;
        }
        if (isProtected(entity.world, entity.getPosition()) || isProtected(entity.world, entity.getPosition().down())) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static void forgetPlaced(World world, BlockPos pos) {
        CitadelInstance citadel = CitadelWorldSavedData.get(world).getCitadelAt(pos);
        if (citadel != null && citadel.consumePlayerPlaced(pos)) {
            CitadelWorldSavedData.get(world).markDirty();
        }
    }

    private static void notify(EntityPlayer player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_MESSAGE.get(player.getUniqueID());
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) {
            return;
        }
        LAST_MESSAGE.put(player.getUniqueID(), now);
        TextComponentTranslation line = new TextComponentTranslation("shatteredcitadels.protection.unbreakable");
        line.getStyle().setColor(TextFormatting.GRAY);
        player.sendStatusMessage(line, true);
    }
}
