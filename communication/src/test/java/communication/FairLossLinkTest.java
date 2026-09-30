package communication;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FairLossLinkTest {

    @Test
    public void testSendAndReceive() throws Exception {
        int senderPort = 13000;
        int receiverPort = 13001;
        String testMessage = "hello fair loss";
        CountDownLatch latch = new CountDownLatch(1);

        // Receiver thread
        Thread receiverThread = new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket(receiverPort);
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());
                assertEquals(testMessage, received);
                latch.countDown();
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        receiverThread.start();

        // Give the receiver a moment to start
        Thread.sleep(200);

        // Sender
        FairLossLink sender = new FairLossLink(senderPort);
        sender.send("localhost", receiverPort, testMessage);

        // Wait for the receiver to get the message
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Message was not received in time");
    }
}