package com.shatteredcitadels.network;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.floor.FloorHudState;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server -&gt; client: full floor HUD state (or "outside") whenever it changes. */
public final class PacketSyncFloorHud implements IMessage {

    private FloorHudState state = FloorHudState.NONE;

    /** Required by SimpleNetworkWrapper. */
    public PacketSyncFloorHud() {
    }

    public PacketSyncFloorHud(FloorHudState state) {
        this.state = state == null ? FloorHudState.NONE : state;
    }

    public FloorHudState getState() {
        return state;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        state.write(buf);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        state = FloorHudState.read(buf);
    }

    /** Runs on the network thread; the proxy hands the state to the client thread. */
    public static final class Handler implements IMessageHandler<PacketSyncFloorHud, IMessage> {
        @Override
        public IMessage onMessage(PacketSyncFloorHud message, MessageContext ctx) {
            ShatteredCitadelsMod.PROXY.handleFloorHudSync(message);
            return null;
        }
    }
}
