package com.shatteredcitadels.command;

import com.shatteredcitadels.compat.castledungeons.CastleDungeonsIntegration;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.proxy.CommonProxy;
import com.shatteredcitadels.structure.CitadelDataRecord;
import com.shatteredcitadels.structure.CitadelSavedData;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.CitadelPlan;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Operator command for pack makers: list/inspect tracked structures, print resolved palettes,
 * show the Castle Dungeons integration status, and force-generate a citadel or crypt.
 */
public final class CommandShatteredCitadels extends CommandBase {

    private static final String[] SUBCOMMANDS = {"list", "info", "palettes", "status", "generate"};

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
        CitadelSavedData data = CitadelSavedData.get(world);
        switch (args[0]) {
            case "list":
                send(sender, "Tracked structures in dim " + world.provider.getDimension() + ": " + data.size());
                for (CitadelDataRecord record : data.all()) {
                    send(sender, describe(record));
                }
                break;
            case "info": {
                BlockPos pos = sender.getPosition();
                CitadelDataRecord record = data.findContaining(pos);
                if (record == null) {
                    send(sender, "No tracked structure at " + pos);
                } else {
                    send(sender, describe(record));
                    CitadelDataRecord.FloorSpan floor = record.floorAt(pos.getY());
                    send(sender, "Floor: " + (floor == null ? "none" : floor.index + " [" + floor.minY + ".." + floor.maxY + "]")
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
                break;
            case "generate":
                generate(sender, world, args);
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
        String kind = args[1].toLowerCase(java.util.Locale.ROOT);
        CitadelDataRecord record;
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
        send(sender, "Generated " + describe(record));
    }

    private static String describe(CitadelDataRecord record) {
        StructureBoundingBox b = record.getTotalBounds();
        return "#" + record.getId() + " " + record.getType() + " (" + record.getArchetype() + ") floors="
                + record.getFloors().size() + " bounds=[" + b.minX + "," + b.minY + "," + b.minZ + " -> "
                + b.maxX + "," + b.maxY + "," + b.maxZ + "]" + (record.getBossAltarPos() != null
                ? " altar=" + record.getBossAltarPos().getX() + "," + record.getBossAltarPos().getY() + "," + record.getBossAltarPos().getZ()
                : "");
    }

    private static void send(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString(message));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
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
