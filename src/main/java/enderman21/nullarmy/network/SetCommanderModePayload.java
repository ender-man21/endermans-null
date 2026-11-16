package enderman21.nullarmy.network;

import enderman21.nullarmy.EndermanSNull;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент → сервер: режим командира (порядковый номер NullWarrior.CommanderMode). */
public record SetCommanderModePayload(int mode) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SetCommanderModePayload> TYPE =
			new CustomPacketPayload.Type<>(EndermanSNull.id("set_commander_mode"));

	public static final StreamCodec<ByteBuf, SetCommanderModePayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.INT, SetCommanderModePayload::mode, SetCommanderModePayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
