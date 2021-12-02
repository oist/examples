package org.tensorflow.lite.examples.detection;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
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
        speedBufferL[bufferIdx] = wheelSpeedBufferedL;
        speedBufferR[bufferIdx] = wheelSpeedBufferedR;
        bufferIdx++;
        bufferIdx = bufferIdx % bufferLen;
    }

    /**
     * Call this with a controller on a delayed executor. If returns true, shut down that wheel.
     * This class counts the number of times isStalled and if it exceeds 20 times. Turns off robot.
     * @return
     */
    public boolean isStalled(@NonNull WheelSide wheelSide, float expectedOutput){
        OptionalDouble avgSpeed = OptionalDouble.of(0);
        expectedOutput = expectedOutput * 220; // appx scaling from [-1,1] to [full speed rev, full speed forward]
        double lowerLimit = 0.1; // Wheel may not have reached full speed yet, but it should at least have increased towards the expected direction.

        switch (wheelSide){
            case LEFT:
                avgSpeed = Arrays.stream(speedBufferL).average();
                Log.d("StallWarning", "SpeedAvg LEFT wheel : " + avgSpeed);
                break;
            case RIGHT:
                avgSpeed = Arrays.stream(speedBufferR).average();
                Log.d("StallWarning", "SpeedAvg RIGHT wheel : " + avgSpeed);
                break;
        }

//        Log.d("StallWarning", "SpeedL:" + Arrays.toString(speedBufferL));
//        Log.d("StallWarning", "SpeedR:" + Arrays.toString(speedBufferR));
        Log.d("StallWarning", "ExpectedOutput*LLimit: " + Math.abs(expectedOutput * lowerLimit));


        if (avgSpeed.isPresent()){
            Log.d("StallWarning", "Error: " + Math.abs(expectedOutput - avgSpeed.getAsDouble()));
            // You've stalled
            if ((Math.abs(avgSpeed.getAsDouble()) < Math.abs(expectedOutput * lowerLimit))){
                // set stuck to true so controller will try getFree next loop.
                stuck = true;
                return true;
            }
        }
        return false;
    }
}