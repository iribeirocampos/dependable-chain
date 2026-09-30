package node;

import blockchain.Block;
import blockchain.BlockchainService;
import blockchain.Transaction;
import blockchain.Utils;
import com.google.gson.JsonObject;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ISTCoinRuntimeExecutionTest {

    private static final String CLIENT_3000_ADDRESS = "6753ca98a25e609aa69483c8ae3b0bdee3658c63";
    private static final String CLIENT_2999_ADDRESS = "012ae005c54c4eed2dda26e4cba73fbdff4b3b70";
    private static final String SELECTOR_TRANSFER = "a9059cbb";
    private static final String SELECTOR_BALANCE_OF = "70a08231";

    @Test
    public void testIstCoinTransferTransactionUpdatesRecipientBalance() throws Exception {
        Path tempDir = Files.createTempDirectory("istcoin-runtime-test");
        Files.copy(Path.of("../database/genesis.json"), tempDir.resolve("genesis.json"));

        BlockchainService service = new BlockchainService(tempDir.toFile());
        String contractAddress = loadSingleContractAddress(tempDir.resolve("smart_contracts.json"));

        Ed25519PublicKeyParameters senderPublicKey = KeysGen.generateDeterministicKeys(3001, 4, 3001).clientPublicKey;
        JsonObject txJson = new JsonObject();
        txJson.addProperty("nonce", 1);
        txJson.addProperty("to", contractAddress);
        txJson.addProperty("value", 0L);
        txJson.addProperty("gasLimit", 100_000L);
        txJson.addProperty("maxFeePerGas", 3_000L);
        txJson.addProperty("data", "0x" + SELECTOR_TRANSFER
                + Utils.padHexTo256Bits(CLIENT_2999_ADDRESS)
                + Utils.padHexTo256Bits(BigInteger.valueOf(25).toString(16)));
        txJson.addProperty("publicKey", Base64.getEncoder().encodeToString(senderPublicKey.getEncoded()));
        txJson.addProperty("signature", "");

        Transaction tx = new Transaction(txJson);
        assertTrue(service.checkTransaction(tx));
        service.runTransaction(tx);

        Ed25519PublicKeyParameters proposerKey = new Ed25519PublicKeyParameters(
                Base64.getDecoder().decode("t68gwnkZmbkosAeSZQlIzFjug09JTgbrgQ6Z0/7tTEI="), 0);
        service.onDecide(new Block(List.of(tx), "genesis", 3001, proposerKey, "test-state-root"));

        BigInteger recipientBalance = service.callContractUint256ReadOnly(
                CLIENT_3000_ADDRESS,
                contractAddress,
                "0x" + SELECTOR_BALANCE_OF + Utils.padHexTo256Bits(CLIENT_2999_ADDRESS));

        assertEquals(BigInteger.valueOf(25), recipientBalance);
    }

    private static String loadSingleContractAddress(Path contractsFile) throws IOException {
        JsonObject contracts = com.google.gson.JsonParser.parseString(Files.readString(contractsFile)).getAsJsonObject();
        return contracts.keySet().iterator().next();
    }
}
