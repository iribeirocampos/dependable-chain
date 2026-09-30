package node.stages;

import node.HotStuffMessage;
import node.QuorumCertificate;
import node.Utils;
import java.util.HashMap;

public abstract class Stage {

    private final HashMap<Integer, String> voters = new HashMap<>(); // senderPort -> signature
    private boolean complete = false;

    protected abstract String getStageName();

    // ---- shared/common helpers ----

    protected void broadcast(StageContext ctx, HotStuffMessage msg) {
        // msg = signOutgoing(msg, ctx); // Sign before broadcast
        for (int port : ctx.nodePorts) {
            if (port != ctx.nodeId) {
                try {
                    ctx.link.send("localhost", port, msg.serialize());
                } catch (Exception e) {
                    System.err.println("Broadcast failed to " + port + ": " + e.getMessage());
                }
            }
        }
    }

    protected void sendToLeader(StageContext ctx, HotStuffMessage msg) {
        try {
            ctx.link.send("localhost", ctx.leaderId, msg.serialize());
        } catch (Exception e) {
            System.err.println("[STAGE] Send to leader failed: " + e.getMessage());
        }
    }

    protected void addVote(int senderPort, String signature) {

        voters.put(senderPort, signature);
    }

    protected int voteCount() {
        return voters.size();
    }

    private boolean hasQuorum(StageContext ctx) {
        return voters.size() >= ctx.getQuorum();
    }

    protected boolean hasValidQuorum(StageContext ctx, String expectedHash) {
        if (!hasQuorum(ctx)) {
            return false;
        }
        boolean isValid = Utils.verifyCombinedSignature(voters, expectedHash, ctx.nodesPublicKeys);
        if (!isValid) {
            System.err.println("Quorum reached but combined signature verification failed.");
        }
        return isValid;
    }

    protected QuorumCertificate formQC(String phase, StageContext ctx) {
        QuorumCertificate qc = new QuorumCertificate(
                phase,
                ctx.viewNumber,
                ctx.block != null ? ctx.block.getHash() : "",
                voters);

        return qc;
    }

    protected void markComplete() {
        complete = true;
    }

    public boolean isComplete() {
        return complete;
    }

    public void start(StageContext ctx) {
    }

    public void wakeUp(StageContext ctx) {
    }

    public void handleMessage(HotStuffMessage msg, StageContext ctx) {
    }

    public String getDataToSign(StageContext ctx) {
        return ctx.viewNumber + "|" + this.getStageName() + "|" + (ctx.block != null ? ctx.block.getHash() : "");
    }

    public String getDataToSign(StageContext ctx, String stageName) {
        return ctx.viewNumber + "|" + stageName + "|" + (ctx.block != null ? ctx.block.getHash() : "");
    }

}
