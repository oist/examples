package org.tensorflow.lite.examples.detection;

public interface StallAwareController {
    double getCurrentWheelSpeed(WheelSide wheelSide);
    void stalledShutdownRequest();
}
