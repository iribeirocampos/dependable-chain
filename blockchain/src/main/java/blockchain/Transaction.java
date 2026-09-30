package blockchain;

import com.google.gson.*;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.util.Base64;
import java.util.Objects;
import java.nio.charset.StandardCharsets;

public class Transaction {
    // All tokens are in WEI (1 DepCoin = 1_000_000_000 WEI) to avoid floating point
    private final int nonce;
    private final String to; // hex address (EOA or contract)
    private final long value;
    private final long gasLimit;
    private final long maxFeePerGas;
    private final String data;
    private final Ed25519PublicKeyParameters publicKey;
    private final String hash;
    private String signature; // We keep the signature as a field for easier access during verification

    public Transaction(JsonObject json) {
        try {
            this.nonce = json.get("nonce").getAsInt();
            this.to = json.get("to").getAsString();
            this.value = json.get("value").getAsLong();
            this.gasLimit = json.get("gasLimit").getAsLong();
            this.maxFeePerGas = json.get("maxFeePerGas").getAsLong();
            this.data = json.get("data").getAsString();
            this.hash = computeHash(json);
            byte[] pubKeyBytes = Base64.getDecoder().decode(json.get("publicKey").getAsString());
            this.publicKey = new Ed25519PublicKeyParameters(pubKeyBytes, 0);
            this.signature = json.get("signature").getAsString();
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to create transaction from JSON: " + e.getMessage());
            throw new RuntimeException("Failed to create transaction from JSON", e);
        }
    }

    private String computeHash(JsonObject json) {
        JsonObject hashJson = new JsonObject();
        hashJson.addProperty("nonce", json.get("nonce").getAsInt());
        hashJson.addProperty("to", json.get("to").getAsString());
        hashJson.addProperty("value", json.get("value").getAsLong());
        hashJson.addProperty("gasLimit", json.get("gasLimit").getAsLong());
        hashJson.addProperty("maxFeePerGas", json.get("maxFeePerGas").getAsLong());
        hashJson.addProperty("data", json.get("data").getAsString());
        hashJson.addProperty("publicKey", json.get("publicKey").getAsString());
        Keccak.Digest256 keccak = new Keccak.Digest256();
        byte[] hashBytes = keccak.digest(hashJson.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public String getSignature() {
        return signature;
    }

    public int getNonce() {
        return nonce;
    }

    public String getTo() {
        return to;
    }

    public long getValue() {
        // In DepCoin
        return value;
    }

    public long getGasLimit() {
        // In WEI
        return gasLimit;
    }

    public long getMaxFeePerGas() {
        // In WEI
        return maxFeePerGas;
    }

    public String getData() {
        return data;
    }

    public Ed25519PublicKeyParameters getPublicKey() {
        return publicKey;
    }

    public String getHash() {
        return hash;
    }

    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("nonce", nonce);
        json.addProperty("to", to);
        json.addProperty("value", value);
        json.addProperty("gasLimit", gasLimit);
        json.addProperty("maxFeePerGas", maxFeePerGas);
        json.addProperty("data", data);
        json.addProperty("publicKey", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        json.addProperty("signature", signature);

        return json;
    }

    public long getFee() {
        // In WEI
        try {
            return Math.multiplyExact((long) maxFeePerGas, (long) gasLimit); // This can throw ArithmeticException if
                                                                             // the result overflows
        } catch (ArithmeticException e) {
            System.err.println(
                    "[ERROR] Fee calculation overflow: maxFeePerGas=" + maxFeePerGas + ", gasLimit=" + gasLimit);
            return 0L;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Transaction))
            return false;
        Transaction t = (Transaction) o;
        return Objects.equals(hash, t.hash);
    }

    @Override
    public String toString() {
        return toJsonObject().toString();
    }

    public static String serializeList(java.util.List<Transaction> transactions) {
        JsonArray array = new JsonArray();
        for (Transaction t : transactions)
            array.add(t.toJsonObject());
        return array.toString();
    }

    public static java.util.List<Transaction> deserializeList(String raw) {
        JsonArray array = JsonParser.parseString(raw).getAsJsonArray();
        java.util.List<Transaction> transactions = new java.util.ArrayList<>();
        for (JsonElement elem : array)
            transactions.add(new Transaction(elem.getAsJsonObject()));
        return transactions;
    }
}
