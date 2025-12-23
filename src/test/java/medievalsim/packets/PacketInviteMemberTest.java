package medievalsim.packets;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PacketInviteMemberTest {
    @Test
    public void serializationRoundTrip() {
        PacketInviteMember p = new PacketInviteMember(42, 12345L);
        PacketInviteMember copy = new PacketInviteMember(p.getPacketData());
        assertEquals(42, copy.guildID);
        assertEquals(12345L, copy.targetAuth);
    }
}
