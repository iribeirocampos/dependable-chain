package blockchain;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

public class Block {
    private List<Transaction> data;
    private String previousHash;
    private String stateRoot;
    private String hash;
    private int proposerId;
    private Ed25519PublicKeyParameters proposerPublicKey;

    public Block(List<Transaction> data, String previousHash, int proposerId,
            Ed25519PublicKeyParameters proposerPublicKey, String stateRoot) {
        System.out.println("[BLOCK] Creating block with data: \"" + data + "\", previousHash: " + previousHash
                + ", proposerId: " + proposerId);
        this.proposerId = proposerId;
        this.proposerPublicKey = proposerPublicKey;
        this.data = data;
        this.previousHash = previousHash;
        this.stateRoot = stateRoot;
        this.hash = calculateHash();
    }

    private String calculateHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String proposerPublicKeyB64 = Base64.getEncoder().encodeToString(proposerPublicKey.getEncoded());
            byte[] hashBytes = digest.digest(
                    (Transaction.serializeList(data) + previousHash + proposerId + proposerPublicKeyB64 + stateRoot)
                            .getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1)
                    hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public String serialize() {
        JsonObject blockJson = new JsonObject();
        blockJson.addProperty("proposerId", proposerId);
        blockJson.addProperty("previousHash", previousHash);
        blockJson.add("data", JsonParser.parseString(Transaction.serializeList(data)).getAsJsonArray());
        blockJson.addProperty("proposerPublicKey", Base64.getEncoder().encodeToString(proposerPublicKey.getEncoded()));
        blockJson.addProperty("stateRoot", stateRoot);
        return blockJson.toString();
    }

    public static Block deserialize(String raw) {
        JsonObject blockJson = JsonParser.parseString(raw).getAsJsonObject();
        return new Block(
                Transaction.deserializeList(blockJson.get("data").toString()),
                blockJson.get("previousHash").getAsString(),
                blockJson.get("proposerId").getAsInt(),
                new Ed25519PublicKeyParameters(
                        Base64.getDecoder().decode(blockJson.get("proposerPublicKey").getAsString()), 0),
                blockJson.get("stateRoot").getAsString());
    }

    public int getProposerId() {
        return proposerId;
    }

    public String getHash() {
        return hash;
    }

    public List<Transaction> getData() {
        return data;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getStateRoot() {
        return stateRoot;
    }

    public Ed25519PublicKeyParameters getProposerPublicKey() {
        return proposerPublicKey;
    }

    @Override
    public String toString() {
        return "Block{data=" + data + ", hash='" + hash.substring(0, 8) + "...', proposer=" + proposerId + "}";
    }
}
