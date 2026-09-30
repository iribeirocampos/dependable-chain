package blockchain;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.tracing.OperationTracer;

final class ContractExecutor {

    private ContractExecutor() {}

    static OutputCapture executeMutable(State state, String senderHex, String contractHex, String callDataHex, long value, long gasLimit) {
        SimpleWorld world = buildWorld(state);
        OutputCapture result = callContract(world, state, senderHex, contractHex, callDataHex, value, gasLimit);
        System.out.println("[ContractExecutor] Execution result: " + result.output.toHexString());
        syncWorldIntoState(world, state);
        return result;
    }

    static String executeReadOnly(State state, String senderHex, String contractHex, String callDataHex) {
        OutputCapture capture = callContract(buildWorld(state), state, senderHex, contractHex, callDataHex, 0L, 0L);
        System.out.println("[ContractExecutor] Read-only execution result: " + capture.output.toHexString());
        return capture.output.toHexString();
    }

    /** Deploy a contract during genesis using CONTRACT_CREATION. */
    static void deployContract(State state, String deployerHex, String deploymentBytecode, String runtimeBytecode) {
        EOA deployer = state.getAccount(deployerHex);
        if (deployer == null) throw new IllegalArgumentException("Genesis deployer not found: " + deployerHex);
        if (runtimeBytecode == null || runtimeBytecode.isBlank())
            throw new IllegalArgumentException("Missing runtimeCode");

        Address deployerAddress = Utils.toBesuAddress(deployerHex);
        Address contractAddress = Address.contractAddress(deployerAddress, deployer.getNonce());

        SimpleWorld world = buildWorld(state);
        world.createAccount(contractAddress, 0, Wei.ZERO);

        var executor = EVMExecutor.evm(EvmSpecVersion.CANCUN);
        executor.worldUpdater(world.updater());
        executor.sender(deployerAddress);
        executor.receiver(contractAddress);
        executor.contract(contractAddress);
        executor.code(Bytes.fromHexString(deploymentBytecode));
        executor.messageFrameType(MessageFrame.Type.CONTRACT_CREATION);
        executor.commitWorldState();
        executor.execute();

        MutableAccount contractAccount = (MutableAccount) world.get(contractAddress);
        if (contractAccount == null) throw new IllegalStateException("Contract deployment failed");
        contractAccount.setCode(Bytes.fromHexString(runtimeBytecode));

        deployer.increaseNonce();
        String contractAddressHex = Utils.stripHexPrefix(contractAddress.toHexString());
        state.getSmartContracts().put(contractAddressHex, new CA(contractAddressHex,
                "0x" + Utils.stripHexPrefix(runtimeBytecode),
                contractAccount.getBalance().getAsBigInteger().longValueExact(),
                serializeStorage(contractAccount)));
    }

    static String serializeStorage(MutableAccount contractAccount) {
        JsonObject storageJson = new JsonObject();
        new TreeMap<>(contractAccount.getUpdatedStorage()).forEach((storageSlot, storageValue) -> {
            if (!storageValue.isZero()) {
                storageJson.addProperty(storageSlot.toBytes().toHexString(), storageValue.toBytes().toHexString());
            }
        });
        return storageJson.toString();
    }

    static String keccakAddressToStorageSlot(String addressHex, long slotIndex) {
        byte[] hash = Utils.keccakDigest(
                Utils.hexToBytes(Utils.padHexTo256Bits(addressHex) + String.format("%064x", slotIndex)));
        return Utils.bytesToHex(hash);
    }

    /** Workaround: EVMExecutor.execute() returns getReturnData() (empty for top-level calls).
     *  This tracer captures getOutputData() which is set by the RETURN opcode. */
    public static class OutputCapture implements OperationTracer {
        Bytes output = Bytes.EMPTY;
        long remainingGas = 0L;
        long gasUsed = 0L;
        boolean reverted = false;
        String revertReason = null;


        @Override
        public void tracePostExecution(MessageFrame frame, Operation.OperationResult result) {
            if (frame.getState() == MessageFrame.State.CODE_SUCCESS) {
                output = frame.getOutputData();
                reverted = false;
            } else if (frame.getState() == MessageFrame.State.REVERT) {
                reverted = true;
                revertReason = frame.getRevertReason().map(Bytes::toHexString).orElse(null);
                System.out.println("[ContractExecutor] [REVERTED] Execution reverted. Revert reason (if any): " +
                 (revertReason == null ? "None" : revertReason));
            }
            remainingGas = frame.getRemainingGas();
        }
    }

    private static OutputCapture callContract(SimpleWorld world, State state, String senderHex,
            String contractHex, String callDataHex, long value, long gasLimit) {
        for (CA contract : state.getSmartContracts().values()) {
            MutableAccount ca = world.createAccount(
                    Utils.toBesuAddress(contract.getAddress()), 0, Wei.of(contract.getBalance()));
            ca.setCode(Bytes.fromHexString(contract.getCode()));
            loadStorage(ca, contract.getStorage());
        }

        Address contractAddress = Utils.toBesuAddress(contractHex);
        MutableAccount contractAccount = (MutableAccount) world.get(contractAddress);
        if (contractAccount == null) throw new IllegalArgumentException("Contract not found: " + contractHex);

        OutputCapture capture = new OutputCapture();
        var executor = EVMExecutor.evm(EvmSpecVersion.CANCUN);
        executor.worldUpdater(world);
        executor.sender(Utils.toBesuAddress(senderHex));
        executor.receiver(contractAddress);
        executor.contract(contractAddress);
        executor.code(contractAccount.getCode());
        executor.callData(Bytes.fromHexString(callDataHex));
        executor.ethValue(Wei.of(value));
        executor.tracer(capture);
        if (gasLimit > 0)
            executor.gas(gasLimit);
        executor.execute();
        System.out.println("[ContractExecutor] Execution completed. Output: " + capture.output.toHexString()
        + ", gas consumed: " + (gasLimit - capture.remainingGas) );
        executor.commitWorldState();
        capture.gasUsed = gasLimit - capture.remainingGas;
        return capture;
    }

    private static SimpleWorld buildWorld(State state) {
        SimpleWorld world = new SimpleWorld();
        for (EOA account : state.getAccounts().values())
            world.createAccount(Utils.toBesuAddress(account.getAddress()), account.getNonce(),
                    Wei.of(account.getBalance()));
        return world;
    }

    private static void syncWorldIntoState(SimpleWorld world, State state) {
        for (EOA account : state.getAccounts().values()) {
            MutableAccount worldAccount = (MutableAccount) world.get(Utils.toBesuAddress(account.getAddress()));
            if (worldAccount != null) {
                account.setBalance(worldAccount.getBalance().getAsBigInteger().longValueExact());
                account.setNonce((int) worldAccount.getNonce());
            }
        }
        for (CA contract : state.getSmartContracts().values()) {
            MutableAccount worldAccount = (MutableAccount) world.get(Utils.toBesuAddress(contract.getAddress()));
            if (worldAccount != null) {
                contract.setBalance(worldAccount.getBalance().getAsBigInteger().longValueExact());
                contract.setCode(worldAccount.getCode().toHexString());
                contract.setStorage(serializeStorage(worldAccount));
            }
        }
    }

    private static void loadStorage(MutableAccount account, String storageJson) {
        if (storageJson == null || storageJson.isBlank()) return;
        for (Map.Entry<String, JsonElement> storageEntry : JsonParser.parseString(storageJson).getAsJsonObject().entrySet()) {
            account.setStorageValue(
                    UInt256.fromHexString(storageEntry.getKey()),
                    UInt256.fromHexString(storageEntry.getValue().getAsString()));
        }
    }
}
