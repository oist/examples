package org.tensorflow.lite.examples.detection;

import android.util.Log;

public class StallChecker implements Runnable{

    private final StuckDetector stuckDetector;
    private final float expectedL;
    private final float expectedR;
    private final StallAwareController controller;
    private final UsageStats usageStats;
    private final float pwm2speedScalingFactor = 150;
    private final float batteryVScalingFactor = 2.8f;

    public StallChecker(StuckDetector stuckDetector, float expectedL, float expectedR, StallAwareController controller, UsageStats usageStats, float batteryVoltage){
        this.stuckDetector = stuckDetector;
        this.expectedL = expectedL * pwm2speedScalingFactor * (float) Math.pow((double) (batteryVoltage / batteryVScalingFactor), 5);
        this.expectedR = expectedR * pwm2speedScalingFactor * (float) Math.pow((double) (batteryVoltage / batteryVScalingFactor), 5);;
        this.controller = controller;
        this.usageStats = usageStats;
    }
    @Override
    public void run() {
        usageStats.onExpectedOutput(expectedL, expectedR);
        // Note if checkStall returns true, it will also set stuck=true, so next control loop will execture the stuck sequence.
        if (stuckDetector.checkStall(WheelSide.LEFT, (float) expectedL, controller, usageStats)){
            Log.w("StallWarning", "Robot has stalled once on left wheel");
        }
        if(stuckDetector.checkStall(WheelSide.RIGHT, (float) expectedR, controller, usageStats)){
            Log.w("StallWarning", "Robot has stalled once on right wheel");
        }
    }
}
