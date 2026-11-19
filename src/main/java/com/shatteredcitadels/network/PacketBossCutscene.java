package com.shatteredcitadels.network;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.floor.FloorHudState;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server -&gt; client: start or stop the boss entrance cutscene. */
public final class PacketBossCutscene implements IMessage {

    private boolean start;
    private String citadelName = "";
    private String archetypeKey = "";
    private int durationTicks;
    private BlockPos altarPos = BlockPos.ORIGIN;
    private boolean sky;

    public PacketBossCutscene() {
    }

    public static PacketBossCutscene start(String name, String archetypeKey, int durationTicks, BlockPos altar, boolean sky) {
        PacketBossCutscene packet = new PacketBossCutscene();
        packet.start = true;
        packet.citadelName = clamp(name);
        packet.archetypeKey = clamp(archetypeKey);
        packet.durationTicks = durationTicks;
        packet.altarPos = altar;
        packet.sky = sky;
        return packet;
    }

    public static PacketBossCutscene stop() {
        return new PacketBossCutscene();
    }

    private static String clamp(String value) {
        String v = value == null ? "" : value;
        return v.length() > FloorHudState.MAX_NAME_LENGTH ? v.substring(0, FloorHudState.MAX_NAME_LENGTH) : v;
    }

    public boolean isStart() { return start; }
    public String getCitadelName() { return citadelName; }
    public String getArchetypeKey() { return archetypeKey; }
    public int getDurationTicks() { return durationTicks; }
    public BlockPos getAltarPos() { return altarPos; }
    public boolean isSky() { return sky; }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(start);
        if (!start) {
            return;
        }
        ByteBufUtils.writeUTF8String(buf, citadelName);
        ByteBufUtils.writeUTF8String(buf, archetypeKey);
        buf.writeShort(durationTicks);
        buf.writeLong(altarPos.toLong());
        buf.writeBoolean(sky);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        start = buf.readBoolean();
        if (!start) {
            return;
        }
        citadelName = clamp(ByteBufUtils.readUTF8String(buf));
        archetypeKey = clamp(ByteBufUtils.readUTF8String(buf));
        durationTicks = Math.max(0, Math.min(400, buf.readShort()));
        altarPos = BlockPos.fromLong(buf.readLong());
        sky = buf.readBoolean();
    }

    public static final class Handler implements IMessageHandler<PacketBossCutscene, IMessage> {
        @Override
        public IMessage onMessage(PacketBossCutscene message, MessageContext ctx) {
            ShatteredCitadelsMod.PROXY.handleBossCutscene(message);
            return null;
        }
    }
}
