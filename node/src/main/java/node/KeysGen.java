package node;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import java.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class KeysGen {
    private static final String MASTER_SEED = "my-deterministic-master-seed";

    public static class NodeKeys {
        public final List<Ed25519PublicKeyParameters> publicKeys;
        public final Ed25519PrivateKeyParameters privateKey;
        // public final List<Ed25519PublicKeyParameters> clientPublicKey;
        public final Ed25519PublicKeyParameters clientPublicKey;

        public NodeKeys(List<Ed25519PublicKeyParameters> publicKeys, Ed25519PrivateKeyParameters privateKey,
                Ed25519PublicKeyParameters clientPublicKey) {
            this.publicKeys = publicKeys;
            this.privateKey = privateKey;
            this.clientPublicKey = clientPublicKey;
        }
    }

    public static NodeKeys generateDeterministicKeys(int initialPortNode, int numberNodes, int currentNode)
            throws Exception {
        List<Ed25519PublicKeyParameters> publicKeys = new ArrayList<>();
        Ed25519PrivateKeyParameters myPrivateKey = null;

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // Generate Node Keys
        for (int i = initialPortNode; i < initialPortNode + numberNodes; i++) {
            // Create a deterministic 32-byte seed for each node
            String nodeDeterminsticString = MASTER_SEED + "-node-" + i;
            byte[] seedBytes = digest.digest(nodeDeterminsticString.getBytes(StandardCharsets.UTF_8));

            // Generate Ed25519 private key directly from the 32-byte seed
            Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(seedBytes, 0);
            Ed25519PublicKeyParameters publicKey = privateKey.generatePublicKey();
            System.out.println("[DEBUG CRYPTO]Node " + i + " public key: "
                    + Base64.getEncoder().encodeToString(publicKey.getEncoded()));

            publicKeys.add(publicKey);

            // Save the private key for the current node
            if (i == currentNode) {
                myPrivateKey = privateKey;
            }
        }

        // Generate Client Key
        List<Ed25519PublicKeyParameters> clientPublicKeys = new ArrayList<>();
        for (int i = 3000; i < 3001; i++) {
            String clientDeterminsticString = MASTER_SEED + "-client" + i;
            byte[] clientSeedBytes = digest.digest(clientDeterminsticString.getBytes(StandardCharsets.UTF_8));

            Ed25519PrivateKeyParameters clientPrivateKey = new Ed25519PrivateKeyParameters(clientSeedBytes, 0);
            Ed25519PublicKeyParameters clientPublicKey = clientPrivateKey.generatePublicKey();
            clientPublicKeys.add(clientPublicKey);
            System.out.println(
                    "[DEBUG CRYPTO]Client public key: "
                            + Base64.getEncoder().encodeToString(clientPublicKey.getEncoded()));
        }

        return new NodeKeys(publicKeys, myPrivateKey, clientPublicKeys.get(0));
    }
}
