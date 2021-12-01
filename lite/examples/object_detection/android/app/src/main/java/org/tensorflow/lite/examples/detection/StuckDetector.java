package org.tensorflow.lite.examples.detection;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StuckDetector {
    private long stuckTime = 15000; // in ms
    private ScheduledFuture<?> whenStuck;
    private boolean stuck = false;
    private ScheduledExecutorService stuckTimer = Executors.newSingleThreadScheduledExecutor();

    public StuckDetector(){

    }

    public synchronized void startTimer(){
        // If the scheduled future is finished (determined to be stuck)
        if (whenStuck.isDone()){
            whenStuck = stuckTimer.schedule(() -> {
                stuck = true;
            }, stuckTime, TimeUnit.MILLISECONDS);
        }
        // else cancel the timer and restart it.
        else {
            whenStuck.cancel(true);
            whenStuck = stuckTimer.schedule(() -> {
                stuck = true;
            }, stuckTime, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isStuck() {
        return stuck;
    }
}