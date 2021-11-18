package org.tensorflow.lite.examples.detection;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Random;

import jp.oist.abcvlib.core.inputs.microcontroller.WheelDataSubscriber;
import jp.oist.abcvlib.core.inputs.phone.ImageData;
import jp.oist.abcvlib.core.inputs.phone.ImageDataSubscriber;
import jp.oist.abcvlib.core.inputs.phone.QRCodeDataSubscriber;
import jp.oist.abcvlib.core.outputs.AbcvlibController;
import jp.oist.abcvlib.util.QRCode;

public class MatingController extends AbcvlibController implements WheelDataSubscriber, QRCodeDataSubscriber, ImageDataSubscriber {

    private QRCodePublisher qrCodePublisher;
    private float proximity = 0;
    private float minProximity = 0.2f;

    private enum State {
        SEARCHING, DECIDING, APPROACHING, WAITING, FLEEING
    }
    private State state = State.SEARCHING;
    private boolean targetAquired = false;
    private int visibleFrameCount = 0;
    private int minVisibleFrameCount = 10; // A means to avoid random misclassified distractors
    private enum RobotFacing {
        FRONT, BACK, INVISIBLE
    }
    private RobotFacing robotFacing = RobotFacing.INVISIBLE;
    private boolean qrCodeVisible = false;
    private float staticApproachSpeed = 0.5f;
    private float randTurnSpeed = 0.5f;
    private float fleeSpeed = -0.8f;
    private float variableApproachSpeed = 0;
    private float minSpeed = 0.0f;
    private float maxSpeed = 0.5f;
    private Random rand = new Random();
    private int randomSign = rand.nextBoolean() ? 1 : -1;
    private int fleeBackupTime = 3000; // Milliseconds to backup
    private int randTurnTime = 3000; // Milliseconds to turn in a random direction after dismounting
    private int maxSearchSameSpeedCnt = 20; // Number of loops to search using same wheel speeds. Prevents fast jerky movement that makes it hard to detect pucks. Multiple by time step (100 ms here to get total time)
    private int searchSameSpeedCnt = 0;
    private float phi = 0;
    private float p_phi = 0.45f;
    private Context context;

    @Override
    public void onWheelDataUpdate(long timestamp, int wheelCountL, int wheelCountR, double wheelDistanceL, double wheelDistanceR, double wheelSpeedInstantL, double wheelSpeedInstantR, double wheelSpeedBufferedL, double wheelSpeedBufferedR, double wheelSpeedExpAvgL, double wheelSpeedExpAvgR) {

    }

    @Override
    public void onQRCodeDetected(String qrDataDecoded) {

    }

    @Override
    public void onImageDataUpdate(long timestamp, int width, int height, Bitmap bitmap, String qrDecodedData) {

    }

    public void setQrCodePublisher(QRCodePublisher publisher){
        this.qrCodePublisher = publisher;
    }

    @Override
    public void run() {
        assert qrCodePublisher != null;
        if (targetAquired){
            Log.d("MatingController", "state: " + state);
            switch (state){
                case SEARCHING:
//                    search();
                    state = State.DECIDING;
                    break;
                case DECIDING:
                    decide();
                    if (visibleFrameCount > minVisibleFrameCount){
                        state = State.APPROACHING;
                        visibleFrameCount = 0;
                    }else{
                        visibleFrameCount++;
                    }
                    break;
                case APPROACHING:
                    approach();
                    Log.d("MatingController", "prox: " + proximity);
                    if (proximity > minProximity){
                        state = State.WAITING;
                    }
                    break;
                case WAITING:
                    waiting();
                    switch (robotFacing){
                        case INVISIBLE:
                            break;
                        case BACK:
                            break;
                        case FRONT:
                            if (qrCodeVisible){
                                exchangeQRcode();
                                updateGenes();
                                state = State.FLEEING;
                            }
                            break;
                    }
                    break;
                case FLEEING:
                    break;
            }
        }else{
//            search();
        }
    }

    private void search(){
        if (searchSameSpeedCnt == 0){
            Log.v("controller", "starting new search");
            startNewSearch();
            searchSameSpeedCnt++;
        }else{
            searchSameSpeedCnt++;
        }
        if (searchSameSpeedCnt >= maxSearchSameSpeedCnt){
            searchSameSpeedCnt = 0;
        }
    }

    /**
     * Just a way of generating new random speeds on the wheels.
     */
    private void startNewSearch(){
        randomSign = rand.nextBoolean() ? 1 : -1;
        float randomSpeed = minSpeed + (float) Math.random() * (maxSpeed - minSpeed);
        float outputLeft = randomSign * randomSpeed;
        randomSign = rand.nextBoolean() ? 1 : -1;
        randomSpeed = minSpeed + (float) Math.random() * (maxSpeed - minSpeed);
        float outputRight = randomSign * randomSpeed;
        setOutput(outputLeft, outputRight);
    }

    private void decide(){
        setOutput(0, 0);
    }

    private void approach(){
        // Turn on QR code
        // generate new qrcode using the string you want to encode. Use JSONObject.toString for more complex data sets.
        Log.d("genQR", "mating controller");
        qrCodePublisher.turnOnQRCode(new int[]{1,2,3});
//        imageData.setQrcodescanning(true);

        // Todo check polarity on these turns. Could be opposite
        float outputLeft = -(phi * p_phi) + (staticApproachSpeed);
        float outputRight = (phi * p_phi) + (staticApproachSpeed);
        setOutput(outputLeft, outputRight);
    }

    private void waiting(){
        setOutput(0, 0);
    }

    private void flee(){
        // Turn off QR Code
        qrCodePublisher.turnOffQRCode();

        // Hard coded flee sequence
        try {
            setOutput(fleeSpeed, fleeSpeed);
            Log.d("controller", "fleeing at speed: " + fleeSpeed);
            Thread.sleep(fleeBackupTime);

            randomSign = rand.nextBoolean() ? 1 : -1;
            float outputLeft = randomSign * randTurnSpeed;
            setOutput(outputLeft, -outputLeft);
            Log.d("controller", "rand turn at speedL: " + outputLeft);
            Thread.sleep(randTurnTime);

            state = State.SEARCHING;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void exchangeQRcode(){}

    private void updateGenes(){}

    public void setContext(Context context) {
        this.context = context;
    }

    protected void setTarget(boolean targetAquired, float phi, float proximity){
        this.targetAquired = targetAquired;
        this.phi = phi;
        this.proximity = proximity;
    }
}
