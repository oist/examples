package org.tensorflow.lite.examples.detection;

import android.util.Log;

import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.core.inputs.microcontroller.BatteryDataSubscriber;
import jp.oist.abcvlib.core.inputs.microcontroller.WheelDataSubscriber;
import jp.oist.abcvlib.core.outputs.AbcvlibController;
import jp.oist.abcvlib.util.ProcessPriorityThreadFactory;
import jp.oist.abcvlib.util.ScheduledExecutorServiceWithException;

public class ChargeController extends AbcvlibController implements WheelDataSubscriber, BatteryDataSubscriber {

    private QRCodePublisher qrCodePublisher;

    private enum State {
        SEARCHING, MOUNTING, CHARGING, DISMOUNTING, DECIDING
    }
    private State state = State.SEARCHING;
    private float phi = 0;
    private float p_phi = 0.25f;
    private boolean targetAquired = false;
    private float proximity = 0; // from 0 to 1 where 1 is directly in front of the camera and zero being invisible.
    private float staticApproachSpeed = 0.7f;
    private float randTurnSpeed = 0.5f;
    private float dismountSpeed = -0.8f;
    private float variableApproachSpeed = 0;
    private float minSpeed = 0.0f;
    private float maxSpeed = 0.5f;
    private Random rand = new Random();
    // Random choice between -1 and 1.
    private int randomSign = rand.nextBoolean() ? 1 : -1;
    private ScheduledExecutorServiceWithException executor =
            new ScheduledExecutorServiceWithException(1,
                    new ProcessPriorityThreadFactory(Thread.NORM_PRIORITY,
                            "controllerTimer"));
    private ScheduledFuture<?> mountTimer;
    private ScheduledFuture<?> dismountTimer;
    private ScheduledFuture<?> randTurnTimer;
    private int missTime = 7000; // Milliseconds to wait before assuming you've missed the puck.
    private int dismountTime = 3000; // Milliseconds to backup
    private int randTurnTime = 3000; // Milliseconds to turn in a random direction after dismounting
    private boolean missedPuck = false;

    private int visibleFrameCount = 0;
    private int minVisibleFrameCount = 10; // A means to avoid random misclassified distractors

    private int lowCurrentFrameCount = 0;
    private int maxlowCurrentFrameCount = 50; // A means to dismounting when puck current goes low momentarily

    private float chargerCurrent = 0;
    private float coilCurrent = 0;
    private float batteryVoltage = 0;
    private float chargingCurrent = 2.0f;// todo check range of values
    private float chargedVoltage = 4.0f; //todo check range of values

    private ExponentialMovingAverage batteryVoltageLP = new ExponentialMovingAverage(0.1f);
    private ExponentialMovingAverage chargingVoltageLP = new ExponentialMovingAverage(0.1f);
    private ExponentialMovingAverage coilVoltageLP = new ExponentialMovingAverage(0.1f);


    private int maxSearchSameSpeedCnt = 20; // Number of loops to search using same wheel speeds. Prevents fast jerky movement that makes it hard to detect pucks. Multiple by time step (100 ms here to get total time)
    private int searchSameSpeedCnt = 0;

    public void run(){
        Log.d("ChargeController", "state: " + state);
        if (targetAquired){
            switch (state){
                case SEARCHING:
                    state = State.DECIDING;
                    qrCodePublisher.setFace(Face.CHARGING_DECIDING);
                    break;
                case DECIDING:
                    Log.d("controller", "visibleFrameCount: "+ visibleFrameCount);
                    if (visibleFrameCount > minVisibleFrameCount){
                        state = State.MOUNTING;
                        qrCodePublisher.setFace(Face.CHARGING_MOUNTING);
                        visibleFrameCount = 0;
                    }else{
                        qrCodePublisher.setFace(Face.CHARGING_DECIDING);
                        decide();
                        visibleFrameCount++;
                    }
                    break;
                case MOUNTING:
                    qrCodePublisher.setFace(Face.CHARGING_MOUNTING);
                    mount();
            }
        }
        else{
            switch (state){
                case DECIDING:
                    visibleFrameCount = 0;
                    state = State.SEARCHING;
                    qrCodePublisher.setFace(Face.CHARGING_SEARCHING);
                case SEARCHING:
                    search();
                    break;
                case MOUNTING:
                    mount();
                    if (mountTimer == null){
                        Log.d("controller", "starting mount timer");
                        mountTimer = executor.schedule(() -> missedPuck = true, missTime, TimeUnit.MILLISECONDS);
                    }
                    Log.d("controller", "charger pin: " + chargerCurrent);
                    Log.d("controller", "coil pin: " + chargerCurrent);
                    Log.d("controller", "missedpuck: " + missedPuck);

                    if (chargerCurrent > chargingCurrent){
                        Log.d("controller", "puck mounted, charging");
                        state = State.CHARGING;
                        qrCodePublisher.setFace(Face.CHARGING_CHARGING);
                        charge();
                    }else if (missedPuck){
                        Log.d("controller", "puck missed, dismounting");
                        mountTimer = null;
                        state = State.DISMOUNTING;
                        qrCodePublisher.setFace(Face.CHARGING_DISMOUNTING);
                    }
                    break;
                case CHARGING:
                    Log.d("controller", "charge pin: " + chargerCurrent);
                    Log.d("controller", "battery voltage: " + batteryVoltage);
                    if ((batteryVoltage > chargedVoltage)){
                        Log.d("controller", "finished charging, dismounting");
                        state = State.DISMOUNTING;
                        qrCodePublisher.setFace(Face.CHARGING_DISMOUNTING);
                    }else if (chargerCurrent < chargingCurrent){
                        if (lowCurrentFrameCount > maxlowCurrentFrameCount){
                            state = State.DISMOUNTING;
                            qrCodePublisher.setFace(Face.CHARGING_DISMOUNTING);
                            lowCurrentFrameCount = 0;
                        }else{
                            Log.d("controller", "lowCurrentFrameCnt = " + lowCurrentFrameCount);
                            lowCurrentFrameCount++;
                        }
                    }
                    break;
                case DISMOUNTING:
                    dismount();
                    break;
            }
        }
    }

    public void setQrCodePublisher(QRCodePublisher publisher){
        this.qrCodePublisher = publisher;
    }

    private void decide(){
        setOutput(0, 0);
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

    private void charge(){
        setOutput(0, 0);
    }

    private void mount(){
        float outputLeft = -(phi * p_phi) + (staticApproachSpeed);
        float outputRight = (phi * p_phi) + (staticApproachSpeed);
        setOutput(outputLeft, outputRight);
    }

    private void dismount(){
        missedPuck = false; //todo is this the best place?

        // Hard coded dismount sequence
        try {
            setOutput(dismountSpeed, dismountSpeed);
            Log.d("controller", "dismounting at speed: " + dismountSpeed);
            Thread.sleep(dismountTime);

            randomSign = rand.nextBoolean() ? 1 : -1;
            float outputLeft = randomSign * randTurnSpeed;
            setOutput(outputLeft, -outputLeft);
            Log.d("controller", "rand turn at speedL: " + outputLeft);
            Thread.sleep(randTurnTime);

            state = State.SEARCHING;
            qrCodePublisher.setFace(Face.CHARGING_SEARCHING);
        } catch (InterruptedException e) {
            e.printStackTrace();
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

    @Override
    public void onWheelDataUpdate(long timestamp, int wheelCountL, int wheelCountR, double wheelDistanceL, double wheelDistanceR, double wheelSpeedInstantL, double wheelSpeedInstantR, double wheelSpeedBufferedL, double wheelSpeedBufferedR, double wheelSpeedExpAvgL, double wheelSpeedExpAvgR) {

    }

    @Override
    public void onBatteryVoltageUpdate(double voltage, long timestamp) {
        this.batteryVoltage = batteryVoltageLP.average((float) voltage);
    }

    @Override
    public void onChargerVoltageUpdate(double chargerVoltage, double coilVoltage, long timestamp) {
        this.chargerCurrent = chargingVoltageLP.average((float) chargerVoltage);
        this.coilCurrent = coilVoltageLP.average((float) coilVoltage);
    }
}