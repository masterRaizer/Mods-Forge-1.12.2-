package com.shatteredcitadels.command;

import com.shatteredcitadels.compat.castledungeons.CastleDungeonsIntegration;
import com.shatteredcitadels.config.MobTiers;
import com.shatteredcitadels.floor.FloorTrackerHandler;
import com.shatteredcitadels.mob.MobEntry;
import com.shatteredcitadels.mob.MobTierRegistry;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.proxy.CommonProxy;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.FloorData;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.CitadelPlan;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Operator command for pack makers: list/inspect tracked structures, print resolved palettes,
 * show the Castle Dungeons integration status, and force-generate a citadel or crypt.
 */
public final class CommandShatteredCitadels extends CommandBase {

    private static final String[] SUBCOMMANDS = {"list", "info", "palettes", "status", "generate", "floorstate", "mobs"};

    @Override
    public String getName() {
        return "shatteredcitadels";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("scitadels");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.shatteredcitadels.usage";
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
        World world = sender.getEntityWorld();
        CitadelWorldSavedData data = CitadelWorldSavedData.get(world);
        switch (args[0]) {
            case "list":
                send(sender, "Tracked structures in dim " + world.provider.getDimension() + ": " + data.size());
                for (CitadelInstance record : data.getAll()) {
                    sender.sendMessage(describe(record));
                }
                break;
            case "info": {
                BlockPos pos = sender.getPosition();
                CitadelInstance record = data.getCitadelAt(pos);
                if (record == null) {
                    send(sender, "No tracked structure at " + pos);
                } else {
                    sender.sendMessage(describe(record));
                    FloorData floor = record.floorAt(pos.getY());
                    send(sender, "Floor: " + (floor == null ? "none" : floor.getIndex() + " [" + floor.getMinY() + ".."
                            + floor.getMaxY() + "] tier " + floor.getDifficultyTier() + ", wave " + floor.getActiveWave()
                            + "/" + floor.getTotalWaves() + ", remaining " + floor.getRemainingMobs()
                            + (floor.isCleared() ? ", cleared" : ""))
                            + ", spawners " + record.getSpawnerPoints().size() + ", chests " + record.getChestPoints().size()
                            + ", rooms " + record.getRooms().size());
                }
                break;
            }
            case "palettes":
                for (CitadelArchetype archetype : CitadelArchetype.values()) {
                    send(sender, archetype.key() + ": " + ModPaletteRegistry.get(archetype).getSources());
                }
                break;
            case "status":
                send(sender, "Castle Dungeons installed: " + CastleDungeonsIntegration.isLoaded() + "; "
                        + CastleDungeonsIntegration.describeStatus());
                send(sender, "Floor tracker: " + FloorTrackerHandler.cachedPlayerCount() + " cached player state(s), "
                        + server.getCurrentPlayerCount() + " online");
                break;
            case "generate":
                generate(sender, world, args);
                break;
            case "floorstate":
                floorState(server, sender, args);
                break;
            case "mobs":
                listMobs(sender);
                break;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private void generate(ICommandSender sender, World world, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException(getUsage(sender));
        }
        BlockPos pos = sender.getPosition();
        int x = args.length >= 4 ? parseInt(args[2]) : pos.getX();
        int z = args.length >= 4 ? parseInt(args[3]) : pos.getZ();
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        // A command may load chunks: make the 2x2 build area exist before writing.
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                world.getChunk(chunkX + dx, chunkZ + dz);
            }
        }
        StructureBoundingBox clip = BlockWriter.populationBox(chunkX, chunkZ);
        int minX = (chunkX << 4) + 1;
        int minZ = (chunkZ << 4) + 1;
        Random random = new Random(world.getSeed() ^ ((long) chunkX << 32) ^ chunkZ ^ System.nanoTime());
        String kind = args[1].toLowerCase(Locale.ROOT);
        CitadelInstance record;
        if ("crypt".equals(kind)) {
            record = CommonProxy.WORLD_GENERATOR.buildStandaloneCrypt(world, minX, minZ, random, clip, true);
        } else {
            boolean sky = "sky".equals(kind) || "sky_citadel".equals(kind);
            CitadelArchetype archetype = sky ? CitadelArchetype.SKY_CITADEL : CitadelArchetype.byKey(kind);
            if (archetype == null) {
                throw new CommandException("Unknown archetype '" + kind + "'");
            }
            CitadelPlan plan = CommonProxy.WORLD_GENERATOR.planCitadel(world, minX, minZ, random, sky, archetype, true);
            if (plan == null) {
                throw new CommandException("Site rejected: no room below the build limit");
            }
            record = CommonProxy.WORLD_GENERATOR.buildCitadel(world, plan, random, clip);
        }
        if (record == null) {
            throw new CommandException("Generation failed at " + minX + " " + minZ);
        }
        sender.sendMessage(new TextComponentString("Generated ").appendSibling(describe(record)));
    }

    /** floorstate <player> <activeWave> <remainingMobs> [cleared] [bossDefeated]: debug/admin override. */
    private void floorState(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 4) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP player = getPlayer(server, sender, args[1]);
        CitadelWorldSavedData data = CitadelWorldSavedData.get(player.world);
        CitadelInstance citadel = data.getCitadelAt(new BlockPos(player));
        FloorData floor = citadel == null ? null : citadel.floorAt(MathHelper.floor(player.posY));
        if (floor == null) {
            throw new CommandException("Player " + player.getName() + " is not on a citadel floor");
        }
        floor.setActiveWave(parseInt(args[2], 0));
        floor.setRemainingMobs(parseInt(args[3], 0));
        floor.setCleared(args.length > 4 && parseBoolean(args[4]));
        floor.setBossDefeated(args.length > 5 && parseBoolean(args[5]));
        data.markDirty();
        FloorTrackerHandler.resyncCitadel(player.world, citadel.getCitadelId());
        send(sender, "Floor " + floor.getIndex() + ": wave " + floor.getActiveWave() + "/" + floor.getTotalWaves()
                + ", remaining " + floor.getRemainingMobs() + ", cleared " + floor.isCleared());
    }

    private static void listMobs(ICommandSender sender) {
        MobTierRegistry registry = MobTierRegistry.get();
        send(sender, "Mob pools (scanned: " + registry.isScanned() + ", " + registry.size() + " entries):");
        for (int tier = MobTiers.MIN_TIER; tier <= MobTiers.MAX_TIER; tier++) {
            send(sender, "T" + tier + " " + summarize(registry.getTier(tier)));
        }
        send(sender, "Underground " + summarize(registry.getUnderground()));
    }

    private static String summarize(List<MobEntry> entries) {
        StringBuilder text = new StringBuilder("(" + entries.size() + "): ");
        for (int i = 0; i < entries.size() && i < 10; i++) {
            text.append(i == 0 ? "" : ", ").append(entries.get(i).getId());
        }
        return entries.size() > 10 ? text.append(", ...").toString() : text.toString();
    }

    private static ITextComponent describe(CitadelInstance record) {
        StructureBoundingBox b = record.getTotalBounds();
        ITextComponent line = new TextComponentString("[" + record.getCitadelId().toString().substring(0, 8) + "] ");
        line.appendSibling(CitadelNameGenerator.toComponent(record.getDisplayName()));
        line.appendText(" - " + record.getType() + " (" + record.getArchetypeKey() + "), floors "
                + record.getSurfaceFloorCount() + "+" + record.getUndergroundLevelCount() + " underground, bounds ["
                + b.minX + "," + b.minY + "," + b.minZ + " -> " + b.maxX + "," + b.maxY + "," + b.maxZ + "]");
        BlockPos altar = record.getBossAltarPos();
        if (altar != null) {
            line.appendText(", altar " + altar.getX() + "," + altar.getY() + "," + altar.getZ());
        }
        return line;
    }

    private static void send(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString(message));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        }
        if (args.length == 2 && "floorstate".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 2 && "generate".equals(args[0])) {
            List<String> kinds = new ArrayList<>();
            for (CitadelArchetype archetype : CitadelArchetype.values()) {
                kinds.add(archetype.key());
            }
            kinds.add("sky");
            kinds.add("crypt");
            return getListOfStringsMatchingLastWord(args, kinds);
        }
        return Collections.emptyList();
    }
}
