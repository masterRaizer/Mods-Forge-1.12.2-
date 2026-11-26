package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.block.BlockVase;
import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.loot.ModLootTables;
import com.shatteredcitadels.palette.BlockPalette;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.structure.RoomTag;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Furnishes rooms so they look lived in and then abandoned: pantries, bedrooms and smelting
 * workshops, vases, cobwebs, rubble and overturned furniture. Modded furniture (MrCrayfish,
 * Future MC) is looked up by registry name and simply skipped when the mod is missing.
 * Writes only through the {@link Site}, so every builder keeps its own clipping and reservations.
 */
public final class RoomFurnisher {

    /** What a builder exposes to the furnisher. */
    public interface Site {
        /** @return true if decor may go at this position (inside the clip, not reserved, air). */
        boolean free(int x, int y, int z);

        IBlockState get(int x, int y, int z);

        boolean set(int x, int y, int z, IBlockState state);

        void chest(BlockPos pos, EnumFacing facing, ResourceLocation lootTable);

        /** Configures the tile entity at {@code pos} once the block has been written. */
        void afterPlace(BlockPos pos, Blueprint.TileAction action);
    }

    private final Site site;
    private final Random rand;
    private final BlockPalette pal;
    private final BlockVase.Variant vaseVariant;

    public RoomFurnisher(Site site, Random random, BlockPalette palette, BlockVase.Variant vaseVariant) {
        this.site = site;
        this.rand = random;
        this.pal = palette;
        this.vaseVariant = vaseVariant;
    }

    /** @return true if the tag is one of the lived-in rooms furnished here. */
    public static boolean isLivedIn(RoomTag tag) {
        return tag == RoomTag.PANTRY || tag == RoomTag.BEDROOM || tag == RoomTag.SMELTERY;
    }

    /** Furnishes a lived-in room; the floor of the room is at {@code y0}, its interior is y0+1.. */
    public void furnish(RoomTag tag, int minX, int minZ, int maxX, int maxZ, int y0) {
        List<int[]> walls = wallCells(minX, minZ, maxX, maxZ, y0 + 1);
        switch (tag) {
            case PANTRY:
                pantry(walls, y0);
                break;
            case BEDROOM:
                bedroom(walls, minX, minZ, maxX, maxZ, y0);
                break;
            case SMELTERY:
                smeltery(walls, minX, minZ, maxX, maxZ, y0);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ room types

    private void pantry(List<int[]> walls, int y0) {
        boolean fridge = false;
        boolean oven = false;
        boolean chest = false;
        for (int[] c : walls) {
            if (rand.nextFloat() > 0.75F) {
                continue;
            }
            EnumFacing facing = EnumFacing.byHorizontalIndex(c[3]);
            int y = y0 + 1;
            if (!chest) {
                site.chest(new BlockPos(c[0], y, c[2]), facing, ModLootTables.PANTRY);
                chest = true;
                continue;
            }
            int roll = rand.nextInt(12);
            if (!fridge && roll == 0) {
                IBlockState body = ModPaletteRegistry.resolveDecor("cfm:fridge", "cfm:*fridge*");
                if (body != null && site.free(c[0], y + 1, c[2]) && place(c[0], y, c[2], body, facing)) {
                    IBlockState top = ModPaletteRegistry.resolveDecor("cfm:freezer", "cfm:*freezer*");
                    if (top != null) {
                        place(c[0], y + 1, c[2], top, facing);
                    }
                    stock(new BlockPos(c[0], y, c[2]), ModLootTables.PANTRY, 1.0F);
                    fridge = true;
                    continue;
                }
            }
            if (!oven && roll == 1) {
                IBlockState stove = ModPaletteRegistry.resolveDecor("cfm:oven", "cfm:*oven*", "modernbackports:smoker", "futuremc:smoker");
                if (stove != null && place(c[0], y, c[2], stove, facing)) {
                    oven = true;
                    continue;
                }
            }
            if (roll <= 4) {
                IBlockState barrel = ModPaletteRegistry.resolveDecor("modernbackports:barrel", "futuremc:barrel");
                if (barrel != null && place(c[0], y, c[2], barrel, EnumFacing.UP)) {
                    stock(new BlockPos(c[0], y, c[2]), ModLootTables.PANTRY, 0.35F);
                    continue;
                }
            }
            if (roll <= 6) {
                IBlockState counter = ModPaletteRegistry.resolveDecor("cfm:*counter*", "cfm:*kitchen*");
                if (counter != null && place(c[0], y, c[2], counter, facing)) {
                    continue;
                }
            }
            IBlockState food = roll <= 8 ? Blocks.HAY_BLOCK.getDefaultState()
                    : roll == 9 ? Blocks.MELON_BLOCK.getDefaultState()
                    : roll == 10 ? orient(Blocks.PUMPKIN.getDefaultState(), facing)
                    : ModPaletteRegistry.resolveDecor("modernbackports:honeycomb_block", "futuremc:honeycomb_block", "minecraft:hay_block");
            place(c[0], y, c[2], food, facing);
            if (rand.nextInt(4) == 0 && site.free(c[0], y + 1, c[2])) {
                // Stacked crates of food.
                place(c[0], y + 1, c[2], Blocks.HAY_BLOCK.getDefaultState(), facing);
            }
        }
    }

    private void bedroom(List<int[]> walls, int minX, int minZ, int maxX, int maxZ, int y0) {
        int y = y0 + 1;
        int beds = 1 + rand.nextInt(2);
        boolean chest = false;
        for (int[] c : walls) {
            EnumFacing intoRoom = EnumFacing.byHorizontalIndex(c[3]);
            if (beds > 0) {
                int fx = c[0] + intoRoom.getXOffset();
                int fz = c[2] + intoRoom.getZOffset();
                if (fx >= minX && fx <= maxX && fz >= minZ && fz <= maxZ && site.free(fx, y, fz)) {
                    placeBed(c[0], y, c[2], fx, fz, intoRoom.getOpposite());
                    beds--;
                    if (!chest && site.free(fx + intoRoom.getXOffset(), y, fz + intoRoom.getZOffset())) {
                        site.chest(new BlockPos(fx + intoRoom.getXOffset(), y, fz + intoRoom.getZOffset()),
                                intoRoom, ModLootTables.BEDROOM);
                        chest = true;
                    }
                    continue;
                }
            }
            if (rand.nextFloat() > 0.45F) {
                continue;
            }
            IBlockState decor = rand.nextBoolean()
                    ? ModPaletteRegistry.resolveDecor("cfm:*bedside*", "cfm:*cabinet*", "minecraft:bookshelf")
                    : ModPaletteRegistry.resolveDecor("cfm:*wardrobe*", "minecraft:bookshelf");
            place(c[0], y, c[2], decor, intoRoom);
        }
        if (!chest && !walls.isEmpty()) {
            int[] c = walls.get(walls.size() - 1);
            site.chest(new BlockPos(c[0], y, c[2]), EnumFacing.byHorizontalIndex(c[3]), ModLootTables.BEDROOM);
        }
        // Worn rug in the middle of the room.
        EnumDyeColor color = EnumDyeColor.byMetadata(rand.nextInt(16));
        int cx = (minX + maxX) / 2;
        int cz = (minZ + maxZ) / 2;
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                if (rand.nextFloat() < 0.8F && site.free(x, y, z)) {
                    site.set(x, y, z, Blocks.CARPET.getDefaultState().withProperty(BlockCarpet.COLOR, color));
                }
            }
        }
    }

    private void smeltery(List<int[]> walls, int minX, int minZ, int maxX, int maxZ, int y0) {
        int y = y0 + 1;
        boolean chest = false;
        boolean cauldron = false;
        boolean table = false;
        for (int[] c : walls) {
            EnumFacing facing = EnumFacing.byHorizontalIndex(c[3]);
            if (!chest) {
                site.chest(new BlockPos(c[0], y, c[2]), facing, ModLootTables.SMELTERY);
                chest = true;
                continue;
            }
            if (rand.nextFloat() > 0.8F) {
                continue;
            }
            int roll = rand.nextInt(10);
            IBlockState block;
            if (roll < 4) {
                block = Blocks.FURNACE.getDefaultState();
            } else if (roll < 6) {
                block = ModPaletteRegistry.resolveDecor("modernbackports:blast_furnace", "futuremc:blast_furnace", "minecraft:furnace");
            } else if (roll == 6) {
                block = ModPaletteRegistry.resolveDecor("modernbackports:smoker", "futuremc:smoker", "minecraft:furnace");
            } else if (roll == 7 && !cauldron) {
                block = Blocks.CAULDRON.getDefaultState().withProperty(BlockCauldron.LEVEL, rand.nextInt(2));
                cauldron = true;
            } else if (roll == 8 && !table) {
                block = Blocks.CRAFTING_TABLE.getDefaultState();
                table = true;
            } else {
                block = ModPaletteRegistry.resolveDecor("immersiveengineering:stone_decoration[type=blastbrick]",
                        "minecraft:brick_block");
            }
            place(c[0], y, c[2], block, facing);
        }
        // Anvils on the workshop floor, worn by years of hammering.
        int anvils = 1 + rand.nextInt(2);
        for (int i = 0; i < anvils * 4 && anvils > 0; i++) {
            int x = minX + 1 + rand.nextInt(Math.max(1, maxX - minX - 1));
            int z = minZ + 1 + rand.nextInt(Math.max(1, maxZ - minZ - 1));
            if (site.free(x, y, z)) {
                site.set(x, y, z, Blocks.ANVIL.getDefaultState()
                        .withProperty(BlockAnvil.FACING, EnumFacing.byHorizontalIndex(rand.nextInt(4)))
                        .withProperty(BlockAnvil.DAMAGE, 1 + rand.nextInt(2)));
                anvils--;
            }
        }
    }

    // ------------------------------------------------------------------ shared decor

    /** Places one loot chest against a wall of the room, facing into it. */
    public void chestAgainstWall(int minX, int minZ, int maxX, int maxZ, int y0, ResourceLocation lootTable) {
        List<int[]> walls = wallCells(minX, minZ, maxX, maxZ, y0 + 1);
        if (!walls.isEmpty()) {
            int[] c = walls.get(0);
            site.chest(new BlockPos(c[0], y0 + 1, c[2]), EnumFacing.byHorizontalIndex(c[3]), lootTable);
        }
    }

    /** Places up to {@code count} vases along the walls of the room. */
    public void vases(int minX, int minZ, int maxX, int maxZ, int y0, int count) {
        List<int[]> walls = wallCells(minX, minZ, maxX, maxZ, y0 + 1);
        int placed = 0;
        for (int[] c : walls) {
            if (placed >= count) {
                return;
            }
            if (site.free(c[0], y0 + 1, c[2])) {
                BlockVase.Variant variant = rand.nextInt(10) == 0 ? BlockVase.Variant.GILDED : vaseVariant;
                if (site.set(c[0], y0 + 1, c[2], ModBlocks.VASE.getDefaultState().withProperty(BlockVase.VARIANT, variant))) {
                    placed++;
                }
            }
        }
    }

    /** Cobwebs in the upper corners, rubble and an overturned stair: years of neglect. */
    public void abandon(int minX, int minZ, int maxX, int maxZ, int y0, int height) {
        int top = y0 + height - 1;
        int[][] corners = {{minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}};
        for (int[] c : corners) {
            if (rand.nextFloat() < 0.45F && site.free(c[0], top, c[1])) {
                site.set(c[0], top, c[1], Blocks.WEB.getDefaultState());
            }
            if (rand.nextFloat() < 0.2F && site.free(c[0], top - 1, c[1])) {
                site.set(c[0], top - 1, c[1], Blocks.WEB.getDefaultState());
            }
        }
        int rubble = rand.nextInt(3);
        for (int i = 0; i < rubble * 3 && rubble > 0; i++) {
            int x = minX + rand.nextInt(maxX - minX + 1);
            int z = minZ + rand.nextInt(maxZ - minZ + 1);
            if (site.free(x, y0 + 1, z)) {
                site.set(x, y0 + 1, z, rand.nextBoolean() ? Blocks.MOSSY_COBBLESTONE.getDefaultState()
                        : Blocks.COBBLESTONE.getDefaultState());
                rubble--;
            }
        }
        if (rand.nextFloat() < 0.3F) {
            int x = minX + rand.nextInt(maxX - minX + 1);
            int z = minZ + rand.nextInt(maxZ - minZ + 1);
            if (site.free(x, y0 + 1, z)) {
                site.set(x, y0 + 1, z, pal.stairs(EnumFacing.byHorizontalIndex(rand.nextInt(4)), true));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * @return free cells along the room's walls as {x, y, z, facingIntoRoom}, shuffled; cells in
     * front of a doorway (no solid wall behind them) are left out so nothing blocks a door.
     */
    private List<int[]> wallCells(int minX, int minZ, int maxX, int maxZ, int y) {
        List<int[]> cells = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                EnumFacing wall = x == minX ? EnumFacing.WEST : x == maxX ? EnumFacing.EAST
                        : z == minZ ? EnumFacing.NORTH : z == maxZ ? EnumFacing.SOUTH : null;
                if (wall == null || !site.free(x, y, z)) {
                    continue;
                }
                IBlockState behind = site.get(x + wall.getXOffset(), y, z + wall.getZOffset());
                if (!behind.getMaterial().isSolid()) {
                    continue;
                }
                cells.add(new int[]{x, y, z, wall.getOpposite().getHorizontalIndex()});
            }
        }
        Collections.shuffle(cells, rand);
        return cells;
    }

    private boolean place(int x, int y, int z, IBlockState state, EnumFacing facing) {
        return state != null && site.free(x, y, z) && site.set(x, y, z, orient(state, facing));
    }

    /** Turns any block with a horizontal "facing" property to face {@code facing}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static IBlockState orient(IBlockState state, EnumFacing facing) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if ("facing".equals(property.getName()) && property instanceof PropertyDirection
                    && property.getAllowedValues().contains(facing)) {
                return state.withProperty((PropertyDirection) property, facing);
            }
        }
        return state;
    }

    private void placeBed(int headX, int y, int headZ, int footX, int footZ, EnumFacing towardsHead) {
        IBlockState bed = Blocks.BED.getDefaultState().withProperty(BlockBed.FACING, towardsHead)
                .withProperty(BlockBed.OCCUPIED, false);
        EnumDyeColor color = EnumDyeColor.byMetadata(rand.nextInt(16));
        if (site.set(footX, y, footZ, bed.withProperty(BlockBed.PART, BlockBed.EnumPartType.FOOT))
                && site.set(headX, y, headZ, bed.withProperty(BlockBed.PART, BlockBed.EnumPartType.HEAD))) {
            Blueprint.TileAction dye = (world, at) -> {
                TileEntity tile = world.getTileEntity(at);
                if (tile instanceof TileEntityBed) {
                    ((TileEntityBed) tile).setColor(color);
                }
            };
            site.afterPlace(new BlockPos(footX, y, footZ), dye);
            site.afterPlace(new BlockPos(headX, y, headZ), dye);
        }
    }

    /** Gives a container the loot table (Lootr-friendly) or, failing that, fills it directly. */
    private void stock(BlockPos pos, ResourceLocation table, float chance) {
        if (rand.nextFloat() >= chance) {
            return;
        }
        long seed = rand.nextLong();
        site.afterPlace(pos, (world, at) -> {
            TileEntity tile = world.getTileEntity(at);
            try {
                if (tile instanceof TileEntityLockableLoot) {
                    ((TileEntityLockableLoot) tile).setLootTable(table, seed);
                } else if (tile instanceof IInventory && world instanceof WorldServer) {
                    // Small appliance inventories (fridges): what fits goes into random free slots, no overflow.
                    Random random = new Random(seed);
                    IInventory inventory = (IInventory) tile;
                    List<Integer> free = new ArrayList<>();
                    for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                        if (inventory.getStackInSlot(slot).isEmpty()) {
                            free.add(slot);
                        }
                    }
                    Collections.shuffle(free, random);
                    List<ItemStack> loot = ((WorldServer) world).getLootTableManager().getLootTableFromLocation(table)
                            .generateLootForPools(random, new LootContext.Builder((WorldServer) world).build());
                    for (int i = 0; i < loot.size() && i < free.size(); i++) {
                        inventory.setInventorySlotContents(free.get(i), loot.get(i));
                    }
                }
            } catch (RuntimeException e) {
                ShatteredCitadelsMod.LOGGER.debug("Could not stock {} at {}: {}", tile, at, e.toString());
            }
        });
    }
}
