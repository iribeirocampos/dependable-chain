package communication;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
/**
 * 1st implementation of links: Fair Loss Links (FLL)
 * FLL1. Fair-loss: If a message is sent infinitely often from
 * correct process pi to correct process pj, then m is
 * delivered infinitely often by pj
 * • FLL2. Finite duplication: If a message m is sent a finite
 * number of times from a correct process pi to pj, m is
 * delivered a finite number of times by pj
 * • FLL3. No creation: No message is delivered unless it was
 * sent
 */
public class FairLossLink {
    private final DatagramSocket socket;

    public FairLossLink(int localPort) throws Exception {
        this.socket = new DatagramSocket(localPort);
    }

    // UDP send method (reuse the same socket)
    public void send(String host, int port, String message) throws Exception {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(host), port);
        //System.out.println("Sending message: " + message + " to " + host + ":" + port);
        socket.send(packet);
    }

    // Expose the socket for receiving
    public DatagramSocket getSocket() {
        return socket;
    }
}
