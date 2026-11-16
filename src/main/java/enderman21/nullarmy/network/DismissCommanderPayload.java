package enderman21.nullarmy.network;

import enderman21.nullarmy.EndermanSNull;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент → сервер: «Отозвать командира» из радиального меню знамени. */
public record DismissCommanderPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DismissCommanderPayload> TYPE =
			new CustomPacketPayload.Type<>(EndermanSNull.id("dismiss_commander"));

	public static final StreamCodec<ByteBuf, DismissCommanderPayload> CODEC =
			StreamCodec.unit(new DismissCommanderPayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
