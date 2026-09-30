package communication;

import java.net.DatagramPacket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * 2nd implementation of links, uses FairLossLinks: Stubborn Links (SL)
 * SL1. Stubborn delivery: if a correct process pi sends
 * a message m to a correct process pj, then pj delivers
 * m an infinite number of times.
 * SL2. No creation: No message is delivered unless it was sent.
 */
public class StubbornLink {
    private final FairLossLink fairLossLink;
    private final Map<String, AtomicBoolean> waitingByPeer = new ConcurrentHashMap<>();

    public StubbornLink(int localPort) throws Exception {
        fairLossLink = new FairLossLink(localPort);
        startListening(this::handleMessage);
    }

    public StubbornLink(int localPort, BiConsumer<String, DatagramPacket> handler) throws Exception {
        fairLossLink = new FairLossLink(localPort);
        startListening(handler);
    }

    public void startListening(BiConsumer<String, DatagramPacket> handler) {
        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024*16];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    fairLossLink.getSocket().receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    handler.accept(msg, packet);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleMessage(String msg, DatagramPacket packet) {
        if (msg.startsWith("ACK")) {
            ReceivedAck(packet.getAddress().getHostAddress(), packet.getPort());
            return;
        }

        deliver(msg);

        try {
            sendAck(packet.getAddress().getHostAddress(), packet.getPort(), "ACK");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendAck(String host, int port, String message) throws Exception {
        fairLossLink.send(host, port, message);
    }

    public void send(String host, int port, String message) throws Exception {
        String key = peerKey(host, port);
        AtomicBoolean waiting = waitingByPeer.computeIfAbsent(key, k -> new AtomicBoolean(false));
        waiting.set(true);

        while (waiting.get()) {
            fairLossLink.send(host, port, message);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void deliver(String message) {
        // override if needed
    }

    public void ReceivedAck(String host, int port) {
        String key = peerKey(host, port);
        AtomicBoolean waiting = waitingByPeer.get(key);
        if (waiting != null) {
            waiting.set(false);
        }
    }

    private String peerKey(String host, int port) {
        return normalizeHost(host) + ":" + port;
    }

    private String normalizeHost(String host) {
        if ("localhost".equals(host) || "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host)) {
            return "127.0.0.1";
        }
        return host;
    }
}
