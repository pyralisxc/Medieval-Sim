package medievalsim.packets;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PacketGuildInvitedTest {
    @Test
    public void serializationRoundTrip() {
        PacketGuildInvited p = new PacketGuildInvited(7, "Knights", 55L, "Leader");
        PacketGuildInvited copy = new PacketGuildInvited(p.getPacketData());
        assertEquals(7, copy.guildID);
        assertEquals("Knights", copy.guildName);
        assertEquals(55L, copy.senderAuth);
        assertEquals("Leader", copy.senderName);
    }
}
