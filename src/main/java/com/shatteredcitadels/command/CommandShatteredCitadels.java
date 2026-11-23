package com.shatteredcitadels.command;

import com.shatteredcitadels.boss.ArenaSpots;
import com.shatteredcitadels.boss.BossCutsceneHandler;
import com.shatteredcitadels.boss.BossTags;
import com.shatteredcitadels.compat.castledungeons.CastleDungeonsIntegration;
import com.shatteredcitadels.config.MobTiers;
import com.shatteredcitadels.floor.FloorTrackerHandler;
import com.shatteredcitadels.item.ModItems;
import com.shatteredcitadels.item.RelicEquipment;
import com.shatteredcitadels.lore.LoreBookGenerator;
import com.shatteredcitadels.lore.LoreTracker;
import com.shatteredcitadels.mob.MobEntry;
import com.shatteredcitadels.mob.MobTierRegistry;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.palette.ModPaletteRegistry;
import com.shatteredcitadels.proxy.CommonProxy;
import com.shatteredcitadels.spawner.WaveMobFactory;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.FloorData;
import com.shatteredcitadels.tile.TileEntityBossAltar;
import com.shatteredcitadels.tile.TileEntityTrialSpawner;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.CastlePlanner;
import com.shatteredcitadels.worldgen.CitadelPlan;
import com.shatteredcitadels.worldgen.CitadelWorldGenerator;
import com.shatteredcitadels.worldgen.StructureJobs;
import com.shatteredcitadels.worldgen.StructurePlan;
import net.minecraft.block.material.Material;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
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

    private static final String[] SUBCOMMANDS = {"list", "info", "palettes", "status", "generate", "floorstate", "mobs", "spawners", "boss", "relics", "lore"};

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
                    FloorData floor = record.floorAt(pos);
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
                    send(sender, archetype.key() + ": " + ModPaletteRegistry.describe(archetype));
                }
                break;
            case "status":
                if (world instanceof net.minecraft.world.WorldServer) {
                    for (String jobLine : StructureJobs.get(world).describe((net.minecraft.world.WorldServer) world)) {
                        send(sender, "Building: " + jobLine);
                    }
                }
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
            case "spawners":
                listSpawners(server, sender, args);
                break;
            case "boss":
                bossInfo(server, sender, args);
                break;
            case "relics":
                relics(server, sender, args);
                break;
            case "lore": {
                if (args.length < 3) {
                    throw new WrongUsageException(getUsage(sender));
                }
                EntityPlayerMP target = getPlayer(server, sender, args[1]);
                int chapter = parseInt(args[2], 1, LoreBookGenerator.CHAPTERS);
                target.inventory.addItemStackToInventory(LoreBookGenerator.create(chapter));
                send(sender, "Gave lore chapter " + chapter + " (" + LoreBookGenerator.title(chapter) + ") to " + target.getName());
                break;
            }
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
        // generate <kind> [x z] [variant]
        String variant = args.length == 3 ? args[2] : args.length >= 5 ? args[4] : null;
        StructurePlan plan = CommandCitadel.planAndQueue(world, args[1], variant, x >> 4, z >> 4);
        send(sender, "Queued " + plan.describe() + "; it is written as its chunks load.");
    }

    /** floorstate <player> <activeWave> <remainingMobs> [cleared] [bossDefeated]: debug/admin override. */
    private void floorState(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 4) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP player = getPlayer(server, sender, args[1]);
        CitadelWorldSavedData data = CitadelWorldSavedData.get(player.world);
        CitadelInstance citadel = data.getCitadelAt(new BlockPos(player));
        FloorData floor = citadel == null ? null : citadel.floorAt(new BlockPos(player));
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

    /** spawners <player> [floorIndex]: state of every trial spawner on a floor. */
    private void listSpawners(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP player = getPlayer(server, sender, args[1]);
        CitadelInstance citadel = CitadelWorldSavedData.get(player.world).getCitadelAt(new BlockPos(player));
        if (citadel == null) {
            throw new CommandException("Player " + player.getName() + " is not inside a tracked structure");
        }
        FloorData playerFloor = citadel.floorAt(new BlockPos(player));
        int floorIndex = args.length > 2 ? parseInt(args[2]) : (playerFloor == null ? 0 : playerFloor.getIndex());
        FloorData floor = citadel.getFloor(floorIndex);
        String tag = WaveMobFactory.floorTag(citadel.getCitadelId(), floorIndex);
        int mobs = player.world.getEntities(EntityLiving.class,
                e -> e != null && e.isEntityAlive() && WaveMobFactory.belongsTo(e, tag)).size();
        send(sender, "Floor " + floorIndex + (floor == null ? " (unknown)" : " tier " + floor.getDifficultyTier()
                + ", wave " + floor.getActiveWave() + "/" + floor.getTotalWaves() + ", remaining "
                + floor.getRemainingMobs() + ", cleared " + floor.isCleared()) + "; wave mobs alive: " + mobs
                + "; players on floor: " + FloorTrackerHandler.countPlayersOnFloor(player.world, citadel.getCitadelId(), floorIndex));
        for (CitadelInstance.SpawnerPoint point : citadel.getSpawnerPoints()) {
            if (point.floorIndex != floorIndex) {
                continue;
            }
            TileEntity tile = player.world.isBlockLoaded(point.pos) ? player.world.getTileEntity(point.pos) : null;
            String state;
            if (tile instanceof TileEntityTrialSpawner) {
                TileEntityTrialSpawner s = (TileEntityTrialSpawner) tile;
                state = "wave " + s.getCurrentWave() + "/" + s.getTotalWaves() + (s.isWaveInProgress() ? " running" : " idle")
                        + ", alive " + s.getAliveSpawnedMobs() + ", remaining " + s.getRemainingMobs() + ", cooldown "
                        + s.getCooldownTicks() + ", exhausted " + s.isExhausted() + ", combo " + s.getAssignedMobCombo();
            } else {
                state = tile == null && player.world.isBlockLoaded(point.pos) ? "removed" : "not loaded / other";
            }
            send(sender, "  " + point.pos.getX() + "," + point.pos.getY() + "," + point.pos.getZ() + ": " + state);
        }
    }

    /** boss <player>: altar, cutscene, boss and minion state of the structure the player is in. */
    private void bossInfo(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP player = getPlayer(server, sender, args[1]);
        CitadelInstance citadel = CitadelWorldSavedData.get(player.world).getCitadelAt(new BlockPos(player));
        if (citadel == null || citadel.getBossArenaBounds() == null || citadel.getBossAltarPos() == null) {
            throw new CommandException("Player " + player.getName() + " is not in a structure with a boss arena");
        }
        StructureBoundingBox arena = citadel.getBossArenaBounds();
        BlockPos altarPos = citadel.getBossAltarPos().down();
        TileEntity tile = player.world.getTileEntity(altarPos);
        FloorData bossFloor = citadel.getBossFloor();
        send(sender, "Arena [" + arena.minX + "," + arena.minY + "," + arena.minZ + " -> " + arena.maxX + "," + arena.maxY + ","
                + arena.maxZ + "], altar " + altarPos.getX() + "," + altarPos.getY() + "," + altarPos.getZ()
                + ", bossDefeated " + (bossFloor != null && bossFloor.isBossDefeated()));
        if (!(tile instanceof TileEntityBossAltar)) {
            send(sender, "  altar block missing");
            return;
        }
        TileEntityBossAltar altar = (TileEntityBossAltar) tile;
        send(sender, "  state " + altar.getState() + ", cutscene running " + BossCutsceneHandler.hasActiveCutscene(citadel.getCitadelId())
                + ", seen by " + player.getName() + " " + BossCutsceneHandler.hasSeen(player, citadel.getCitadelId())
                + ", boss bar " + altar.hasBossBar());
        Entity boss = altar.getBossUuid() == null ? null : ((WorldServer) player.world).getEntityFromUuid(altar.getBossUuid());
        if (boss instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) boss;
            send(sender, "  boss " + EntityList.getKey(boss) + " hp " + Math.round(living.getHealth()) + "/"
                    + Math.round(living.getMaxHealth()) + ", phase " + boss.getEntityData().getInteger(BossTags.PHASE)
                    + ", pos " + Math.round(boss.posX * 10) / 10.0 + "," + Math.round(boss.posY * 10) / 10.0 + ","
                    + Math.round(boss.posZ * 10) / 10.0 + ", inside arena " + ArenaSpots.isInside(arena, boss)
                    + ", in wall " + boss.isEntityInsideOpaqueBlock());
        } else {
            send(sender, "  boss entity: none");
        }
        int inside = 0;
        int total = 0;
        for (EntityLiving minion : player.world.getEntities(EntityLiving.class, e -> e != null && e.isEntityAlive() && BossTags.isMinion(e))) {
            total++;
            if (ArenaSpots.isInside(arena, minion) && !minion.isEntityInsideOpaqueBlock()) {
                inside++;
            }
        }
        send(sender, "  minions alive " + total + " (inside arena, not in walls: " + inside + ")");
        int solid = 0;
        int fire = 0;
        for (BlockPos p : BlockPos.getAllInBoxMutable(arena.minX, arena.minY, arena.minZ, arena.maxX, arena.maxY, arena.maxZ)) {
            Material material = player.world.getBlockState(p).getMaterial();
            if (material == Material.FIRE) {
                fire++;
            } else if (material.blocksMovement()) {
                solid++;
            }
        }
        send(sender, "  arena integrity: solid blocks " + solid + ", fire " + fire);
    }

    /** relics <player> [give|status]: hand out the four relics or report what is active. */
    private void relics(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP player = getPlayer(server, sender, args[1]);
        if (args.length > 2 && "give".equals(args[2])) {
            for (Item item : ModItems.all()) {
                player.inventory.addItemStackToInventory(new ItemStack(item));
            }
            send(sender, "Gave the four relics to " + player.getName());
            return;
        }
        boolean bauble = false;
        for (Class<?> iface : ModItems.CROWN_SHARD.getClass().getInterfaces()) {
            bauble |= "baubles.api.IBauble".equals(iface.getName());
        }
        send(sender, "Baubles loaded " + RelicEquipment.isBaublesLoaded() + ", ItemRelic implements IBauble " + bauble);
        send(sender, "Active: crown " + RelicEquipment.isActive(player, ModItems.CROWN_SHARD) + ", aegis "
                + RelicEquipment.isActive(player, ModItems.CLOUDSPIRE_AEGIS) + ", heart "
                + RelicEquipment.isActive(player, ModItems.GOBLIN_FORGE_HEART) + "; allowFlying " + player.capabilities.allowFlying
                + ", attack damage " + Math.round(player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue() * 100) / 100.0
                + ", attack speed " + Math.round(player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).getAttributeValue() * 100) / 100.0
                + ", lore chapters " + Integer.bitCount(LoreTracker.collectedMask(player)));
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
        if (args.length == 2 && ("floorstate".equals(args[0]) || "spawners".equals(args[0]) || "boss".equals(args[0])
                || "relics".equals(args[0]) || "lore".equals(args[0]))) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 2 && "generate".equals(args[0])) {
            List<String> kinds = new ArrayList<>();
            for (CitadelArchetype archetype : CitadelArchetype.values()) {
                kinds.add(archetype.key());
            }
            kinds.add("sky");
            kinds.add("tower");
            kinds.add("castle");
            kinds.add("manor");
            kinds.add("crypt");
            return getListOfStringsMatchingLastWord(args, kinds);
        }
        return Collections.emptyList();
    }
}
