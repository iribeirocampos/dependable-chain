package client;

import communication.AuthenticatedPerfectLink;
import com.google.gson.*;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Scanner;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Client {
    private static final String MASTER_SEED = "my-deterministic-master-seed";
    private int[] nodePorts;
    private int clientPort;
    private int currentNonce = 0;
    private AuthenticatedPerfectLink authenticatedPerfectLink;
    private Ed25519PrivateKeyParameters clientPrivateKey;
    private Ed25519PublicKeyParameters clientPublicKey;
    private String myAddress;
    private String contractAddress;
    private final int quorum;
    private volatile CountDownLatch queryLatch;
    private final ConcurrentHashMap<String, Integer> queryResponses = new ConcurrentHashMap<>();
    private volatile String queryResult;
    private final long NATIVE_GAS_LIMIT = 100L;
    private final long NATIVE_MAX_FEE_PER_GAS = 30L;
    private final long CONTRACT_GAS_LIMIT = 100000L;
    private final long CONTRACT_MAX_FEE_PER_GAS = 30L;
    /*
     * Send Message Format
     * {
     * "request": "CLIENT_REQUEST",
     * "command": "append" or "read" or "pool"
     * "payload": "hello",
     * }
     */

    /*
     * Transaction format:
     * {
     * "nonce": 12,
     * "to": "0x2333330000000000000000000000000000000000",
     * "value": "5000000000000000000",
     * "gasLimit": 21000,
     * "maxFeePerGas": "30000000000",
     * "data": "0x",
     * "publicKey": "base64-encoded-public-key",
     * }
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: <client_port> <number_of_nodes> <starting_node_port>");
            return;
        }
        int clientPort = Integer.parseInt(args[0]);
        int numNodes = Integer.parseInt(args[1]);
        int startingNodePort = Integer.parseInt(args[2]);

        Client client = new Client(clientPort, numNodes, startingNodePort);
        client.run();
    }

    public Client(int clientPort, int numNodes, int startingNodePort) throws Exception {
        this.clientPort = clientPort;
        this.nodePorts = new int[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodePorts[i] = startingNodePort + i;
        }
        this.quorum = (numNodes - 1) / 3 + 1;
        generateKeys();
        this.myAddress = publicKeyToAddress(clientPublicKey);
        this.contractAddress = loadContractAddress();
        this.currentNonce = loadPersistedNonce(Path.of("../database/accounts.json"), clientPublicKey);
        System.out.println("[CLIENT] Address: " + myAddress);
        System.out.println("[CLIENT] Starting nonce: " + currentNonce);
        if (contractAddress != null)
            System.out.println("[CLIENT] IST Coin contract: " + contractAddress);
        this.authenticatedPerfectLink = new AuthenticatedPerfectLink(clientPort) {
            @Override
            public void deliver(String msg) { handleResponse(msg); }
        };
    }
    /*
     * private String signRequestData(String data) throws Exception {
     * Ed25519Signer signer = new Ed25519Signer();
     * signer.init(true, clientPrivateKey);
     * byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
     * signer.update(dataBytes, 0, dataBytes.length);
     * return Base64.getEncoder().encodeToString(signer.generateSignature());
     * }
     */

    private void signTransaction(JsonObject tx) {
        // Serialize transaction to JSON string
        String txData = tx.toString();
        // Hash with Keccak-256
        Keccak.Digest256 keccak = new Keccak.Digest256();
        System.out.println("Signing transaction with data: " + txData);
        byte[] hash = keccak.digest(txData.getBytes(StandardCharsets.UTF_8));
        System.out.println("Signing transaction with hash: " + hash);
        // Sign the hash
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, clientPrivateKey);
        signer.update(hash, 0, hash.length);
        byte[] signature = signer.generateSignature();
        // Add signature and public key to the transaction
        tx.addProperty("signature", Base64.getEncoder().encodeToString(signature));
    }

    private JsonObject buildTransaction(String destination, long amount, long gasLimit, long maxFeePerGas,
            String data) {
        JsonObject tx = new JsonObject();
        tx.addProperty("nonce", currentNonce);
        tx.addProperty("to", destination);
        tx.addProperty("value", amount);
        tx.addProperty("gasLimit", gasLimit);
        tx.addProperty("maxFeePerGas", maxFeePerGas);
        tx.addProperty("data", data);
        signTransaction(tx);
        tx.addProperty("publicKey", Base64.getEncoder().encodeToString(clientPublicKey.getEncoded()));
        return tx;
    }

    private String buildRequestJson(JsonElement payload, String command) throws Exception {
        JsonObject obj = new JsonObject();
        obj.addProperty("request", "CLIENT_REQUEST");
        obj.addProperty("command", command);
        obj.add("payload", payload); // payload can be a JsonPrimitive (string) or JsonObject

        // Optionally, add signature/publicKey if needed at this level
        return obj.toString();
    }

    public boolean sendCommand(String line) throws Exception {
        if (line == null)
            return false;
        String normalizedLine = line.trim();
        if (normalizedLine.isEmpty())
            return true;
        if (normalizedLine.equalsIgnoreCase("exit"))
            return false;

        String command = normalizedLine.split("\\s+", 2)[0];
        System.out.println("Sending command: " + command);
        switch (command) {
            case "read" -> {
                String json = buildRequestJson(new JsonObject(), command);
                for (int port : nodePorts) {
                    authenticatedPerfectLink.send("localhost", port, json);
                }

            }
            case "pool" -> {
                // Just a test command to check content of node pool
                String json = buildRequestJson(new JsonObject(), "pool");
                for (int port : nodePorts) {
                    authenticatedPerfectLink.send("localhost", port, json);
                }
            }
            case "addresses" -> printAddresses();
            case "ist" -> handleIstCommand(normalizedLine.split("\\s+"));
            case "depcoin" -> handleDepcoinCommand(normalizedLine.split("\\s+"));
            default -> System.out.println("Commands: depcoin, ist, addresses, read, pool, exit");
        }
        return true;
    }

    public void run() throws Exception {
        System.out.println("Client application started on port " + clientPort);

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine();
            if (!sendCommand(line))
                break;
        }
        scanner.close();
    }


    private void handleResponse(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            if (!"CLIENT_RESPONSE".equals(json.get("request").getAsString())) return;
            String result = json.get("result").getAsString();
            if (queryResponses.merge(result, 1, Integer::sum) >= quorum
                    && queryLatch != null && queryLatch.getCount() > 0) {
                queryResult = result;
                queryLatch.countDown();
            }
        } catch (Exception ignored) {}
    }

    private void sendQuery(String command, JsonObject payload) throws Exception {
        queryResponses.clear();
        queryResult = null;
        queryLatch = new CountDownLatch(1);
        JsonObject obj = new JsonObject();
        obj.addProperty("request", "CLIENT_REQUEST");
        obj.addProperty("command", command);
        obj.addProperty("replyPort", clientPort);
        obj.add("payload", payload);
        String json = obj.toString();
        for (int port : nodePorts)
            authenticatedPerfectLink.send("localhost", port, json);
        if (queryLatch.await(10, TimeUnit.SECONDS))
            System.out.println("\n" + queryResult);
        else
            System.out.println("\nQuery timed out (no quorum reached).");
        System.out.print("> ");
    }

    private static final String SEL_TRANSFER = "a9059cbb", SEL_APPROVE = "426a8493",
            SEL_TRANSFER_FROM = "23b872dd", SEL_BALANCE_OF = "70a08231",
            SEL_ALLOWANCE = "dd62ed3e", SEL_TOTAL_SUPPLY = "18160ddd",
            SEL_DECREASE_ALLOWANCE = "a457c2d7";

    private void handleIstCommand(String[] parts) throws Exception {
        if (contractAddress == null) contractAddress = loadContractAddress();
        if (contractAddress == null) { System.out.println("No IST Coin contract found."); return; }
        if (parts.length < 2) { printIstUsage(); return; }
        switch (parts[1]) {
            case "transfer" -> {
                if (parts.length < 4) { System.out.println("Usage: ist transfer <to> <amount>"); return; }
                sendTx(contractAddress, 0, CONTRACT_GAS_LIMIT, CONTRACT_MAX_FEE_PER_GAS, callData(SEL_TRANSFER, parts[2], uint256(parts[3])));
            }
            case "approve" -> {
                if (parts.length < 5) {
                    System.out.println("Usage: ist approve <spender> <expected-current> <new-amount>");
                    return;
                }
                sendTx(contractAddress, 0, CONTRACT_GAS_LIMIT, CONTRACT_MAX_FEE_PER_GAS,
                        callData(SEL_APPROVE, parts[2], uint256(parts[3]), uint256(parts[4])));
            }
            case "decreaseallowance" -> {
                if (parts.length < 4) { System.out.println("Usage: ist decreaseallowance <spender> <amount>"); return; }
                sendTx(contractAddress, 0, CONTRACT_GAS_LIMIT, CONTRACT_MAX_FEE_PER_GAS, callData(SEL_DECREASE_ALLOWANCE, parts[2], uint256(parts[3])));
            }
            case "transferfrom" -> {
                if (parts.length < 5) { System.out.println("Usage: ist transferfrom <from> <to> <amount>"); return; }
                sendTx(contractAddress, 0, CONTRACT_GAS_LIMIT, CONTRACT_MAX_FEE_PER_GAS, callData(SEL_TRANSFER_FROM, parts[2], parts[3], uint256(parts[4])));
            }
            case "balance" -> {
                if (parts.length < 3) { System.out.println("Usage: ist balance <address>"); return; }
                sendContractRead(callData(SEL_BALANCE_OF, parts[2]));
            }
            case "allowance" -> {
                if (parts.length < 4) { System.out.println("Usage: ist allowance <owner> <spender>"); return; }
                sendContractRead(callData(SEL_ALLOWANCE, parts[2], parts[3]));
            }
            case "totalsupply" -> sendContractRead("0x" + SEL_TOTAL_SUPPLY);
            default -> printIstUsage();
        }
    }

    private void sendTx(String to, long value, long gasLimit, long maxFee, String data) throws Exception {
        JsonObject tx = buildTransaction(to, value, gasLimit, maxFee, data);
        String json = buildRequestJson(tx, "tx");
        for (int port : nodePorts)
            authenticatedPerfectLink.send("localhost", port, json);
        currentNonce++;
    }

    private void sendContractRead(String data) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("sender", myAddress);
        payload.addProperty("contract", contractAddress);
        payload.addProperty("callData", data);
        sendQuery("contract_read", payload);
    }

    private static String callData(String selector, String... args) {
        StringBuilder sb = new StringBuilder("0x").append(selector);
        for (String arg : args) {
            String hex = arg.startsWith("0x") ? arg.substring(2) : arg;
            sb.append("0".repeat(Math.max(0, 64 - hex.length()))).append(hex);
        }
        return sb.toString();
    }

    private static String uint256(String decimal) {
        return new BigInteger(decimal).toString(16);
    }

    private void printIstUsage() {
        System.out.println("IST Coin: ist transfer|approve|decreaseallowance|transferfrom|balance|allowance|totalsupply");
    }

    // --- DepCoin commands ---

    private void handleDepcoinCommand(String[] parts) throws Exception {
        if (parts.length < 2) { System.out.println("DepCoin: depcoin balance [addr] | depcoin transfer <to> <amount>"); return; }
        switch (parts[1]) {
            case "balance" -> {
                JsonObject payload = new JsonObject();
                payload.addProperty("address", parts.length >= 3 ? parts[2] : myAddress);
                sendQuery("balance_read", payload);
            }
            case "transfer" -> {
                if (parts.length < 4) { System.out.println("Usage: depcoin transfer <to> <amount>"); return; }
                sendTx(parts[2], Long.parseLong(parts[3]), NATIVE_GAS_LIMIT, NATIVE_MAX_FEE_PER_GAS, "0x");
            }
            default -> System.out.println("DepCoin: depcoin balance [addr] | depcoin transfer <to> <amount>");
        }
    }

    // --- Addresses (local read from database) ---

    private void printAddresses() {
        try {
            StringBuilder sb = new StringBuilder("=== Accounts (EOA) ===\n");
            JsonObject accounts = JsonParser.parseString(Files.readString(Path.of("../database/accounts.json"))).getAsJsonObject();
            for (String addr : accounts.keySet()) {
                JsonObject acc = accounts.getAsJsonObject(addr);
                sb.append("  ").append(addr);
                if (addr.equals(myAddress)) sb.append(" (you)");
                sb.append("  balance=").append(acc.get("balance").getAsLong())
                  .append("  nonce=").append(acc.get("nonce").getAsInt()).append("\n");
            }
            sb.append("=== Smart Contracts ===\n");
            JsonObject contracts = JsonParser.parseString(Files.readString(Path.of("../database/smart_contracts.json"))).getAsJsonObject();
            for (String addr : contracts.keySet())
                sb.append("  ").append(addr).append("\n");
            System.out.println(sb);
        } catch (Exception e) {
            System.out.println("Could not read addresses: " + e.getMessage());
        }
    }

    private static String loadContractAddress() {
        try {
            String json = Files.readString(Path.of("../database/smart_contracts.json"));
            JsonObject contracts = JsonParser.parseString(json).getAsJsonObject();
            if (!contracts.isEmpty()) return contracts.keySet().iterator().next();
        } catch (Exception ignored) {}
        return null;
    }

    private void generateKeys() throws Exception {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        // Generate Client Key
        String clientDeterminsticString = MASTER_SEED + "-client" + clientPort;
        byte[] clientSeedBytes = digest.digest(clientDeterminsticString.getBytes(StandardCharsets.UTF_8));

        clientPrivateKey = new Ed25519PrivateKeyParameters(clientSeedBytes, 0);
        clientPublicKey = clientPrivateKey.generatePublicKey();
        System.out.println(
                "[DEBUG CRYPTO]Client public key: " + Base64.getEncoder().encodeToString(clientPublicKey.getEncoded()));
    }

    static int loadPersistedNonce(Path accountsFile, Ed25519PublicKeyParameters publicKey) {
        String address = publicKeyToAddress(publicKey);

        for (int attempt = 0; attempt < 50; attempt++) {
            if (Files.exists(accountsFile)) {
                try {
                    JsonObject accounts = JsonParser.parseString(Files.readString(accountsFile)).getAsJsonObject();
                    if (accounts.has(address)) {
                        JsonObject account = accounts.getAsJsonObject(address);
                        return account.get("nonce").getAsInt();
                    }
                } catch (IOException | IllegalStateException ignored) {
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return 0;
    }

    static String publicKeyToAddress(Ed25519PublicKeyParameters pubKey) {
        byte[] publicKeyBytes = pubKey.getEncoded();
        Keccak.Digest256 keccak = new Keccak.Digest256();
        byte[] hash = keccak.digest(publicKeyBytes);
        byte[] addressBytes = Arrays.copyOfRange(hash, hash.length - 20, hash.length);
        StringBuilder sb = new StringBuilder();
        for (byte b : addressBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}
