package com.shatteredcitadels.network;

import com.shatteredcitadels.boss.BossCutsceneHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client -&gt; server: the player pressed sneak to skip the cutscene. No payload. */
public final class PacketCutsceneSkip implements IMessage {

    @Override
    public void toBytes(ByteBuf buf) {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    public static final class Handler implements IMessageHandler<PacketCutsceneSkip, IMessage> {
        @Override
        public IMessage onMessage(PacketCutsceneSkip message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> BossCutsceneHandler.skip(player));
            return null;
        }
    }
}
