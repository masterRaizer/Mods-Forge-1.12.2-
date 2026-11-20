package com.shatteredcitadels.network;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.floor.FloorHudState;
import com.shatteredcitadels.structure.FloorData;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server -&gt; client: show the fading title banner (entering a citadel or a new floor). */
public final class PacketFloorTitleBanner implements IMessage {

    private String citadelName = "";
    private int floorIndex;
    private int surfaceFloors;
    private int difficultyTier = 1;
    private boolean skyCitadel;
    private boolean bossFloor;
    private boolean hasCourtyard;

    /** Required by SimpleNetworkWrapper. */
    public PacketFloorTitleBanner() {
    }

    public PacketFloorTitleBanner(FloorHudState state) {
        this.citadelName = state.getCitadelName();
        this.floorIndex = state.getFloorIndex();
        this.surfaceFloors = state.getSurfaceFloors();
        this.difficultyTier = state.getDifficultyTier();
        this.skyCitadel = state.isSkyCitadel();
        this.bossFloor = state.isBossFloor();
        this.hasCourtyard = state.hasCourtyard();
    }

    public String getCitadelName() { return citadelName; }
    public int getFloorIndex() { return floorIndex; }
    public int getSurfaceFloors() { return surfaceFloors; }
    public int getDifficultyTier() { return difficultyTier; }
    public boolean isSkyCitadel() { return skyCitadel; }
    public boolean isBossFloor() { return bossFloor; }
    public boolean hasCourtyard() { return hasCourtyard; }
    public boolean isUndergroundLevel() { return floorIndex < 0; }

    @Override
    public void toBytes(ByteBuf buf) {
        String name = citadelName.length() > FloorHudState.MAX_NAME_LENGTH
                ? citadelName.substring(0, FloorHudState.MAX_NAME_LENGTH) : citadelName;
        ByteBufUtils.writeUTF8String(buf, name);
        buf.writeShort(floorIndex);
        buf.writeByte(surfaceFloors);
        buf.writeByte(difficultyTier);
        buf.writeByte((skyCitadel ? 1 : 0) | (bossFloor ? 2 : 0) | (hasCourtyard ? 4 : 0));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        String name = ByteBufUtils.readUTF8String(buf);
        citadelName = name.length() > FloorHudState.MAX_NAME_LENGTH ? name.substring(0, FloorHudState.MAX_NAME_LENGTH) : name;
        floorIndex = buf.readShort();
        surfaceFloors = buf.readUnsignedByte();
        difficultyTier = FloorData.clampDifficulty(buf.readUnsignedByte());
        int flags = buf.readUnsignedByte();
        skyCitadel = (flags & 1) != 0;
        bossFloor = (flags & 2) != 0;
        hasCourtyard = (flags & 4) != 0;
    }

    public static final class Handler implements IMessageHandler<PacketFloorTitleBanner, IMessage> {
        @Override
        public IMessage onMessage(PacketFloorTitleBanner message, MessageContext ctx) {
            ShatteredCitadelsMod.PROXY.handleFloorBanner(message);
            return null;
        }
    }
}
