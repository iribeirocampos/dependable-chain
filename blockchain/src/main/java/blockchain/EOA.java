package blockchain;

public class EOA {
    private String address;
    private long balance; // in WEI
    private int nonce;

    public EOA() {
    }

    public EOA(String address, long balance, int nonce) {
        this.address = address;
        this.balance = balance;
        this.nonce = nonce;
    }

    public String getAddress() {
        return address;
    }

    public long getBalance() {
        return balance;
    }

    public int getNonce() {
        return nonce;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public void increaseNonce() {
        this.nonce++;
    }

    public void decreaseBalance(long amount) {
        this.balance -= amount;
    }

    public void increaseBalance(long amount) {
        this.balance += amount;
    }
}
