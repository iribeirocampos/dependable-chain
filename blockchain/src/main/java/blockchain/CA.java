package blockchain;

public class CA {
    private String address;
    private String code;
    private long balance; // Balance in WEI
    private String storage;

    public CA() {
    }

    public CA(String address, String code, long balance, String storage) {
        this.address = address;
        this.code = code;
        this.balance = balance;
        this.storage = storage;
    }

    public String getAddress() {
        return address;
    }

    public String getCode() {
        return code;
    }

    public long getBalance() {
        return balance;
    }

    public String getStorage() {
        return storage;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public void increaseBalance(long amount) {
        this.balance += amount;
    }

    public void decreaseBalance(long amount) {
        this.balance -= amount;
    }

}
