package blockchain;

import java.util.HashMap;
import java.io.IOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class State {
    private final File databaseDir;
    private HashMap<String, EOA> accounts = new HashMap<>();
    private HashMap<String, CA> smartContracts = new HashMap<>();

    public State() {
        this(new File("../database"));
    }

    public State(File databaseDir) {
        this.databaseDir = databaseDir;
    }

    public HashMap<String, EOA> getAccounts() {
        return accounts;
    }

    public HashMap<String, CA> getSmartContracts() {
        return smartContracts;
    }

    public EOA getAccount(String address) {
        return accounts.get(address);
    }

    @SuppressWarnings("unchecked")
    public void loadState() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            File accountsFile = new File(databaseDir, "accounts.json");
            if (accountsFile.exists()) {
                accounts = mapper.readValue(accountsFile, mapper.getTypeFactory()
                        .constructMapType(HashMap.class, String.class, EOA.class));
            }
            File contractsFile = new File(databaseDir, "smart_contracts.json");
            if (contractsFile.exists()) {
                smartContracts = mapper.readValue(contractsFile, mapper.getTypeFactory()
                        .constructMapType(HashMap.class, String.class, CA.class));
            }
            System.out.println("State loaded successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadGenesisBlock() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode root = mapper.readTree(new File(databaseDir, "genesis.json"));
            JsonNode state = root.get("state");
            if (state != null) {
                state.fields().forEachRemaining(entry -> {
                    String address = entry.getKey();
                    JsonNode account = entry.getValue();
                    long balance = account.get("balance").longValue();
                    int nonce = account.get("nonce").intValue();
                    accounts.put(address, new EOA(address, balance, nonce));
                });
            }
            JsonNode transactions = root.get("transactions");
            if (transactions != null && transactions.isArray()) {
                for (JsonNode tx : transactions) {
                    if (tx.get("to").isNull()) {
                        ContractExecutor.deployContract(this,
                                tx.get("from").asText(),
                                tx.get("input").asText(),
                                tx.get("runtimeCode").asText());
                    }
                }
            }
            saveState();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveState() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            databaseDir.mkdirs();
            // Save accounts
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(databaseDir, "accounts.json"), accounts);
            // Save smart contracts
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(databaseDir, "smart_contracts.json"),
                    smartContracts);
            System.out.println("State saved successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public State hardCopy() {
        State newState = new State(databaseDir);
        newState.accounts = new HashMap<>();
        for (String key : this.accounts.keySet()) {
            EOA account = this.accounts.get(key);
            newState.accounts.put(key, new EOA(account.getAddress(), account.getBalance(), account.getNonce()));
        }
        newState.smartContracts = new HashMap<>();
        for (String key : this.smartContracts.keySet()) {
            CA contract = this.smartContracts.get(key);
            newState.smartContracts.put(key, new CA(contract.getAddress(), contract.getCode(), contract.getBalance(),
                    contract.getStorage()));
        }
        return newState;
    }

    public String calculateStateRoot() {
        JsonObject stateJson = new JsonObject();
        JsonArray accountsArray = new JsonArray();
        for (Map.Entry<String, EOA> entry : new TreeMap<>(accounts).entrySet()) {
            EOA account = entry.getValue();
            JsonObject accountJson = new JsonObject();
            accountJson.addProperty("type", "EOA");
            accountJson.addProperty("address", entry.getKey());
            accountJson.addProperty("balance", account.getBalance());
            accountJson.addProperty("nonce", account.getNonce());
            accountsArray.add(accountJson);
        }
        stateJson.add("accounts", accountsArray);

        JsonArray contractsArray = new JsonArray();
        for (Map.Entry<String, CA> entry : new TreeMap<>(smartContracts).entrySet()) {
            CA contract = entry.getValue();
            JsonObject contractJson = new JsonObject();
            contractJson.addProperty("type", "CA");
            contractJson.addProperty("address", entry.getKey());
            contractJson.addProperty("balance", contract.getBalance());
            contractJson.addProperty("code", contract.getCode() == null ? "" : contract.getCode());
            contractJson.addProperty("storage", contract.getStorage() == null ? "" : contract.getStorage());
            contractsArray.add(contractJson);
        }
        stateJson.add("smartContracts", contractsArray);

        return Utils.bytesToHex(Utils.keccakDigest(stateJson.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
