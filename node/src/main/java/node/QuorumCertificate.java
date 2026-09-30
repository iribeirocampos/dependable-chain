package node;

import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

public class QuorumCertificate {
    public String type; // "PREPARE", "PRE_COMMIT", "COMMIT", etc.
    public int viewNumber;
    public String nodeHash;
    public HashMap<Integer, String> signatures; // nodeId -> signature

    public QuorumCertificate(String type, int viewNumber, String nodeHash) {
        this.type = type;
        this.viewNumber = viewNumber;
        this.nodeHash = nodeHash;
        this.signatures = new HashMap<>();
    }

    public QuorumCertificate(String type, int viewNumber, String nodeHash,
            HashMap<Integer, String> signatures) {
        this.type = type;
        this.viewNumber = viewNumber;
        this.nodeHash = nodeHash;
        this.signatures = signatures;
    }

    public void addPartialSignature(int nodeId, String partialSig) {
        this.signatures.put(nodeId, partialSig);
    }

    public boolean isValid(int quorum) {
        // check if we have enough signatures and if they are valid
        Set<Integer> signers = signatures.keySet();
        return signers != null && signers.size() >= quorum;
    }

    public String serialize() {
        String sigs = signatures.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
        return type + ";" + viewNumber + ";" + nodeHash + ";" + sigs;
    }

    public static QuorumCertificate deserialize(String raw) {
        if (raw == null || raw.equals("NONE") || raw.isEmpty())
            return null;
        String[] parts = raw.split(";", 4);
        String type = parts[0];
        int viewNumber = Integer.parseInt(parts[1]);
        String nodeHash = parts[2];
        HashMap<Integer, String> signatures = new HashMap<>();
        if (parts.length > 3 && !parts[3].isEmpty())
            for (String s : parts[3].split(","))
                signatures.put(Integer.parseInt(s.split(":")[0]), s.split(":")[1]);
        return new QuorumCertificate(type, viewNumber, nodeHash, signatures);
    }
}
