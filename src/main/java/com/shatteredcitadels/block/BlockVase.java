package com.shatteredcitadels.block;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.config.MobTiers;
import com.shatteredcitadels.loot.ModLootTables;
import com.shatteredcitadels.mob.MobEntry;
import com.shatteredcitadels.mob.MobTierRegistry;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.FloorData;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Breakable vase: shatters in one hit, drops loot from the vase table of the floor's difficulty
 * and sometimes releases a mob of the floor's tier. Never drops itself.
 */
public class BlockVase extends Block {

    public enum Variant implements IStringSerializable {
        CLAY, DARK, GILDED;

        @Override
        public String getName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Variant byMeta(int meta) {
            return meta >= 0 && meta < values().length ? values()[meta] : CLAY;
        }
    }

    public static final PropertyEnum<Variant> VARIANT = PropertyEnum.create("variant", Variant.class);
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0.1875D, 0.0D, 0.1875D, 0.8125D, 0.875D, 0.8125D);

    public BlockVase() {
        super(Material.GLASS);
        setHardness(0.0F);
        setSoundType(SoundType.GLASS);
        setCreativeTab(CreativeTabs.DECORATIONS);
        setDefaultState(blockState.getBaseState().withProperty(VARIANT, Variant.CLAY));
    }

    @Override
    public void onBlockHarvested(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        super.onBlockHarvested(world, pos, state, player);
        if (world.isRemote || !(world instanceof WorldServer) || player.capabilities.isCreativeMode) {
            return;
        }
        shatter((WorldServer) world, pos, state.getValue(VARIANT), player);
    }

    private static void shatter(WorldServer world, BlockPos pos, Variant variant, EntityPlayer player) {
        world.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0F, 0.55F + world.rand.nextFloat() * 0.2F);
        CitadelInstance citadel = CitadelWorldSavedData.get(world).getCitadelAt(pos);
        FloorData floor = citadel == null ? null : citadel.floorAt(pos);
        int difficulty = floor == null ? 1 : floor.getDifficultyTier();
        if (variant == Variant.GILDED) {
            difficulty += 2;
        }
        try {
            LootTable table = world.getLootTableManager().getLootTableFromLocation(ModLootTables.forVase(difficulty));
            LootContext context = new LootContext.Builder(world).withLuck(player.getLuck()).withPlayer(player).build();
            for (ItemStack stack : table.generateLootForPools(world.rand, context)) {
                spawnAsEntity(world, pos, stack);
            }
        } catch (RuntimeException e) {
            ShatteredCitadelsMod.LOGGER.warn("Vase loot failed at {}: {}", pos, e.toString());
        }
        if (world.rand.nextDouble() < ConfigCache.get().getVaseMobChance()) {
            releaseMob(world, pos, floor);
        }
    }

    private static void releaseMob(WorldServer world, BlockPos pos, FloorData floor) {
        int tier = MobTiers.clamp(floor == null ? 1 : Math.min(floor.getDifficultyTier(), MobTiers.MAX_TIER));
        boolean underground = floor != null && floor.getIndex() < 0;
        List<MobEntry> picked = MobTierRegistry.get().pickCombo(world.rand, tier, underground, 1);
        ResourceLocation id = picked.isEmpty() ? new ResourceLocation("minecraft", "silverfish") : picked.get(0).getId();
        try {
            Entity entity = EntityList.createEntityByIDFromName(id, world);
            if (!(entity instanceof EntityLiving)) {
                return;
            }
            EntityLiving mob = (EntityLiving) entity;
            mob.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.rand.nextFloat() * 360.0F, 0.0F);
            mob.onInitialSpawn(world.getDifficultyForLocation(pos), null);
            world.spawnEntity(mob);
            mob.spawnExplosionParticle();
        } catch (VirtualMachineError e) {
            if (!(e instanceof StackOverflowError)) {
                throw e;
            }
        } catch (Throwable t) {
            ShatteredCitadelsMod.LOGGER.debug("Vase mob {} could not spawn: {}", id, t.toString());
        }
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public boolean canSilkHarvest(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
        for (Variant variant : Variant.values()) {
            items.add(new ItemStack(this, 1, variant.ordinal()));
        }
    }

    @Override
    public int damageDropped(IBlockState state) {
        return state.getValue(VARIANT).ordinal();
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(VARIANT, Variant.byMeta(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(VARIANT).ordinal();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, VARIANT);
    }
}
