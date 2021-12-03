package org.tensorflow.lite.examples.detection;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.OptionalDouble;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.core.inputs.microcontroller.WheelDataSubscriber;

public class StuckDetector implements WheelDataSubscriber {
    private long stuckTime = 15000; // in ms
    private ScheduledFuture<?> whenStuck;
    private boolean stuck = false;
    private ScheduledExecutorService stuckTimer = Executors.newSingleThreadScheduledExecutor();
    int bufferLen = 100;
    private double[] speedBufferL = new double[bufferLen];
    private double[] speedBufferR = new double[bufferLen];
    private int bufferIdx = 0;
    private long wheelDataLastUpdateTimestamp = System.nanoTime();
    private int stallCnt = 0;
    private int maxStallCnt = 20;
    private int stuckCount = 5;

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

    @Override
    public void onWheelDataUpdate(long timestamp, int wheelCountL,
                                  int wheelCountR, double wheelDistanceL,
                                  double wheelDistanceR, double wheelSpeedInstantL,
                                  double wheelSpeedInstantR, double wheelSpeedBufferedL,
                                  double wheelSpeedBufferedR, double wheelSpeedExpAvgL,
                                  double wheelSpeedExpAvgR) {
        double updateTime = (System.nanoTime() - wheelDataLastUpdateTimestamp) * 1e-3;
        Log.v("WheelUpdate", String.format("Update Time: %.2f microseconds", updateTime));
        wheelDataLastUpdateTimestamp = System.nanoTime();

        speedBufferL[bufferIdx] = wheelSpeedBufferedL;
        speedBufferR[bufferIdx] = wheelSpeedBufferedR;
        bufferIdx++;
        bufferIdx = bufferIdx % bufferLen;
    }

    /**
     * Call this with a controller on a delayed executor. If returns true, shut down that wheel.
     * This class counts the number of times checkStall and if it exceeds 20 times. Turns off robot.
     * @return
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
//                avgSpeed = Arrays.stream(speedBufferL).average();
//                Log.d("StallWarning", "SpeedAvg LEFT wheel : " + avgSpeed);
                break;
            case RIGHT:
                currentSpeed = controller.getCurrentWheelSpeed(WheelSide.LEFT);
                Log.d("StallWarning", "CurrentSpeed RIGHT wheel : " + currentSpeed);
//                avgSpeed = Arrays.stream(speedBufferR).average();
//                Log.d("StallWarning", "SpeedAvg RIGHT wheel : " + avgSpeed);
                break;
        }

//        Log.d("StallWarning", "SpeedL:" + Arrays.toString(speedBufferL));
//        Log.d("StallWarning", "SpeedR:" + Arrays.toString(speedBufferR));
        Log.d("StallWarning", "ExpectedOutput*LLimit: " + Math.abs(expectedOutput * lowerLimit));


//        if (avgSpeed.isPresent()){
//            Log.d("StallWarning", "Error: " + Math.abs(expectedOutput - avgSpeed.getAsDouble()));
//            // You've stalled
//            if ((Math.abs(avgSpeed.getAsDouble()) < Math.abs(expectedOutput * lowerLimit))){
//                // set stuck to true so controller will try getFree next loop.
//                stuck = true;
//                return true;
//            }
//        }
        Log.d("StallWarning", "Error: " + Math.abs(expectedOutput - currentSpeed));
        // You've stalled
        if ((Math.abs(currentSpeed) < Math.abs(expectedOutput * lowerLimit))){
            // set stuck to true so controller will try getFree next loop.
            // keep track of number of times stalled
            stallCnt++;
            if (stallCnt > stuckCount){
                // Setting stuck to true will use controllers getFree method on next action selection.
                Log.d("StallWarning", "You stalled more than stuckCount");
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