package node.stages;

import node.HotStuffMessage;

import java.util.Timer;
import java.util.TimerTask;

import blockchain.Block;

/**
 * Manages the sequential execution of HotStuff consensus stages.
 * Event-driven: routes incoming messages to the current stage, and advances
 * to the next stage only when the current one signals it is complete.
 */
public class StageManager {
    public static final long TIMEOUT_MS = 500000;

    // use a single shared daemon timer to avoid creating a new thread per view
    private static final Timer timer = new Timer("pacemaker", true /* daemon */);

    private final StageContext context;
    private final Stage[] stages;
    private int currentStageIndex = 0;
    private TimerTask timeoutTask;

    public StageManager(StageContext context, Runnable onTimeout) {
        this.context = context;
        this.stages = new Stage[] { new Prepare(), new PreCommit(), new Commit(), new Decide() };
        // start the per-view timeout (algorithm 2, lines 35-36)
        timeoutTask = new TimerTask() {
            @Override
            public void run() {
                onTimeout.run();
            }
        };
        timer.schedule(timeoutTask, TIMEOUT_MS);
    }

    public void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timer.purge();
            timeoutTask = null;
        }
    }

    public void startConsensus() {
        System.out.println("[" + stages[0].getStageName() + "]");
        stages[0].start(context);
        advanceIfComplete();
    }

    public void handleMessage(HotStuffMessage msg) {
        if (isConsensusComplete())
            return;
        stages[currentStageIndex].handleMessage(msg, context);
        advanceIfComplete();
    }

    private void advanceIfComplete() {
        while (!isConsensusComplete() && stages[currentStageIndex].isComplete()) {
            currentStageIndex++;
            if (!isConsensusComplete()) {
                System.out.println("[" + stages[currentStageIndex].getStageName() + "]");
                stages[currentStageIndex].start(context);
            }
        }
    }

    public boolean isConsensusComplete() {
        return currentStageIndex >= stages.length;
    }

    public void wakeUp() {
        if (!isConsensusComplete())
            stages[currentStageIndex].wakeUp(context);
    }

    public Block getBlock() {
        return context.block;
    }

    public StageContext getContext() {
        return context;
    }
}