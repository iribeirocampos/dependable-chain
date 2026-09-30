package communication;

import java.net.DatagramPacket;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class AuthenticatedPerfectLink {
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String DH_PREFIX = "DH_KEY_EXCHANGE";
    private static final String DELIM = "|||";

    private final int localPort;
    private final ConcurrentHashMap<Integer, String> macSecrets = new ConcurrentHashMap<>();
    private final StubbornLink stubbornLink;
    private final Map<String, Integer> lastDelivered = new HashMap<>();

    private int msgId = 0;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    private final Object secretLock = new Object();
    private final Set<Integer> dhInProgress = new HashSet<>();

    public AuthenticatedPerfectLink(int port) throws Exception {
        this.localPort = port;
        this.stubbornLink = new StubbornLink(port, this::handleMessage);
        generateKeys();
    }

    public String getSecretForTests(int port) {
        return macSecrets.get(port);
    }

    public void handleMessage(String msg, DatagramPacket packet) {
        if (msg.startsWith("ACK")) {
            stubbornLink.ReceivedAck(packet.getAddress().getHostAddress(), packet.getPort());
            return;
        }

        if (msg.startsWith(DH_PREFIX)) {
            try {
                stubbornLink.sendAck(packet.getAddress().getHostAddress(), packet.getPort(), "ACK" + DELIM + msg);
                handleDiffieHellman(packet.getPort(), msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            String[] parts = msg.split(java.util.regex.Pattern.quote(DELIM), 4);
            int incomingMsgId = Integer.parseInt(parts[0]);
            String sender = parts[1];
            String text = parts[2];
            String mac = parts[3];

            int senderPort = packet.getPort();
            if (verifyauth(sender, text, mac, incomingMsgId, senderPort, localPort)) {
                String senderKey = packet.getAddress().getHostAddress() + DELIM + packet.getPort();
                int lastDeliveredId = lastDelivered.getOrDefault(senderKey, 0);

                if (incomingMsgId > lastDeliveredId) {
                    deliver(text);
                    lastDelivered.put(senderKey, incomingMsgId);
                }

                stubbornLink.sendAck(
                        packet.getAddress().getHostAddress(),
                        packet.getPort(),
                        "ACK" + DELIM + incomingMsgId);
            } else {
                System.out.println("MAC verification failed! Message dropped. from node " + packet.getPort()
                        + "with MAC: " + mac + " and expected MAC: "
                        + authenticate(sender, text, incomingMsgId, senderPort, localPort));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected String authenticate(String sender, String message, int msgId, int secretMAC, int normalizedReceiverPort)
            throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        String secret = macSecrets.get(secretMAC);
        // System.out.println("[authenticate] Using secret for port " + secretMAC + ": "
        // + normalizedReceiverPort);
        if (secret == null) {
            throw new IllegalStateException("Shared secret not established for port " + secretMAC);
        }

        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(), HMAC_ALGO);
        mac.init(keySpec);

        String normalizedReceiver = Integer.toString(normalizedReceiverPort);
        String data = msgId + DELIM + sender + DELIM + normalizedReceiver + DELIM + message;
        // System.out.println("[authenticate] Data to authenticate: " + data);

        byte[] macBytes = mac.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(macBytes);
    }

    protected boolean verifyauth(String sender, String message, String macStr, int msgId, int secretMAC,
            int normalizedReceiverPort)
            throws Exception {
        String expectedMac = authenticate(sender, message, msgId, secretMAC, normalizedReceiverPort);
        return expectedMac.equals(macStr);
    }

    public void send(String ip, int port, String msg) throws Exception {
        boolean shouldStartDh = false;

        synchronized (secretLock) {
            if (macSecrets.get(port) == null && !dhInProgress.contains(port)) {
                dhInProgress.add(port);
                shouldStartDh = true;
            }
        }

        if (shouldStartDh) {
            handleDiffieHellman(port, null);
        }

        synchronized (secretLock) {
            long deadline = System.currentTimeMillis() + 5000;
            while (macSecrets.get(port) == null) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    dhInProgress.remove(port);
                    throw new IllegalStateException("Timed out waiting for shared secret for port " + port);
                }
                secretLock.wait(remaining);
            }
        }

        sendAuthenticated(ip, port, msg);
    }

    private void setSecret(int port, String secret) {
        // System.out
        // .println("[macSecrets] NODE " + localPort + " Setting shared secret for port
        // " + port + ": " + secret);
        synchronized (secretLock) {
            macSecrets.put(port, secret);
            dhInProgress.remove(port);
            secretLock.notifyAll();
        }
    }

    public void sendAuthenticated(String ip, int port, String msg) throws Exception {
        String sender = java.net.InetAddress.getLocalHost().getHostAddress();
        int nextMsgId = ++msgId;
        String mac = authenticate(sender, msg, nextMsgId, port, port);
        String fullMsg = nextMsgId + DELIM + sender + DELIM + msg + DELIM + mac;

        stubbornLink.send(ip, port, fullMsg);
        // System.out.println("[APL] Sent authenticated message to " + ip + ":" + port +
        // " msgId=" + nextMsgId);
    }

    public void deliver(String msg) {
        System.out.println("+++++++ Delivered: " + msg);
    }

    public int getLocalPort() {
        return localPort;
    }

    protected String getDelim() {
        return DELIM;
    }

    protected void setSecretForTests(int port, String secret) {
        macSecrets.put(port, secret);
    }

    private void handleDiffieHellman(int port, String message) throws Exception {
        synchronized (secretLock) {
            if (macSecrets.get(port) != null) {
                dhInProgress.remove(port);
                secretLock.notifyAll();
                return;
            }
        }

        if (message == null) {
            String initMsg = DH_PREFIX + DELIM + Base64.getEncoder().encodeToString(publicKey.getEncoded());
            new Thread(() -> {
                try {
                    stubbornLink.send("127.0.0.1", port, initMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            return;
        }

        String[] parts = message.split(java.util.regex.Pattern.quote(DELIM), 2);
        byte[] otherPubKeyBytes = Base64.getDecoder().decode(parts[1]);

        KeyFactory kf = KeyFactory.getInstance("DH");
        PublicKey otherPubKey = kf.generatePublic(new X509EncodedKeySpec(otherPubKeyBytes));

        KeyAgreement ka = KeyAgreement.getInstance("DH");
        ka.init(privateKey);
        ka.doPhase(otherPubKey, true);

        byte[] sharedSecret = ka.generateSecret();
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(sharedSecret);
        String derived = Base64.getEncoder().encodeToString(hash);

        setSecret(port, derived);
        // setSecret(localPort, derived);

        // System.out.println("[macSecrets] NODE " + localPort + " Derived shared secret
        // for port " + port);

        String replyMsg = DH_PREFIX + DELIM + Base64.getEncoder().encodeToString(publicKey.getEncoded());
        new Thread(() -> {
            try {
                stubbornLink.send("127.0.0.1", port, replyMsg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        privateKey = kp.getPrivate();
        publicKey = kp.getPublic();
    }

}