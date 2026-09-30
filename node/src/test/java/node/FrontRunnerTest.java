package node;

import blockchain.Block;
import blockchain.BlockchainService;
import blockchain.Transaction;
import blockchain.Utils;
import com.google.gson.JsonObject;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FrontRunnerTest extends ConsensusTestSupport {

    private static final String SELECTOR_APPROVE = "426a8493";
    private static final String SELECTOR_DECREASE_ALLOWANCE = "a457c2d7";
    private static final String SELECTOR_TRANSFER_FROM = "23b872dd";
    private static final String SELECTOR_ALLOWANCE = "dd62ed3e";
    private static final String SELECTOR_BALANCE_OF = "70a08231";

    @Test
    public void testFrontRunningAllowanceAttack() throws Exception {
        // Setup temp blockchain state
        Path tempDir = Files.createTempDirectory("frontrunner-test");
        Files.copy(Path.of("../database/genesis.json"), tempDir.resolve("genesis.json"));

        BlockchainService service = new BlockchainService(tempDir.toFile());
        String contractAddress = loadSingleContractAddress(tempDir.resolve("smart_contracts.json"));

        // Deterministic keys for Alice and Bob
        Ed25519PublicKeyParameters aliceKey = KeysGen.generateDeterministicKeys(3001, 4, 3001).clientPublicKey;
        Ed25519PublicKeyParameters bobKey = KeysGen.generateDeterministicKeys(3002, 4, 3002).publicKeys.get(0);
        String aliceAddress = Utils.publicKeyToAddress(aliceKey);
        String bobAddress = Utils.publicKeyToAddress(bobKey);

        // Pad addresses for calldata
        String alicePadded = Utils.padHexTo256Bits(aliceAddress);
        String bobPadded = Utils.padHexTo256Bits(bobAddress);

        // 1. Alice approves Bob for 100 tokens
        JsonObject approveTxJson = new JsonObject();
        approveTxJson.addProperty("nonce", 1);
        approveTxJson.addProperty("to", contractAddress);
        approveTxJson.addProperty("value", 0L);
        approveTxJson.addProperty("gasLimit", 100_000L);
        approveTxJson.addProperty("maxFeePerGas", 3_000L);
        approveTxJson.addProperty("data", "0x" + SELECTOR_APPROVE + bobPadded
                + Utils.padHexTo256Bits("0") + Utils.padHexTo256Bits("64")); // 100 decimal = 0x64
        approveTxJson.addProperty("publicKey", Base64.getEncoder().encodeToString(aliceKey.getEncoded()));
        approveTxJson.addProperty("signature", "");
        Transaction approveTx = new Transaction(approveTxJson);

        // 2. Bob tries to front-run with transferFrom using the old allowance
        JsonObject attackTxJson = new JsonObject();
        attackTxJson.addProperty("nonce", 1);
        attackTxJson.addProperty("to", contractAddress);
        attackTxJson.addProperty("value", 0L);
        attackTxJson.addProperty("gasLimit", 100_000L);
        attackTxJson.addProperty("maxFeePerGas", 3_000L);
        attackTxJson.addProperty("data", "0x" + SELECTOR_TRANSFER_FROM + alicePadded + bobPadded + Utils.padHexTo256Bits("64"));
        attackTxJson.addProperty("publicKey", Base64.getEncoder().encodeToString(bobKey.getEncoded()));
        attackTxJson.addProperty("signature", "");
        Transaction attackTx = new Transaction(attackTxJson);

        // 3. Alice submits a stale approve(bob, 50) that still expects the old allowance of 100
        JsonObject staleApproveTxJson = new JsonObject();
        staleApproveTxJson.addProperty("nonce", 2);
        staleApproveTxJson.addProperty("to", contractAddress);
        staleApproveTxJson.addProperty("value", 0L);
        staleApproveTxJson.addProperty("gasLimit", 100_000L);
        staleApproveTxJson.addProperty("maxFeePerGas", 3_000L);
        staleApproveTxJson.addProperty("data", "0x" + SELECTOR_APPROVE + bobPadded
                + Utils.padHexTo256Bits("64") + Utils.padHexTo256Bits("32"));
        staleApproveTxJson.addProperty("publicKey", Base64.getEncoder().encodeToString(aliceKey.getEncoded()));
        staleApproveTxJson.addProperty("signature", "");
        Transaction staleApproveTx = new Transaction(staleApproveTxJson);

        // Block 1: approve, attack, then stale approve
        service.runTransaction(approveTx);
        service.runTransaction(attackTx);
        service.runTransaction(staleApproveTx);
        service.onDecide(new Block(List.of(approveTx, attackTx, staleApproveTx),
                "genesis", 3001, aliceKey, "front-run-test-state"));

        // Assert: the stale approve must revert, leaving allowance at 0
        BigInteger allowance = service.callContractUint256ReadOnly(
                aliceAddress, contractAddress,
                "0x" + SELECTOR_ALLOWANCE + alicePadded + bobPadded
        );
        assertEquals(BigInteger.ZERO, allowance, "Stale approve must not re-grant allowance");

        // Assert: Bob only receives the originally approved 100 tokens
        BigInteger bobBalance = service.callContractUint256ReadOnly(
                bobAddress, contractAddress,
                "0x" + SELECTOR_BALANCE_OF + bobPadded
        );
        System.out.println("Bob's balance after: " + bobBalance);
        assertEquals(BigInteger.valueOf(100), bobBalance, "Bob should only receive the originally approved 100 tokens");

        // Optionally, check Alice's balance decreased by 100
        // assertEquals(expected, aliceBalance, ...);
        cleanup();
    }

    private static String loadSingleContractAddress(Path contractsFile) throws Exception {
        JsonObject contracts = com.google.gson.JsonParser.parseString(Files.readString(contractsFile)).getAsJsonObject();
        return contracts.keySet().iterator().next();
    }
}
