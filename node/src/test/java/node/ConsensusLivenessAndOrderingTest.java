package node;

import blockchain.Block;
import blockchain.Transaction;
import node.stages.Commit;
import node.stages.PreCommit;
import node.stages.Prepare;
import node.stages.StageContext;
import node.stages.StageManager;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class ConsensusLivenessAndOrderingTest extends ConsensusTestSupport {
    @Test
    public void testDroppedMessagesBelowQuorumPreventPrepareQC() throws Exception {
        ensureInitialized();
        List<Transaction> transactions = Arrays.asList(buildTransaction());

        Block testBlock = buildBlockWithTransactions(transactions, 1, leaderPort);
        StageContext ctx = createStageContext(leaderPort, leaderPort, testBlock, 1);
        Prepare prepareStage = new Prepare();
        prepareStage.start(ctx);

        HotStuffMessage oneVote = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[1]);
        oneVote.blockHash = testBlock.getHash();
        oneVote.partialSignature = signData(nodePorts[1], testBlock.getHash(), 1, "Prepare");
        prepareStage.handleMessage(oneVote, ctx);

        assertNull(ctx.prepareQC, "prepareQC must not form below quorum");
        assertFalse(prepareStage.isComplete(), "Prepare stage must remain incomplete below quorum");
    }

    @Test
    public void testOutOfOrderStageMessagesAreRejected() throws Exception {
        ensureInitialized();
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Block block = buildBlockWithTransactions(transactions, 1, leaderPort);

        StageContext preCommitCtx = createStageContext(nodePorts[1], leaderPort, block, 1);
        PreCommit preCommit = new PreCommit();
        HotStuffMessage preCommitMsg = new HotStuffMessage(HotStuffMessage.Type.PRE_COMMIT, 1, leaderPort);
        preCommitMsg.blockHash = block.getHash();
        preCommit.handleMessage(preCommitMsg, preCommitCtx);

        assertFalse(preCommit.isComplete(), "Replica must reject PRE_COMMIT without prepareQC");
        assertNull(preCommitCtx.prepareQC, "prepareQC must remain unset on out-of-order PRE_COMMIT");

        StageContext commitCtx = createStageContext(nodePorts[1], leaderPort, block, 1);
        Commit commit = new Commit();
        HotStuffMessage commitMsg = new HotStuffMessage(HotStuffMessage.Type.COMMIT, 1, leaderPort);
        commitMsg.blockHash = block.getHash();
        commit.handleMessage(commitMsg, commitCtx);

        assertFalse(commit.isComplete(), "Replica must reject COMMIT without precommitQC");
        assertNull(commitCtx.lockedQC, "lockedQC must remain unset on out-of-order COMMIT");
        assertNull(commitCtx.commitQC, "commitQC must remain unset on out-of-order COMMIT");
    }

    @Test
    public void testConsensusDoesNotCompleteWhenNodesDoNotRespond() throws Exception {
        ensureInitialized();
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Block testBlock = buildBlockWithTransactions(transactions, 1, leaderPort);
        StageContext ctx = createStageContext(leaderPort, leaderPort, testBlock, 1);

        StageManager manager = new StageManager(ctx, () -> {
        });
        manager.startConsensus();

        manager.wakeUp();
        manager.wakeUp();

        assertFalse(manager.isConsensusComplete(), "Consensus must not complete without enough responsive nodes");
        assertNull(ctx.prepareQC, "prepareQC must remain null when replicas do not respond");
        manager.cancelTimeout();
    }

    @Test
    public void testReplicaConsensusStallsWhenLeaderDoesNotRespond() throws Exception {
        ensureInitialized();
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Block block = buildBlockWithTransactions(transactions, 1, leaderPort);
        StageContext ctx = createStageContext(nodePorts[1], leaderPort, block, 1);

        StageManager manager = new StageManager(ctx, () -> {
        });
        manager.startConsensus();

        HotStuffMessage replicaVoteNoise = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[2]);
        replicaVoteNoise.blockHash = block.getHash();
        replicaVoteNoise.partialSignature = signData(nodePorts[2], block.getHash(), 1, "Prepare");
        manager.handleMessage(replicaVoteNoise);
        manager.wakeUp();

        assertFalse(manager.isConsensusComplete(), "Replica consensus must stall without leader proposal/response");
        assertNull(ctx.prepareQC, "prepareQC must remain null when leader is non-responsive");
        manager.cancelTimeout();
    }
}
