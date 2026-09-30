package node.stages;

import node.HotStuffMessage;
import node.Utils;

public class PreCommit extends Stage {

    @Override
    protected String getStageName() {
        return "PreCommit";
    }

    @Override
    public void start(StageContext ctx) {
        if (ctx.isLeader()) {
            tryLeaderBroadcastPreCommit(ctx);
        }
    }

    @Override
    public void wakeUp(StageContext ctx) {
        if (ctx.isLeader()) {
            tryLeaderBroadcastPreCommit(ctx);
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
        if ("PRE_COMMIT_VOTE".equals(msg.getType().name())) {
            if (!Utils.verifySignatureFromNode(msg.senderPort, this.getDataToSign(ctx), msg.partialSignature,
                    ctx.nodesPublicKeys)) {
                System.err.println("[WARNING] [PreCommit] Invalid signature from " + msg.senderPort);
                return;
            }

            addVote(msg.senderPort, msg.partialSignature);
            System.out.println("[PreCommit] vote " + voteCount() + "/" + ctx.getQuorum());

            if (hasValidQuorum(ctx, this.getDataToSign(ctx))) {
                ctx.precommitQC = formQC("PRE_COMMIT", ctx);
                markComplete();
            }
        }
    }

    private void handleReplica(HotStuffMessage msg, StageContext ctx) {
        if (!"PRE_COMMIT".equals(msg.getType().name()))
            return;

        if (msg.viewNumber != ctx.viewNumber) {
            System.err.println("[ERROR] PRE_COMMIT message has mismatched view. Got " + msg.viewNumber
                    + ", expected " + ctx.viewNumber);
            return;
        }

        if (msg.prepareQC == null) {
            System.err.println("[ERROR] PRE_COMMIT message missing prepareQC.");
            return;
        }

        if (msg.prepareQC.viewNumber != ctx.viewNumber) {
            System.err.println("[ERROR] PRE_COMMIT message contains prepareQC for mismatched view "
                    + msg.prepareQC.viewNumber + " (expected " + ctx.viewNumber + ")");
            return;
        }

        // Need verifiy prepareQC to ensure safety
        if (!Utils.verifyCombinedSignature(msg.prepareQC.signatures, getDataToSign(ctx, "Prepare"),
                ctx.nodesPublicKeys)) {
            System.err.println("[ERROR] Invalid prepareQC received.");
            return;
        }
        ctx.prepareQC = msg.prepareQC;
        System.out.println("[PreCommit] Replica received PRE_COMMIT, prepareQC updated and Validated.");
        String partialSig = null;
        String blockHash = msg.blockHash;
        try {
            // SHOULD WE sign the block hash or the prepareQC hash?
            partialSig = Utils.signData(getDataToSign(ctx), ctx.getPrivateKey());
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to sign precommit vote share: " + e.getMessage());
            e.printStackTrace();
        }

        HotStuffMessage vote = new HotStuffMessage(HotStuffMessage.Type.PRE_COMMIT_VOTE, ctx.viewNumber, ctx.nodeId);
        vote.blockHash = blockHash;
        vote.partialSignature = partialSig;
        sendToLeader(ctx, vote);
        markComplete();
    }

    private void tryLeaderBroadcastPreCommit(StageContext ctx) {
        if (ctx.prepareQC == null)
            return;

        String blockHash = ctx.block != null ? ctx.block.getHash() : "";
        HotStuffMessage precommit = new HotStuffMessage(HotStuffMessage.Type.PRE_COMMIT, ctx.viewNumber, ctx.nodeId);
        precommit.blockHash = blockHash;
        precommit.prepareQC = ctx.prepareQC;

        System.out.println("[PreCommit] Leader broadcasting PRE_COMMIT");
        broadcast(ctx, precommit);

        String partialSig = null;
        try {
            partialSig = Utils.signData(this.getDataToSign(ctx), ctx.getPrivateKey());
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to sign precommit vote share: " + e.getMessage());
            e.printStackTrace();
        }
        addVote(ctx.nodeId, partialSig); // leader self-vote

        if (hasValidQuorum(ctx, this.getDataToSign(ctx))) {
            ctx.precommitQC = formQC("PRE_COMMIT", ctx);
            markComplete();
        }
    }
}
