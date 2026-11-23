package com.shatteredcitadels.block;

import com.shatteredcitadels.item.ModItems;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.SealedGates;
import com.shatteredcitadels.structure.VaultLock;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Idol socket at a jungle temple's vault door: right-click with a jungle idol to set it (the idol is
 * used up). When every socket of the vault holds an idol, the vault's sealed gates open.
 */
public class BlockIdolSocket extends BlockVaultBrick {

    public static final PropertyBool FILLED = PropertyBool.create("filled");

    public BlockIdolSocket() {
        setLightLevel(0.3F);
        setDefaultState(blockState.getBaseState().withProperty(FILLED, Boolean.FALSE));
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote || hand != EnumHand.MAIN_HAND) {
            return true;
        }
        if (state.getValue(FILLED)) {
            status(player, "shatteredcitadels.socket.full", TextFormatting.GOLD);
            return true;
        }
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty() || held.getItem() != ModItems.JUNGLE_IDOL) {
            status(player, "shatteredcitadels.socket.needs_idol", TextFormatting.DARK_GREEN);
            return true;
        }
        CitadelWorldSavedData data = CitadelWorldSavedData.get(world);
        // Not getCitadelAt: a buried fragment overlapping the vault would answer first.
        CitadelInstance citadel = VaultLock.ownerOf(data.getAll(), pos);
        VaultLock vault = citadel == null ? null : citadel.getVault();
        if (vault == null || !vault.place(pos)) {
            status(player, "shatteredcitadels.socket.dead", TextFormatting.GRAY);
            return true;
        }
        if (!player.capabilities.isCreativeMode) {
            held.shrink(1);
        }
        world.setBlockState(pos, state.withProperty(FILLED, Boolean.TRUE), 3);
        data.markDirty();
        world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0F, 0.8F);
        if (vault.isOpen()) {
            SealedGates.open(world, citadel, VaultLock.GATE_FLOOR);
        } else {
            int set = 0;
            for (BlockPos socket : vault.sockets()) {
                set += vault.isFilled(socket) ? 1 : 0;
            }
            TextComponentTranslation line = new TextComponentTranslation("shatteredcitadels.socket.placed", set,
                    vault.sockets().size());
            line.getStyle().setColor(TextFormatting.GREEN);
            player.sendStatusMessage(line, true);
        }
        return true;
    }

    private static void status(EntityPlayer player, String key, TextFormatting colour) {
        TextComponentTranslation line = new TextComponentTranslation(key);
        line.getStyle().setColor(colour);
        player.sendStatusMessage(line, true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FILLED, (meta & 1) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FILLED) ? 1 : 0;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FILLED);
    }
}
