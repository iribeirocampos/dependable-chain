package communication;

import java.net.DatagramPacket;
import java.util.Map;
import java.util.HashMap;
import java.util.function.BiConsumer;

/**
 * 3rd implementation of links, uses StubbornLinks: Perfect Links (PL)
 * PL1. Validity: If pi and pj are correct, then every
 * message sent by pi to pj is eventually delivered by
 * pj
 * • PL2. No duplication: No message is delivered (to a
 * process) more than once
 * • PL3. No creation: No message is delivered unless
 * it was sent
 */
public class PerfectLink {
    StubbornLink stubbornLink;
    int msgId = 0;
    Map<String, Integer> lastDelivered = new HashMap<>();

    public PerfectLink(int port) throws Exception {
        stubbornLink = new StubbornLink(port, this::handleMessage);
    }

    public PerfectLink(int port, BiConsumer<String, DatagramPacket> handler) throws Exception {
        stubbornLink = new StubbornLink(port, handler);
    }

    public void handleMessage(String msg, DatagramPacket packet) {
        if (msg.startsWith("ACK")) {
            ReceivedAck(packet.getAddress().getHostAddress(), packet.getPort());
            return;
        }
        String[] parts = msg.split(":", 2);
        int msgId = Integer.parseInt(parts[0]);
        String text = parts[1];
        System.out.println(text + " with msgId: " + msgId);

        // Identify sender by IP:Port
        String senderKey = packet.getAddress().getHostAddress() + ":" + packet.getPort();
        int lastDeliveredId = lastDelivered.getOrDefault(senderKey, 0);

        if (msgId > lastDeliveredId) {
            System.out.println("Delivering message: " + text);
            deliver(text);
            lastDelivered.put(senderKey, msgId);
        }

        // Always ACK valid messages (even duplicates) to stop stubborn retransmission
        try {
            sendAck(packet.getAddress().getHostAddress(), packet.getPort(), msgId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendAck(String ip, int port, int msgId) throws Exception {
        stubbornLink.sendAck(ip, port, "ACK:" + msgId);
    }

    public void send(String ip, int port, String msg) throws Exception {
        this.msgId++;
        stubbornLink.send(ip, port, this.msgId + ":" + msg);
    }

    protected void deliver(String msg) {
        System.out.println("Delivered: " + msg);
    }

    public void ReceivedAck(String host, int port) {
        stubbornLink.ReceivedAck(host, port);
    }
}