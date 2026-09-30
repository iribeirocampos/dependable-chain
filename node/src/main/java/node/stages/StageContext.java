package node.stages;

import communication.AuthenticatedPerfectLink;
import node.QuorumCertificate;

import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

import com.google.gson.JsonObject;

import blockchain.Block;
import blockchain.BlockchainService;
import blockchain.Transaction;

public class StageContext {
    public AuthenticatedPerfectLink link;
    public int[] nodePorts;
    public int nodeId;
    public int leaderId;
    public int viewNumber;

    public Block block;
    public QuorumCertificate highQC;
    public QuorumCertificate prepareQC;
    public QuorumCertificate precommitQC;
    public QuorumCertificate commitQC;
    public QuorumCertificate lockedQC;

    public Map<String, Block> blockchain;
    public JsonObject originalMessage;
    public String lastBlockHash; // Track the hash of the last committed block

    public Ed25519PublicKeyParameters clientPublicKey;
    private Ed25519PrivateKeyParameters privateKey;
    public HashMap<Integer, Ed25519PublicKeyParameters> nodesPublicKeys;
    public Map<Integer, Integer> lastReqByClient;
    private Consumer<Transaction> validatedTransactionSink;
    private BlockchainService blockchainService;

    public StageContext(Block block, AuthenticatedPerfectLink link,
            int[] nodePorts, int nodeId, int leaderId, int viewNumber,
            Map<String, Block> blockchain, JsonObject originalMessage,
            Ed25519PrivateKeyParameters privateKey,
            HashMap<Integer, Ed25519PublicKeyParameters> nodesPublicKeys,
            Map<Integer, Integer> lastReqByClient, String lastCommittedHash,
            Consumer<Transaction> validatedTransactionSink,
            BlockchainService blockchainService) {
        this.block = block;
        this.link = link;
        this.nodePorts = nodePorts;
        this.nodeId = nodeId;
        this.leaderId = leaderId;
        this.viewNumber = viewNumber;
        this.blockchain = blockchain;

        this.originalMessage = originalMessage;
        // this.clientPublicKey = clientPublicKey;
        this.privateKey = privateKey;
        this.nodesPublicKeys = nodesPublicKeys;
        this.lastReqByClient = lastReqByClient;
        this.lastBlockHash = lastCommittedHash; // Initialize to genesis
        this.validatedTransactionSink = validatedTransactionSink;
        this.blockchainService = blockchainService;
    }

    public Ed25519PrivateKeyParameters getPrivateKey() {
        return privateKey;
    }

    public int getQuorum() {
        int n = nodePorts.length;
        return n - (n - 1) / 3;
    }

    public boolean isLeader() {
        return nodeId == leaderId;
    }

    public boolean safeNode(Block proposedBlock, QuorumCertificate justify) {
        if (lockedQC == null)
            return true;
        boolean safetyRule = proposedBlock.getPreviousHash().equals(lockedQC.nodeHash);
        boolean livenessRule = justify != null && justify.viewNumber > lockedQC.viewNumber;
        return safetyRule || livenessRule;
    }

    /*
     * public boolean verifyClientRequestFromBlock(JsonObject clientRequest) {
     * if (clientRequest == null) {
     * System.err.println("[WARNING] clientRequest is null");
     * return false;
     * }
     * System.out.println("Verifying client request from block: " +
     * clientRequest.toString());
     * return node.Utils.verifyClientRequest(clientRequest, clientPublicKey,
     * lastReqByClient);
     * }
     */
    public void addValidTxToTxPool(Transaction tx) {
        if (validatedTransactionSink != null) {
            validatedTransactionSink.accept(tx);
        }
    }

    public BlockchainService getBlockchainService() {
        return blockchainService;
    }

}
