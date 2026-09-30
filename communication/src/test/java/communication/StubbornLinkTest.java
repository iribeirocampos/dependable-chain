package communication;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class StubbornLinkTest {

    @Test
    public void testSendAndReceiveAck() throws Exception {
        int senderPort = 4000;
        int receiverPort = 4001;
        String testMessage = "hello stubborn";
        CountDownLatch deliveredLatch = new CountDownLatch(1);
        CountDownLatch ackLatch = new CountDownLatch(1);

        // Use single-element arrays to allow assignment inside lambdas
        final StubbornLink[] receiver = new StubbornLink[1];
        final StubbornLink[] sender = new StubbornLink[1];

        // Receiver (acts as a node)
        receiver[0] = new StubbornLink(receiverPort, (msg, packet) -> {
            if (msg.equals(testMessage)) {
                deliveredLatch.countDown();
                try {
                    receiver[0].sendAck(packet.getAddress().getHostAddress(), packet.getPort(), "ACK");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (msg.equals("ACK")) {
                receiver[0].ReceivedAck("localhost", senderPort);
            }
        });

        // Sender (acts as a client)
        sender[0] = new StubbornLink(senderPort, (msg, packet) -> {
            if (msg.equals("ACK")) {
                ackLatch.countDown();
                sender[0].ReceivedAck("localhost", receiverPort);
            }
        });

        // Give the receiver a moment to start
        Thread.sleep(200);

        // Send message from sender to receiver
        new Thread(() -> {
            try {
                sender[0].send("localhost", receiverPort, testMessage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Wait for delivery and ACK
        assertTrue(deliveredLatch.await(2, TimeUnit.SECONDS), "Receiver did not deliver the message in time");
        assertTrue(ackLatch.await(2, TimeUnit.SECONDS), "Sender did not receive ACK in time");
    }
}
