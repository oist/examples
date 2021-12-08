package org.tensorflow.lite.examples.detection;

import android.util.Log;

public class StallChecker implements Runnable{

    private final StuckDetector stuckDetector;
    private final float expectedL;
    private final float expectedR;
    private final StallAwareController controller;
    private final UsageStats usageStats;
    private final float scalingFactor = 150;

    public StallChecker(StuckDetector stuckDetector, float expectedL, float expectedR, StallAwareController controller, UsageStats usageStats){
        this.stuckDetector = stuckDetector;
        this.expectedL = expectedL * scalingFactor;
        this.expectedR = expectedR * scalingFactor;
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
