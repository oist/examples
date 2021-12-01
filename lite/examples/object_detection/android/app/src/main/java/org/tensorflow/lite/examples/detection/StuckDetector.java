package org.tensorflow.lite.examples.detection;

import android.util.Log;

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
        Log.d("StuckDetector", "stuck Timer started");
        whenStuck = stuckTimer.schedule(() -> {
            Log.d("StuckDetector", "Stuck!");
            stuck = true;
        }, stuckTime, TimeUnit.MILLISECONDS);
    }

    /**
     * Default is 15seconds to assume stuck
     */
    public synchronized void startTimer(){
        stuck = false;
        Log.d("StuckDetector", "stuck Timer started");
        // If the scheduled future is finished (determined to be stuck)
        if (whenStuck.isDone()){
            whenStuck = stuckTimer.schedule(() -> {
                Log.d("StuckDetector", "Stuck!");
                stuck = true;
            }, stuckTime, TimeUnit.MILLISECONDS);
        }
        // else cancel the timer and restart it.
        else {
            whenStuck.cancel(true);
            whenStuck = stuckTimer.schedule(() -> {
                Log.d("StuckDetector", "Stuck!");
                stuck = true;
            }, stuckTime, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * @param stuckTime time in milliseconds to wait before assuming stuck
     */
    public synchronized void startTimer(int stuckTime){
        stuck = false;
        Log.d("StuckDetector", "stuck Timer started");
        // If the scheduled future is finished (determined to be stuck)
        if (whenStuck.isDone()){
            whenStuck = stuckTimer.schedule(() -> {
                Log.d("StuckDetector", "Stuck!");
                stuck = true;
            }, stuckTime, TimeUnit.MILLISECONDS);
        }
        // else cancel the timer and restart it.
        else {
            whenStuck.cancel(true);
            whenStuck = stuckTimer.schedule(() -> {
                Log.d("StuckDetector", "Stuck!");
                stuck = true;
            }, stuckTime, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isStuck() {
        return stuck;
    }
}