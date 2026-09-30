# <p align="center">DepChain</p>
<p align="center">A dependable permissioned blockchain built on Basic HotStuff, with EVM smart contracts</p>

## School and Course
<img src="https://epg.ulisboa.pt/sites/ulisboa.pt/files/styles/logos_80px_vert/public/uo/logos/logo_ist.jpg?itok=2NCqbcIP" width="100" height="50">

[Instituto Superior Técnico](https://tecnico.ulisboa.pt/)

[Computer Science and Engineering](https://tecnico.ulisboa.pt/en/education/courses/masters-programmes/computer-science-and-engineering/)

## Class Subject and Goals
### Class: [Highly Dependable Systems](https://fenix.tecnico.ulisboa.pt/cursos/meic-t/disciplina-curricular/845953938490557) - 2025/26
### Goals

- Java and the Java Crypto API
- Byzantine fault tolerant consensus (Basic HotStuff)
- Layered communication abstractions over UDP (Fair Loss, Stubborn, Perfect and Authenticated Perfect Links)
- Cryptography: Diffie-Hellman key exchange, HMAC, digital signatures
- Blockchain transaction processing, gas fees and account state
- Smart contracts on the EVM (Hyperledger Besu) and ERC-20 security

### Grade: 18/20 ![Grade](https://img.shields.io/badge/Grade-A-brightgreen)

## Problem Specification

The goal was to build **DepChain**, a simplified permissioned (closed membership)
blockchain with high dependability guarantees. The project had two stages.

### Stage 1: Consensus layer

Implement the **Basic HotStuff** algorithm (chaining is not required) with these assumptions:

- The membership is static and known by everyone before the system starts.
- A public key infrastructure exists: members use pre-distributed self-generated key pairs.
- Up to *f* blockchain members can be Byzantine, and the network is unreliable and unsecured
  (messages can be dropped, delayed, duplicated or corrupted). Secure channels such as TLS are not allowed.
  The communication uses **UDP**, with layered abstractions built on top.

Clients only submit a string to be appended to the blockchain and wait for a confirmation.
The tests must be able to change how messages are delivered and how Byzantine nodes behave.

### Stage 2: Transaction processing layer

- Clients (accounts) perform transactions: native cryptocurrency transfers (**DepCoin**) and smart contract calls.
- Blocks group several transactions, ordered by transaction fee.
- The genesis block deploys an **ERC-20 token** ("IST Coin", symbol `IST`, 2 decimals, 100 million units of total supply), compiled from Solidity to EVM bytecode. The standard ERC-20 must be modified to resist the **Approval Front-Running attack**.
- Every transaction pays a gas fee, `min(gas_price * gas_limit, gas_price * gas_used)`, deducted from the sender's DepCoin balance.
- Account balances must be non-negative, only authorized users can change an account's state, and operations cannot be repudiated.
- The EVM is provided by [Hyperledger Besu](https://github.com/hyperledger/besu).
- The system must tolerate Byzantine servers and Byzantine clients.

## Implemented Features

| Area | What the system does |
| --- | --- |
| Communication | `FairLossLink` → `StubbornLink` → `PerfectLink` → `AuthenticatedPerfectLink` (Diffie-Hellman handshake, then HMAC-SHA256 on every message) |
| Consensus | Basic HotStuff with four phases (Prepare, Pre-Commit, Commit, Decide), quorum certificates of `N - f` signatures, round-robin leader rotation |
| Failure handling | Per-view timeout (pacemaker) triggers a `NEW-VIEW` and a change of leader when the leader is faulty or silent |
| Byzantine tolerance | Signature and sender checks, replay and stale-view protection, rejection of out-of-order or forged stage messages, `safeNode` predicate and locked QC |
| Blockchain | Genesis block, EOA and contract accounts, nonces, blocks persisted as JSON, state root check on every proposed block |
| Transactions | Ed25519-signed transactions (Keccak-256 payload hash), priority queue ordered by nonce and fee, fee charged to the sender and paid to the miner |
| Smart contracts | EVM execution through Hyperledger Besu; `ISTCoin.sol` is an ERC-20 contract deployed in the genesis block |
| Front-running protection | `approve` is a compare-and-set: it reverts unless the caller supplies the current allowance they expect. `decreaseAllowance` is also available |
| Client | CLI to transfer DepCoin, use IST Coin, and read the chain and the transaction pool |

## Tech Stack

- Java 17, Maven (multi-module)
- Hyperledger Besu EVM, Apache Tuweni, Bouncy Castle
- Gson, JUnit 5
- Solidity (`ISTCoin.sol`, compiled to EVM bytecode)

## Project Structure

```
dependable-chain/
├── communication/   # Communication abstractions and protocols (UDP links)
├── node/            # Consensus node (HotStuff replicas), IST Coin contract
├── blockchain/      # Blockchain state, transactions, EVM execution
├── client/          # Client application (CLI)
├── database/        # Genesis block and demo keys
├── start-all.sh     # Launches the demo topology in tmux
└── pom.xml
```

## Setup

### Prerequisites

- Java 17
- Maven 3.x
- `bash`, `tmux` and `lsof` (only for the `start-all.sh` demo script)

The demo script needs a Unix shell. On Windows, use [WSL](https://learn.microsoft.com/windows/wsl/), or start the nodes and clients by hand as described below.

### Build the Project

From the project root, run:

```sh
mvn -DskipTests install
```

This compiles all modules without running the test suite. If you want the full build with tests, run:

```sh
mvn clean install
```

### Keys and genesis

The demo runs from the files in [`database/`](database):

- [`genesis.json`](database/genesis.json): the genesis block, with the funded accounts and the deployment of IST Coin.
- [`keys.json`](database/keys.json): the key pairs and addresses of the demo clients (ports 2999 and 3000) and nodes (ports 3001 to 3004).

> **The keys in `keys.json` are throwaway demo keys** published only so the demo can run. Never reuse them for anything real.

---

## Running the Applications

### Start Nodes

You can start a node by running:

```sh
cd node
mvn exec:java -Dexec.args="<node_port> <number_of_nodes> <starting_node_port>"
```
- `<node_port>`: The port for this node (e.g., 3001, 3002, ...).
- `<number_of_nodes>`: The total number of nodes in the system.
- `<starting_node_port>`: The port of the first node (usually 3001).

**Example:**
```sh
cd node
mvn exec:java -Dexec.args="3001 4 3001"
```
This starts a node on port 3001, with 4 nodes in total, starting at port 3001.

---

### Start Client

You can start a client by running:

```sh
cd client
mvn exec:java -Dexec.args="3000 <number_of_nodes> 3001"
```
- `3000`: The client port for this client instance.
- `<number_of_nodes>`: The number of nodes in the system.
- `3001`: The starting node port (first node).

**Example:**
```sh
cd client
mvn exec:java -Dexec.args="3000 4 3001"
```
This starts the client on port 3000, with 4 nodes starting at port 3001.

For the provided demo genesis, the funded client ports are **2999** and **3000**.

### Using the Client CLI

Once started, the client supports the following commands:

#### IST Coin (ERC-20) Commands

- `ist transfer <to> <amount>`  
   Transfer IST tokens to another address.

- `ist approve <spender> <expected-current> <new-amount>`  
   Compare-and-set allowance update. The transaction reverts unless the current on-chain allowance matches `expected-current`.

- `ist decreaseallowance <spender> <amount>`  
   Decrease the allowance for a spender.

- `ist transferfrom <from> <to> <amount>`  
   Transfer IST tokens from one address to another (using allowance).

- `ist balance <address>`  
   Query the IST token balance of an address.

- `ist allowance <owner> <spender>`  
   Query the allowance from owner to spender.

- `ist totalsupply`  
   Query the total supply of IST tokens.

#### DepCoin Commands

- `depcoin balance [address]`  
   Query the DepCoin balance of an address (or your own if omitted).

- `depcoin transfer <to> <amount>`  
   Transfer DepCoin to another address.

#### Other Commands

- `addresses`  
   Print all known EOA and smart contract addresses.

- `read`  
   Read the blockchain state from the nodes.

- `pool`  
   Request the current contents of the transaction pool from the nodes.

- `exit`  
   Exit the client.

**Example usage:**
```
> ist transfer 012ae005c54c4eed2dda26e4cba73fbdff4b3b70 1000
> ist approve 6753ca98a25e609aa69483c8ae3b0bdee3658c63 0 500
> depcoin balance
> depcoin transfer 6c5820ae0c4a09561efaeb6fcb0f143c42ab9d53 100
> addresses
> read
> pool
> exit
```

### Running The Demo Topology

Use the provided script to build and launch the fixed demo topology in `tmux`:

- clients on UDP ports **2999** and **3000**
- nodes on UDP ports **3001**, **3002**, **3003**, **3004**

```bash
./start-all.sh [--clean] [--force]
```

The script:

- runs `mvn -q -DskipTests install` from the project root
- restarts the `sec-all` `tmux` session on every run
- opens one tiled `nodes` window plus two client windows named `2999` and `3000`
- forces the `tmux` status bar on and enables mouse mode automatically

Flags:

- `--clean`: delete `accounts.json` and `smart_contracts.json` (the persisted state in `database/`) before starting, so the next run reloads from [genesis.json](./database/genesis.json)
- `--force`: kill processes using UDP ports `2999` to `3004` before starting

If any of those UDP ports are busy and you do not pass `--force`, the script exits and tells you to rerun with `--force`.

Navigation:

- the bottom `tmux` status bar shows the `nodes`, `2999`, and `3000` windows
- click `2999` or `3000` with the mouse to switch to a client window

**Examples:**

```bash
./start-all.sh
./start-all.sh --clean
./start-all.sh --force --clean
```

**Requirements:**  

- `tmux` must be installed
- `lsof` must be installed
- Make the script executable if needed:
  ```sh
  chmod +x start-all.sh
  ```

Typical installs:

- macOS: `brew install tmux`
- Ubuntu/Debian: `sudo apt install tmux lsof`
- Fedora: `sudo dnf install tmux lsof`

## Testing

### Communication

#### FairLossLink Test

To run the unit/integration test for `FairLossLink`, use the following Maven command from the root of the project:

```sh
mvn -pl communication test -Dtest=FairLossLinkTest
```

#### StubbornLink Test

To run the unit/integration test for `StubbornLink`, use:

```sh
mvn -pl communication test -Dtest=StubbornLinkTest
```

#### PerfectLink Test

To run the unit/integration test for `PerfectLink`, use:

```sh
mvn -pl communication test -Dtest=PerfectLinkTest
```

**Edge Cases Covered in `PerfectLinkTest`:**

1. **Correct message delivery:**
   - Ensures a message with a new ID is delivered exactly once.
2. **Duplicate message:**
   - Ensures that if a message with the same ID is received more than once, it is only delivered once.
3. **Past ID message:**
   - Ensures that if a message with an ID lower than the last delivered is received, it is not delivered again.

#### AuthenticatedPerfectLink Test

To run the unit/integration test for `AuthenticatedPerfectLink`, use:

```sh
mvn -pl communication test -Dtest=AuthenticatedPerfectLinkTest
```

**Edge Cases Covered in `AuthenticatedPerfectLinkTest`:**

1. **Correct message:**
   - Ensures a valid message with a correct MAC is delivered.
2. **Attack message (wrong port):**
   - Ensures that a message crafted for a different port (receiver) with a valid MAC is **not** delivered.
3. **Attack message (tampered text):**
   - Ensures that a message with a valid MAC for different content (tampered text) is **not** delivered.
4. **Replay attack:**
   - Ensures replayed messages with the same message ID are **not** delivered twice.
5. **Invalid request:**
   - Ensures malformed requests (invalid message format) are dropped.
6. **Drop messages:**
   - Ensures that if an earlier message is dropped, a later valid message is still delivered.
7. **Invalid MAC:**
   - Ensures messages with an invalid MAC are dropped.

---

### Consensus

#### Run All Consensus Suites

```sh
mvn -pl node test -Dtest=Consensus*Test
```

Consensus tests are organized by concern into three suites:

#### Replay and View Tests

Run:

```sh
mvn -pl node test -Dtest=ConsensusReplayAndViewTest
```

Covers:

1. **Duplicate replay from same sender:**
   - Verifies duplicate `PREPARE_VOTE` replays do not count twice.
2. **Replay after completion:**
   - Verifies replayed messages do not mutate `prepareQC` after stage completion.
3. **Old-view replay handling:**
   - Verifies stale-view votes are ignored.
4. **Mixed current + stale views near quorum:**
   - Verifies stale-view votes cannot be used to cross quorum.
5. **Wrong-stage replay:**
   - Verifies replaying `PREPARE_VOTE` during `PreCommit` is ignored.

#### Signature and Integrity Tests

Run:

```sh
mvn -pl node test -Dtest=ConsensusSignatureAndIntegrityTest
```

Covers:

1. **Malformed signature rejection:**
   - Verifies malformed signatures are rejected.
2. **Forged sender/signature mismatch rejection:**
   - Verifies votes signed by a different node than claimed sender are rejected.
3. **Wrong block-hash vote handling:**
   - Verifies votes with tampered `blockHash` do not contribute to quorum.
4. **Previous-view QC handling:**
   - Verifies previous-view `prepareQC` is rejected.

#### Liveness and Ordering Tests

Run:

```sh
mvn -pl node test -Dtest=ConsensusLivenessAndOrderingTest
```

Covers:

1. **Dropped messages below quorum:**
   - Verifies consensus does not progress without quorum.
2. **Out-of-order stage messages:**
   - Verifies `PRE_COMMIT`/`COMMIT` messages without required prior QC are rejected.
3. **Non-responding nodes:**
   - Verifies consensus does not complete when enough nodes do not respond.
4. **Non-responding leader (replica perspective):**
   - Verifies replicas stall without leader proposal/progress.

---

### Blockchain EVM

#### Blockchain Gas Consumption Test

To run the unit/integration test for blockchain gas and fee logic, use:

```sh
mvn -pl node test -Dtest=GasConsumptionTest
```

**What does GasConsumptionTest cover?**

- **DepCoin native transfer:**
   - Checks that a native DepCoin transfer deducts the correct amount and fee from the sender, credits the receiver, and charges the miner the correct fee.
- **ISTCoin (ERC-20) transfer:**
   - Validates that token transfers and gas fees are handled correctly for smart contract transactions.
- **Insufficient fee scenario:**
   - Ensures that transactions with insufficient fees do not alter token balances but still pay the miner.

**Note:** For simplification, the gas limit and gas price are fixed in the blockchain implementation and tests. This allows for predictable fee calculations and easier validation of balance changes.

These tests use temporary blockchain state and real contract logic to validate fee and balance correctness for both native and contract-based transactions.

#### Front-Running Attack Test

To run the unit/integration test for the front-running attack scenario on ERC-20 allowance logic, use:

```sh
mvn -pl node test -Dtest=FrontRunnerTest
```

This test simulates a front-running attack where an attacker races a stale compare-and-set `approve` against `transferFrom`. The mitigation is that `approve` now reverts unless the caller supplies the current allowance they expect, so the attacker cannot spend the old allowance and still receive a fresh nonzero one.

To run the lower-level revert check for the stale `approve`, use:

```sh
mvn -pl node test -Dtest=ContractExecutorRevertTest
```

---

## Simplifications

- Gas price and gas limits are fixed constants in the blockchain service and in the client.
- Blocks are kept in memory, while the account and contract state are persisted to JSON files.
- Membership, keys and addresses are static and known in advance.

<h2>Credits</h2>

- Author: <a href="https://github.com/iribeirocampos" target="_blank">Iuri Campos</a>
- Co-author: <a href="https://github.com/vilhenad" target="_blank">David Vilhena</a>
- Co-author: <a href="https://github.com/tiagogb1" target="_blank">Tiago Bernardo</a>

<h2>Copyright</h2>
This project is licensed under the terms of the MIT license and protected by IST Honor Code and Community Code of Conduct.

<img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white"> <img src="https://img.shields.io/badge/Apache%20Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white"> <img src="https://img.shields.io/badge/Solidity-363636?style=for-the-badge&logo=solidity&logoColor=white"> <img src="https://img.shields.io/badge/Ethereum-3C3C3D?style=for-the-badge&logo=ethereum&logoColor=white">
