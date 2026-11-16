package enderman21.nullarmy.network;

import enderman21.nullarmy.EndermanSNull;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент → сервер: меню знамени открылось, пришли состояние командира. */
public record CommanderRequestPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CommanderRequestPayload> TYPE =
			new CustomPacketPayload.Type<>(EndermanSNull.id("commander_request"));

	public static final StreamCodec<ByteBuf, CommanderRequestPayload> CODEC =
			StreamCodec.unit(new CommanderRequestPayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
