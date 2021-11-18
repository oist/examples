package org.tensorflow.lite.examples.detection;

public interface QRCodePublisher {
    void turnOnQRCode(int[] genes);
    void turnOffQRCode();
}
