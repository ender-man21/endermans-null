package enderman21.nullarmy.network;

import enderman21.nullarmy.EndermanSNull;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент → сервер: «Позвать Нулл» из радиального меню знамени. */
public record CallArmyPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CallArmyPayload> TYPE =
			new CustomPacketPayload.Type<>(EndermanSNull.id("call_army"));

	public static final StreamCodec<ByteBuf, CallArmyPayload> CODEC = StreamCodec.unit(new CallArmyPayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
