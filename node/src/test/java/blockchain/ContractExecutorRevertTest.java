package blockchain;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContractExecutorRevertTest {

    private static final String OWNER_ADDRESS = "6753ca98a25e609aa69483c8ae3b0bdee3658c63";
    private static final String SPENDER_ADDRESS = "012ae005c54c4eed2dda26e4cba73fbdff4b3b70";
    private static final String SELECTOR_APPROVE = "426a8493";
    private static final String SELECTOR_TRANSFER_FROM = "23b872dd";
    private static final String SELECTOR_ALLOWANCE = "dd62ed3e";
    private static final String SELECTOR_BALANCE_OF = "70a08231";

    @Test
    public void testStaleApproveReverts() throws Exception {
        Path tempDir = Files.createTempDirectory("contract-revert-test");
        Files.copy(Path.of("../database/genesis.json"), tempDir.resolve("genesis.json"));

        State state = new State(tempDir.toFile());
        state.loadGenesisBlock();
        String contractAddress = state.getSmartContracts().keySet().iterator().next();

        ContractExecutor.OutputCapture approve100 = ContractExecutor.executeMutable(
                state,
                OWNER_ADDRESS,
                contractAddress,
                "0x" + SELECTOR_APPROVE
                        + Utils.padHexTo256Bits(SPENDER_ADDRESS)
                        + Utils.padHexTo256Bits("0")
                        + Utils.padHexTo256Bits("64"),
                0L,
                100_000L);
        assertTrue(!approve100.reverted, "Initial compare-and-set approve should succeed");

        ContractExecutor.OutputCapture spend100 = ContractExecutor.executeMutable(
                state,
                SPENDER_ADDRESS,
                contractAddress,
                "0x" + SELECTOR_TRANSFER_FROM
                        + Utils.padHexTo256Bits(OWNER_ADDRESS)
                        + Utils.padHexTo256Bits(SPENDER_ADDRESS)
                        + Utils.padHexTo256Bits("64"),
                0L,
                100_000L);
        assertTrue(!spend100.reverted, "transferFrom should consume the approved allowance");

        ContractExecutor.OutputCapture staleApprove50 = ContractExecutor.executeMutable(
                state,
                OWNER_ADDRESS,
                contractAddress,
                "0x" + SELECTOR_APPROVE
                        + Utils.padHexTo256Bits(SPENDER_ADDRESS)
                        + Utils.padHexTo256Bits("64")
                        + Utils.padHexTo256Bits("32"),
                0L,
                100_000L);
        assertTrue(staleApprove50.reverted, "Stale compare-and-set approve must revert");

        BigInteger allowance = new BigInteger(Utils.stripHexPrefix(ContractExecutor.executeReadOnly(
                state,
                OWNER_ADDRESS,
                contractAddress,
                "0x" + SELECTOR_ALLOWANCE
                        + Utils.padHexTo256Bits(OWNER_ADDRESS)
                        + Utils.padHexTo256Bits(SPENDER_ADDRESS))), 16);
        assertEquals(BigInteger.ZERO, allowance);

        BigInteger spenderBalance = new BigInteger(Utils.stripHexPrefix(ContractExecutor.executeReadOnly(
                state,
                SPENDER_ADDRESS,
                contractAddress,
                "0x" + SELECTOR_BALANCE_OF + Utils.padHexTo256Bits(SPENDER_ADDRESS))), 16);
        assertEquals(BigInteger.valueOf(100), spenderBalance);
    }
}
