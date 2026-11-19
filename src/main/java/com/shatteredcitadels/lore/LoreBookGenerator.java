package com.shatteredcitadels.lore;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * "Chronicles of the Shattered Crown": seven written books. Pages are JSON text components that
 * reference lang keys ({@code {"translate":"shatteredcitadels.lore.N.P"}}), so every client reads
 * them in its own language. The NBT layout is the vanilla 1.12.2 written-book format: title
 * (max 32 chars), author, generation and a list of JSON page strings.
 */
public final class LoreBookGenerator {

    public static final int CHAPTERS = 7;
    public static final int PAGES_PER_CHAPTER = 3;
    /** Item NBT key holding the chapter number (1-7), used for collection tracking. */
    public static final String TAG_CHAPTER = "ShatteredCitadelsLore";
    public static final String AUTHOR = "Архивариус Короны";

    private static final String[] TITLES = {
            "I. Древние Архитекторы",
            "II. Корона девяти осколков",
            "III. Башни до облаков",
            "IV. Ночь Раскола",
            "V. Гоблинская кузня",
            "VI. Проклятие глубин",
            "VII. Наследие Короны"
    };

    private LoreBookGenerator() {
    }

    public static String pageKey(int chapter, int page) {
        return "shatteredcitadels.lore." + chapter + "." + page;
    }

    public static String title(int chapter) {
        return TITLES[clamp(chapter) - 1];
    }

    /** @param chapter 1-7 (clamped) */
    public static ItemStack create(int chapter) {
        int c = clamp(chapter);
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", TITLES[c - 1]);
        tag.setString("author", AUTHOR);
        tag.setInteger("generation", 0);
        NBTTagList pages = new NBTTagList();
        for (int p = 1; p <= PAGES_PER_CHAPTER; p++) {
            ITextComponent page = new TextComponentTranslation(pageKey(c, p));
            pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(page)));
        }
        tag.setTag("pages", pages);
        tag.setBoolean("resolved", true);
        tag.setInteger(TAG_CHAPTER, c);
        book.setTagCompound(tag);
        return book;
    }

    /** @return the chapter of a lore book, or 0 for anything else. */
    public static int chapterOf(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.WRITTEN_BOOK || !stack.hasTagCompound()) {
            return 0;
        }
        int chapter = stack.getTagCompound().getInteger(TAG_CHAPTER);
        return chapter >= 1 && chapter <= CHAPTERS ? chapter : 0;
    }

    private static int clamp(int chapter) {
        return chapter < 1 ? 1 : (chapter > CHAPTERS ? CHAPTERS : chapter);
    }
}
