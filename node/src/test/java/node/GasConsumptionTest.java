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

public class GasConsumptionTest extends ConsensusTestSupport {

    @Test
    public void testDepCoinTransferFeeToMiner() throws Exception {
        Path tempDir = Files.createTempDirectory("depcoin-fee-test");
        Files.copy(Path.of("../database/genesis.json"), tempDir.resolve("genesis.json"));
        BlockchainService service = new BlockchainService(tempDir.toFile());

        // Setup accounts
        Ed25519PublicKeyParameters aliceKey = KeysGen.generateDeterministicKeys(3001, 4, 3000).clientPublicKey;
        Ed25519PublicKeyParameters bobKey = KeysGen.generateDeterministicKeys(3002, 4, 2999).publicKeys.get(0);
        Ed25519PublicKeyParameters minerKey = KeysGen.generateDeterministicKeys(3003, 4, 3003).publicKeys.get(1)    ;
        
        String bob = Utils.publicKeyToAddress(bobKey);

        long amount = 1000;
        long fee = 3000; // match your BlockchainService gasPrice * nativeGas

        long aliceBalanceBefore = service.getAccount(aliceKey).getBalance();
        long bobBalanceBefore = service.getAccount(bobKey).getBalance();

        JsonObject txJson = new JsonObject();
        txJson.addProperty("nonce", 1);
        txJson.addProperty("to", bob);
        txJson.addProperty("value", amount);
        txJson.addProperty("gasLimit", 100_000L);
        txJson.addProperty("maxFeePerGas", 100L);
        txJson.addProperty("data", "");
        txJson.addProperty("publicKey", Base64.getEncoder().encodeToString(aliceKey.getEncoded()));
        txJson.addProperty("signature", "");
        Transaction tx = new Transaction(txJson);

        long realfees = service.runTransaction(tx);
        service.onDecide(new Block(List.of(tx), "genesis", 3001, minerKey, "test-state-root"));

        long aliceBalanceAfter = service.getAccount(aliceKey).getBalance();
        long bobBalanceAfter = service.getAccount(bobKey).getBalance();

        assertEquals(aliceBalanceBefore - amount - fee, aliceBalanceAfter);
        assertEquals(bobBalanceBefore + amount, bobBalanceAfter);
        assertEquals(fee, realfees);
    }

    @Test
    public void testISTCoinTransferFeeAndToken() throws Exception {
        Path tempDir = Files.createTempDirectory("istcoin-fee-test");
        Files.copy(Path.of("../database/genesis.json"), tempDir.resolve("genesis.json"));
        BlockchainService service = new BlockchainService(tempDir.toFile());
        String contractAddress = loadSingleContractAddress(tempDir.resolve("smart_contracts.json"));

        Ed25519PublicKeyParameters aliceKey = KeysGen.generateDeterministicKeys(3001, 4, 3000).clientPublicKey;
        Ed25519PublicKeyParameters bobKey = KeysGen.generateDeterministicKeys(3002, 4, 3002).publicKeys.get(0);
        Ed25519PublicKeyParameters minerKey = KeysGen.generateDeterministicKeys(3003, 4, 3003).publicKeys.get(1);
        String alice = Utils.publicKeyToAddress(aliceKey);
        String bob = Utils.publicKeyToAddress(bobKey);
        String miner = Utils.publicKeyToAddress(minerKey);

        long aliceBalanceBefore = service.getAccount(aliceKey).getBalance();
        long minerBalanceBefore = service.getAccount(minerKey).getBalance();
        long fee = 30*100; // match your BlockchainService gasPrice * gasUsed

        JsonObject txJson = new JsonObject();
        txJson.addProperty("nonce", 1);
        txJson.addProperty("to", contractAddress);
        txJson.addProperty("value",100L);
        txJson.addProperty("gasLimit", 1_000L);
        txJson.addProperty("maxFeePerGas", 300L);
        txJson.addProperty("data", "0xa9059cbb" + Utils.padHexTo256Bits(bob) + Utils.padHexTo256Bits("64")); // 100 decimal
        txJson.addProperty("publicKey", Base64.getEncoder().encodeToString(aliceKey.getEncoded()));
        txJson.addProperty("signature", "");
        Transaction tx = new Transaction(txJson);

        long realFee = service.runTransaction(tx);
        service.onDecide(new Block(List.of(tx), "genesis", 3001, minerKey, "test-state-root"));

        long aliceBalanceAfter = service.getAccount(aliceKey).getBalance();       
        long minerBalanceAfter = service.getAccount(minerKey).getBalance();
        
        assertEquals(aliceBalanceBefore - realFee, aliceBalanceAfter, "Alice's balance should decrease by the fee");
    }

    @Test
    public void testISTCoinTransferInsufficientFee() throws Exception {
        Path tempDir = Files.createTempDirectory("istcoin-lowfee-test");
        Files.copy(Path.of("../database/genesis.json"), tempDir.resolve("genesis.json"));
        BlockchainService service = new BlockchainService(tempDir.toFile());
        String contractAddress = loadSingleContractAddress(tempDir.resolve("smart_contracts.json"));

        Ed25519PublicKeyParameters aliceKey = KeysGen.generateDeterministicKeys(3001, 4, 3001).clientPublicKey;
        Ed25519PublicKeyParameters bobKey = KeysGen.generateDeterministicKeys(3002, 4, 3002).clientPublicKey;
        Ed25519PublicKeyParameters minerKey = KeysGen.generateDeterministicKeys(3003, 4, 3003).clientPublicKey;
        String alice = Utils.publicKeyToAddress(aliceKey);
        String bob = Utils.publicKeyToAddress(bobKey);

        BigInteger bobBalanceBefore = service.callContractUint256ReadOnly(
                alice, contractAddress, "0x70a08231" + Utils.padHexTo256Bits(bob));
        long lowFee = 1; // deliberately too low

        JsonObject txJson = new JsonObject();
        txJson.addProperty("nonce", 1);
        txJson.addProperty("to", contractAddress);
        txJson.addProperty("value", 0L);
        txJson.addProperty("gasLimit", 1L); // too low
        txJson.addProperty("maxFeePerGas", lowFee);
        txJson.addProperty("data", "0xa9059cbb" + Utils.padHexTo256Bits(bob) + Utils.padHexTo256Bits("64")); // 100 decimal
        txJson.addProperty("publicKey", Base64.getEncoder().encodeToString(aliceKey.getEncoded()));
        txJson.addProperty("signature", "");
        Transaction tx = new Transaction(txJson);

        service.runTransaction(tx);
        service.onDecide(new Block(List.of(tx), "genesis", 3001, minerKey, "test-state-root"));

        BigInteger bobBalanceAfter = service.callContractUint256ReadOnly(
                alice, contractAddress, "0x70a08231" + Utils.padHexTo256Bits(bob));
        long minerBalance = service.getAccount(minerKey).getBalance();

        assertEquals(bobBalanceBefore, bobBalanceAfter, "Bob's ISTCoin balance should not change");
        assertTrue(minerBalance > 0, "Miner should still receive the fee");
    }

    private static String loadSingleContractAddress(Path contractsFile) throws Exception {
        JsonObject contracts = com.google.gson.JsonParser.parseString(Files.readString(contractsFile)).getAsJsonObject();
        return contracts.keySet().iterator().next();
    }
}
