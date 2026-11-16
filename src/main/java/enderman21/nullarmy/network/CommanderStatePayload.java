package enderman21.nullarmy.network;

import enderman21.nullarmy.EndermanSNull;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер → клиент: есть ли у армии командир и его режим (для меню знамени). */
public record CommanderStatePayload(boolean exists, int mode, boolean paid) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CommanderStatePayload> TYPE =
			new CustomPacketPayload.Type<>(EndermanSNull.id("commander_state"));

	public static final StreamCodec<ByteBuf, CommanderStatePayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.BOOL, CommanderStatePayload::exists,
					ByteBufCodecs.INT, CommanderStatePayload::mode,
					ByteBufCodecs.BOOL, CommanderStatePayload::paid,
					CommanderStatePayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
