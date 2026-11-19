package com.shatteredcitadels.lore;

import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemWrittenBook;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The books must pass the vanilla 1.12.2 validation, otherwise clients show "* Invalid book tag *". */
public class LoreBookTest {

    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    public void everyChapterIsAValidWrittenBook() throws IOException {
        Set<String> en = keys("en_us.lang");
        Set<String> ru = keys("ru_ru.lang");
        for (int chapter = 1; chapter <= LoreBookGenerator.CHAPTERS; chapter++) {
            ItemStack book = LoreBookGenerator.create(chapter);
            NBTTagCompound tag = book.getTagCompound();
            assertTrue("chapter " + chapter, ItemWrittenBook.validBookTagContents(tag));
            assertTrue(tag.getString("title").length() <= 32);
            assertEquals(chapter, LoreBookGenerator.chapterOf(book));
            NBTTagList pages = tag.getTagList("pages", Constants.NBT.TAG_STRING);
            assertEquals(LoreBookGenerator.PAGES_PER_CHAPTER, pages.tagCount());
            for (int p = 0; p < pages.tagCount(); p++) {
                ITextComponent component = ITextComponent.Serializer.jsonToComponent(pages.getStringTagAt(p));
                assertTrue(component instanceof TextComponentTranslation);
                String key = ((TextComponentTranslation) component).getKey();
                assertEquals(LoreBookGenerator.pageKey(chapter, p + 1), key);
                assertTrue("missing en " + key, en.contains(key));
                assertTrue("missing ru " + key, ru.contains(key));
            }
        }
    }

    static Set<String> keys(String file) throws IOException {
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(Paths.get("src/main/resources/assets/shatteredcitadels/lang/" + file), StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq > 0 && !line.startsWith("#")) {
                keys.add(line.substring(0, eq));
            }
        }
        return keys;
    }
}
