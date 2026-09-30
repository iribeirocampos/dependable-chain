package node.stages;

import node.HotStuffMessage;
import node.Utils;

public class Decide extends Stage {

    @Override
    protected String getStageName() {
        return "Decide";
    }

    @Override
    public void start(StageContext ctx) {
        if (ctx.isLeader()) {
            tryLeaderBroadcastDecide(ctx);
        }
    }

    @Override
    public void wakeUp(StageContext ctx) {
        if (ctx.isLeader()) {
            tryLeaderBroadcastDecide(ctx);
        }
    }

    @Override
    public void handleMessage(HotStuffMessage msg, StageContext ctx) {
        if (isComplete())
            return;

        if (ctx.isLeader()) {
            // Leader: decide stage has no vote round
            markComplete();
        } else {
            handleReplica(msg, ctx);
        }
    }

    private void handleReplica(HotStuffMessage msg, StageContext ctx) {
        if (!"DECIDE".equals(msg.getType().name()))
            return;
        if (msg.commitQC == null) {
            System.err.println("[ERROR] DECIDE message missing commitQC.");
            return;
        }
        if (!Utils.verifyCombinedSignature(msg.commitQC.signatures, getDataToSign(ctx, "Commit"),
                ctx.nodesPublicKeys)) {
            System.err.println("[ERROR] Invalid commitQC received.");
            return;
        }
        ctx.commitQC = msg.commitQC;

        System.out.println("[Decide] Replica received DECIDE from leader");
        System.out.println("[Decide] Replica: executing block " + msg.blockHash);

        // Execute the block (add to blockchain, apply state changes, etc.)
        if (ctx.block != null) {
            System.out.println("[Decide] Block committed: " + ctx.block.getHash());
        }

        markComplete();
    }

    private void tryLeaderBroadcastDecide(StageContext ctx) {
        if (ctx.commitQC == null)
            return;

        String blockHash = ctx.block != null ? ctx.block.getHash() : "";
        HotStuffMessage decide = new HotStuffMessage(HotStuffMessage.Type.DECIDE, ctx.viewNumber, ctx.nodeId);
        decide.blockHash = blockHash;
        decide.commitQC = ctx.commitQC;

        System.out.println("[Decide] Leader broadcasting DECIDE");
        broadcast(ctx, decide);
        markComplete();
    }
}
