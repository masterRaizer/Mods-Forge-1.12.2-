package com.shatteredcitadels.herobrine;

import com.shatteredcitadels.worldgen.StructureJobs;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The Herobrine branches of {@code /citadel}: {@code spawn herobrine_lair [variant]} and {@code summon herobrine}. */
public final class HerobrineCommands {

    public static final String LAIR = "herobrine_lair";
    public static final String SUMMON = "summon";
    private static final String BOSS = "herobrine";
    private static final String USAGE = "commands.citadel.herobrine.usage";

    private HerobrineCommands() {
    }

    /** {@code /citadel spawn herobrine_lair [keep|tower|shrine]}: a lair centred on the sender. */
    public static void spawnLair(ICommandSender sender, World world, BlockPos pos, String[] args) throws CommandException {
        LairVariant variant;
        if (args.length > 2) {
            variant = LairVariant.byId(args[2]);
            if (variant == null) {
                throw new WrongUsageException(USAGE);
            }
        } else {
            variant = LairVariant.values()[world.rand.nextInt(LairVariant.values().length)];
        }
        int ground = HerobrineLairGenerator.groundAt(world, pos.getX(), pos.getZ());
        if (ground < 0) {
            ground = pos.getY() - 1;
        }
        HerobrineLairPlan plan = new HerobrineLairPlan(variant, pos.getX(), pos.getZ(), ground, world.rand.nextLong(),
                world.provider.getDimension());
        StructureJobs.get(world).submit(plan);
        sender.sendMessage(new TextComponentTranslation("commands.citadel.herobrine.lair", plan.describe()));
    }

    /** {@code /citadel summon herobrine}: the boss at the sender, bound to no lair. */
    public static void summon(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2 || !BOSS.equalsIgnoreCase(args[1])) {
            throw new WrongUsageException(USAGE);
        }
        World world = sender.getEntityWorld();
        if (world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            throw new CommandException("shatteredcitadels.herobrine.altar.peaceful");
        }
        AltarHandler.summon(world, sender.getPosition(), null);
    }

    /** @return the completions of the Herobrine branches, or null when the arguments are not ours */
    public static List<String> complete(String[] args) {
        if (args.length == 2 && SUMMON.equalsIgnoreCase(args[0])) {
            return CommandBase.getListOfStringsMatchingLastWord(args, BOSS);
        }
        if (args.length == 3 && "spawn".equalsIgnoreCase(args[0]) && LAIR.equalsIgnoreCase(args[1])) {
            return CommandBase.getListOfStringsMatchingLastWord(args, variantIds());
        }
        return null;
    }

    static List<String> variantIds() {
        List<String> ids = new ArrayList<>();
        for (LairVariant variant : LairVariant.values()) {
            ids.add(variant.id().toLowerCase(Locale.ROOT));
        }
        return ids;
    }
}
