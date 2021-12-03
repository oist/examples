package org.tensorflow.lite.examples.detection;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.OptionalDouble;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StuckDetector {
    private final long stuckTime = 15000; // in ms
    private ScheduledFuture<?> whenStuck;
    private boolean stuck = false;
    private final ScheduledExecutorService stuckTimer = Executors.newSingleThreadScheduledExecutor();
    private int stallCnt = 0;
    private final int maxStallCnt = 20;
    private final int stuckCount = 5;

    public StuckDetector(){
        Log.d("StuckDetector", "stuck Timer started");
        whenStuck = stuckTimer.schedule(() -> {
            Log.d("StuckDetector", "Stuck! Initialization of StuckDetector");
            stuck = true;
        }, stuckTime, TimeUnit.MILLISECONDS);
    }

    /**
     * Default is 15seconds to assume stuck
     */
    public synchronized void startTimer(){
        startTimer(15000);
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
                Log.d("StuckDetector", "Stuck! StuckDetector timer depleted");
                stuck = true;
            }, stuckTime, TimeUnit.MILLISECONDS);
        }
        // else cancel the timer and restart it.
        else {
            whenStuck.cancel(true);
            whenStuck = stuckTimer.schedule(() -> {
                Log.d("StuckDetector", "Stuck! StuckDetector timer depleted2");
                stuck = true;
            }, stuckTime, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isStuck() {
        return stuck;
    }

    /**
     * Call this with a controller on a delayed executor. If returns true, shut down that wheel.
     * This class counts the number of times checkStall and if it exceeds 20 times. Turns off robot.
     */
    public boolean checkStall(@NonNull WheelSide wheelSide, float expectedOutput, StallAwareController controller){
        OptionalDouble avgSpeed = OptionalDouble.of(0);
        expectedOutput = expectedOutput * 220; // appx scaling from [-1,1] to [full speed rev, full speed forward]
        double currentSpeed = 0;
        double lowerLimit = 0.1; // Wheel may not have reached full speed yet, but it should at least have increased towards the expected direction.

        switch (wheelSide){
            case LEFT:
                currentSpeed = controller.getCurrentWheelSpeed(WheelSide.LEFT);
                Log.d("StallWarning", "CurrentSpeed LEFT wheel : " + currentSpeed);
                break;
            case RIGHT:
                currentSpeed = controller.getCurrentWheelSpeed(WheelSide.LEFT);
                Log.d("StallWarning", "CurrentSpeed RIGHT wheel : " + currentSpeed);
                break;
        }
        Log.d("StallWarning", "ExpectedOutput*LLimit: " + Math.abs(expectedOutput * lowerLimit));
        Log.d("StallWarning", "Error: " + Math.abs(expectedOutput - currentSpeed));
        // You've stalled
        if ((Math.abs(currentSpeed) < Math.abs(expectedOutput * lowerLimit))){
            // set stuck to true so controller will try getFree next loop.
            // keep track of number of times stalled
            stallCnt++;
            if (stallCnt > stuckCount){
                // Setting stuck to true will use controllers getFree method on next action selection.
                Log.d("StallWarning", "You stalled more than stuckCount");
                Log.d("StuckDetector", "You stalled more than stuckCount");
                stuck = true;
            }
            if (stallCnt > maxStallCnt){
                // If you've tried to get unstuck, but remain stuck, shut down to prevent further wear to motors
                Log.e("StallWarning", "You stalled more than maxStallCnt Shutting Down");
                controller.stalledShutdown();
            }
            return true;
        }else {
            // Clear stall counts as you're no longer stalling
            Log.e("StallWarning", "You Appear to be free. Resetting stuck and stall counts");
            stallCnt = 0;
            stuck = false;
        }
        return false;
    }
}