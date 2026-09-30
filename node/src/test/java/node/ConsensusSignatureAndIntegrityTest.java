package node;

import blockchain.Block;
import blockchain.Transaction;
import com.google.gson.JsonObject;
import node.stages.PreCommit;
import node.stages.Prepare;
import node.stages.StageContext;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class ConsensusSignatureAndIntegrityTest extends ConsensusTestSupport {

    @Test
    public void testMalformedSignatureIsRejected() throws Exception {
        ensureInitialized();
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Block testBlock = buildBlockWithTransactions(transactions, initialPort, leaderPort);
        StageContext ctx = createStageContext(leaderPort, leaderPort, testBlock, 1);
        Prepare prepareStage = new Prepare();
        prepareStage.start(ctx);

        HotStuffMessage malformedSigVote = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[1]);
        malformedSigVote.blockHash = testBlock.getHash();
        malformedSigVote.partialSignature = "not-base64";

        prepareStage.handleMessage(malformedSigVote, ctx);

        assertNull(ctx.prepareQC, "Malformed signature must not be counted toward quorum");
        assertFalse(prepareStage.isComplete(), "Prepare must not complete with malformed signatures");
        cleanup();
    }

    @Test
    public void testForgedSenderSignatureMismatchIsRejected() throws Exception {
        ensureInitialized();
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Block testBlock = buildBlockWithTransactions(transactions, initialPort, leaderPort);
        StageContext ctx = createStageContext(leaderPort, leaderPort, testBlock, 1);
        Prepare prepareStage = new Prepare();
        prepareStage.start(ctx);

        HotStuffMessage honestVote = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[1]);
        honestVote.blockHash = testBlock.getHash();
        honestVote.partialSignature = signData(nodePorts[1], testBlock.getHash(), 1, "Prepare");
        prepareStage.handleMessage(honestVote, ctx);

        HotStuffMessage forgedVote = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[2]);
        forgedVote.blockHash = testBlock.getHash();
        forgedVote.partialSignature = signData(nodePorts[1], testBlock.getHash(), 1, "Prepare");
        prepareStage.handleMessage(forgedVote, ctx);

        assertNull(ctx.prepareQC, "Forged sender/signature mismatch must not contribute to quorum");
        assertFalse(prepareStage.isComplete(), "Prepare stage must stay incomplete after forged vote");
        cleanup();
    }

    @Test
    public void testPrepareVoteWithWrongBlockHashIsRejected() throws Exception {
        ensureInitialized();
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Block block = buildBlockWithTransactions(transactions, initialPort, leaderPort);
        StageContext ctx = createStageContext(leaderPort, leaderPort, block, 1);
        Prepare prepare = new Prepare();
        prepare.start(ctx);

        HotStuffMessage tamperedVote = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[1]);
        tamperedVote.blockHash = "tampered-hash";
        tamperedVote.partialSignature = signData(nodePorts[1], block.getHash(), 1, "Prepare");
        prepare.handleMessage(tamperedVote, ctx);

        HotStuffMessage validVote = new HotStuffMessage(HotStuffMessage.Type.PREPARE_VOTE, 1, nodePorts[2]);
        validVote.blockHash = block.getHash();
        validVote.partialSignature = signData(nodePorts[2], block.getHash(), 1, "Prepare");
        prepare.handleMessage(validVote, ctx);

        assertNull(ctx.prepareQC, "Tampered blockHash vote must not contribute to quorum");
        assertFalse(prepare.isComplete(), "Prepare must remain incomplete if tampered vote is rejected");
        cleanup();
    }

    @Test
    public void testBlockHashChangesWhenProposerPublicKeyChanges() throws Exception {
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Ed25519PublicKeyParameters proposerKey1 = new Ed25519PublicKeyParameters(
                java.util.Base64.getDecoder().decode("t68gwnkZmbkosAeSZQlIzFjug09JTgbrgQ6Z0/7tTEI="), 0);
        Ed25519PublicKeyParameters proposerKey2 = new Ed25519PublicKeyParameters(
                java.util.Base64.getDecoder().decode("a+6sngaEb0PT0Q3sPgkTtJt6oZIksjANiUVky1PxlvE="), 0);

        Block block1 = new Block(transactions, "genesis", leaderPort, proposerKey1, "test-state-root");
        Block block2 = new Block(transactions, "genesis", leaderPort, proposerKey2, "test-state-root");

        assertNotEquals(block1.getHash(), block2.getHash(),
                "Block hash must commit to proposer public key because it affects execution");
        cleanup();
    }

    @Test
    public void testBlockHashChangesWhenStateRootChanges() throws Exception {
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Ed25519PublicKeyParameters proposerKey = new Ed25519PublicKeyParameters(
                java.util.Base64.getDecoder().decode("t68gwnkZmbkosAeSZQlIzFjug09JTgbrgQ6Z0/7tTEI="), 0);

        Block block1 = new Block(transactions, "genesis", leaderPort, proposerKey, "state-root-1");
        Block block2 = new Block(transactions, "genesis", leaderPort, proposerKey, "state-root-2");

        assertNotEquals(block1.getHash(), block2.getHash(),
                "Block hash must commit to post-state because it affects execution");
        cleanup();
    }

    @Test
    public void testBlockSerializeRoundTripAllowsTildeInTransactionData() throws Exception {
        JsonObject txJson = new JsonObject();
        txJson.addProperty("nonce", 0);
        txJson.addProperty("to", "a+6sngaEb0PT0Q3sPgkTtJt6oZIksjANiUVky1PxlvE=");
        txJson.addProperty("value", 0L);
        txJson.addProperty("gasLimit", 21_000L);
        txJson.addProperty("maxFeePerGas", 1_000_000_000L);
        txJson.addProperty("data", "payload~with~tildes");
        txJson.addProperty("publicKey", "a+6sngaEb0PT0Q3sPgkTtJt6oZIksjANiUVky1PxlvE=");
        txJson.addProperty("signature", "");

        Transaction tx = new Transaction(txJson);
        Ed25519PublicKeyParameters proposerKey = new Ed25519PublicKeyParameters(
                java.util.Base64.getDecoder().decode("t68gwnkZmbkosAeSZQlIzFjug09JTgbrgQ6Z0/7tTEI="), 0);
        Block originalBlock = new Block(List.of(tx), "genesis", leaderPort, proposerKey, "state-root");

        Block roundTrippedBlock = Block.deserialize(originalBlock.serialize());

        assertEquals(originalBlock.getHash(), roundTrippedBlock.getHash());
        assertEquals("payload~with~tildes", roundTrippedBlock.getData().get(0).getData());
        cleanup();
    }

    @Test
    public void testPrepareRejectsBlockWithMismatchedStateRoot() throws Exception {
        ensureInitialized();
        Ed25519PublicKeyParameters proposerKey = new Ed25519PublicKeyParameters(
                java.util.Base64.getDecoder().decode("t68gwnkZmbkosAeSZQlIzFjug09JTgbrgQ6Z0/7tTEI="), 0);
        Block proposedBlock = new Block(List.of(), "genesis", leaderPort, proposerKey, "wrong-state-root");
        StageContext ctx = createStageContext(nodePorts[1], leaderPort, null, 1);
        Prepare prepare = new Prepare();

        HotStuffMessage prepareMsg = new HotStuffMessage(HotStuffMessage.Type.PREPARE, 1, leaderPort);
        prepareMsg.blockData = proposedBlock.serialize();
        prepareMsg.blockHash = proposedBlock.getHash();

        prepare.handleMessage(prepareMsg, ctx);

        assertNull(ctx.block, "Replica must not accept a block with a mismatched recomputed state root");
        assertFalse(prepare.isComplete(), "Prepare must remain incomplete when state root validation fails");
        cleanup();
    }

    @Test
    public void testPreviousViewPrepareQCIsRejectedEvenWithValidSignatures() throws Exception {
        ensureInitialized();
        List<Transaction> transactions = Arrays.asList(buildTransaction());
        Block block = buildBlockWithTransactions(transactions, initialPort, leaderPort);
        StageContext ctx = createStageContext(nodePorts[1], leaderPort, block, 5);
        PreCommit preCommit = new PreCommit();

        HashMap<Integer, String> sigs = new HashMap<>();
        sigs.put(leaderPort, signData(leaderPort, block.getHash(), 5, "Prepare"));
        sigs.put(nodePorts[2], signData(nodePorts[2], block.getHash(), 5, "Prepare"));
        sigs.put(nodePorts[3], signData(nodePorts[3], block.getHash(), 5, "Prepare"));

        QuorumCertificate oldViewQC = new QuorumCertificate("PREPARE", 4, block.getHash(), sigs);

        HotStuffMessage preCommitMsg = new HotStuffMessage(HotStuffMessage.Type.PRE_COMMIT, 5, leaderPort);
        preCommitMsg.blockHash = block.getHash();
        preCommitMsg.prepareQC = oldViewQC;
        preCommit.handleMessage(preCommitMsg, ctx);

        assertFalse(preCommit.isComplete(), "Replica must reject PRE_COMMIT carrying old-view prepareQC");
        assertNull(ctx.prepareQC, "Old-view prepareQC must not be accepted into context");
        cleanup();
    }
}
