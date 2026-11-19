package com.shatteredcitadels.lore;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.shatteredcitadels.Reference;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;

import java.util.Random;

/**
 * Loot function {@code shatteredcitadels:lore_book}: turns the entry into one of the given lore
 * chapters, e.g. {@code {"function":"shatteredcitadels:lore_book","chapters":[1,2]}}.
 */
public class LootFunctionLoreBook extends LootFunction {

    private final int[] chapters;

    public LootFunctionLoreBook(LootCondition[] conditions, int[] chapters) {
        super(conditions);
        this.chapters = chapters.length == 0 ? new int[]{1} : chapters.clone();
    }

    @Override
    public ItemStack apply(ItemStack stack, Random rand, LootContext context) {
        return LoreBookGenerator.create(chapters[rand.nextInt(chapters.length)]);
    }

    public static class Serializer extends LootFunction.Serializer<LootFunctionLoreBook> {

        public Serializer() {
            super(new ResourceLocation(Reference.MODID, "lore_book"), LootFunctionLoreBook.class);
        }

        @Override
        public void serialize(JsonObject object, LootFunctionLoreBook function, JsonSerializationContext context) {
            JsonArray array = new JsonArray();
            for (int chapter : function.chapters) {
                array.add(chapter);
            }
            object.add("chapters", array);
        }

        @Override
        public LootFunctionLoreBook deserialize(JsonObject object, JsonDeserializationContext context, LootCondition[] conditions) {
            if (!object.has("chapters") || !object.get("chapters").isJsonArray()) {
                return new LootFunctionLoreBook(conditions, new int[]{1});
            }
            JsonArray array = object.getAsJsonArray("chapters");
            int[] chapters = new int[array.size()];
            int i = 0;
            for (JsonElement element : array) {
                chapters[i++] = element.getAsInt();
            }
            return new LootFunctionLoreBook(conditions, chapters);
        }
    }
}
