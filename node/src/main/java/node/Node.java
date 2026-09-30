package node;

import blockchain.Block;
import blockchain.BlockchainService;
import blockchain.Transaction;
import communication.AuthenticatedPerfectLink;
import node.stages.StageContext;
import node.stages.StageManager;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

import com.google.gson.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public class Node {
    private static final long BLOCK_BATCHING_PERIOD_MS = 12_000;
    private static final long GAS_LIMIT_BLOCK = 500_000_000;

    private final LinkedHashMap<String, Block> blockchain = new LinkedHashMap<>();
    private final BlockchainService blockchainService = new BlockchainService();
    private final Timer batchTimer = new Timer("block-batcher", true);
    private AuthenticatedPerfectLink link;
    private int[] nodePorts;
    private int nodeId;
    private String lastCommittedHash = "genesis";
    private StageManager stageManager;
    private int viewNumber = 1;
    private QuorumCertificate highestPrepareQC = null;
    private final List<HotStuffMessage> bufferedConsensusMessages = new ArrayList<>();
    private Ed25519PrivateKeyParameters privateKey;
    private HashMap<Integer, Ed25519PublicKeyParameters> nodesPublicKeys;
    // private Ed25519PublicKeyParameters clientPublicKey;
    private Map<Integer, Integer> lastReqByClient = new HashMap<>();
    // For deduplication of client requests: set of "clientId:requestId"
    private final Set<String> seenTxIds = ConcurrentHashMap.newKeySet();
    // Keep transactions ordered by timestamp and requestId
    private final PriorityBlockingQueue<Transaction> txPool = new PriorityBlockingQueue<>(
            1000,
            Comparator.comparingInt(Transaction::getNonce)
                    .thenComparing(Transaction::getFee, Comparator.reverseOrder()));

    public static void main(String[] args) {
        try {
            new Node().start(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: <node_port> <number_of_nodes> <starting_node_port>");
            return;
        }

        nodeId = Integer.parseInt(args[0]);
        int numNodes = Integer.parseInt(args[1]);
        int startingPort = Integer.parseInt(args[2]);

        nodePorts = new int[numNodes];
        for (int i = 0; i < numNodes; i++)
            nodePorts[i] = startingPort + i;

        try {
            KeysGen.NodeKeys keys = KeysGen.generateDeterministicKeys(startingPort, numNodes, nodeId);
            privateKey = keys.privateKey;
            // clientPublicKey = keys.clientPublicKey;
            nodesPublicKeys = new HashMap<>();
            for (int i = 0; i < numNodes; i++) {
                int port = startingPort + i;
                nodesPublicKeys.put(port, keys.publicKeys.get(i));
            }

        } catch (Exception e) {
            System.err.println("Failed to load keys: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        System.out.println("Node " + nodeId + " starting, leader for view 1: " + getLeader(1));
        try {
            link = new AuthenticatedPerfectLink(nodeId) {
                @Override
                public void deliver(String msg) {
                    new Thread(() -> processMessage(msg)).start();
                }
            };
            batchTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    synchronized (Node.this) {
                        maybeProposeBlockFromPool();
                    }
                }
            }, BLOCK_BATCHING_PERIOD_MS, BLOCK_BATCHING_PERIOD_MS);
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // hardcode client 3000 nonce increment - Smartcontract deployment
        lastReqByClient.put(3000, 1);
    }

    public synchronized void processMessage(String message) {
        // System.out.println("Node " + nodeId + " received message: " + message);
        JsonObject json = Utils.tryParseJson(message);
        if (json != null && json.has("request")) {
            String messageType = json.get("request").getAsString();
            switch (messageType) {
                case "CLIENT_REQUEST":
                    handleClientRequest(json);
                    return;
                case "NODE_MESSAGE":
                    handleClientRequest(json);
                    break;
                case "CONSENSUS":
                    HotStuffMessage msg = HotStuffMessage.deserialize(message);
                    handleConsensusMessage(msg);
                    break;
                default:
                    System.out.println("Unknown message type: " + messageType);
                    return;
            }
        }

    }

    private void handleClientRequest(JsonObject req) {
        String command = req.get("command").getAsString();
        System.out.println("[CLIENT REQUEST] Received client request: " + command);
        switch (command) {
            case "pool":
                // No need for siganture confirmation for this debug command, just print current
                // pool
                System.out.println("[CLIENT REQUEST] Current TxPool:");
                for (Transaction tx : txPool) {
                    System.out
                            .println("[CLIENT REQUEST] - " + tx.getHash() + " (publicKey: " + tx.getPublicKey() + ")");
                }
                break;
            case "read":
                // No need for siganture confirmation for this debug command, just print current
                // blockchain state
                blockchainService.printBlockchain();
                break;
            case "addresses":
                sendClientResponse(req, "addresses", blockchainService.getAddressList());
                break;
            case "balance_read":
                try {
                    String address = req.getAsJsonObject("payload").get("address").getAsString();
                    sendClientResponse(req, "balance_read", blockchainService.getBalanceString(address));
                } catch (Exception e) {
                    sendClientResponse(req, "balance_read", "Error: " + e.getMessage());
                }
                break;
            case "contract_read":
                try {
                    JsonObject payload = req.getAsJsonObject("payload");
                    String result = blockchainService.readContract(
                            payload.get("sender").getAsString(),
                            payload.get("contract").getAsString(),
                            payload.get("callData").getAsString());
                    String hex = result.startsWith("0x") ? result.substring(2) : result;
                    sendClientResponse(req, "contract_read",
                            hex.isEmpty() ? "0" : new BigInteger(hex, 16).toString());
                } catch (Exception e) {
                    sendClientResponse(req, "contract_read", "Error: " + e.getMessage());
                }
                break;
            case "tx":
                // Need to check signature and deduplication for real transactions, then add to
                // pool and possibly
                // Need to check if possible to run?
                // System.out.println("[CLIENT REQUEST] Received transaction request: " + req);

                Transaction tx = new Transaction(req.getAsJsonObject().get("payload").getAsJsonObject());
                System.out.println("[CLIENT REQUEST] Processing transaction: " + tx.getHash());
                if (!Utils.verifyTransactionSignature(tx)) {
                    System.out.println("[WARNING] [NODE] Invalid transaction signature, ignoring");
                    return;
                }

                // Add to pool only if not seen before (deduplication)
                if (seenTxIds.add(tx.getHash())) {
                    txPool.offer(tx);
                    System.out.println("[TxPool] Added transaction: " + tx.getHash());
                    forwardTransactionToLeader(tx);
                } else {
                    System.out.println("[TxPool] Duplicate transaction ignored: " + tx.getHash());
                }
                break;
            default:
                System.out.println("Unknown client command: " + command);
        }

    }

    private void sendClientResponse(JsonObject req, String command, String result) {
        if (!req.has("replyPort"))
            return;
        int replyPort = req.get("replyPort").getAsInt();
        JsonObject response = new JsonObject();
        response.addProperty("request", "CLIENT_RESPONSE");
        response.addProperty("command", command);
        response.addProperty("result", result);
        try {
            link.send("localhost", replyPort, response.toString());
        } catch (Exception e) {
            System.err.println("[NODE] Failed to send response to client: " + e.getMessage());
        }
    }

    private void forwardTransactionToLeader(Transaction tx) {
        int leaderId = getLeader(viewNumber);
        if (nodeId == leaderId) {
            return;
        }

        JsonObject fwd = new JsonObject();
        fwd.addProperty("request", "NODE_MESSAGE");
        fwd.addProperty("command", "tx");
        fwd.add("payload", tx.toJsonObject());

        try {
            link.send("localhost", leaderId, fwd.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void maybeProposeBlockFromPool() {
        int leaderId = getLeader(viewNumber);
        if (nodeId != leaderId) {
            return;
        }

        if (txPool.isEmpty()) {
            return;
        }

        String prevHash = lastCommittedHash != null ? lastCommittedHash : "genesis";
        Block proposedBlock = createBlockFromPool(prevHash);
        if (proposedBlock == null) {
            return;
        }

        ensureConsensusRunning(proposedBlock, leaderId, viewNumber);

        checkConsensusComplete();
    }

    private Block createBlockFromPool(String prevHash) {
        blockchainService.resetTemporaryState();
        // Limit Fees per block 500_000 WEI
        System.out.println("[Block Creation] Creating block from pool with prevHash: " + prevHash);
        List<Transaction> availableTransactions = new ArrayList<>();
        long totalGas = 0;
        PriorityBlockingQueue<Transaction> txPoolCopy = new PriorityBlockingQueue<>(txPool);
        while (!txPoolCopy.isEmpty()) {
            Transaction tx = txPoolCopy.poll();
            System.out.println("[Block Creation] Evaluating transaction for block proposal: " + tx.getHash());
            if (!blockchainService.checkTransaction(tx)) {
                txPool.remove(tx);
                System.out
                        .println("[Block Creation] [REJECTED]Transaction invalid, removed from pool: " + tx.getHash());
                continue;
            }

            if (totalGas + tx.getGasLimit() * tx.getMaxFeePerGas() <= GAS_LIMIT_BLOCK) {
                System.out
                        .println("[Block Creation] Checking transaction validity for block proposal: " + tx.getHash());
                totalGas += blockchainService.runTransaction(tx);
                availableTransactions.add(tx);
            } else {
                System.out.println("[WARNING] [Block Creation] Transaction exceeds gas limit: " + tx.getHash() + "Gas:"
                        + (tx.getGasLimit() * tx.getMaxFeePerGas()) + " TotalGas:" + totalGas);
                break;
            }
        }
        if (availableTransactions.isEmpty()) {
            return null;
        }
        Ed25519PublicKeyParameters proposerPublicKey = nodesPublicKeys.get(nodeId);
        blockchainService.payMiner(proposerPublicKey, totalGas);
        String stateRoot = blockchainService.getTemporaryStateRoot();
        return new Block(availableTransactions, prevHash, nodeId, proposerPublicKey, stateRoot);
    }

    private void handleConsensusMessage(HotStuffMessage msg) {
        if (msg.viewNumber < viewNumber)
            return;

        if (stageManager != null && msg.viewNumber > viewNumber) {
            bufferedConsensusMessages.add(msg);
            return;
        }

        if (stageManager == null) {
            int leaderId = getLeader(msg.viewNumber);
            viewNumber = msg.viewNumber;
            String role = (nodeId == leaderId) ? "LEADER" : "replica (leader=" + leaderId + ")";
            log("\n====================================");
            log("view " + viewNumber + " | " + role);
            log("====================================");
            ensureConsensusRunning(null, leaderId, viewNumber);
        }

        stageManager.handleMessage(msg);
        checkConsensusComplete();
    }

    private void checkConsensusComplete() {
        if (stageManager == null || !stageManager.isConsensusComplete())
            return;

        Block decided = stageManager.getBlock();
        if (decided != null) {
            // algorithm 2, line 34: "execute through node"
            blockchain.putIfAbsent(decided.getHash(), decided);

            List<Block> toCommit = new ArrayList<>();
            Block cur = decided;

            while (cur != null && !cur.getHash().equals(lastCommittedHash)) {
                toCommit.add(0, cur);
                cur = blockchain.get(cur.getPreviousHash());
            }
            for (Block b : toCommit) {
                removeCommittedTransactionsFromPool(b);
                lastCommittedHash = b.getHash();
                blockchainService.onDecide(b);
            }
        }

        QuorumCertificate highestQC = stageManager.getContext().prepareQC;
        if (highestQC != null)
            highestPrepareQC = highestQC;
        stageManager.cancelTimeout();
        viewNumber++;
        stageManager = null;
        int nextLeader = getLeader(viewNumber);
        HotStuffMessage newView = new HotStuffMessage(HotStuffMessage.Type.NEW_VIEW, viewNumber, nodeId);
        if (nodeId != nextLeader) {
            try {
                link.send("localhost", nextLeader, newView.serialize());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            handleConsensusMessage(newView);
        }

        drainBufferedConsensusMessages();

    }

    // algorithm 2, lines 35-36
    public synchronized void triggerNextView(int timedOutView) {
        if (viewNumber != timedOutView || stageManager == null)
            return;

        System.out.println("[Timeout] View " + viewNumber + " timed out -> nextView interrupt");

        // QuorumCertificate qcToSend = stageManager.getContext().prepareQC != null
        // ? stageManager.getContext().prepareQC
        // : highestPrepareQC;

        stageManager = null;
        viewNumber++;

        int nextLeader = getLeader(viewNumber);
        HotStuffMessage newView = new HotStuffMessage(HotStuffMessage.Type.NEW_VIEW, viewNumber, nodeId);
        if (nodeId != nextLeader) {
            try {
                link.send("localhost", nextLeader, newView.serialize());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            handleConsensusMessage(newView);
        }

        drainBufferedConsensusMessages();
    }

    private void removeCommittedTransactionsFromPool(Block block) {
        try {
            for (Transaction tx : block.getData()) {
                System.out.println("[TxPool] Removing committed transaction: " + tx.getHash());
                txPool.remove(tx);
            }
        } catch (JsonParseException | IllegalStateException e) {
            System.err.println("[TxPool] Failed to remove committed transactions from pool: " + e.getMessage());
        }
    }

    private int getLeader(int view) {
        return nodePorts[(view - 1) % nodePorts.length];
    }

    private void log(String msg) {
        System.out.println(msg);
    }

    private StageContext createStageContext(Block block, int leaderId, int viewNumber) {
        System.out.println(
                "[CONTEXT] Creating stage context with lastcommited" + lastCommittedHash
                        + ", leaderId: "
                        + leaderId + ", viewNumber: " + viewNumber);
        return new StageContext(
                block,
                link,
                nodePorts,
                nodeId,
                leaderId,
                viewNumber,
                blockchain,
                null, // originalMessage - set later if needed
                // clientPublicKey,
                privateKey,
                nodesPublicKeys,
                lastReqByClient,
                lastCommittedHash,
                this::addValidTxToTxPool, blockchainService);
    }

    private void addValidTxToTxPool(Transaction tx) {
        if (seenTxIds.add(tx.getHash())) {
            txPool.offer(tx);
            System.out.println("[TxPool] Added transaction: " + tx.getHash());
        }
    }

    private void ensureConsensusRunning(Block proposedBlock, int leaderId, int viewNumber) {
        if (stageManager == null) {
            if (proposedBlock != null) {
                log("\n====================================");
                log("view " + viewNumber + " | LEADER | propose from pool");
                log("====================================");
                blockchain.put(proposedBlock.getHash(), proposedBlock);
            }

            StageContext ctx = createStageContext(proposedBlock, leaderId, viewNumber);
            stageManager = new StageManager(ctx, () -> triggerNextView(ctx.viewNumber));
            stageManager.startConsensus();
        } else if (proposedBlock != null && stageManager.getContext().block == null) {
            blockchain.put(proposedBlock.getHash(), proposedBlock);
            stageManager.getContext().block = proposedBlock;
            stageManager.wakeUp();
        }
    }

    private void drainBufferedConsensusMessages() {
        while (true) {
            List<HotStuffMessage> ready = new ArrayList<>();

            for (HotStuffMessage msg : bufferedConsensusMessages) {
                if (msg.viewNumber == viewNumber) {
                    ready.add(msg);
                }
            }

            if (ready.isEmpty()) {
                return;
            }

            bufferedConsensusMessages.removeAll(ready);

            for (HotStuffMessage msg : ready) {
                handleConsensusMessage(msg);
            }
        }
    }
}
