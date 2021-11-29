package org.tensorflow.lite.examples.detection;

public interface QRCodePublisher {
    void turnOnQRCode(String genes);
    void turnOffQRCode();
    void setFace(Face face);
}
