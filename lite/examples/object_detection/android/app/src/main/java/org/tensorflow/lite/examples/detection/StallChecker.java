package org.tensorflow.lite.examples.detection;

import android.util.Log;

public class StallChecker implements Runnable{

    private final StuckDetector stuckDetector;
    private final float expectedL;
    private final float expectedR;
    private final StallAwareController controller;

    public StallChecker(StuckDetector stuckDetector, float expectedL, float expectedR, StallAwareController controller){
        this.stuckDetector = stuckDetector;
        this.expectedL = expectedL;
        this.expectedR = expectedR;
        this.controller = controller;
    }
    @Override
    public void run() {
        // Note if checkStall returns true, it will also set stuck=true, so next control loop will execture the stuck sequence.
        if (stuckDetector.checkStall(WheelSide.LEFT, (float) expectedL, controller)){
            Log.w("StallWarning", "Robot has stalled once on left wheel");
        }
        if(stuckDetector.checkStall(WheelSide.RIGHT, (float) expectedR, controller)){
            Log.w("StallWarning", "Robot has stalled once on right wheel");

        }
    }
}