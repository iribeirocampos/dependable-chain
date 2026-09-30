package node;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.FileReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.*;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;

public class ISTCoinTest {
	// ================= ERC-20 bytecodes =================
	private static final String DEPLOYMENT_BYTECODE = "608060405234801561000f575f5ffd5b5061003f33600260ff16600a61002591906102ec565b6305f5e1006100349190610336565b61004460201b60201c565b61042a565b5f73ffffffffffffffffffffffffffffffffffffffff168273ffffffffffffffffffffffffffffffffffffffff16036100b4575f6040517fec442f050000000000000000000000000000000000000000000000000000000081526004016100ab91906103b6565b60405180910390fd5b805f5f8282546100c491906103cf565b925050819055508060015f8473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f82825461011791906103cf565b925050819055508173ffffffffffffffffffffffffffffffffffffffff165f73ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef8360405161017b9190610411565b60405180910390a35050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52601160045260245ffd5b5f8160011c9050919050565b5f5f8291508390505b6001851115610209578086048111156101e5576101e4610187565b5b60018516156101f45780820291505b8081029050610202856101b4565b94506101c9565b94509492505050565b5f8261022157600190506102dc565b8161022e575f90506102dc565b8160018114610244576002811461024e5761027d565b60019150506102dc565b60ff8411156102605761025f610187565b5b8360020a91508482111561027757610276610187565b5b506102dc565b5060208310610133831016604e8410600b84101617156102b25782820a9050838111156102ad576102ac610187565b5b6102dc565b6102bf84848460016101c0565b925090508184048111156102d6576102d5610187565b5b81810290505b9392505050565b5f819050919050565b5f6102f6826102e3565b9150610301836102e3565b925061032e7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff8484610212565b905092915050565b5f610340826102e3565b915061034b836102e3565b9250828202610359816102e3565b915082820484148315176103705761036f610187565b5b5092915050565b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f6103a082610377565b9050919050565b6103b081610396565b82525050565b5f6020820190506103c95f8301846103a7565b92915050565b5f6103d9826102e3565b91506103e4836102e3565b92508282019050808211156103fc576103fb610187565b5b92915050565b61040b816102e3565b82525050565b5f6020820190506104245f830184610402565b92915050565b610f28806104375f395ff3fe608060405234801561000f575f5ffd5b506004361061009c575f3560e01c806370a082311161006457806370a082311461015a57806395d89b411461018a578063a457c2d7146101a8578063a9059cbb146101d8578063dd62ed3e146102085761009c565b806306fdde03146100a057806318160ddd146100be57806323b872dd146100dc578063313ce5671461010c578063426a84931461012a575b5f5ffd5b6100a8610238565b6040516100b59190610b6b565b60405180910390f35b6100c6610275565b6040516100d39190610ba3565b60405180910390f35b6100f660048036038101906100f19190610c44565b61027d565b6040516101039190610cae565b60405180910390f35b61011461029f565b6040516101219190610ce2565b60405180910390f35b610144600480360381019061013f9190610cfb565b6102a7565b6040516101519190610cae565b60405180910390f35b610174600480360381019061016f9190610d4b565b610385565b6040516101819190610ba3565b60405180910390f35b6101926103cb565b60405161019f9190610b6b565b60405180910390f35b6101c260048036038101906101bd9190610d76565b610408565b6040516101cf9190610cae565b60405180910390f35b6101f260048036038101906101ed9190610d76565b6104e6565b6040516101ff9190610cae565b60405180910390f35b610222600480360381019061021d9190610db4565b6104fc565b60405161022f9190610ba3565b60405180910390f35b60606040518060400160405280600881526020017f49535420436f696e000000000000000000000000000000000000000000000000815250905090565b5f5f54905090565b5f61028984338461057e565b6102948484846106c8565b600190509392505050565b5f6002905090565b5f5f60025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054905083811461036e57338585836040517fa80e32710000000000000000000000000000000000000000000000000000000081526004016103659493929190610e01565b60405180910390fd5b610379338685610934565b60019150509392505050565b5f60015f8373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20549050919050565b60606040518060400160405280600381526020017f4953540000000000000000000000000000000000000000000000000000000000815250905090565b5f5f60025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20549050828110156104ce578381846040517ffb8f41b20000000000000000000000000000000000000000000000000000000081526004016104c593929190610e44565b60405180910390fd5b6104db3385858403610934565b600191505092915050565b5f6104f23384846106c8565b6001905092915050565b5f60025f8473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054905092915050565b5f60025f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054905081811015610643578281836040517ffb8f41b200000000000000000000000000000000000000000000000000000000815260040161063a93929190610e44565b60405180910390fd5b81810360025f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f208190555050505050565b5f73ffffffffffffffffffffffffffffffffffffffff168373ffffffffffffffffffffffffffffffffffffffff1603610738575f6040517f96c6fd1e00000000000000000000000000000000000000000000000000000000815260040161072f9190610e79565b60405180910390fd5b5f73ffffffffffffffffffffffffffffffffffffffff168273ffffffffffffffffffffffffffffffffffffffff16036107a8575f6040517fec442f0500000000000000000000000000000000000000000000000000000000815260040161079f9190610e79565b60405180910390fd5b5f60015f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054905081811015610832578381836040517fe450d38c00000000000000000000000000000000000000000000000000000000815260040161082993929190610e44565b60405180910390fd5b81810360015f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20819055508160015f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8282546108c29190610ebf565b925050819055508273ffffffffffffffffffffffffffffffffffffffff168473ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef846040516109269190610ba3565b60405180910390a350505050565b5f73ffffffffffffffffffffffffffffffffffffffff168373ffffffffffffffffffffffffffffffffffffffff16036109a4575f6040517fe602df0500000000000000000000000000000000000000000000000000000000815260040161099b9190610e79565b60405180910390fd5b5f73ffffffffffffffffffffffffffffffffffffffff168273ffffffffffffffffffffffffffffffffffffffff1603610a14575f6040517f94280d62000000000000000000000000000000000000000000000000000000008152600401610a0b9190610e79565b60405180910390fd5b8060025f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20819055508173ffffffffffffffffffffffffffffffffffffffff168373ffffffffffffffffffffffffffffffffffffffff167f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b92583604051610aee9190610ba3565b60405180910390a3505050565b5f81519050919050565b5f82825260208201905092915050565b8281835e5f83830152505050565b5f601f19601f8301169050919050565b5f610b3d82610afb565b610b478185610b05565b9350610b57818560208601610b15565b610b6081610b23565b840191505092915050565b5f6020820190508181035f830152610b838184610b33565b905092915050565b5f819050919050565b610b9d81610b8b565b82525050565b5f602082019050610bb65f830184610b94565b92915050565b5f5ffd5b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f610be982610bc0565b9050919050565b610bf981610bdf565b8114610c03575f5ffd5b50565b5f81359050610c1481610bf0565b92915050565b610c2381610b8b565b8114610c2d575f5ffd5b50565b5f81359050610c3e81610c1a565b92915050565b5f5f5f60608486031215610c5b57610c5a610bbc565b5b5f610c6886828701610c06565b9350506020610c7986828701610c06565b9250506040610c8a86828701610c30565b9150509250925092565b5f8115159050919050565b610ca881610c94565b82525050565b5f602082019050610cc15f830184610c9f565b92915050565b5f60ff82169050919050565b610cdc81610cc7565b82525050565b5f602082019050610cf55f830184610cd3565b92915050565b5f5f5f60608486031215610d1257610d11610bbc565b5b5f610d1f86828701610c06565b9350506020610d3086828701610c30565b9250506040610d4186828701610c30565b9150509250925092565b5f60208284031215610d6057610d5f610bbc565b5b5f610d6d84828501610c06565b91505092915050565b5f5f60408385031215610d8c57610d8b610bbc565b5b5f610d9985828601610c06565b9250506020610daa85828601610c30565b9150509250929050565b5f5f60408385031215610dca57610dc9610bbc565b5b5f610dd785828601610c06565b9250506020610de885828601610c06565b9150509250929050565b610dfb81610bdf565b82525050565b5f608082019050610e145f830187610df2565b610e216020830186610df2565b610e2e6040830185610b94565b610e3b6060830184610b94565b95945050505050565b5f606082019050610e575f830186610df2565b610e646020830185610b94565b610e716040830184610b94565b949350505050565b5f602082019050610e8c5f830184610df2565b92915050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52601160045260245ffd5b5f610ec982610b8b565b9150610ed483610b8b565b9250828201905080821115610eec57610eeb610e92565b5b9291505056fea264697066735822122068ff266986bd452ecd431b72ae0b1d9b496eb9f7311cba10b4de778f2ad3442764736f6c63430008220033";
	private static final String RUNTIME_BYTECODE = "608060405234801561000f575f5ffd5b506004361061009c575f3560e01c806370a082311161006457806370a082311461015a57806395d89b411461018a578063a457c2d7146101a8578063a9059cbb146101d8578063dd62ed3e146102085761009c565b806306fdde03146100a057806318160ddd146100be57806323b872dd146100dc578063313ce5671461010c578063426a84931461012a575b5f5ffd5b6100a8610238565b6040516100b59190610b6b565b60405180910390f35b6100c6610275565b6040516100d39190610ba3565b60405180910390f35b6100f660048036038101906100f19190610c44565b61027d565b6040516101039190610cae565b60405180910390f35b61011461029f565b6040516101219190610ce2565b60405180910390f35b610144600480360381019061013f9190610cfb565b6102a7565b6040516101519190610cae565b60405180910390f35b610174600480360381019061016f9190610d4b565b610385565b6040516101819190610ba3565b60405180910390f35b6101926103cb565b60405161019f9190610b6b565b60405180910390f35b6101c260048036038101906101bd9190610d76565b610408565b6040516101cf9190610cae565b60405180910390f35b6101f260048036038101906101ed9190610d76565b6104e6565b6040516101ff9190610cae565b60405180910390f35b610222600480360381019061021d9190610db4565b6104fc565b60405161022f9190610ba3565b60405180910390f35b60606040518060400160405280600881526020017f49535420436f696e000000000000000000000000000000000000000000000000815250905090565b5f5f54905090565b5f61028984338461057e565b6102948484846106c8565b600190509392505050565b5f6002905090565b5f5f60025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054905083811461036e57338585836040517fa80e32710000000000000000000000000000000000000000000000000000000081526004016103659493929190610e01565b60405180910390fd5b610379338685610934565b60019150509392505050565b5f60015f8373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20549050919050565b60606040518060400160405280600381526020017f4953540000000000000000000000000000000000000000000000000000000000815250905090565b5f5f60025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20549050828110156104ce578381846040517ffb8f41b20000000000000000000000000000000000000000000000000000000081526004016104c593929190610e44565b60405180910390fd5b6104db3385858403610934565b600191505092915050565b5f6104f23384846106c8565b6001905092915050565b5f60025f8473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054905092915050565b5f60025f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054905081811015610643578281836040517ffb8f41b200000000000000000000000000000000000000000000000000000000815260040161063a93929190610e44565b60405180910390fd5b81810360025f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f208190555050505050565b5f73ffffffffffffffffffffffffffffffffffffffff168373ffffffffffffffffffffffffffffffffffffffff1603610738575f6040517f96c6fd1e00000000000000000000000000000000000000000000000000000000815260040161072f9190610e79565b60405180910390fd5b5f73ffffffffffffffffffffffffffffffffffffffff168273ffffffffffffffffffffffffffffffffffffffff16036107a8575f6040517fec442f0500000000000000000000000000000000000000000000000000000000815260040161079f9190610e79565b60405180910390fd5b5f60015f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054905081811015610832578381836040517fe450d38c00000000000000000000000000000000000000000000000000000000815260040161082993929190610e44565b60405180910390fd5b81810360015f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20819055508160015f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8282546108c29190610ebf565b925050819055508273ffffffffffffffffffffffffffffffffffffffff168473ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef846040516109269190610ba3565b60405180910390a350505050565b5f73ffffffffffffffffffffffffffffffffffffffff168373ffffffffffffffffffffffffffffffffffffffff16036109a4575f6040517fe602df0500000000000000000000000000000000000000000000000000000000815260040161099b9190610e79565b60405180910390fd5b5f73ffffffffffffffffffffffffffffffffffffffff168273ffffffffffffffffffffffffffffffffffffffff1603610a14575f6040517f94280d62000000000000000000000000000000000000000000000000000000008152600401610a0b9190610e79565b60405180910390fd5b8060025f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20819055508173ffffffffffffffffffffffffffffffffffffffff168373ffffffffffffffffffffffffffffffffffffffff167f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b92583604051610aee9190610ba3565b60405180910390a3505050565b5f81519050919050565b5f82825260208201905092915050565b8281835e5f83830152505050565b5f601f19601f8301169050919050565b5f610b3d82610afb565b610b478185610b05565b9350610b57818560208601610b15565b610b6081610b23565b840191505092915050565b5f6020820190508181035f830152610b838184610b33565b905092915050565b5f819050919050565b610b9d81610b8b565b82525050565b5f602082019050610bb65f830184610b94565b92915050565b5f5ffd5b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f610be982610bc0565b9050919050565b610bf981610bdf565b8114610c03575f5ffd5b50565b5f81359050610c1481610bf0565b92915050565b610c2381610b8b565b8114610c2d575f5ffd5b50565b5f81359050610c3e81610c1a565b92915050565b5f5f5f60608486031215610c5b57610c5a610bbc565b5b5f610c6886828701610c06565b9350506020610c7986828701610c06565b9250506040610c8a86828701610c30565b9150509250925092565b5f8115159050919050565b610ca881610c94565b82525050565b5f602082019050610cc15f830184610c9f565b92915050565b5f60ff82169050919050565b610cdc81610cc7565b82525050565b5f602082019050610cf55f830184610cd3565b92915050565b5f5f5f60608486031215610d1257610d11610bbc565b5b5f610d1f86828701610c06565b9350506020610d3086828701610c30565b9250506040610d4186828701610c30565b9150509250925092565b5f60208284031215610d6057610d5f610bbc565b5b5f610d6d84828501610c06565b91505092915050565b5f5f60408385031215610d8c57610d8b610bbc565b5b5f610d9985828601610c06565b9250506020610daa85828601610c30565b9150509250929050565b5f5f60408385031215610dca57610dc9610bbc565b5b5f610dd785828601610c06565b9250506020610de885828601610c06565b9150509250929050565b610dfb81610bdf565b82525050565b5f608082019050610e145f830187610df2565b610e216020830186610df2565b610e2e6040830185610b94565b610e3b6060830184610b94565b95945050505050565b5f606082019050610e575f830186610df2565b610e646020830185610b94565b610e716040830184610b94565b949350505050565b5f602082019050610e8c5f830184610df2565b92915050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52601160045260245ffd5b5f610ec982610b8b565b9150610ed483610b8b565b9250828201905080821115610eec57610eeb610e92565b5b9291505056fea264697066735822122068ff266986bd452ecd431b72ae0b1d9b496eb9f7311cba10b4de778f2ad3442764736f6c63430008220033";

	// ========== ERC-20 Function Selectors ==========
	private static final String SELECTOR_NAME = "06fdde03";           // name()
	private static final String SELECTOR_SYMBOL = "95d89b41";         // symbol()
	private static final String SELECTOR_DECIMALS = "313ce567";       // decimals()
	private static final String SELECTOR_TOTAL_SUPPLY = "18160ddd";   // totalSupply()
	private static final String SELECTOR_BALANCE_OF = "70a08231";     // balanceOf(address)
	private static final String SELECTOR_TRANSFER = "a9059cbb";       // transfer(address,uint256)
	private static final String SELECTOR_APPROVE = "426a8493";        // approve(address,uint256,uint256)
	private static final String SELECTOR_ALLOWANCE = "dd62ed3e";      // allowance(address,address)
	private static final String SELECTOR_TRANSFER_FROM = "23b872dd";  // transferFrom(address,address,uint256)
	private static final String SELECTOR_DECREASE_ALLOWANCE = "a457c2d7"; // decreaseAllowance(address,uint256)
    // IST Coin: total supply as minted by the constructor
    // The constructor mints the full supply to msg.sender
    private static final BigInteger TOTAL_SUPPLY = new BigInteger("10000000000"); // 100 million tokens with 2 decimals


    public static void main(String[] args) throws Exception {
        System.out.println("==============================================");
        System.out.println("    ISTCoin (ERC-20) Smart Contract Test");
        System.out.println("==============================================\n");

        // ========== Setup World State ==========
        SimpleWorld simpleWorld = new SimpleWorld();

        Address ownerAddress = Address.fromHexString(
            "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
        );
        simpleWorld.createAccount(ownerAddress, 0, Wei.fromEth(100));

        Address aliceAddress = Address.fromHexString(
            "1111111111111111111111111111111111111111"
        );
        simpleWorld.createAccount(aliceAddress, 0, Wei.fromEth(50));

        Address bobAddress = Address.fromHexString(
            "2222222222222222222222222222222222222222"
        );
        simpleWorld.createAccount(bobAddress, 0, Wei.fromEth(50));

        Address contractAddress = Address.fromHexString(
            "1234567891234567891234567891234567891234"
        );
        simpleWorld.createAccount(contractAddress, 0, Wei.fromEth(0));

        System.out.println("Accounts created:");
        System.out.println("  Owner:    " + ownerAddress);
        System.out.println("  Alice:    " + aliceAddress);
        System.out.println("  Bob:      " + bobAddress);
        System.out.println("  Contract: " + contractAddress);
        System.out.println();

        // ========== Deploy ISTCoin Contract ==========

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(baos);
        StandardJsonTracer tracer = new StandardJsonTracer(
            printStream, true, true, true, true
        );

        var executor = EVMExecutor.evm(EvmSpecVersion.CANCUN);
        executor.tracer(tracer);
        executor.code(Bytes.fromHexString(DEPLOYMENT_BYTECODE));
        executor.sender(ownerAddress);
        executor.receiver(contractAddress);
        executor.contract(contractAddress);
        executor.worldUpdater(simpleWorld.updater());
        executor.commitWorldState();

        // Execute deployment (runs constructor which mints totalSupply to msg.sender)
        executor.execute();
        System.out.println("Contract deployed successfully.");
        System.out.println();

        // Switch to runtime bytecode for all subsequent calls
        executor.code(Bytes.fromHexString(RUNTIME_BYTECODE));
        // ========== Test 1: name() ==========
        System.out.println("--- Test 1: Test name() ---");
        executor.callData(Bytes.fromHexString(SELECTOR_NAME));
        executor.execute();
        String name = extractStringFromReturnData(baos);
        System.out.println("  name() = \"" + name + "\"");
        assert name.equals("IST Coin") : "Expected 'IST Coin' but got '" + name + "'";
        System.out.println("   PASS\n");

        // ========== Test 2: symbol() ==========
        System.out.println("--- Test 2: Test symbol() ---");
        executor.callData(Bytes.fromHexString(SELECTOR_SYMBOL));
        executor.execute();
        String symbol = extractStringFromReturnData(baos);
        System.out.println("  symbol() = \"" + symbol + "\"");
        assert symbol.equals("IST") : "Expected 'IST' but got '" + symbol + "'";
        System.out.println("   PASS\n");

        // ========== Test 3: decimals() ==========
        System.out.println("--- Test 3: Test decimals() ---");
        executor.callData(Bytes.fromHexString(SELECTOR_DECIMALS));
        executor.execute();
        BigInteger decimals = extractBigIntegerFromReturnData(baos);
        System.out.println("  decimals() = " + decimals);
        assert decimals.intValue() == 2 : "Expected 2 but got " + decimals;
        System.out.println("   PASS\n");

        // ========== Test 4: totalSupply() ==========
        System.out.println("--- Test 4: Test totalSupply() ---");
        executor.callData(Bytes.fromHexString(SELECTOR_TOTAL_SUPPLY));
        executor.execute();
        BigInteger totalSupply = extractBigIntegerFromReturnData(baos);
        System.out.println("  totalSupply() = " + totalSupply);
        assert totalSupply.equals(TOTAL_SUPPLY) :
            "Expected " + TOTAL_SUPPLY + " but got " + totalSupply;
        System.out.println("   PASS\n");

        // ========== Test 5: balanceOf(owner) — should have all tokens ==========
        System.out.println("--- Test 5: Test balanceOf(owner) ---");
        executor.callData(Bytes.fromHexString(
            SELECTOR_BALANCE_OF + padAddressTo256Bit(ownerAddress.toHexString())
        ));
        executor.execute();
        BigInteger ownerBalance = extractBigIntegerFromReturnData(baos);
        System.out.println("  balanceOf(owner) = " + ownerBalance);
        assert ownerBalance.equals(TOTAL_SUPPLY) :
            "Expected " + TOTAL_SUPPLY + " but got " + ownerBalance;
        System.out.println("   PASS\n");

        // ========== Test 6: transfer(alice, 1000) from owner ==========
        System.out.println("--- Test 6: Test transfer(alice, 1000) ---");
        BigInteger transferAmount = BigInteger.valueOf(1000);
        executor.sender(ownerAddress);
        executor.callData(Bytes.fromHexString(
            SELECTOR_TRANSFER +
            padAddressTo256Bit(aliceAddress.toHexString()) +
            convertBigIntToHex256Bit(transferAmount)
        ));
        executor.execute();
        BigInteger transferResult = extractBigIntegerFromReturnData(baos);
        System.out.println("  transfer() returned: " + transferResult + " (1 = true)");
        assert transferResult.intValue() == 1 : "transfer() should return true";

        // Verify balances
        executor.callData(Bytes.fromHexString(
            SELECTOR_BALANCE_OF + padAddressTo256Bit(ownerAddress.toHexString())
        ));
        executor.execute();
        BigInteger ownerBalanceAfter = extractBigIntegerFromReturnData(baos);
        System.out.println("  balanceOf(owner) = " + ownerBalanceAfter);
        assert ownerBalanceAfter.equals(TOTAL_SUPPLY.subtract(transferAmount));

        executor.callData(Bytes.fromHexString(
            SELECTOR_BALANCE_OF + padAddressTo256Bit(aliceAddress.toHexString())
        ));
        executor.execute();
        BigInteger aliceBalance = extractBigIntegerFromReturnData(baos);
        System.out.println("  balanceOf(alice) = " + aliceBalance);
        assert aliceBalance.equals(transferAmount);
        System.out.println("   PASS\n");

        // ========== Test 7: owner approve(bob, 500) + read allowance() ==========
        System.out.println("--- Test 7: Test owner approve(bob, 500) + allowance() ---");
        BigInteger approveAmount = BigInteger.valueOf(500);
        executor.sender(ownerAddress);
        executor.callData(Bytes.fromHexString(
            SELECTOR_APPROVE +
            padAddressTo256Bit(bobAddress.toHexString()) +
            convertBigIntToHex256Bit(BigInteger.ZERO) +
            convertBigIntToHex256Bit(approveAmount)
        ));
        executor.execute();
        BigInteger approveResult = extractBigIntegerFromReturnData(baos);
        System.out.println("  approve() returned: " + approveResult + " (1 = true)");
        assert approveResult.intValue() == 1;

        executor.callData(Bytes.fromHexString(
            SELECTOR_ALLOWANCE +
            padAddressTo256Bit(ownerAddress.toHexString()) +
            padAddressTo256Bit(bobAddress.toHexString())
        ));
        executor.execute();
        BigInteger allowance = extractBigIntegerFromReturnData(baos);
        System.out.println("  allowance(owner, bob) = " + allowance);
        assert allowance.equals(approveAmount);
        System.out.println("   PASS\n");

        // ========== Test 8: transferFrom(owner→alice, 200) by bob ==========
        System.out.println("--- Test 8: Test transferFrom(owner, alice, 200) by bob ---");
        BigInteger transferFromAmount = BigInteger.valueOf(200);
        executor.sender(bobAddress);
        executor.callData(Bytes.fromHexString(
            SELECTOR_TRANSFER_FROM +
            padAddressTo256Bit(ownerAddress.toHexString()) +
            padAddressTo256Bit(aliceAddress.toHexString()) +
            convertBigIntToHex256Bit(transferFromAmount)
        ));
        executor.execute();
        BigInteger transferFromResult = extractBigIntegerFromReturnData(baos);
        System.out.println("  transferFrom() returned: " + transferFromResult + " (1 = true)");
        assert transferFromResult.intValue() == 1;

        // Check remaining allowance (500 - 200 = 300)
        executor.callData(Bytes.fromHexString(
            SELECTOR_ALLOWANCE +
            padAddressTo256Bit(ownerAddress.toHexString()) +
            padAddressTo256Bit(bobAddress.toHexString())
        ));
        executor.execute();
        BigInteger remainingAllowance = extractBigIntegerFromReturnData(baos);
        System.out.println("  allowance(owner, bob) = " + remainingAllowance);
        assert remainingAllowance.equals(approveAmount.subtract(transferFromAmount));

        // Alice should have 1000 + 200 = 1200
        executor.callData(Bytes.fromHexString(
            SELECTOR_BALANCE_OF + padAddressTo256Bit(aliceAddress.toHexString())
        ));
        executor.execute();
        BigInteger aliceBalanceAfter = extractBigIntegerFromReturnData(baos);
        System.out.println("  balanceOf(alice) = " + aliceBalanceAfter);
        assert aliceBalanceAfter.equals(transferAmount.add(transferFromAmount));
        System.out.println("   PASS\n");

        // ========== Test 9: decreaseAllowance(bob, 100) as owner ==========
        System.out.println("--- Test 9: Test decreaseAllowance(bob, 100) as owner ---");
        BigInteger decreaseAmount = BigInteger.valueOf(100);
        executor.sender(ownerAddress);
        executor.callData(Bytes.fromHexString(
            SELECTOR_DECREASE_ALLOWANCE +
            padAddressTo256Bit(bobAddress.toHexString()) +
            convertBigIntToHex256Bit(decreaseAmount)
        ));
        executor.execute();
        BigInteger decreaseResult = extractBigIntegerFromReturnData(baos);
        System.out.println("  decreaseAllowance() returned: " + decreaseResult + " (1 = true)");
        assert decreaseResult.intValue() == 1;

        executor.callData(Bytes.fromHexString(
            SELECTOR_ALLOWANCE +
            padAddressTo256Bit(ownerAddress.toHexString()) +
            padAddressTo256Bit(bobAddress.toHexString())
        ));
        executor.execute();
        BigInteger finalAllowance = extractBigIntegerFromReturnData(baos);
        System.out.println("  allowance(owner, bob) = " + finalAllowance);
        BigInteger expectedAllowance = remainingAllowance.subtract(decreaseAmount);
        assert finalAllowance.equals(expectedAllowance);
        System.out.println("   PASS\n");

        // ========== Test 10: Simulate Stale Frontrunning Attack (Using compare-and-set approve) ==========
		System.out.println("--- Test 10: Simulate Stale Frontrunning Attack (Using compare-and-set approve) ---");
		// bobs allowance before attack
		executor.callData(Bytes.fromHexString(
			SELECTOR_ALLOWANCE +
			padAddressTo256Bit(ownerAddress.toHexString()) +
			padAddressTo256Bit(bobAddress.toHexString())
		));
		executor.execute();
		BigInteger allowanceBeforeAttack = extractBigIntegerFromReturnData(baos);
		System.out.println("  allowance(owner, bob) = " + allowanceBeforeAttack);
        executor.callData(Bytes.fromHexString(
            SELECTOR_BALANCE_OF + padAddressTo256Bit(bobAddress.toHexString())
        ));
        executor.execute();
        BigInteger bobBalanceBeforeAttack = extractBigIntegerFromReturnData(baos);
		System.out.println("  balanceOf(bob) = " + bobBalanceBeforeAttack);

        System.out.println("  transferFrom(owner, bob, 200)");
        executor.sender(bobAddress);
        executor.callData(Bytes.fromHexString(
            SELECTOR_TRANSFER_FROM +
            padAddressTo256Bit(ownerAddress.toHexString()) +
            padAddressTo256Bit(bobAddress.toHexString()) + 
            convertBigIntToHex256Bit(BigInteger.valueOf(200))
        ));
        executor.execute();
        BigInteger tf1Result = extractBigIntegerFromReturnData(baos);
        System.out.println("  transferFrom() returned: " + tf1Result + " (1 = true)");
        
        System.out.println("  Owner's original approve(bob, 50) is executed with stale expected allowance");
        executor.sender(ownerAddress);
        executor.callData(Bytes.fromHexString(
            SELECTOR_APPROVE +
            padAddressTo256Bit(bobAddress.toHexString()) +
            convertBigIntToHex256Bit(allowanceBeforeAttack) +
            convertBigIntToHex256Bit(BigInteger.valueOf(50))
        ));
        executor.execute();
        
        System.out.println("  transferFrom(owner, bob, 50)");
        executor.sender(bobAddress);
        executor.callData(Bytes.fromHexString(
            SELECTOR_TRANSFER_FROM +
            padAddressTo256Bit(ownerAddress.toHexString()) +
            padAddressTo256Bit(bobAddress.toHexString()) +
            convertBigIntToHex256Bit(BigInteger.valueOf(50))
        ));
        executor.execute();
        
        executor.callData(Bytes.fromHexString(
            SELECTOR_ALLOWANCE +
            padAddressTo256Bit(ownerAddress.toHexString()) +
            padAddressTo256Bit(bobAddress.toHexString())
        ));
        executor.execute();

        BigInteger allowanceAfterApproveAttack = extractBigIntegerFromReturnData(baos);
        System.out.println("  allowance(owner, bob) = " + allowanceAfterApproveAttack);
        assert allowanceAfterApproveAttack.equals(BigInteger.ZERO);

        executor.callData(Bytes.fromHexString(
            SELECTOR_BALANCE_OF + padAddressTo256Bit(bobAddress.toHexString())
        ));
        executor.execute();

		BigInteger bobBalanceAfterApproveAttack = extractBigIntegerFromReturnData(baos);
		System.out.println("  balanceOf(bob) = " + bobBalanceAfterApproveAttack);
		// bob balance diff using assert
		BigInteger expectedBobBalance = bobBalanceBeforeAttack.add(BigInteger.valueOf(200));
		assert bobBalanceAfterApproveAttack.equals(expectedBobBalance) :
			"Expected bob balance to be " + expectedBobBalance + " but got " + bobBalanceAfterApproveAttack;
		// print bob diff
		System.out.println("  Bob balance increased by: " + bobBalanceAfterApproveAttack.subtract(bobBalanceBeforeAttack));
        
        System.out.println("   PASS\n");


        // ========== Test 11: Defending Frontrunning Attack (Using decreaseAllowance) ==========
        System.out.println("--- Test 11: Defending Frontrunning Attack (Using decreaseAllowance) ---");
        System.out.println("  approve(bob, 200)");
        executor.sender(ownerAddress);
        executor.callData(Bytes.fromHexString(
            SELECTOR_APPROVE +
            padAddressTo256Bit(bobAddress.toHexString()) +
            convertBigIntToHex256Bit(BigInteger.ZERO) +
            convertBigIntToHex256Bit(BigInteger.valueOf(200))
        ));
        executor.execute();
        BigInteger approveResult3 = extractBigIntegerFromReturnData(baos);
        System.out.println("  approve() returned: " + approveResult3 + " (1 = true)");
		// bobs allowance before
		executor.callData(Bytes.fromHexString(
			SELECTOR_ALLOWANCE +
			padAddressTo256Bit(ownerAddress.toHexString()) +
			padAddressTo256Bit(bobAddress.toHexString())
		));
		executor.execute();
		BigInteger allowanceBeforeDefend = extractBigIntegerFromReturnData(baos);
		System.out.println("  allowance(owner, bob) = " + allowanceBeforeDefend);
		// bobs balance before
		executor.callData(Bytes.fromHexString(
			SELECTOR_BALANCE_OF + padAddressTo256Bit(bobAddress.toHexString())
		));
		executor.execute();
		BigInteger bobBalanceBeforeAttack2 = extractBigIntegerFromReturnData(baos);
		System.out.println("  balanceOf(bob) = " + bobBalanceBeforeAttack2);

        System.out.println("  frontrun: transferFrom(owner, bob, 200)");
        executor.sender(bobAddress);
        executor.callData(Bytes.fromHexString(
            SELECTOR_TRANSFER_FROM +
            padAddressTo256Bit(ownerAddress.toHexString()) +
            padAddressTo256Bit(bobAddress.toHexString()) +
            convertBigIntToHex256Bit(BigInteger.valueOf(200))
        ));
        executor.execute();
        BigInteger tf3Result = extractBigIntegerFromReturnData(baos);
        System.out.println("  transferFrom() returned: " + tf3Result + " (1 = true)");

        System.out.println("  Owner's original decreaseAllowance(bob, 150) is executed (equivalent to approve(bob, 50))");
        executor.sender(ownerAddress);
        executor.callData(Bytes.fromHexString(
            SELECTOR_DECREASE_ALLOWANCE +
            padAddressTo256Bit(bobAddress.toHexString()) +
            convertBigIntToHex256Bit(BigInteger.valueOf(150))
        ));
        executor.execute(); // This executes but the EVM reverts it securely.
        System.out.println("  failed frontrun: transferFrom(owner, bob, 50)");
        BigInteger transferFromAmount2 = BigInteger.valueOf(50);
        executor.sender(bobAddress);
        executor.callData(Bytes.fromHexString(
            SELECTOR_TRANSFER_FROM +
            padAddressTo256Bit(ownerAddress.toHexString()) +
            padAddressTo256Bit(bobAddress.toHexString()) +
            convertBigIntToHex256Bit(transferFromAmount2)
        ));
        executor.execute();

        // bob balance after
        executor.callData(Bytes.fromHexString(
            SELECTOR_BALANCE_OF + padAddressTo256Bit(bobAddress.toHexString())
        ));
        executor.execute();
        BigInteger bobBalanceAfterDefend = extractBigIntegerFromReturnData(baos);
        System.out.println("  balanceOf(bob) = " + bobBalanceAfterDefend);
        
        // bob balance diff using assert (should only have increased by the initial 200)
        BigInteger expectedBobBalance2 = bobBalanceBeforeAttack2.add(BigInteger.valueOf(200));
        assert bobBalanceAfterDefend.equals(expectedBobBalance2) :
            "Expected bob balance to be " + expectedBobBalance2 + " but got " + bobBalanceAfterDefend;
        
        // print bob diff
        System.out.println("  Bob balance increased by: " + bobBalanceAfterDefend.subtract(bobBalanceBeforeAttack2));
        System.out.println("   PASS\n");
    }

    // ==========================================
    //       Helper Methods
    // ==========================================

    public static BigInteger extractBigIntegerFromReturnData(ByteArrayOutputStream baos) {
        String[] lines = baos.toString().split("\\r?\\n");
        JsonObject jsonObject = JsonParser.parseString(
            lines[lines.length - 1]
        ).getAsJsonObject();

        String memory = jsonObject.get("memory").getAsString();
        JsonArray stack = jsonObject.get("stack").getAsJsonArray();
        int offset = Integer.decode(stack.get(stack.size() - 1).getAsString());
        int size = Integer.decode(stack.get(stack.size() - 2).getAsString());

        String returnData = memory.substring(2 + offset * 2, 2 + offset * 2 + size * 2);
        return new BigInteger(returnData, 16);
    }

    public static String extractStringFromReturnData(ByteArrayOutputStream baos) {
        String[] lines = baos.toString().split("\\r?\\n");
        JsonObject jsonObject = JsonParser.parseString(
            lines[lines.length - 1]
        ).getAsJsonObject();

        String memory = jsonObject.get("memory").getAsString();
        JsonArray stack = jsonObject.get("stack").getAsJsonArray();
        int offset = Integer.decode(stack.get(stack.size() - 1).getAsString());
        int size = Integer.decode(stack.get(stack.size() - 2).getAsString());

        String returnData = memory.substring(2 + offset * 2, 2 + offset * 2 + size * 2);

        int stringOffset = Integer.decode("0x" + returnData.substring(0, 64));
        int stringLength = Integer.decode(
            "0x" + returnData.substring(stringOffset * 2, stringOffset * 2 + 64)
        );
        String hexString = returnData.substring(
            stringOffset * 2 + 64, stringOffset * 2 + 64 + stringLength * 2
        );
        return new String(hexStringToByteArray(hexString), StandardCharsets.UTF_8);
    }

    public static byte[] hexStringToByteArray(String hexString) {
        int length = hexString.length();
        byte[] byteArray = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            byteArray[i / 2] = (byte) Integer.parseInt(hexString.substring(i, i + 2), 16);
        }
        return byteArray;
    }

    public static String convertBigIntToHex256Bit(BigInteger number) {
        return String.format("%064x", number);
    }

    public static String padAddressTo256Bit(String hexString) {
        if (hexString.startsWith("0x")) hexString = hexString.substring(2);
        int targetLength = 64;
        if (hexString.length() >= targetLength) return hexString.substring(0, targetLength);
        return "0".repeat(targetLength - hexString.length()) + hexString;
    }
}
