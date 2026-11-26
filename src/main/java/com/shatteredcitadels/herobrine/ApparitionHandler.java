package com.shatteredcitadels.herobrine;

import com.shatteredcitadels.Reference;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shows the watching figure to players near a lair whose master still lives. Cheap by design: one
 * look per player every five seconds, one attempt per player per interval, and the figure is only
 * ever placed in a chunk that is already loaded.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class ApparitionHandler {

    private static final int CHECK_INTERVAL = 100;
    private static final int SPOT_TRIES = 4;
    private static final Map<UUID, Long> NEXT_ATTEMPT = new HashMap<>();

    private ApparitionHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer player = event.player;
        World world = player.world;
        if (event.phase != TickEvent.Phase.END || world.isRemote || player.ticksExisted % CHECK_INTERVAL != 0
                || !HerobrineSettings.apparitions() || world.provider.getDimension() != 0 || player.isSpectator()) {
            return;
        }
        long now = world.getTotalWorldTime();
        Long next = NEXT_ATTEMPT.get(player.getUniqueID());
        if (next == null || next - now > ApparitionRule.nextAttempt(0L, HerobrineSettings.apparitionIntervalMinutes())) {
            // A player just seen (or a clock that went backwards) waits one full interval first.
            NEXT_ATTEMPT.put(player.getUniqueID(), ApparitionRule.nextAttempt(now, HerobrineSettings.apparitionIntervalMinutes()));
            return;
        }
        if (!ApparitionRule.isDue(now, next)
                || !HerobrineSavedData.get(world).isHaunted(player.posX, player.posZ, HerobrineSettings.apparitionRadiusBlocks())) {
            return;
        }
        BlockPos feet = new BlockPos(player);
        boolean underground = !world.canSeeSky(feet) && feet.getY() < world.getSeaLevel() - 4;
        if (!ApparitionRule.isMoment(!world.isDaytime(), world.isThundering(), underground)) {
            return;
        }
        NEXT_ATTEMPT.put(player.getUniqueID(), ApparitionRule.nextAttempt(now, HerobrineSettings.apparitionIntervalMinutes()));
        if (!ApparitionRule.rolls(world.rand, HerobrineSettings.apparitionChance())) {
            return;
        }
        for (int i = 0; i < SPOT_TRIES; i++) {
            int[] spot = ApparitionRule.spot(world.rand, player.posX, player.posZ);
            BlockPos stand = standingPlace(world, spot[0], spot[1], feet.getY(), underground);
            if (stand != null) {
                EntityHerobrineApparition figure = new EntityHerobrineApparition(world);
                figure.setLocationAndAngles(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D, 0.0F, 0.0F);
                figure.faceEntity(player, 360.0F, 360.0F);
                world.spawnEntity(figure);
                return;
            }
        }
    }

    /** @return where the figure can stand in that column, or null; never touches an unloaded chunk */
    private static BlockPos standingPlace(World world, int x, int z, int playerY, boolean underground) {
        BlockPos column = new BlockPos(x, playerY, z);
        if (!world.isBlockLoaded(column)) {
            return null;
        }
        if (!underground) {
            BlockPos top = world.getTopSolidOrLiquidBlock(column);
            return isFree(world, top) ? top : null;
        }
        for (int dy = 0; dy <= 6; dy++) {
            if (isFree(world, column.up(dy))) {
                return column.up(dy);
            }
            if (dy > 0 && isFree(world, column.down(dy))) {
                return column.down(dy);
            }
        }
        return null;
    }

    private static boolean isFree(World world, BlockPos pos) {
        return pos.getY() > 1 && pos.getY() < 254
                && world.getBlockState(pos.down()).isSideSolid(world, pos.down(), net.minecraft.util.EnumFacing.UP)
                && !world.getBlockState(pos).getMaterial().blocksMovement() && !world.getBlockState(pos).getMaterial().isLiquid()
                && !world.getBlockState(pos.up()).getMaterial().blocksMovement()
                && !world.getBlockState(pos.up()).getMaterial().isLiquid();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT_ATTEMPT.remove(event.player.getUniqueID());
    }
}
