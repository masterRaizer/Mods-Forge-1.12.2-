package com.shatteredcitadels.structure;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SealedGatesTextTest {

    @Test
    public void vaultGateTalksAboutIdols() {
        assertEquals("shatteredcitadels.gate.vault_sealed", SealedGates.sealedKey(VaultLock.GATE_FLOOR));
        assertEquals("shatteredcitadels.gate.vault_open", SealedGates.openKey(false, VaultLock.GATE_FLOOR));
    }

    @Test
    public void floorGatesKeepTheirLines() {
        assertEquals("shatteredcitadels.gate.sealed", SealedGates.sealedKey(2));
        assertEquals("shatteredcitadels.gate.sealed", SealedGates.sealedKey(-1));
        assertEquals("shatteredcitadels.gate.open_up", SealedGates.openKey(false, 1));
        assertEquals("shatteredcitadels.gate.open_down", SealedGates.openKey(false, -1));
        assertEquals("shatteredcitadels.gate.all_open", SealedGates.openKey(true, 0));
    }

    @Test
    public void bothLanguagesHaveTheVaultLines() throws Exception {
        for (String lang : new String[] {"en_us", "ru_ru"}) {
            String text = read("/assets/shatteredcitadels/lang/" + lang + ".lang");
            assertTrue(lang, text.contains("\nshatteredcitadels.gate.vault_sealed="));
            assertTrue(lang, text.contains("\nshatteredcitadels.gate.vault_open="));
        }
    }

    private static String read(String path) throws Exception {
        InputStream in = SealedGatesTextTest.class.getResourceAsStream(path);
        assertNotNull(path, in);
        StringBuilder sb = new StringBuilder("\n");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
