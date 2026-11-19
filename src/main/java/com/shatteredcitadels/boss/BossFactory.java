package com.shatteredcitadels.boss;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.config.MobTiers;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelNameGenerator;
import com.shatteredcitadels.structure.FloorData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Summons exactly one floor boss: a strong mob from {@code bossPoolByTier}, or, if every candidate
 * fails, a reinforced wither skeleton in enchanted gear. Health, damage and armor scale with the
 * citadel's height; the Citadel Above the Clouds gets its uber multiplier.
 */
public final class BossFactory {

    private static final UUID HEALTH = UUID.fromString("7b3e2a10-6c4d-4f8e-9a21-3d5b7c9e0f01");
    private static final UUID DAMAGE = UUID.fromString("7b3e2a10-6c4d-4f8e-9a21-3d5b7c9e0f02");
    private static final UUID ARMOR = UUID.fromString("7b3e2a10-6c4d-4f8e-9a21-3d5b7c9e0f03");
    private static final UUID KNOCKBACK = UUID.fromString("7b3e2a10-6c4d-4f8e-9a21-3d5b7c9e0f04");
    private static final UUID FOLLOW = UUID.fromString("7b3e2a10-6c4d-4f8e-9a21-3d5b7c9e0f05");
    /** Bosses start this many times tougher than the mob they are made from, before floor scaling. */
    private static final double BASE_HEALTH_FACTOR = 4.0D;
    private static final double BASE_DAMAGE_FACTOR = 1.5D;
    private static final int MAX_CANDIDATES = 4;

    private BossFactory() {
    }

    /** @return boss tier 1-5 derived from the top floor's difficulty. */
    public static int bossTier(CitadelInstance citadel) {
        FloorData top = citadel.getBossFloor();
        int difficulty = top == null ? 3 : top.getDifficultyTier();
        return MobTiers.clamp(difficulty);
    }

    /** @return the spawned boss, or null if no valid spot exists in the arena right now. */
    public static EntityLiving spawnBoss(WorldServer world, CitadelInstance citadel, BlockPos altarPos, Random random) {
        StructureBoundingBox arena = citadel.getBossArenaBounds();
        if (arena == null) {
            return null;
        }
        int tier = bossTier(citadel);
        List<ResourceLocation> candidates = new ArrayList<>(ConfigCache.get().getBossCandidates(tier));
        Collections.shuffle(candidates, random);
        for (int i = 0; i < candidates.size() && i < MAX_CANDIDATES; i++) {
            EntityLiving boss = create(world, candidates.get(i));
            if (boss != null && place(world, boss, arena, altarPos, random)) {
                return finish(world, boss, citadel, altarPos, tier, false);
            }
        }
        for (String fallback : new String[]{"minecraft:wither_skeleton", "minecraft:zombie"}) {
            EntityLiving boss = create(world, new ResourceLocation(fallback));
            if (boss != null && place(world, boss, arena, altarPos, random)) {
                return finish(world, boss, citadel, altarPos, tier, true);
            }
        }
        return null;
    }

    private static EntityLiving create(WorldServer world, ResourceLocation id) {
        if (!ForgeRegistries.ENTITIES.containsKey(id)) {
            return null;
        }
        EntityEntry entry = ForgeRegistries.ENTITIES.getValue(id);
        try {
            Entity entity = entry == null ? null : entry.newInstance(world);
            return entity instanceof EntityLiving ? (EntityLiving) entity : null;
        } catch (VirtualMachineError e) {
            if (e instanceof StackOverflowError) {
                return null;
            }
            throw e;
        } catch (Throwable t) {
            ShatteredCitadelsMod.LOGGER.warn("Boss candidate {} could not be created: {}", id, t.toString());
            return null;
        }
    }

    /** Altar top first, then a ring around it; always fully inside the arena. */
    static boolean place(WorldServer world, EntityLiving boss, StructureBoundingBox arena, BlockPos altarPos, Random random) {
        double x = altarPos.getX() + 0.5D;
        double z = altarPos.getZ() + 0.5D;
        int y = altarPos.getY() + 1;
        double[] spot = ArenaSpots.isValid(world, boss, arena, x, y, z) ? new double[]{x, y, z}
                : ArenaSpots.findAround(world, boss, arena, x, y, z, 6, random, 48);
        if (spot == null) {
            return false;
        }
        boss.setLocationAndAngles(spot[0], spot[1], spot[2], random.nextFloat() * 360.0F, 0.0F);
        return true;
    }

    private static EntityLiving finish(WorldServer world, EntityLiving boss, CitadelInstance citadel, BlockPos altarPos,
                                       int tier, boolean fallback) {
        try {
            boss.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(boss)), null);
        } catch (VirtualMachineError e) {
            if (!(e instanceof StackOverflowError)) {
                throw e;
            }
        } catch (Throwable t) {
            ShatteredCitadelsMod.LOGGER.debug("Boss onInitialSpawn failed: {}", t.toString());
        }
        if (fallback) {
            equipFallback(boss, tier, world.rand);
        }
        boss.enablePersistence();
        BossTags.markBoss(boss, citadel.getCitadelId(), altarPos);
        boss.getEntityData().setInteger(BossTags.PHASE, 1);

        ConfigCache.Snapshot config = ConfigCache.get();
        int floors = Math.max(1, citadel.getSurfaceFloorCount());
        boolean uber = citadel.isSkyCitadel();
        modify(boss, SharedMonsterAttributes.MAX_HEALTH, HEALTH, BASE_HEALTH_FACTOR * config.bossHealthScale(floors, uber) - 1.0D, 2);
        modify(boss, SharedMonsterAttributes.ATTACK_DAMAGE, DAMAGE, BASE_DAMAGE_FACTOR * config.bossDamageScale(floors, uber) - 1.0D, 2);
        modify(boss, SharedMonsterAttributes.ARMOR, ARMOR, 2.0D * tier + (uber ? 10.0D : 0.0D), 0);
        modify(boss, SharedMonsterAttributes.KNOCKBACK_RESISTANCE, KNOCKBACK, 0.7D, 0);
        modify(boss, SharedMonsterAttributes.FOLLOW_RANGE, FOLLOW, 24.0D, 0);
        boss.setHealth(boss.getMaxHealth());

        if (!world.spawnEntity(boss)) {
            return null;
        }
        world.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, boss.posX, boss.posY + 1.0D, boss.posZ, 4, 0.6D, 0.6D, 0.6D, 0.0D);
        world.spawnParticle(EnumParticleTypes.SPELL_WITCH, boss.posX, boss.posY + 1.0D, boss.posZ, 60, 1.0D, 1.0D, 1.0D, 0.2D);
        ShatteredCitadelsMod.LOGGER.info("Boss {} summoned for citadel {} (tier {}, {} HP{})", typeOf(boss),
                citadel.getCitadelId(), tier, Math.round(boss.getMaxHealth()), uber ? ", uber" : "");
        return boss;
    }

    private static void equipFallback(EntityLiving boss, int tier, Random random) {
        boolean diamond = tier >= 4;
        Item[] armor = diamond
                ? new Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS}
                : new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
        EntityEquipmentSlot[] slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};
        int level = 10 + tier * 5;
        for (int i = 0; i < slots.length; i++) {
            boss.setItemStackToSlot(slots[i], EnchantmentHelper.addRandomEnchantment(random, new ItemStack(armor[i]), level, false));
            boss.setDropChance(slots[i], 0.0F);
        }
        boss.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, EnchantmentHelper.addRandomEnchantment(random,
                new ItemStack(diamond ? Items.DIAMOND_SWORD : Items.IRON_SWORD), level, false));
        boss.setDropChance(EntityEquipmentSlot.MAINHAND, 0.0F);
    }

    private static void modify(EntityLiving boss, IAttribute attribute, UUID id, double amount, int operation) {
        IAttributeInstance instance = boss.getEntityAttribute(attribute);
        if (instance == null || amount <= 0.0D || instance.getModifier(id) != null) {
            return;
        }
        instance.applyModifier(new AttributeModifier(id, "Shattered Citadel boss", amount, operation));
    }

    /** Localised boss title for the boss bar: "Warden of the Shattered Crown" / «Страж Расколотой Короны». */
    public static ITextComponent bossName(CitadelInstance citadel) {
        String[] keys = CitadelNameGenerator.parseKeys(citadel.getDisplayName());
        String group = citadel.getArchetype() != null ? citadel.getArchetype().key()
                : citadel.isUnderground() ? "crypt" : "default";
        ITextComponent title = new TextComponentTranslation("shatteredcitadels.boss.title." + group);
        if (keys == null) {
            return title;
        }
        return new TextComponentTranslation(CitadelNameGenerator.KEY_FORMAT, title, new TextComponentTranslation(keys[1]));
    }

    private static String typeOf(Entity entity) {
        ResourceLocation id = EntityList.getKey(entity);
        return id == null ? entity.getClass().getSimpleName() : id.toString();
    }
}
