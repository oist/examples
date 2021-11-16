package org.tensorflow.lite.examples.detection;

class ExponentialMovingAverage {
    private final float alpha;
    private Float oldValue;
    public ExponentialMovingAverage(float alpha) {
        this.alpha = alpha;
    }

    public float average(float value) {
        if (oldValue == null) {
            oldValue = value;
            return value;
        }
        float newValue = oldValue + alpha * (value - oldValue);
        oldValue = newValue;
        return newValue;
    }
}
