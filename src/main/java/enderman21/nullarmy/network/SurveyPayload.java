package enderman21.nullarmy.network;

import enderman21.nullarmy.EndermanSNull;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер → клиент владельца: id сущностей, которые разведка командира подсвечивает (только ему). */
public record SurveyPayload(List<Integer> ids) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SurveyPayload> TYPE =
			new CustomPacketPayload.Type<>(EndermanSNull.id("survey"));

	public static final StreamCodec<ByteBuf, SurveyPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), SurveyPayload::ids,
					SurveyPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
