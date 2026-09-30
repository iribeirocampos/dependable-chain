package node.stages;

import node.HotStuffMessage;
import node.Utils;

public class Commit extends Stage {

    @Override
    protected String getStageName() {
        return "Commit";
    }

    @Override
    public void start(StageContext ctx) {
        if (ctx.isLeader()) {
            tryLeaderBroadcastCommit(ctx);
        }
    }

    @Override
    public void wakeUp(StageContext ctx) {
        if (ctx.isLeader()) {
            tryLeaderBroadcastCommit(ctx);
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
        if ("COMMIT_VOTE".equals(msg.getType().name())) {
            if (!Utils.verifySignatureFromNode(msg.senderPort, this.getDataToSign(ctx), msg.partialSignature,
                    ctx.nodesPublicKeys)) {
                System.err.println("[WARNING] [Commit] Invalid signature from " + msg.senderPort);
                return;
            }

            addVote(msg.senderPort, msg.partialSignature);
            System.out.println("[Commit] vote " + voteCount() + "/" + ctx.getQuorum());

            if (hasValidQuorum(ctx, this.getDataToSign(ctx))) {
                ctx.commitQC = formQC("COMMIT", ctx);
                markComplete();
            }
        }
    }

    private void handleReplica(HotStuffMessage msg, StageContext ctx) {
        if (!"COMMIT".equals(msg.getType().name()))
            return;

        if (msg.precommitQC == null) {
            System.err.println("[ERROR] No precommitQC in context when handling COMMIT message.");
            return;
        }
        if (!Utils.verifyCombinedSignature(msg.precommitQC.signatures, getDataToSign(ctx, "PreCommit"),
                ctx.nodesPublicKeys)) {
            System.err.println("[ERROR] Invalid precommitQC received.");
            return;
        }
        ctx.precommitQC = msg.precommitQC;
        ctx.lockedQC = msg.precommitQC; // lock on precommitQC
        System.out.println("[Commit] Replica: lockedQC <- msg.precommitQC");

        String blockHash = msg.blockHash;

        String partialSig = null;
        try {
            partialSig = Utils.signData(getDataToSign(ctx), ctx.getPrivateKey());

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to sign commit vote: " + e.getMessage());
            e.printStackTrace();
        }

        HotStuffMessage vote = new HotStuffMessage(HotStuffMessage.Type.COMMIT_VOTE, ctx.viewNumber, ctx.nodeId);
        vote.blockHash = blockHash;
        vote.partialSignature = partialSig;

        sendToLeader(ctx, vote);
        markComplete();
    }

    private void tryLeaderBroadcastCommit(StageContext ctx) {
        if (ctx.precommitQC == null)
            return;

        String blockHash = ctx.block != null ? ctx.block.getHash() : "";
        HotStuffMessage commit = new HotStuffMessage(HotStuffMessage.Type.COMMIT, ctx.viewNumber, ctx.nodeId);
        commit.blockHash = blockHash;
        commit.precommitQC = ctx.precommitQC;

        System.out.println("[Commit] Leader broadcasting COMMIT");
        broadcast(ctx, commit);

        String partialSig = null;
        try {
            partialSig = Utils.signData(this.getDataToSign(ctx), ctx.getPrivateKey());
        } catch (Exception e) {
            System.err.println("Failed to sign commit vote: " + e.getMessage());
            e.printStackTrace();
        }
        addVote(ctx.nodeId, partialSig); // leader self-vote

        if (hasValidQuorum(ctx, this.getDataToSign(ctx))) {
            ctx.commitQC = formQC("COMMIT", ctx);
            markComplete();
        }
    }
}
