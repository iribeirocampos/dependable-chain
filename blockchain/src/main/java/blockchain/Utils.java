package blockchain;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hyperledger.besu.datatypes.Address;

import java.util.Arrays;

public class Utils {

    public static String publicKeyToAddress(Ed25519PublicKeyParameters pubKey) {
        byte[] hash = keccakDigest(pubKey.getEncoded());
        byte[] addressBytes = Arrays.copyOfRange(hash, hash.length - 20, hash.length);
        return bytesToHex(addressBytes);
    }

    /** Keccak-256 digest, used for addresses, state roots, and transaction hashes. */
    public static byte[] keccakDigest(byte[] input) {
        return new Keccak.Digest256().digest(input);
    }

    // Helper method to convert bytes to hex string
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String stripHexPrefix(String hex) {
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            return hex.substring(2);
        }
        return hex;
    }

    /** Left-pad a hex string to 256 bits (64 hex chars). */
    public static String padHexTo256Bits(String hex) {
        String normalized = stripHexPrefix(hex);
        return "0".repeat(Math.max(0, 64 - normalized.length())) + normalized;
    }

    public static byte[] hexToBytes(String hex) {
        String clean = stripHexPrefix(hex);
        int length = clean.length();
        byte[] byteArray = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            byteArray[i / 2] = (byte) Integer.parseInt(clean.substring(i, i + 2), 16);
        }
        return byteArray;
    }

    /** Convert a hex address string to a Besu Address. */
    public static Address toBesuAddress(String addressHex) {
        return Address.fromHexString("0x" + stripHexPrefix(addressHex));
    }

}
