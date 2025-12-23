package medievalsim.packets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PacketGuildCreatedTest {

    @Test
    public void serializationRoundTrip() {
        PacketGuildCreated original = new PacketGuildCreated(123, "Knights", true);
        PacketGuildCreated copy = new PacketGuildCreated(original.getPacketData());
        assertEquals(123, copy.guildID);
        assertEquals("Knights", copy.guildName);
        assertTrue(copy.isCreator);
    }

    @Test
    public void serializationRoundTrip_notCreator() {
        PacketGuildCreated original = new PacketGuildCreated(456, "Dragons", false);
        PacketGuildCreated copy = new PacketGuildCreated(original.getPacketData());
        assertEquals(456, copy.guildID);
        assertEquals("Dragons", copy.guildName);
        assertFalse(copy.isCreator);
    }
}
