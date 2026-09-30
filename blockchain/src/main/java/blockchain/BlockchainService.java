package blockchain;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

/**
 * spec: "for now it can be handled by a simple service implementation that
 * keeps an array with all the appended strings in memory."
 */
public class BlockchainService {

    private final List<Block> chain = new ArrayList<>();
    private State state;
    private State temporaryState;
    private long gasPrice = 30L;
    private long nativeGas = 100L;

    public BlockchainService() {
        this(new File("../database"));
    }

    public BlockchainService(File databaseDir) {
        state = new State(databaseDir);
        temporaryState = new State(databaseDir);
        state.loadState();
        resetTemporaryState();
        // If both maps are empty, load genesis block
        if (state.getAccounts().isEmpty() && state.getSmartContracts().isEmpty()) {
            state.loadGenesisBlock();
            resetTemporaryState();
            System.out.println("Genesis block loaded.");
        } else {
            System.out.println("State loaded from persistent storage.");
        }
    }

    public void resetTemporaryState() {
        temporaryState = state.hardCopy();
        System.out.println("[STATE COPY] Temporary state reset to current state.");
    }

    private boolean checkNonce(EOA sender, int nonce) {
        System.out.println("[CHECKING NONCE] Sender nonce: " + sender.getNonce() + ", tx nonce: " + nonce);
        return sender.getNonce() <= nonce;
    }

    private boolean checkBalance(EOA sender, double value, double fee) {
        return sender.getBalance() >= value + fee;
    }

    public long runTransaction(Transaction tx) {
        System.out.println("[TX] Running transaction: " + tx);
        long fees =0;
        try {
            String senderAddress = Utils.publicKeyToAddress(tx.getPublicKey());
            EOA sender = temporaryState.getAccount(senderAddress);
            if (sender == null) {
                System.out.println("[TX] Sender account not found: " + senderAddress);
                return 0L;
            }
            String toAddress = tx.getTo();
            if (temporaryState.getSmartContracts().containsKey(toAddress)) {
                // Smart contract call — EVM handles value transfer via msg.value
                ContractExecutor.OutputCapture result = ContractExecutor.executeMutable(temporaryState, senderAddress, toAddress, tx.getData(), tx.getValue(), tx.getGasLimit());
                System.out.println("[TX] Contract call result: " + result.output.toHexString() + ", gas used: " + result.gasUsed);
                fees = result.gasUsed * gasPrice;
                sender.decreaseBalance(fees);
            } else {
                System.out.println("[TX] NATIVE TRANSFER");
                // Native DepCoin transfer
                EOA receiver = temporaryState.getAccounts().get(toAddress);
                if (receiver == null) {
                    System.out.println("[TX] Receiver account not found: " + toAddress);
                    return 0L;
                }
                fees = nativeGas*gasPrice; // Example fixed fee for native transfers
                receiver.increaseBalance(tx.getValue());
                System.out.println("[TX] Transferred " + tx.getValue() + " from " + senderAddress + " to " + toAddress + ", fee: " + fees);
                sender.decreaseBalance(tx.getValue() + fees);
            }
            sender.increaseNonce();
            System.out.println("[TX] Completed: " + senderAddress + " -> " + toAddress);
        } catch (RuntimeException e) {
            System.out.println("Transaction failed: " + e.getMessage());
            throw e;
        }
        return fees;
    }

    public boolean checkTransaction(Transaction tx) {
        try {
            EOA senderAccount = temporaryState.getAccount(Utils.publicKeyToAddress(tx.getPublicKey()));
            if (senderAccount == null) {
                System.out.println("[CHECKING TRANSACTION FAILED] Sender account not found for transaction: " + tx);
                return false;
            }
            if (!checkBalance(senderAccount, tx.getValue(), tx.getFee())) {
                System.out.println("[CHECKING TRANSACTION FAILED] Insufficient balance for transaction: " + tx);
                return false;
            }
            if (!checkNonce(senderAccount, tx.getNonce())) {
                System.out.println("[CHECKING TRANSACTION FAILED] Invalid nonce for transaction: " + tx);
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            System.out.println("[CHECKING TRANSACTION FAILED] Error checking transaction: " + e.getMessage());
            return false;
        }
    }

    private void commitState() {
        state = temporaryState.hardCopy();
        state.saveState();
    }

    public void onDecide(Block data) {
        chain.add(data);
        commitState();
        System.out.println("COMMITTED: \"" + data + "\" | chain=" + chain.size());
    }

    public void printBlockchain() {
        System.out.println("Current blockchain state: " + chain);
    }

    public void payMiner(Ed25519PublicKeyParameters proposerPublicKey, long fees) {
        EOA minerAccount = temporaryState.getAccount(Utils.publicKeyToAddress(proposerPublicKey));
        if (minerAccount != null) {
            minerAccount.increaseBalance(fees); // Example reward per transaction
            System.out.println("[MINER REWARD] Miner " + minerAccount.getAddress() + " received reward: "
                    + fees);
        } else {
            System.out.println(
                    "[MINER REWARD] Miner account not found for public key: " + proposerPublicKey);
        }
    }

    public EOA getAccount(Ed25519PublicKeyParameters publicKey) {
        String address = Utils.publicKeyToAddress(publicKey);
        // System.out.println("[BLOCKCHAIN] Getting account for public key: " +
        // publicKey + " -> address: " + address);
        return state.getAccount(address);
    }

    public String getTemporaryStateRoot() {
        return temporaryState.calculateStateRoot();
    }

    public BigInteger callContractUint256ReadOnly(String senderHex, String contractHex, String callDataHex ) {
        String result = ContractExecutor.executeReadOnly(state, senderHex, contractHex, callDataHex);
        return new BigInteger(Utils.stripHexPrefix(result), 16);
    }

    public String readContract(String senderHex, String contractHex, String callDataHex) {
        return ContractExecutor.executeReadOnly(state, senderHex, contractHex, callDataHex);
    }

    public String getBalanceString(String address) {
        EOA account = state.getAccount(address);
        if (account == null) return "Account not found: " + address;
        return String.valueOf(account.getBalance());
    }

    public String getAddressList() {
        StringBuilder sb = new StringBuilder("=== Accounts (EOA) ===\n");
        for (Map.Entry<String, EOA> entry : state.getAccounts().entrySet()) {
            EOA a = entry.getValue();
            sb.append("  ").append(entry.getKey())
              .append("  balance=").append(a.getBalance())
              .append("  nonce=").append(a.getNonce()).append("\n");
        }
        sb.append("=== Smart Contracts ===\n");
        for (String addr : state.getSmartContracts().keySet()) {
            sb.append("  ").append(addr).append("\n");
        }
        return sb.toString();
    }

}
