package communication;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramPacket;

// replay Attack
// Invalid requests
// quorum certificates invalidos
// drop messages - Consenso e APL
public class AuthenticatedPerfectLinkTest {

    static class TestAuthenticatedPerfectLink extends AuthenticatedPerfectLink {
        private int deliverCount = 0;
        private String lastDeliveredMsg = null;

        public TestAuthenticatedPerfectLink(int port) throws Exception {
            super(port);
        }

        @Override
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
    public void testHandshake() throws Exception {
        TestAuthenticatedPerfectLink receiver = new TestAuthenticatedPerfectLink(5000);
        TestAuthenticatedPerfectLink sender = new TestAuthenticatedPerfectLink(5001);
        sender.send("localhost", 5000, "Hello");

        String receiverSecret = receiver.getSecretForTests(5001);
        String senderSecret = sender.getSecretForTests(5000);
        assertNotNull(receiverSecret, "Receiver should have a secret after handshake");
        assertNotNull(senderSecret, "Sender should have a secret after handshake");
        assertEquals(receiverSecret, senderSecret, "Both parties should have the same secret after handshake");
    }

    @Test
    public void testCorrectMessage() throws Exception {
        TestAuthenticatedPerfectLink receiver = new TestAuthenticatedPerfectLink(6000);
        receiver.setSecretForTests(6001, "unit-test-secret");
        String d = receiver.getDelim();

        // Simulate a correct message
        String sender = "6001";
        String receiverStr = "6000";
        String text = "hello authenticated";
        int msgId = 1;
        String mac = receiver.authenticate(sender, text, msgId, 6001, 6000); // Correct
        String msg = msgId + d + sender + d + text + d + mac;

        DatagramPacket dummyPacket = new DatagramPacket(new byte[0], 0, java.net.InetAddress.getByName("localhost"),
                6001);
        receiver.handleMessage(msg, dummyPacket);

        assertEquals(1, receiver.getDeliverCount(), "Correct message should be delivered");
        assertEquals(text, receiver.getLastDeliveredMsg());
    }

    @Test
    public void testAttackMessageWrongPort() throws Exception {
        TestAuthenticatedPerfectLink receiver = new TestAuthenticatedPerfectLink(6001);
        receiver.setSecretForTests(6001, "unit-test-secret");

        String sender = "127.0.0.1";
        int receiverPort = 9999; // Wrong port!
        String text = "attack message";

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> receiver.authenticate(sender, text, 0, 9999, 9999));

        assertTrue(ex.getMessage().contains("Shared secret not established for port 9999"));
        assertEquals(0, receiver.getDeliverCount(), "No message should be delivered");
    }

    @Test
    public void testAttackMessageDifferentText() throws Exception {
        TestAuthenticatedPerfectLink receiver = new TestAuthenticatedPerfectLink(6002);
        receiver.setSecretForTests(6001, "unit-test-secret");
        String d = receiver.getDelim();

        // Attacker crafts a message with a valid MAC for "original text", but sends
        // "tampered text"
        String sender = "6001";
        int receiverPort = 6002;
        String originalText = "original text";
        String tamperedText = "tampered text";
        int msgId = 1;
        String mac = receiver.authenticate(sender, originalText, msgId, 6001, 6002); // Correct
        // Now attacker sends a message with tampered text but the MAC for the original
        // text
        String msg = msgId + d + sender + d + tamperedText + d + mac;

        DatagramPacket dummyPacket = new DatagramPacket(new byte[0], 0, java.net.InetAddress.getByName("localhost"),
                6001);
        receiver.handleMessage(msg, dummyPacket);

        assertEquals(0, receiver.getDeliverCount(), "Attack message with tampered text should NOT be delivered");
    }

    public void testReplayAttackMessageNotDeliveredTwice() throws Exception {
        TestAuthenticatedPerfectLink receiver = new TestAuthenticatedPerfectLink(6100);
        receiver.setSecretForTests(6101, "unit-test-secret");
        String d = receiver.getDelim();

        String sender = "127.0.0.1";
        String text = "replay-protected";
        int msgId = 7;
        String mac = receiver.authenticate(sender, text, msgId, 6101, 6100);
        String msg = msgId + d + sender + d + text + d + mac;

        DatagramPacket dummyPacket = new DatagramPacket(new byte[0], 0, java.net.InetAddress.getByName("localhost"),
                6101);

        receiver.handleMessage(msg, dummyPacket);
        receiver.handleMessage(msg, dummyPacket);

        assertEquals(1, receiver.getDeliverCount(), "Replay attack should not deliver the same message twice");
        assertEquals(text, receiver.getLastDeliveredMsg());
    }

    @Test
    public void testInvalidRequestDropped() throws Exception {
        TestAuthenticatedPerfectLink receiver = new TestAuthenticatedPerfectLink(6200);

        DatagramPacket dummyPacket = new DatagramPacket(new byte[0], 0, java.net.InetAddress.getByName("localhost"),
                6201);

        // 1. Invalid format: missing expected 4 fields (msgId|||sender|||text|||mac)
        String invalidMsg1 = "this-is-not-a-valid-authenticated-request";
        receiver.handleMessage(invalidMsg1, dummyPacket);

        // 2. Invalid format: fields exist, but msgId is not an integer
        String d = receiver.getDelim();
        String invalidMsg2 = "NotAnInt" + d + "127.0.0.1" + d + "text" + d + "somemac";
        receiver.handleMessage(invalidMsg2, dummyPacket);

        assertEquals(0, receiver.getDeliverCount(), "Invalid requests must be dropped");
    }

    @Test
    public void testDropMessagesGapAndOutOfOrder() throws Exception {
        TestAuthenticatedPerfectLink receiver = new TestAuthenticatedPerfectLink(6300);
        receiver.setSecretForTests(6301, "unit-test-secret");
        String d = receiver.getDelim();

        String sender = "127.0.0.1";
        DatagramPacket dummyPacket = new DatagramPacket(new byte[0], 0, java.net.InetAddress.getByName("localhost"),
                6301);

        // Simulate a dropped msgId=1 by only delivering msgId=2 first
        int msgId2 = 2;
        String text2 = "message-after-drop";
        String mac2 = receiver.authenticate(sender, text2, msgId2, 6301, 6300);
        String msg2 = msgId2 + d + sender + d + text2 + d + mac2;
        receiver.handleMessage(msg2, dummyPacket);

        assertEquals(1, receiver.getDeliverCount(), "A valid message after a dropped one should still be delivered");
        assertEquals(text2, receiver.getLastDeliveredMsg());

        // Now simulate the dropped msgId=1 arriving late (out of order)
        int msgId1 = 1;
        String text1 = "delayed-message";
        String mac1 = receiver.authenticate(sender, text1, msgId1, 6301, 6300);
        String msg1 = msgId1 + d + sender + d + text1 + d + mac1;
        receiver.handleMessage(msg1, dummyPacket);

        assertEquals(1, receiver.getDeliverCount(), "Out-of-order older messages must be dropped to prevent replay/stale data");
    }

    @Test
    public void testInvalidMADropped() throws Exception {
        TestAuthenticatedPerfectLink receiver = new TestAuthenticatedPerfectLink(6400);
        receiver.setSecretForTests(6401, "unit-test-secret");
        String d = receiver.getDelim();

        String sender = "127.0.0.1";
        String text = "invalid-mac-message";
        int msgId = 3;

        // Deliberately invalid MAC (does not match computed HMAC)
        String invalidMac = "invalid-mac-value";
        String msg = msgId + d + sender + d + text + d + invalidMac;

        DatagramPacket dummyPacket = new DatagramPacket(new byte[0], 0, java.net.InetAddress.getByName("localhost"),
                6401);
        receiver.handleMessage(msg, dummyPacket);

        assertEquals(0, receiver.getDeliverCount(), "Message with invalid MA/MAC must be dropped");
    }

}
