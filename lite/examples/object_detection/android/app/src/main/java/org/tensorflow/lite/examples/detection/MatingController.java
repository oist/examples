package org.tensorflow.lite.examples.detection;

import android.util.Log;

import jp.oist.abcvlib.core.inputs.microcontroller.WheelDataSubscriber;
import jp.oist.abcvlib.core.inputs.phone.QRCodeDataSubscriber;
import jp.oist.abcvlib.core.outputs.AbcvlibController;

public class MatingController extends AbcvlibController implements WheelDataSubscriber, QRCodeDataSubscriber {

    @Override
    public void onWheelDataUpdate(long timestamp, int wheelCountL, int wheelCountR, double wheelDistanceL, double wheelDistanceR, double wheelSpeedInstantL, double wheelSpeedInstantR, double wheelSpeedBufferedL, double wheelSpeedBufferedR, double wheelSpeedExpAvgL, double wheelSpeedExpAvgR) {

    }

    @Override
    public void onQRCodeDetected(String qrDataDecoded) {

    }

    @Override
    public void run() {
        Log.d("MatingController", "I'm Mating!");
    }
}
