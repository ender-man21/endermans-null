package enderman21.nullarmy.network;

import enderman21.nullarmy.EndermanSNull;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент → сервер: выбранный режим армии из радиального меню (порядковый номер NullWarrior.Mode, -1 если не менять)
 * и дистанция, на которой воины держатся от владельца (-1 если не менять). */
public record SetModePayload(int mode, int distance, int formation, int behavior) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SetModePayload> TYPE =
			new CustomPacketPayload.Type<>(EndermanSNull.id("set_mode"));

	public static final StreamCodec<ByteBuf, SetModePayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.INT, SetModePayload::mode,
					ByteBufCodecs.INT, SetModePayload::distance,
					ByteBufCodecs.INT, SetModePayload::formation,
					ByteBufCodecs.INT, SetModePayload::behavior,
					SetModePayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
