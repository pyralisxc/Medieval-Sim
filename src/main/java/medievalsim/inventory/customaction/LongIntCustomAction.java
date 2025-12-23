package medievalsim.inventory.customaction;

import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.inventory.container.customAction.ContainerCustomAction;

/**
 * Custom container action that serializes a long and an int payload.
 */
public abstract class LongIntCustomAction extends ContainerCustomAction {

    public void runAndSend(long longValue, int intValue) {
        Packet packet = new Packet();
        PacketWriter writer = new PacketWriter(packet);
        writer.putNextLong(longValue);
        writer.putNextInt(intValue);
        this.runAndSendAction(packet);
    }

    @Override
    public void executePacket(PacketReader reader) {
        long longValue = reader.getNextLong();
        int intValue = reader.getNextInt();
        run(longValue, intValue);
    }

    protected abstract void run(long longValue, int intValue);
}
