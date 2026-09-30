package node.stages;

import com.google.gson.JsonParseException;
import node.HotStuffMessage;
import node.QuorumCertificate;
import node.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import blockchain.Block;
import blockchain.Transaction;

public class Prepare extends Stage {

    private final List<HotStuffMessage> newViewMessages = new ArrayList<>();
    private final Set<Integer> newViewSenders = new HashSet<>();
    private boolean hasBroadcast = false;

    @Override
    protected String getStageName() {
        return "Prepare";
    }

    @Override
    public void start(StageContext ctx) {
        if (ctx.isLeader()) {
            tryLeaderBroadcastPrepare(ctx);
        }
    }

    @Override
    public void wakeUp(StageContext ctx) {
        if (ctx.isLeader()) {
            tryLeaderBroadcastPrepare(ctx);
        }
    }

    @Override
    public void handleMessage(HotStuffMessage msg, StageContext ctx) {
        if (isComplete())
            return;

        if (ctx.isLeader()) {
            handleLeader(msg, ctx);
        } else {
            handleReplica(msg, ctx);
        }
    }

    private void handleLeader(HotStuffMessage msg, StageContext ctx) {
        if (!hasBroadcast && "NEW_VIEW".equals(msg.getType().name())) {
            if (msg.viewNumber != ctx.viewNumber) {
                System.err.println("[WARNING] [Prepare] Ignoring NEW_VIEW from mismatched view " + msg.viewNumber
                        + " (expected " + ctx.viewNumber + ")");
                return;
            }
            if (newViewSenders.add(msg.senderPort)) {
                newViewMessages.add(msg);
                System.out.println("[Prepare] NEW_VIEW " + newViewMessages.size() + "/" + ctx.getQuorum());
                tryLeaderBroadcastPrepare(ctx);
            }
            return;
        }

        if ("PREPARE_VOTE".equals(msg.getType().name())) {
            if (msg.viewNumber != ctx.viewNumber) {
                System.err.println("[WARNING] [Prepare] Ignoring PREPARE_VOTE from mismatched view " + msg.viewNumber
                        + " (expected " + ctx.viewNumber + ")");
                return;
            }

            String expectedBlockHash = ctx.block != null ? ctx.block.getHash() : "";
            if (msg.blockHash == null || !msg.blockHash.equals(expectedBlockHash)) {
                System.err.println("[WARNING] [Prepare] Ignoring PREPARE_VOTE with mismatched block hash from "
                        + msg.senderPort);
                return;
            }

            if (!Utils.verifySignatureFromNode(msg.senderPort, this.getDataToSign(ctx), msg.partialSignature,
                    ctx.nodesPublicKeys)) {
                System.err.println("[WARNING] [Prepare] Invalid signature from " + msg.senderPort);
                return;
            }
            addVote(msg.senderPort, msg.partialSignature);
            System.out.println("[Prepare] vote " + voteCount() + "/" + ctx.getQuorum());

            if (hasValidQuorum(ctx, this.getDataToSign(ctx))) {
                ctx.prepareQC = formQC("PREPARE", ctx);
                markComplete();
            }
        }

    }

    private void handleReplica(HotStuffMessage msg, StageContext ctx) {
        System.out.println("[Prepare] Replica received message of type " + msg.getType());
        if (!(msg.getType().name().equals("PREPARE")))
            return;

        if (msg.viewNumber != ctx.viewNumber) {
            System.err.println("[WARNING] [Prepare] Replica ignoring PREPARE from mismatched view " + msg.viewNumber
                    + " (expected " + ctx.viewNumber + ")");
            return;
        }

        System.out.println("[Prepare] Replica received PREPARE for block data " + msg.blockData);
        Block proposedBlock = Block.deserialize(msg.blockData);
        if (msg.blockHash == null || !msg.blockHash.equals(proposedBlock.getHash())) {
            System.err.println("[WARNING] [Prepare] Proposed block hash does not match serialized block contents");
            return;
        }

        if (!validateBlockTransactions(proposedBlock, ctx)) {
            System.err.println("[WARNING] [Prepare] Proposed block contains invalid transactions");
            return;
        }

        System.out.println("[Prepare] Checking last Committed Hash: " + ctx.lastBlockHash
                + " vs proposed block's previous hash: " + proposedBlock.getPreviousHash());
        if (ctx.lastBlockHash != null && !proposedBlock.getPreviousHash().equals(ctx.lastBlockHash)) {
            System.err.println("[WARNING] [Prepare] Proposed block's previous hash does not match last committed hash");
            return;
        }

        boolean accepted = ctx.safeNode(proposedBlock, msg.justify);
        System.out.println("[Prepare] Replica checked safe block hash--- OK ");

        if (accepted) {
            ctx.block = proposedBlock;
            if (ctx.blockchain != null)
                ctx.blockchain.put(proposedBlock.getHash(), proposedBlock);
            String partialSig = null;
            try {
                // SHOULD WE sign the block hash or the prepareQC hash?
                partialSig = Utils.signData(this.getDataToSign(ctx), ctx.getPrivateKey());
            } catch (Exception e) {
                System.err.println("[ERROR] Failed to sign precommit vote share: " + e.getMessage());
                e.printStackTrace();
            }
            // Sign the block hash with threshold share
            String blockHash = proposedBlock.getHash();
            HotStuffMessage voteMsg = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, ctx.viewNumber,
                    ctx.nodeId);
            voteMsg.blockHash = blockHash;
            voteMsg.partialSignature = partialSig;
            sendToLeader(ctx, voteMsg);

        }
        markComplete();
    }

    private void tryLeaderBroadcastPrepare(StageContext ctx) {
        if (hasBroadcast || ctx.block == null)
            return;

        if (ctx.viewNumber > 1 && newViewMessages.size() < ctx.getQuorum()) {
            return;
        }

        if (ctx.viewNumber > 1) {
            QuorumCertificate maxQC = null;
            for (HotStuffMessage nv : newViewMessages) {
                if (nv.highQC != null && (maxQC == null || nv.highQC.viewNumber > maxQC.viewNumber)) {
                    maxQC = nv.highQC;
                }
            }
            ctx.highQC = maxQC;

            // String prevHash = (maxQC != null && maxQC.nodeHash != null &&
            // !maxQC.nodeHash.isEmpty())
            // ? maxQC.nodeHash
            // : "genesis";

            // ctx.block = new Block(ctx.block.getData(), prevHash, ctx.nodeId);
        }

        if (ctx.blockchain != null)
            ctx.blockchain.put(ctx.block.getHash(), ctx.block);
        String partialSig = null;
        try {
            partialSig = Utils.signData(this.getDataToSign(ctx), ctx.getPrivateKey());
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to sign prepare vote share: " + e.getMessage());
            e.printStackTrace();
        }
        addVote(ctx.nodeId, partialSig); // leader self-vote

        HotStuffMessage prepare = new HotStuffMessage(HotStuffMessage.Type.PREPARE, ctx.viewNumber, ctx.nodeId);
        prepare.blockData = ctx.block.serialize();
        prepare.blockHash = ctx.block.getHash();
        prepare.partialSignature = partialSig;
        prepare.justify = ctx.highQC;
        prepare.clientMessage = ctx.originalMessage != null ? ctx.originalMessage.toString() : null;

        System.out.println("[Prepare] Leader broadcasting PREPARE");
        broadcast(ctx, prepare);

        hasBroadcast = true;

        if (hasValidQuorum(ctx, this.getDataToSign(ctx))) {
            ctx.prepareQC = formQC("PREPARE", ctx);
            markComplete();
        }
    }

    private boolean validateBlockTransactions(Block proposedBlock, StageContext ctx) {
        ctx.getBlockchainService().resetTemporaryState();
        try {
            Set<String> txIds = new HashSet<>();
            long minerFees = 0;
            for (Transaction tx : proposedBlock.getData()) {
                String txId = tx.getHash();
                if (!txIds.add(txId)) {
                    System.err.println("[WARNING] [Prepare] Duplicate transaction in proposed block: " + txId);
                    return false;
                }

                if (!Utils.verifyTransactionSignature(tx)) {
                    System.err.println("[WARNING] [Prepare] Invalid client signature for transaction: " + txId);
                    return false;
                }
                if (!ctx.getBlockchainService().checkTransaction(tx)) {
                    System.err.println("[WARNING] [Prepare] Non-executable transaction in proposed block: " + txId);
                    return false;
                }
                minerFees += ctx.getBlockchainService().runTransaction(tx);
                ctx.addValidTxToTxPool(tx);
            }
            ctx.getBlockchainService().payMiner(proposedBlock.getProposerPublicKey(), minerFees);
            String expectedStateRoot = ctx.getBlockchainService().getTemporaryStateRoot();
            if (!expectedStateRoot.equals(proposedBlock.getStateRoot())) {
                System.err.println("[WARNING] [Prepare] Proposed block state root does not match recomputed post-state");
                return false;
            }

            return true;
        } catch (JsonParseException | IllegalStateException e) {
            System.err.println("[WARNING] [Prepare] Failed to parse block transactions: " + e.getMessage());
            return false;
        }
    }
}
