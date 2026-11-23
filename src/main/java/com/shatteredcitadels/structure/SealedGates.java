package com.shatteredcitadels.structure;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the sealed gates of a structure. A gate opens when its required floor is cleared (or the
 * boss falls); gates in unloaded chunks stay recorded and are opened by {@link #reconcile} the
 * next time a player is inside the structure.
 */
public final class SealedGates {

    private SealedGates() {
    }

    /** Opens every loaded gate that waits for {@code clearedFloor}. */
    public static void open(World world, CitadelInstance citadel, int clearedFloor) {
        openMatching(world, citadel, clearedFloor, false);
    }

    /**
     * Gates the surface boss's defeat opens: everything above ground. Catacomb gates (negative
     * floors: levels and the mage's sanctum) keep their own order.
     */
    public static boolean opensOnConquest(CitadelInstance.GatePoint gate) {
        return gate.requiredFloor >= 0;
    }

    /** @return false for the gates of a locked idol vault: nothing but the idols opens them. */
    public static boolean mayOpen(CitadelInstance citadel, CitadelInstance.GatePoint gate) {
        return gate.requiredFloor != VaultLock.GATE_FLOOR || citadel.getVault() != null && citadel.getVault().isOpen();
    }

    /** Opens every loaded gate of the structure that conquest opens (boss defeated). */
    public static void openAll(World world, CitadelInstance citadel) {
        openMatching(world, citadel, 0, true);
    }

    /** Opens gates whose floor is already cleared, e.g. ones that were unloaded at the time. */
    public static void reconcile(World world, CitadelInstance citadel) {
        if (citadel == null || citadel.getGates().isEmpty()) {
            return;
        }
        if (citadel.isConquered()) {
            openAll(world, citadel);
        }
        for (CitadelInstance.GatePoint gate : new ArrayList<>(citadel.getGates())) {
            FloorData floor = citadel.getFloor(gate.requiredFloor);
            if (floor == null || floor.isCleared()) {
                open(world, citadel, gate.requiredFloor);
            }
        }
    }

    private static void openMatching(World world, CitadelInstance citadel, int floor, boolean all) {
        if (world == null || world.isRemote || citadel == null || citadel.getGates().isEmpty()) {
            return;
        }
        List<BlockPos> opened = new ArrayList<>();
        for (CitadelInstance.GatePoint gate : new ArrayList<>(citadel.getGates())) {
            if (all ? !opensOnConquest(gate) : gate.requiredFloor != floor) {
                continue;
            }
            if (!mayOpen(citadel, gate)) {
                continue;
            }
            if (!world.isBlockLoaded(gate.pos)) {
                continue;
            }
            if (world.getBlockState(gate.pos).getBlock() == ModBlocks.SEALED_GATE) {
                world.setBlockState(gate.pos, restoreState(gate.restoreStateId), 3);
                opened.add(gate.pos);
            }
            citadel.removeGate(gate);
        }
        CitadelWorldSavedData.get(world).markDirty();
        if (opened.isEmpty()) {
            return;
        }
        BlockPos centre = opened.get(opened.size() / 2);
        world.playSound(null, centre, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.BLOCKS, 0.8F, 0.6F);
        world.playSound(null, centre, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.0F, 0.5F);
        if (world instanceof WorldServer) {
            for (BlockPos pos : opened) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + 0.5D, pos.getY() + 0.5D,
                        pos.getZ() + 0.5D, 24, 0.4D, 0.4D, 0.4D, 0.4D);
            }
        }
        TextComponentTranslation line = new TextComponentTranslation(all ? "shatteredcitadels.gate.all_open"
                : floor < 0 ? "shatteredcitadels.gate.open_down" : "shatteredcitadels.gate.open_up");
        line.getStyle().setColor(TextFormatting.LIGHT_PURPLE);
        for (EntityPlayer player : world.playerEntities) {
            if (citadel.contains(new BlockPos(player))) {
                player.sendStatusMessage(line, true);
            }
        }
        ShatteredCitadelsMod.LOGGER.info("Opened {} sealed gate block(s) of citadel {} ({})", opened.size(),
                citadel.getCitadelId(), all ? "boss defeated" : "floor " + floor + " cleared");
    }

    @SuppressWarnings("deprecation")
    private static IBlockState restoreState(int stateId) {
        IBlockState state = Block.getStateById(stateId);
        return state == null || state.getBlock() == ModBlocks.SEALED_GATE ? Blocks.AIR.getDefaultState() : state;
    }
}
