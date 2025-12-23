package medievalsim.packets;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PacketRespondInviteTest {
    @Test
    public void serializationRoundTrip() {
        PacketRespondInvite p = new PacketRespondInvite(7, true);
        PacketRespondInvite copy = new PacketRespondInvite(p.getPacketData());
        assertEquals(7, copy.guildID);
        assertTrue(copy.accept);
    }
}
