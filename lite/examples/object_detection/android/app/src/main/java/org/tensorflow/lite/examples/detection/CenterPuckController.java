package org.tensorflow.lite.examples.detection;

import android.util.Log;

import java.util.Random;

import jp.oist.abcvlib.core.outputs.AbcvlibController;

public class CenterPuckController extends AbcvlibController {

    private float phi = 0;
    private double CENTER_COL;
    private float p_phi = 0.35f;
    private boolean targetAquired = false;
    private float proximity = 0; // from 0 to 1 where 1 is directly in front of the camera and zero being invisible.
    int noTargetInFrameCounter = 0;
    int targetInFrameCounter = 0;
    int backingUpFrameCounter = 0;
    long noTargetInFrameStartTime;
    long backingUpStartTime;
    long searchingFrameCounter = 0;
    long searchingStartTime;
    float staticApproachSpeed = 0.5f;
    float variableApproachSpeed = 0;
    int searchSpeed = 35;
    Random rand = new Random();
    // Random choice between -1 and 1.
    int randomSign = rand.nextBoolean() ? 1 : -1;
    double approachTime = 3.5e9;
    // How long to backup after landing on puck in nanoseconds.
    double backupTime = 1e9;
    // How long to search after backing up in nanoseconds.
    double searchTime = 5e9;
    // An attempt to stop the robot from flipping to the tail before searching
    double slowDownTime = searchTime * 0.1;

    public void run(){

        if (targetAquired){

            // Todo check polarity on these turns. Could be opposite
            float outputLeft = -(phi * p_phi) + (staticApproachSpeed + (variableApproachSpeed * proximity));
            float outputRight = (phi * p_phi) + (staticApproachSpeed + (variableApproachSpeed * proximity));
            setOutput(outputLeft, outputRight);

            Log.d("centerPuck", "CenterPuckController left:" + output.left + " right:" + output.right);

        }
        else{
            Log.v("centerPuck", "No pucks in sight");

            // Start time stamp when no puck state starts
            if (noTargetInFrameCounter == 0) {
                noTargetInFrameStartTime = System.nanoTime();
            }

            noTargetInFrameCounter++;
            targetInFrameCounter = 0;

            Log.v("centerPuck", "No pucks. Prior to timing logic");

            // If no blob in frame for longer than approachTime...
            if ((System.nanoTime() - noTargetInFrameStartTime) > approachTime){
                // If just starting to backup
                if (backingUpFrameCounter == 0){
                    backingUpStartTime = System.nanoTime();
                    Log.v("centerPuck", "No pucks. Setting backingupStartTime = 0");
                    backingUpFrameCounter++;
                    // Random choice between -1 and 1.
                    randomSign = rand.nextBoolean() ? 1 : -1;
                }
                // If backing up for more than backupTime.
                else if (System.nanoTime() - backingUpStartTime > backupTime) {
                    // Just starting to search
                    if (searchingFrameCounter == 0) {
                        searchingStartTime = System.nanoTime();
                        searchingFrameCounter++;
                    }
                    // Slow down to prevent flipping to tail
                    else if (System.nanoTime() - searchingStartTime < slowDownTime) {
                        setOutput((float)(-staticApproachSpeed * 0.9), (float)(-staticApproachSpeed * 0.9));
                        searchingFrameCounter++;
                    }
                    // Searching for more than ignoreTurnTime but less than searchTime? Continue to search (turn).
                    else {
                        setOutput(searchSpeed * randomSign, -searchSpeed * randomSign);
                        searchingFrameCounter++;
                    }
//                        // Searching for more than searchTime? Try backing up again.
//                        else{
//                            backingUpFrameCounter = 0;
//                            searchingFrameCounter = 0;
//                            ignoreBlobs = false;
//                        }
                }
                // If backing up less than backupTime
                else{
                    float outputLeft = -2.0f * staticApproachSpeed;
                    float outputRight = outputLeft;
                    setOutput(outputLeft, outputRight);
                    backingUpFrameCounter++;
                }
            }
            // blob has not been in frame for less than approachtime. Continue Forward.
            else{
                float outputLeft = staticApproachSpeed;
                float outputRight = staticApproachSpeed;
                setOutput(outputLeft, outputRight);
            }
        }
    }

    /**
     * Phi is not an actual measure of degrees or radians, but relative to the pixel density from the camera
     * For example, if the centroid of interest is at the center of the vertical plane, phi = 0.
     * If the centroid of interest if at the leftmost part of the screen, phi = -1. Likewise, if
     * at the rightmost part of the screen, then phi = 1. As the actual angle depends on the optics
     * of the camera, this is just a first attempt, but OpenCV may have more robust/accuarte 3D motionSensors
     * metrics.
     */
    protected void setTarget(boolean targetAquired, float phi, float proximity){
        this.targetAquired = targetAquired;
        this.phi = phi;
        this.proximity = proximity;
    }

}
