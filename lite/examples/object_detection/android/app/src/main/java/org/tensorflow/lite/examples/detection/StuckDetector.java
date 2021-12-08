package org.tensorflow.lite.examples.detection;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.core.inputs.microcontroller.WheelDataSubscriber;

public class StuckDetector implements WheelDataSubscriber {
    private final long stuckTime = 15000; // in ms
    private ScheduledFuture<?> whenStuck;
    private boolean stuck = false;
    private final ScheduledExecutorService stuckTimer = Executors.newSingleThreadScheduledExecutor();
    private final int maxStallCnt = 10;
    private final int stuckCount = 5;
    private HighLevelControllerService highLevelControllerService;
    private int minFreeCnt = 3;
    private int stallCntL = 0;
    private int stallCntR = 0;
    private int freeCntL = 0;
    private int freeCntR = 0;
    private double wheelSpeedBufferedL = 0;
    private double wheelSpeedBufferedR = 0;

    public StuckDetector(HighLevelControllerService highLevelControllerService){
        this.highLevelControllerService = highLevelControllerService;
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
    public synchronized boolean checkStall(@NonNull WheelSide wheelSide, float expectedOutput, StallAwareController controller){
        double currentSpeed = 0;
        double lowerLimit = 0.1; // Wheel may not have reached full speed yet, but it should at least have increased towards the expected direction.

        switch (wheelSide){
            case LEFT:
                currentSpeed = wheelSpeedBufferedL;
                Log.d("StallWarning", "CurrentSpeed LEFT wheel : " + currentSpeed);
                Log.d("StallWarning", "ExpectedOutputL: " + expectedOutput);
                break;
            case RIGHT:
                currentSpeed = wheelSpeedBufferedR;
                Log.d("StallWarning", "CurrentSpeed RIGHT wheel : " + currentSpeed);
                Log.d("StallWarning", "ExpectedOutputR: " + expectedOutput);
                break;
        }
        double error = Math.abs(expectedOutput - currentSpeed);
        Log.d("StallWarning", "Error: " + error);
        // You've stalled. 200 is arbitrary but based on full speed of 300.
        if (error > 70){
            // set stuck to true so controller will try getFree next loop.
            // keep track of number of times stalled
            switch (wheelSide){
                case LEFT:
                    stallCntL++;
                    break;
                case RIGHT:
                    stallCntR++;
                    break;
            }

            if ((stallCntL > stuckCount) || (stallCntR > stuckCount)){
                // Setting stuck to true will use controllers getFree method on next action selection.
                Log.d("StallWarning", "You stalled more than stuckCount");
                Log.d("StuckDetector", "You stalled more than stuckCount");
                stuck = true;
            }
            if ((stallCntL > maxStallCnt) || (stallCntR > maxStallCnt)){
                // If you've tried to get unstuck, but remain stuck, shut down to prevent further wear to motors
                Log.e("StallWarning", "You stalled more than maxStallCnt Shutting Down");
                highLevelControllerService.stalledShutdownRequest();
            }
            return true;
        }else {
            // Clear stall counts as you're no longer stalling
            switch (wheelSide){
                case LEFT:
                    Log.d("StallWarning", "Left Wheel appears to be free. Adding 1 to freeCntL");
                    freeCntL++;
                    if (freeCntL > minFreeCnt){
                        Log.d("StallWarning", "Left Wheel appears to be free. Resetting stuck and stall counts");
                        stallCntL = 0;
                        freeCntL = 0;
                        stuck = false;
                    }
                    break;
                case RIGHT:
                    Log.d("StallWarning", "Right Wheel appears to be free. Adding 1 to freeCntR");
                    freeCntR++;
                    if (freeCntR > minFreeCnt){
                        Log.d("StallWarning", "Right Wheel appears to be free. Resetting stuck and stall counts");
                        stallCntR = 0;
                        freeCntR = 0;
                        stuck = false;
                    }
                    break;
            }
        }
        return false;
    }

    @Override
    public void onWheelDataUpdate(long timestamp, int wheelCountL, int wheelCountR,
                                  double wheelDistanceL, double wheelDistanceR,
                                  double wheelSpeedInstantL, double wheelSpeedInstantR,
                                  double wheelSpeedBufferedL, double wheelSpeedBufferedR,
                                  double wheelSpeedExpAvgL, double wheelSpeedExpAvgR) {
        this.wheelSpeedBufferedL = wheelSpeedBufferedL;
        this.wheelSpeedBufferedR = wheelSpeedBufferedR;
    }
}