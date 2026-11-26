package com.shatteredcitadels.advancement;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.shatteredcitadels.Reference;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.ICriterionTrigger;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.advancements.critereon.AbstractCriterionInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Advancement criteria of the mod. JSON: {@code {"trigger":"shatteredcitadels:boss_defeated",
 * "conditions":{"sky":true}}}. Fired from the floor tracker, trial spawners, the floor combat
 * state, the boss arena handler and the lore tracker.
 */
public final class CustomAdvancementTriggers {

    /** conditions: optional "type" = citadel | sky_citadel | crypt | castle_dungeons */
    public static final Trigger<EnterInstance> ENTER_STRUCTURE = new Trigger<>("enter_structure", EnterInstance::new);
    /** conditions: optional "min_floor" / "max_floor" (crypt levels are negative) */
    public static final Trigger<FloorInstance> FLOOR_REACHED = new Trigger<>("floor_reached", FloorInstance::new);
    /** conditions: optional "underground" (bool) */
    public static final Trigger<FlagsInstance> FLOOR_CLEARED = new Trigger<>("floor_cleared", FlagsInstance::new);
    public static final Trigger<FlagsInstance> SPAWNER_COMPLETED = new Trigger<>("spawner_completed", FlagsInstance::new);
    /** conditions: optional "sky", "underground", "flawless" (bools) */
    public static final Trigger<FlagsInstance> BOSS_DEFEATED = new Trigger<>("boss_defeated", FlagsInstance::new);
    /** conditions: optional "chapters" = minimum number of distinct chapters collected */
    public static final Trigger<CountInstance> LORE_COLLECTED = new Trigger<>("lore_collected", CountInstance::new);

    /** No conditions: the player lit the altar that called Herobrine. */
    public static final Trigger<SimpleInstance> HEROBRINE_SUMMONED =
            new Trigger<>("herobrine_summoned", json -> new SimpleInstance(new ResourceLocation(Reference.MODID, "herobrine_summoned")));
    /** No conditions: the player was in the fight when Herobrine fell. */
    public static final Trigger<SimpleInstance> HEROBRINE_DEFEATED =
            new Trigger<>("herobrine_defeated", json -> new SimpleInstance(new ResourceLocation(Reference.MODID, "herobrine_defeated")));

    private static boolean registered;

    private CustomAdvancementTriggers() {
    }

    /** Pre-init, before advancements are loaded. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CriteriaTriggers.register(ENTER_STRUCTURE);
        CriteriaTriggers.register(FLOOR_REACHED);
        CriteriaTriggers.register(FLOOR_CLEARED);
        CriteriaTriggers.register(SPAWNER_COMPLETED);
        CriteriaTriggers.register(BOSS_DEFEATED);
        CriteriaTriggers.register(LORE_COLLECTED);
        CriteriaTriggers.register(HEROBRINE_SUMMONED);
        CriteriaTriggers.register(HEROBRINE_DEFEATED);
    }

    // ------------------------------------------------------------------ firing helpers

    public static void enterStructure(EntityPlayerMP player, String type) {
        ENTER_STRUCTURE.trigger(player, i -> i.type == null || i.type.equals(type));
    }

    public static void floorReached(EntityPlayerMP player, int floorIndex) {
        FLOOR_REACHED.trigger(player, i -> (i.min == null || floorIndex >= i.min) && (i.max == null || floorIndex <= i.max));
    }

    public static void floorCleared(EntityPlayerMP player, boolean underground) {
        FLOOR_CLEARED.trigger(player, i -> i.matches(false, underground, false));
    }

    public static void spawnerCompleted(EntityPlayerMP player, boolean underground) {
        SPAWNER_COMPLETED.trigger(player, i -> i.matches(false, underground, false));
    }

    public static void bossDefeated(EntityPlayerMP player, boolean sky, boolean underground, boolean flawless) {
        BOSS_DEFEATED.trigger(player, i -> i.matches(sky, underground, flawless));
    }

    public static void loreCollected(EntityPlayerMP player, int chapters) {
        LORE_COLLECTED.trigger(player, i -> chapters >= i.count);
    }

    public static void herobrineSummoned(EntityPlayerMP player) {
        HEROBRINE_SUMMONED.trigger(player, i -> true);
    }

    public static void herobrineDefeated(EntityPlayerMP player) {
        HEROBRINE_DEFEATED.trigger(player, i -> true);
    }

    // ------------------------------------------------------------------ trigger base

    public static final class Trigger<I extends AbstractCriterionInstance> implements ICriterionTrigger<I> {

        private final ResourceLocation id;
        private final Function<JsonObject, I> factory;
        private final Map<PlayerAdvancements, Set<Listener<I>>> listeners = new HashMap<>();

        Trigger(String name, Function<JsonObject, I> factory) {
            this.id = new ResourceLocation(Reference.MODID, name);
            this.factory = factory;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public void addListener(PlayerAdvancements advancements, Listener<I> listener) {
            listeners.computeIfAbsent(advancements, a -> new HashSet<>()).add(listener);
        }

        @Override
        public void removeListener(PlayerAdvancements advancements, Listener<I> listener) {
            Set<Listener<I>> set = listeners.get(advancements);
            if (set != null) {
                set.remove(listener);
                if (set.isEmpty()) {
                    listeners.remove(advancements);
                }
            }
        }

        @Override
        public void removeAllListeners(PlayerAdvancements advancements) {
            listeners.remove(advancements);
        }

        @Override
        public I deserializeInstance(JsonObject json, JsonDeserializationContext context) {
            return factory.apply(json);
        }

        void trigger(EntityPlayerMP player, Predicate<I> test) {
            PlayerAdvancements advancements = player.getAdvancements();
            Set<Listener<I>> set = listeners.get(advancements);
            if (set == null) {
                return;
            }
            List<Listener<I>> matched = new ArrayList<>();
            for (Listener<I> listener : set) {
                if (test.test(listener.getCriterionInstance())) {
                    matched.add(listener);
                }
            }
            for (Listener<I> listener : matched) {
                listener.grantCriterion(advancements);
            }
        }
    }

    // ------------------------------------------------------------------ instances

    /** A criterion without conditions. */
    public static final class SimpleInstance extends AbstractCriterionInstance {
        SimpleInstance(ResourceLocation trigger) {
            super(trigger);
        }
    }

    public static final class EnterInstance extends AbstractCriterionInstance {
        final String type;

        EnterInstance(JsonObject json) {
            super(ENTER_STRUCTURE.getId());
            this.type = json.has("type") ? JsonUtils.getString(json, "type") : null;
        }
    }

    public static final class FloorInstance extends AbstractCriterionInstance {
        final Integer min;
        final Integer max;

        FloorInstance(JsonObject json) {
            super(FLOOR_REACHED.getId());
            this.min = json.has("min_floor") ? JsonUtils.getInt(json, "min_floor") : null;
            this.max = json.has("max_floor") ? JsonUtils.getInt(json, "max_floor") : null;
        }
    }

    public static final class FlagsInstance extends AbstractCriterionInstance {
        final Boolean sky;
        final Boolean underground;
        final Boolean flawless;

        FlagsInstance(JsonObject json) {
            super(new ResourceLocation(Reference.MODID, "flags"));
            this.sky = json.has("sky") ? JsonUtils.getBoolean(json, "sky") : null;
            this.underground = json.has("underground") ? JsonUtils.getBoolean(json, "underground") : null;
            this.flawless = json.has("flawless") ? JsonUtils.getBoolean(json, "flawless") : null;
        }

        boolean matches(boolean isSky, boolean isUnderground, boolean isFlawless) {
            return (sky == null || sky == isSky) && (underground == null || underground == isUnderground)
                    && (flawless == null || !flawless || isFlawless);
        }
    }

    public static final class CountInstance extends AbstractCriterionInstance {
        final int count;

        CountInstance(JsonObject json) {
            super(LORE_COLLECTED.getId());
            this.count = json.has("chapters") ? JsonUtils.getInt(json, "chapters") : 1;
        }
    }
}
