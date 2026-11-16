package enderman21.nullarmy.network;

import enderman21.nullarmy.EndermanSNull;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент → сервер: «Призвать командира» из радиального меню знамени. */
public record SummonCommanderPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SummonCommanderPayload> TYPE =
			new CustomPacketPayload.Type<>(EndermanSNull.id("summon_commander"));

	public static final StreamCodec<ByteBuf, SummonCommanderPayload> CODEC =
			StreamCodec.unit(new SummonCommanderPayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
