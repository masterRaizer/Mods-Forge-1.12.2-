package com.shatteredcitadels.network;

import com.shatteredcitadels.floor.FloorHudState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PacketSymmetryTest {

    private static FloorHudState state(int floor, int wave, int remaining, boolean cleared) {
        return new FloorHudState(true, UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                "@name:keepers_citadel:2:5", "keepers_citadel", true, false, floor, 6, 3, 4, wave, 3, remaining,
                cleared, floor == 5, false);
    }

    @Test
    public void syncPacketRoundTrip() {
        for (FloorHudState original : new FloorHudState[]{state(2, 2, 5, false), state(-3, 0, 0, true), FloorHudState.NONE}) {
            ByteBuf buf = Unpooled.buffer();
            new PacketSyncFloorHud(original).toBytes(buf);
            PacketSyncFloorHud decoded = new PacketSyncFloorHud();
            decoded.fromBytes(buf);
            assertEquals(original, decoded.getState());
            assertEquals(0, buf.readableBytes());
        }
    }

    @Test
    public void bannerPacketRoundTrip() {
        FloorHudState source = state(-2, 1, 4, false);
        PacketFloorTitleBanner original = new PacketFloorTitleBanner(source);
        ByteBuf buf = Unpooled.buffer();
        original.toBytes(buf);
        PacketFloorTitleBanner decoded = new PacketFloorTitleBanner();
        decoded.fromBytes(buf);
        assertEquals(original.getCitadelName(), decoded.getCitadelName());
        assertEquals(-2, decoded.getFloorIndex());
        assertEquals(6, decoded.getSurfaceFloors());
        assertEquals(4, decoded.getDifficultyTier());
        assertTrue(decoded.isSkyCitadel());
        assertFalse(decoded.isBossFloor());
        assertTrue(decoded.isUndergroundLevel());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    public void changeDetection() {
        assertEquals(state(2, 2, 5, false), state(2, 2, 5, false));
        assertFalse(state(2, 2, 5, false).equals(state(2, 2, 4, false)));
        assertFalse(state(2, 2, 5, false).equals(FloorHudState.NONE));
        assertTrue(state(2, 2, 5, false).sameFloorAs(state(2, 3, 1, false)));
        assertFalse(state(2, 2, 5, false).sameFloorAs(state(3, 2, 5, false)));
        assertFalse(state(2, 2, 5, false).sameFloorAs(FloorHudState.NONE));
    }

    @Test
    public void overlongNamesAreClamped() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longName.append('x');
        }
        FloorHudState s = new FloorHudState(true, UUID.randomUUID(), longName.toString(), "a", false, false,
                0, 1, 0, 1, 0, 0, 0, false, false, false);
        assertEquals(FloorHudState.MAX_NAME_LENGTH, s.getCitadelName().length());
    }

    @Test
    public void bossCutscenePacketRoundTrip() {
        PacketBossCutscene start = PacketBossCutscene.start("@name:sky_citadel:1:2", "sky_citadel", 120,
                new net.minecraft.util.math.BlockPos(100, 200, -50), true);
        ByteBuf buf = Unpooled.buffer();
        start.toBytes(buf);
        PacketBossCutscene decoded = new PacketBossCutscene();
        decoded.fromBytes(buf);
        assertTrue(decoded.isStart());
        assertEquals("@name:sky_citadel:1:2", decoded.getCitadelName());
        assertEquals("sky_citadel", decoded.getArchetypeKey());
        assertEquals(120, decoded.getDurationTicks());
        assertEquals(new net.minecraft.util.math.BlockPos(100, 200, -50), decoded.getAltarPos());
        assertTrue(decoded.isSky());
        assertEquals(0, buf.readableBytes());

        ByteBuf stopBuf = Unpooled.buffer();
        PacketBossCutscene.stop().toBytes(stopBuf);
        PacketBossCutscene stop = new PacketBossCutscene();
        stop.fromBytes(stopBuf);
        assertFalse(stop.isStart());
        assertEquals(0, stopBuf.readableBytes());
    }
}
