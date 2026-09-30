package node;

import blockchain.Block;
import blockchain.Transaction;
import node.stages.PreCommit;
import node.stages.Prepare;
import node.stages.StageContext;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class ConsensusReplayAndViewTest extends ConsensusTestSupport {

    @Test
    public void testDuplicatePrepareVoteFromSameSenderDoesNotReachQuorum() throws Exception {
        ensureInitialized();
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Block testBlock = buildBlockWithTransactions(transactions, initialPort, leaderPort);
        StageContext ctx = createStageContext(leaderPort, leaderPort, testBlock, 1);
        Prepare prepareStage = new Prepare();
        prepareStage.start(ctx);

        HotStuffMessage vote = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[1]);
        vote.blockHash = testBlock.getHash();
        vote.partialSignature = signData(nodePorts[1], testBlock.getHash(), 1, "Prepare");
        prepareStage.handleMessage(vote, ctx);

        assertNull(ctx.prepareQC, "QC must not form before quorum");

        prepareStage.handleMessage(vote, ctx);

        assertNull(ctx.prepareQC, "Duplicate vote replay from same sender must not reach quorum");
        assertFalse(prepareStage.isComplete(), "Stage must remain incomplete after duplicate replay");

        HotStuffMessage secondSenderVote = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[2]);
        secondSenderVote.blockHash = testBlock.getHash();
        secondSenderVote.partialSignature = signData(nodePorts[2], testBlock.getHash(), 1, "Prepare");
        prepareStage.handleMessage(secondSenderVote, ctx);

        assertNotNull(ctx.prepareQC, "QC must form once a second unique valid vote arrives");
        assertTrue(prepareStage.isComplete(), "Stage must complete at quorum");
        cleanup();
    }

    @Test
    public void testReplayAfterPrepareCompletionDoesNotChangeQC() throws Exception {
        ensureInitialized();
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Block testBlock = buildBlockWithTransactions(transactions, 1, leaderPort);
        StageContext ctx = createStageContext(leaderPort, leaderPort, testBlock, 1);
        Prepare prepareStage = new Prepare();
        prepareStage.start(ctx);

        HotStuffMessage vote1 = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[1]);
        vote1.blockHash = testBlock.getHash();
        vote1.partialSignature = signData(nodePorts[1], testBlock.getHash(), 1, "Prepare");
        prepareStage.handleMessage(vote1, ctx);

        HotStuffMessage vote2 = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[2]);
        vote2.blockHash = testBlock.getHash();
        vote2.partialSignature = signData(nodePorts[2], testBlock.getHash(), 1, "Prepare");
        prepareStage.handleMessage(vote2, ctx);

        assertTrue(prepareStage.isComplete(), "Prepare must complete with quorum");
        QuorumCertificate qcBeforeReplay = ctx.prepareQC;

        prepareStage.handleMessage(vote1, ctx);

        assertSame(qcBeforeReplay, ctx.prepareQC, "Replay after completion must not mutate QC");
        cleanup();
    }

    @Test
    public void testOldViewPrepareVotesAreIgnored() throws Exception {
        ensureInitialized();
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Block testBlock = buildBlockWithTransactions(transactions, 1, leaderPort);
        StageContext ctx = createStageContext(leaderPort, leaderPort, testBlock, 5);
        Prepare prepareStage = new Prepare();
        prepareStage.start(ctx);

        int oldView = 3;
        for (int i = 1; i < numNodes; i++) {
            HotStuffMessage oldViewVote = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, oldView,
                    nodePorts[i]);
            oldViewVote.blockHash = testBlock.getHash();
            oldViewVote.partialSignature = signData(nodePorts[i], testBlock.getHash(), 5, "Prepare");
            prepareStage.handleMessage(oldViewVote, ctx);
        }

        assertNull(ctx.prepareQC, "Old-view votes must not contribute to quorum");
        assertFalse(prepareStage.isComplete(), "Prepare must remain incomplete with only old-view votes");
        cleanup();
    }

    @Test
    public void testMixedCurrentAndStaleViewVotesNearQuorum() throws Exception {
        ensureInitialized();
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Block block = buildBlockWithTransactions(transactions, 1, leaderPort);
        StageContext ctx = createStageContext(leaderPort, leaderPort, block, 1);
        Prepare prepare = new Prepare();
        prepare.start(ctx);

        HotStuffMessage currentViewVote = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[1]);
        currentViewVote.blockHash = block.getHash();
        currentViewVote.partialSignature = signData(nodePorts[1], block.getHash(), 1, "Prepare");
        prepare.handleMessage(currentViewVote, ctx);

        HotStuffMessage staleViewVote = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 0, nodePorts[2]);
        staleViewVote.blockHash = block.getHash();
        staleViewVote.partialSignature = signData(nodePorts[2], block.getHash(), 1, "Prepare");
        prepare.handleMessage(staleViewVote, ctx);

        assertNull(ctx.prepareQC, "Stale view vote must not allow quorum crossing");
        assertFalse(prepare.isComplete(), "Prepare must not complete with mixed current+stale votes");

        HotStuffMessage secondCurrentVote = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[3]);
        secondCurrentVote.blockHash = block.getHash();
        secondCurrentVote.partialSignature = signData(nodePorts[3], block.getHash(), 1, "Prepare");
        prepare.handleMessage(secondCurrentVote, ctx);

        assertNotNull(ctx.prepareQC, "Two current-view replica votes plus leader vote should complete quorum");
        assertTrue(prepare.isComplete(), "Prepare should complete only after enough current-view votes");
        cleanup();
    }

    @Test
    public void testReplayPrepareVoteInPreCommitStageIsIgnored() throws Exception {
        ensureInitialized();
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Block block = buildBlockWithTransactions(transactions, 1, leaderPort);
        StageContext ctx = createStageContext(leaderPort, leaderPort, block, 1);

        ctx.prepareQC = new QuorumCertificate("PREPARE", 1, block.getHash(), new HashMap<>());
        PreCommit preCommit = new PreCommit();
        preCommit.start(ctx);

        HotStuffMessage replayedPrepareVote = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[1]);
        replayedPrepareVote.blockHash = block.getHash();
        replayedPrepareVote.partialSignature = signData(nodePorts[1], block.getHash(), 1, "Prepare");

        preCommit.handleMessage(replayedPrepareVote, ctx);

        assertNull(ctx.precommitQC, "PREPARE_VOTE replay in PreCommit stage must be ignored");
        assertFalse(preCommit.isComplete(), "PreCommit must not complete from wrong-stage replay");
        cleanup();
    }
}
