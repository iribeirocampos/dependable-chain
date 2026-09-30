package node;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class HotStuffMessage {

    public enum Type {
        NEW_VIEW,
        PREPARE,
        PREPARE_VOTE,
        PRE_COMMIT,
        PRE_COMMIT_VOTE,
        COMMIT,
        COMMIT_VOTE,
        DECIDE
    }

    public static final String REQUEST_TYPE = "CONSENSUS";

    public Type type; // NEW_VIEW, PREPARE, PREPARE_VOTE, etc.
    public int viewNumber;
    public int senderPort;
    public String partialSignature;
    public String clientMessage;

    // Payload fields (not all used in every message)
    public String blockHash;
    public String blockData;
    public QuorumCertificate highQC;
    public QuorumCertificate justify;
    public QuorumCertificate prepareQC;
    public QuorumCertificate precommitQC;
    public QuorumCertificate commitQC;

    // Constructor
    public HotStuffMessage(Type type, int viewNumber, int senderPort) {
        this.type = type;
        this.viewNumber = viewNumber;
        this.senderPort = senderPort;
    }

    // Empty constructor for deserialization
    public HotStuffMessage() {
    }

    public Type getType() {
        return type;
    }

    public String getPartialSignature() {
        return partialSignature;
    }

    public String serialize() {
        JsonObject obj = new JsonObject();
        obj.addProperty("request", REQUEST_TYPE);
        obj.addProperty("type", type.name());
        obj.addProperty("viewNumber", viewNumber);
        obj.addProperty("senderPort", senderPort);

        if (partialSignature != null) {
            obj.addProperty("partialSignature", partialSignature);
        }
        if (clientMessage != null) {
            obj.addProperty("clientMessage", clientMessage);
        }
        if (blockHash != null) {
            obj.addProperty("blockHash", blockHash);
        }
        if (blockData != null) {
            obj.addProperty("blockData", blockData);
        }
        if (highQC != null) {
            obj.addProperty("highQC", highQC.serialize());
        }
        if (justify != null) {
            obj.addProperty("justify", justify.serialize());
        }
        if (prepareQC != null) {
            obj.addProperty("prepareQC", prepareQC.serialize());
        }
        if (precommitQC != null) {
            obj.addProperty("precommitQC", precommitQC.serialize());
        }
        if (commitQC != null) {
            obj.addProperty("commitQC", commitQC.serialize());
        }

        return obj.toString();
    }

    public static HotStuffMessage deserialize(String raw) {
        try {
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();

            if (!obj.has("request") || !REQUEST_TYPE.equals(obj.get("request").getAsString())) {
                throw new IllegalArgumentException("Not a CONSENSUS message");
            }

            Type type = Type.valueOf(obj.get("type").getAsString());
            HotStuffMessage msg = new HotStuffMessage(type, obj.get("viewNumber").getAsInt(),
                    obj.get("senderPort").getAsInt());

            if (obj.has("partialSignature") && !obj.get("partialSignature").getAsString().isEmpty()) {
                msg.partialSignature = obj.get("partialSignature").getAsString();
            }
            if (obj.has("clientMessage")) {
                msg.clientMessage = obj.get("clientMessage").getAsString();
            }
            if (obj.has("blockHash")) {
                msg.blockHash = obj.get("blockHash").getAsString();
            }
            if (obj.has("blockData")) {
                msg.blockData = obj.get("blockData").getAsString();
            }
            if (obj.has("highQC")) {
                msg.highQC = QuorumCertificate.deserialize(obj.get("highQC").getAsString());
            }
            if (obj.has("justify")) {
                msg.justify = QuorumCertificate.deserialize(obj.get("justify").getAsString());
            }
            if (obj.has("prepareQC")) {
                msg.prepareQC = QuorumCertificate.deserialize(obj.get("prepareQC").getAsString());
            }
            if (obj.has("precommitQC")) {
                msg.precommitQC = QuorumCertificate.deserialize(obj.get("precommitQC").getAsString());
            }
            if (obj.has("commitQC")) {
                msg.commitQC = QuorumCertificate.deserialize(obj.get("commitQC").getAsString());
            }

            return msg;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid HotStuff message: " + raw, e);
        }
    }
}