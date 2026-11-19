package com.shatteredcitadels.command;

import com.shatteredcitadels.boss.ArenaSpots;
import com.shatteredcitadels.boss.BossCutsceneHandler;
import com.shatteredcitadels.boss.BossTags;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.config.MobTiers;
import com.shatteredcitadels.floor.FloorTrackerHandler;
import com.shatteredcitadels.mob.MobEntry;
import com.shatteredcitadels.mob.MobTierRegistry;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.proxy.CommonProxy;
import com.shatteredcitadels.spawner.WaveMobFactory;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.FloorData;
import com.shatteredcitadels.tile.TileEntityBossAltar;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.CitadelPlan;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * {@code /citadel} - debugging tools for pack makers and testers (operator level 2):
 * spawn, locate, floorinfo, listmobs and testboss.
 */
public final class CommandCitadel extends CommandBase {

    private static final String[] SUBCOMMANDS = {"spawn", "locate", "floorinfo", "listmobs", "testboss"};

    @Override
    public String getName() {
        return "citadel";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.citadel.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn":
                spawn(sender, args);
                break;
            case "locate":
                locate(sender);
                break;
            case "floorinfo":
                floorInfo(sender);
                break;
            case "listmobs":
                listMobs(sender, args);
                break;
            case "testboss":
                testBoss(sender);
                break;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    /** /citadel spawn &lt;archetype|sky|dungeon&gt; - generates at the sender's chunk and registers it. */
    private void spawn(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException(getUsage(sender));
        }
        World world = sender.getEntityWorld();
        BlockPos pos = sender.getPosition();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        ensureSiteFree(world, chunkX, chunkZ);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                world.getChunk(chunkX + dx, chunkZ + dz);
            }
        }
        StructureBoundingBox clip = BlockWriter.populationBox(chunkX, chunkZ);
        int minX = (chunkX << 4) + 1;
        int minZ = (chunkZ << 4) + 1;
        Random random = new Random(world.getSeed() ^ pos.toLong() ^ System.nanoTime());
        String kind = args[1].toLowerCase(Locale.ROOT);
        CitadelInstance created;
        if ("dungeon".equals(kind) || "crypt".equals(kind)) {
            created = CommonProxy.WORLD_GENERATOR.buildStandaloneCrypt(world, minX, minZ, random, clip, true);
        } else {
            boolean sky = "sky".equals(kind) || "sky_citadel".equals(kind);
            CitadelArchetype archetype = sky ? CitadelArchetype.SKY_CITADEL : CitadelArchetype.byKey(kind);
            if (archetype == null) {
                throw new CommandException("Unknown type '" + kind + "'. Use: " + String.join(", ", kinds()));
            }
            CitadelPlan plan = CommonProxy.WORLD_GENERATOR.planCitadel(world, minX, minZ, random, sky, archetype, true);
            if (plan == null) {
                throw new CommandException("No room below the build limit here");
            }
            created = CommonProxy.WORLD_GENERATOR.buildCitadel(world, plan, random, clip);
        }
        if (created == null) {
            throw new CommandException("Generation failed at " + minX + " " + minZ);
        }
        StructureBoundingBox b = created.getTotalBounds();
        sender.sendMessage(line("Generated ", TextFormatting.GREEN).appendSibling(CitadelNameGenerator.toComponent(created.getDisplayName()))
                .appendText(" (" + created.getType() + ") at " + b.minX + " " + b.minY + " " + b.minZ + " -> "
                        + b.maxX + " " + b.maxY + " " + b.maxZ));
    }

    /** /citadel locate - nearest tracked structure of the sender's dimension. */
    private void locate(ICommandSender sender) throws CommandException {
        World world = sender.getEntityWorld();
        BlockPos pos = sender.getPosition();
        CitadelInstance nearest = null;
        double best = Double.MAX_VALUE;
        for (CitadelInstance citadel : CitadelWorldSavedData.get(world).getAll()) {
            StructureBoundingBox b = citadel.getTotalBounds();
            double dx = (b.minX + b.maxX) / 2.0D - pos.getX();
            double dz = (b.minZ + b.maxZ) / 2.0D - pos.getZ();
            double d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                nearest = citadel;
            }
        }
        if (nearest == null) {
            throw new CommandException("No citadel or dungeon has been generated in this dimension yet");
        }
        StructureBoundingBox b = nearest.getTotalBounds();
        int x = (b.minX + b.maxX) / 2;
        int z = (b.minZ + b.maxZ) / 2;
        int y = nearest.getOrigin().getY();
        sender.sendMessage(line("Nearest: ", TextFormatting.GOLD).appendSibling(CitadelNameGenerator.toComponent(nearest.getDisplayName()))
                .appendText(" - " + nearest.getType() + " (" + nearest.getArchetypeKey() + ") at " + x + " " + y + " " + z
                        + ", " + Math.round(Math.sqrt(best)) + " blocks away"));
    }

    /** /citadel floorinfo - everything about the floor the sender stands on. */
    private void floorInfo(ICommandSender sender) throws CommandException {
        World world = sender.getEntityWorld();
        BlockPos pos = sender.getPosition();
        CitadelInstance citadel = CitadelWorldSavedData.get(world).getCitadelAt(pos);
        if (citadel == null) {
            throw new CommandException("You are not inside a tracked citadel or dungeon");
        }
        FloorData floor = citadel.floorAt(pos.getY());
        ConfigCache.Snapshot config = ConfigCache.get();
        sender.sendMessage(line("Citadel ", TextFormatting.GOLD).appendSibling(CitadelNameGenerator.toComponent(citadel.getDisplayName()))
                .appendText(" [" + citadel.getCitadelId() + "] " + citadel.getType() + " / " + citadel.getArchetypeKey()));
        if (floor == null) {
            send(sender, "Not on a floor (foundation, roof or between levels)");
        } else {
            String tag = WaveMobFactory.floorTag(citadel.getCitadelId(), floor.getIndex());
            int mobs = world.getEntities(EntityLiving.class, e -> e != null && e.isEntityAlive() && WaveMobFactory.belongsTo(e, tag)).size();
            send(sender, "Floor " + floor.getIndex() + " [" + floor.getMinY() + ".." + floor.getMaxY() + "], difficulty tier "
                    + floor.getDifficultyTier() + (floor.isBossFloor() ? ", boss floor" : ""));
            send(sender, "Wave " + floor.getActiveWave() + "/" + floor.getTotalWaves() + ", remaining " + floor.getRemainingMobs()
                    + ", cleared " + floor.isCleared() + "; wave mobs alive " + mobs + " / entity cap "
                    + config.getMaxActiveMobsPerFloor() + "; players on floor "
                    + FloorTrackerHandler.countPlayersOnFloor(world, citadel.getCitadelId(), floor.getIndex()));
        }
        send(sender, arenaStatus(world, citadel));
    }

    private static String arenaStatus(World world, CitadelInstance citadel) {
        StructureBoundingBox arena = citadel.getBossArenaBounds();
        if (arena == null || citadel.getBossAltarPos() == null) {
            return "Boss arena: none";
        }
        FloorData bossFloor = citadel.getBossFloor();
        TileEntity tile = world.getTileEntity(citadel.getBossAltarPos().down());
        StringBuilder text = new StringBuilder("Boss arena [" + arena.minX + "," + arena.minY + "," + arena.minZ + " -> "
                + arena.maxX + "," + arena.maxY + "," + arena.maxZ + "], defeated " + (bossFloor != null && bossFloor.isBossDefeated())
                + ", anti-grief " + ConfigCache.get().isStrictArenaAntiGrief());
        if (tile instanceof TileEntityBossAltar) {
            TileEntityBossAltar altar = (TileEntityBossAltar) tile;
            text.append(", altar ").append(altar.getState()).append(", cutscene running ")
                    .append(BossCutsceneHandler.hasActiveCutscene(citadel.getCitadelId()));
            Entity boss = altar.getBossUuid() == null || !(world instanceof WorldServer) ? null
                    : ((WorldServer) world).getEntityFromUuid(altar.getBossUuid());
            if (boss instanceof EntityLivingBase && boss.isEntityAlive()) {
                EntityLivingBase living = (EntityLivingBase) boss;
                text.append(", boss HP ").append(Math.round(living.getHealth())).append('/').append(Math.round(living.getMaxHealth()))
                        .append(" phase ").append(boss.getEntityData().getInteger(BossTags.PHASE))
                        .append(", inside arena ").append(ArenaSpots.isInside(arena, boss));
            }
        } else {
            text.append(", altar block missing");
        }
        return text.toString();
    }

    /** /citadel listmobs &lt;1-5|underground&gt; */
    private void listMobs(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException(getUsage(sender));
        }
        MobTierRegistry registry = MobTierRegistry.get();
        List<MobEntry> entries;
        String label;
        if ("underground".equalsIgnoreCase(args[1])) {
            entries = registry.getUnderground();
            label = "Underground pool";
        } else {
            int tier = parseInt(args[1], MobTiers.MIN_TIER, MobTiers.MAX_TIER);
            entries = registry.getTier(tier);
            label = "Tier " + tier;
        }
        send(sender, label + " (" + entries.size() + " mobs, scanned " + registry.isScanned() + "):");
        for (MobEntry entry : entries) {
            send(sender, "  " + entry.getId() + "  power " + Math.round(entry.getPower())
                    + (entry.isUnderground() ? ", underground" : "") + (entry.isManual() ? ", manual" : "")
                    + (entry.isReinforcedFallback() ? ", vanilla fallback" : ""));
        }
    }

    /** /citadel testboss - force the boss of the structure the sender is in. */
    private void testBoss(ICommandSender sender) throws CommandException {
        World world = sender.getEntityWorld();
        CitadelInstance citadel = CitadelWorldSavedData.get(world).getCitadelAt(sender.getPosition());
        if (citadel == null || citadel.getBossAltarPos() == null) {
            throw new CommandException("Stand inside a citadel or dungeon that has a boss arena");
        }
        TileEntity tile = world.getTileEntity(citadel.getBossAltarPos().down());
        if (!(tile instanceof TileEntityBossAltar)) {
            throw new CommandException("The boss altar of this structure is missing");
        }
        String failure = ((TileEntityBossAltar) tile).forceSummon();
        if (failure != null) {
            throw new CommandException("Boss not summoned: " + failure);
        }
        BlockPos altar = citadel.getBossAltarPos();
        send(sender, "Boss summoned at the altar " + altar.getX() + " " + altar.getY() + " " + altar.getZ() + " (cutscene skipped)");
    }

    /** Refuses a site whose 2x2 chunk footprint already holds a tracked structure (no overlapping builds). */
    static void ensureSiteFree(World world, int chunkX, int chunkZ) throws CommandException {
        CitadelWorldSavedData data = CitadelWorldSavedData.get(world);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                if (data.isChunkOccupied(chunkX + dx, chunkZ + dz)) {
                    throw new CommandException("Chunk " + (chunkX + dx) + "," + (chunkZ + dz)
                            + " already holds a citadel or dungeon; move at least two chunks away");
                }
            }
        }
    }

    private static List<String> kinds() {
        List<String> kinds = new ArrayList<>();
        for (CitadelArchetype archetype : CitadelArchetype.values()) {
            kinds.add(archetype.key());
        }
        kinds.add("sky");
        kinds.add("dungeon");
        return kinds;
    }

    private static ITextComponent line(String text, TextFormatting color) {
        ITextComponent component = new TextComponentString(text);
        component.getStyle().setColor(color);
        return component;
    }

    private static void send(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString(message));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        }
        if (args.length == 2 && "spawn".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, kinds());
        }
        if (args.length == 2 && "listmobs".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "1", "2", "3", "4", "5", "underground");
        }
        return Collections.emptyList();
    }
}
