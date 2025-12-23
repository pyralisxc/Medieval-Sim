package medievalsim.packets;

import medievalsim.grandexchange.net.SellActionResultCode;
import medievalsim.grandexchange.net.SellActionResultMessage;
import medievalsim.grandexchange.net.SellActionType;
import medievalsim.packets.core.AbstractPayloadPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Server -> client notification for sell-offer action results. Keeping this
 * packet lightweight lets the UI show immediate feedback even when the
 * server rejects an action before any inventory sync occurs.
 */
public class PacketGESellActionResult extends AbstractPayloadPacket {
    public final SellActionType action;
    public final SellActionResultCode result;
    public final int slotIndex;
    public final String message;
    public final float cooldownSeconds;

    public PacketGESellActionResult(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.action = SellActionType.values()[reader.getNextByteUnsigned()];
        this.result = SellActionResultCode.values()[reader.getNextByteUnsigned()];
        this.slotIndex = reader.getNextInt();
        this.message = reader.getNextString();
        this.cooldownSeconds = reader.getNextFloat();
    }

    public PacketGESellActionResult(SellActionResultMessage message) {
        this(message.getAction(), message.getResult(), message.getSlotIndex(), message.getMessage(), message.getCooldownSeconds());
    }

    public PacketGESellActionResult(SellActionType action,
                                    SellActionResultCode result,
                                    int slotIndex,
                                    String message,
                                    float cooldownSeconds) {
        this.action = action;
        this.result = result;
        this.slotIndex = slotIndex;
        this.message = message == null ? "" : message;
        this.cooldownSeconds = cooldownSeconds;

        PacketWriter writer = new PacketWriter(this);
        writer.putNextByteUnsigned(action.ordinal());
        writer.putNextByteUnsigned(result.ordinal());
        writer.putNextInt(slotIndex);
        writer.putNextString(this.message);
        writer.putNextFloat(cooldownSeconds);
    }

    @Override
    public void processClient(necesse.engine.network.NetworkPacket packet, Client client) {
        if (!(client.getContainer() instanceof medievalsim.grandexchange.ui.GrandExchangeContainer geContainer)) {
            ModLogger.warn("Sell action result received but GE container not open");
            return;
        }
        SellActionResultMessage resultMessage = new SellActionResultMessage(action, result, slotIndex, message, cooldownSeconds);
        geContainer.notifySellActionResult(resultMessage);
    }

    @Override
    public void processServer(necesse.engine.network.NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGESellActionResult.processServer called - server-to-client only!");
    }
}
