package communication;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramPacket;

public class PerfectLinkTest {

    static class TestPerfectLink extends PerfectLink {
        private int deliverCount = 0;
        private String lastDeliveredMsg = null;

        public TestPerfectLink() throws Exception {
            super(0, (msg, packet) -> {
            });
        }

        public void deliver(String msg) {
            deliverCount++;
            lastDeliveredMsg = msg;
        }

        public int getDeliverCount() {
            return deliverCount;
        }

        public String getLastDeliveredMsg() {
            return lastDeliveredMsg;
        }
    }

    @Test
    public void testCorrectMessageDelivery() throws Exception {
        TestPerfectLink receiver = new TestPerfectLink();
        String msg = "10:hello perfect";
        DatagramPacket dummyPacket = new DatagramPacket(new byte[0], 0, java.net.InetAddress.getByName("localhost"),
                12345);
        receiver.handleMessage(msg, dummyPacket);
        assertEquals(1, receiver.getDeliverCount(), "Message should be delivered once");
        assertEquals("hello perfect", receiver.getLastDeliveredMsg());
    }

    @Test
    public void testDuplicateMessageNotDeliveredTwice() throws Exception {
        TestPerfectLink receiver = new TestPerfectLink();
        String msg = "42:dup";
        DatagramPacket dummyPacket = new DatagramPacket(new byte[0], 0, java.net.InetAddress.getByName("localhost"),
                12345);
        receiver.handleMessage(msg, dummyPacket);
        receiver.handleMessage(msg, dummyPacket);
        assertEquals(1, receiver.getDeliverCount(), "Duplicate message was delivered more than once");
    }

    @Test
    public void testPastIdMessageNotDelivered() throws Exception {
        TestPerfectLink receiver = new TestPerfectLink();
        String msg10 = "10:should deliver";
        String msg5 = "5:should not deliver";
        DatagramPacket dummyPacket = new DatagramPacket(new byte[0], 0, java.net.InetAddress.getByName("localhost"),
                12345);
        receiver.handleMessage(msg10, dummyPacket);
        receiver.handleMessage(msg5, dummyPacket);
        assertEquals(1, receiver.getDeliverCount(), "Past id message was incorrectly delivered");
        assertEquals("should deliver", receiver.getLastDeliveredMsg());
    }
}
