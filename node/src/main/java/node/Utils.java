package node;

import com.google.gson.*;

import blockchain.Transaction;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;

public class Utils {

    public static JsonObject tryParseJson(String message) {
        try {
            return JsonParser.parseString(message).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            return null;
        }
    }

    public static String signData(String data, Ed25519PrivateKeyParameters privateKey) throws Exception {
        // System.out.println("[UTILS] Signing data: " + data + " with private key: "
        // + Base64.getEncoder().encodeToString(privateKey.getEncoded()));
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        signer.update(dataBytes, 0, dataBytes.length);
        return Base64.getEncoder().encodeToString(signer.generateSignature());
    }

    public static boolean verifySignatureFromNode(int signerPort, String data, String signatureB64,
            Map<Integer, Ed25519PublicKeyParameters> nodesPublicKeys) {
        try {
            Ed25519PublicKeyParameters pub = nodesPublicKeys.get(signerPort);
            if (pub == null) {
                System.err.println("[WARNING] No public key found for signer port: " + signerPort);
                return false;
            }

            byte[] msg = data.getBytes(StandardCharsets.UTF_8);
            byte[] sig = Base64.getDecoder().decode(signatureB64);

            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, pub); // false = verify
            verifier.update(msg, 0, msg.length);
            // System.out.println("Verifying signature from node " + signerPort + " on data:
            // " + data + " with signature: "
            // + signatureB64);

            return verifier.verifySignature(sig);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean verifyCombinedSignature(HashMap<Integer, String> signatures, String dataToVerify,
            HashMap<Integer, Ed25519PublicKeyParameters> nodesPublicKeys) {
        Integer[] signerPorts = signatures.keySet().toArray(new Integer[0]);
        for (Integer port : signerPorts) {
            String sig = signatures.get(port);
            if (!verifySignatureFromNode(port, dataToVerify, sig, nodesPublicKeys)) {
                System.err.println("[ERROR] Invalid signature from node " + port + " for data: " + dataToVerify);
                return false;
            }
        }
        return true;
    }

    public static boolean verifyTransactionSignature(Transaction tx) {
        try {
            // Rebuild the transaction JSON as the client did
            JsonObject txJson = tx.toJsonObject();
            txJson.remove("publicKey"); // Remove publicKey from the JSON before hashing, as it is not part of the
            txJson.remove("signature"); // Remove signature from the JSON before hashing, as it is not part of the hash
                                        // input
            String txData = txJson.toString();
            Keccak.Digest256 keccak = new Keccak.Digest256();
            byte[] hash = keccak.digest(txData.getBytes(StandardCharsets.UTF_8));
            byte[] signature = Base64.getDecoder().decode(tx.getSignature());
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, tx.getPublicKey());
            verifier.update(hash, 0, hash.length);
            return verifier.verifySignature(signature);
        } catch (Exception e) {
            return false;
        }
    }

}
