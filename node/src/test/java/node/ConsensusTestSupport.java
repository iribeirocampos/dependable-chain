package node;

import blockchain.Block;
import blockchain.BlockchainService;
import blockchain.Transaction;
import communication.AuthenticatedPerfectLink;
import node.stages.StageContext;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.google.gson.JsonObject;
import java.util.Base64;
import java.nio.file.Path;

/** Shared setup for consensus tests. */
public abstract class ConsensusTestSupport {

    protected int leaderPort;
    protected int[] nodePorts;
    protected int initialPort;
    protected int numNodes;
    protected Map<Integer, KeysGen.NodeKeys> keysPerNode;
    protected AuthenticatedPerfectLink mockLink;
    protected Ed25519PublicKeyParameters clientPublicKey;

    public static Block buildBlockWithTransactions(List<Transaction> transactions, int viewNumber, int proposerPort) {
        byte[] pubKeyBytes = Base64.getDecoder().decode("t68gwnkZmbkosAeSZQlIzFjug09JTgbrgQ6Z0/7tTEI=");
        Ed25519PublicKeyParameters pubKey = new Ed25519PublicKeyParameters(pubKeyBytes, 0);
        return new Block(transactions, "genesis", proposerPort, pubKey, "test-state-root");
    }

    public static Transaction buildTransaction() {
        JsonObject txJson = new JsonObject();
        txJson.addProperty("nonce", 0);
        txJson.addProperty("to", "a+6sngaEb0PT0Q3sPgkTtJt6oZIksjANiUVky1PxlvE=");
        txJson.addProperty("value", 0.0);
        txJson.addProperty("gasLimit", 21000);
        txJson.addProperty("maxFeePerGas", "1000000000");
        txJson.addProperty("data", "");
        txJson.addProperty("publicKey", "a+6sngaEb0PT0Q3sPgkTtJt6oZIksjANiUVky1PxlvE=");
        txJson.addProperty("signature", "");
        return new Transaction(txJson);
    }

    static class NoopAuthenticatedPerfectLink extends AuthenticatedPerfectLink {
        NoopAuthenticatedPerfectLink(int port) throws Exception {
            super(port);
        }

        @Override
        public void send(String ip, int port, String msg) {
            // Intentionally drop outbound messages in tests to avoid network waits.
        }
    }

    @BeforeEach
    public void baseSetUp() throws Exception {
        numNodes = 4;
        initialPort = ThreadLocalRandom.current().nextInt(20000, 40000);
        nodePorts = new int[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodePorts[i] = initialPort + i;
        }
        leaderPort = nodePorts[0];

        keysPerNode = new HashMap<>();
        for (int i = 0; i < numNodes; i++) {
            int nodePort = nodePorts[i];
            keysPerNode.put(nodePort, KeysGen.generateDeterministicKeys(initialPort, numNodes, nodePort));
        }

        clientPublicKey = keysPerNode.get(leaderPort).clientPublicKey;
        mockLink = new NoopAuthenticatedPerfectLink(leaderPort);
    }

    protected void ensureInitialized() throws Exception {
        if (keysPerNode == null) {
            baseSetUp();
        }
    }

    protected StageContext createStageContext(int nodeId, int leaderId, Block block, int viewNumber) {
        HashMap<Integer, Ed25519PublicKeyParameters> nodesPublicKeys = new HashMap<>();
        for (int i = 0; i < numNodes; i++) {
            nodesPublicKeys.put(nodePorts[i], keysPerNode.get(nodePorts[i]).publicKeys.get(i));
        }

        return new StageContext(
                block,
                mockLink,
                nodePorts,
                nodeId,
                leaderId,
                viewNumber,
                new HashMap<>(),
                null,
                keysPerNode.get(nodeId).privateKey,
                nodesPublicKeys,
                new HashMap<>(),
                "genesis",
                null,
                new BlockchainService());
    }

    protected String signData(int senderPort, String blockHash, int viewNumber, String stageName) throws Exception {
        String dataToSign = viewNumber + "|" + stageName + "|" + blockHash;
        return Utils.signData(dataToSign, keysPerNode.get(senderPort).privateKey);
    }

    public static void cleanup() throws Exception {
        System.out.println("[CLEANUP]  ------------------- Cleaning up test artifacts...");
        String userDir = System.getProperty("user.dir");
        Path db1 = Path.of(userDir, "database", "accounts.json");
        Path db2 = Path.of(userDir, "../database/accounts.json").normalize();
        Path sc1 = Path.of(userDir, "database", "smart_contracts.json");
        Path sc2 = Path.of(userDir, "../database/smart_contracts.json").normalize();

        boolean deleted1 = java.nio.file.Files.deleteIfExists(db1);
        boolean deleted2 = java.nio.file.Files.deleteIfExists(db2);
        boolean deleted3 = java.nio.file.Files.deleteIfExists(sc1);
        boolean deleted4 = java.nio.file.Files.deleteIfExists(sc2);

        System.out.println("[CLEANUP][AfterEach] Deleted accounts.json: " + (deleted1 || deleted2));
        System.out.println("[CLEANUP][AfterEach] Deleted smart_contracts.json: " + (deleted3 || deleted4));
    }

}
