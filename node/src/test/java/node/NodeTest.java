package node;

import communication.AuthenticatedPerfectLink;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class NodeTest {

    @Test
    public void testThreeRoundConsensus() throws Exception {
        String classpath = System.getProperty("java.class.path");
        List<Process> processes = new ArrayList<>();

        try {
            // start 4 nodes
            for (int i = 0; i < 4; i++) {
                int port = 10000 + i;
                ProcessBuilder pb = new ProcessBuilder("java", "-cp", classpath, "node.Node",
                        String.valueOf(port), "4", "10000");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                processes.add(p);

                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                new Thread(() -> {
                    reader.lines().forEach(line -> System.out.println("[Node " + port + "] " + line));
                }).start();
            }

            Thread.sleep(2000);

            AuthenticatedPerfectLink tempClient = new AuthenticatedPerfectLink(9999) {
                @Override
                public void deliver(String msg) {
                }
            };

            // View 1 (Leader 10000)
            System.out.println("\n--- [TEST] Sending client request for View 1 ---");
            tempClient.send("127.0.0.1", 10000, "Block for View 1");
            Thread.sleep(3000); // wait for consensus

            // View 2 (Leader 10001)
            System.out.println("\n--- [TEST] Sending client request for View 2 ---");
            tempClient.send("127.0.0.1", 10001, "Block for View 2");
            Thread.sleep(3000);

            // View 3 (Leader 10002)
            System.out.println("\n--- [TEST] Sending client request for View 3 ---");
            tempClient.send("127.0.0.1", 10002, "Block for View 3");
            Thread.sleep(3000);

        } finally {
            System.out.println("\n--- Shutting down nodes ---");
            for (Process p : processes) {
                p.destroyForcibly();
            }
        }
    }
}
