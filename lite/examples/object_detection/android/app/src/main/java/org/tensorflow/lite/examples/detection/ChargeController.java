package org.tensorflow.lite.examples.detection;

import android.util.Log;
import android.widget.Toast;

import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.core.inputs.microcontroller.BatteryDataSubscriber;
import jp.oist.abcvlib.core.inputs.microcontroller.WheelDataSubscriber;
import jp.oist.abcvlib.core.outputs.AbcvlibController;
import jp.oist.abcvlib.util.ProcessPriorityThreadFactory;
import jp.oist.abcvlib.util.ScheduledExecutorServiceWithException;

public class ChargeController extends AbcvlibController implements WheelDataSubscriber, BatteryDataSubscriber, StallAwareController {

    private QRCodePublisher qrCodePublisher;
    private UsageStats usageStats;
    private long getFreeTime = 500;
    private float leftWheelMultiplier = 1;
    private float rightWheelMultiplier = 1;
    private long stallDelay; // ms to wait to evaluate if stuck. Should be less than control loop time.
    private boolean stallCheck = true;
    private StuckDetector stuckDetector;
    private double wheelSpeedL = 0;
    private double wheelSpeedR = 0;

    public void setStuckDetector(StuckDetector stuckDetector) {
        this.stuckDetector = stuckDetector;
    }

    public void setStallDelay(long stallDelay) {
        this.stallDelay = stallDelay;
    }

    @Override
    public double getCurrentWheelSpeed(WheelSide wheelSide) {
        switch (wheelSide){
            case LEFT:
                return this.wheelSpeedL;
            case RIGHT:
                return this.wheelSpeedR;
            default:
                return 401;
        }
    }

    private enum State {
        SEARCHING, MOUNTING, CHARGING, DISMOUNTING, DECIDING
    }
    private State state;
    private float phi = 0;
    private float p_phi = 0.25f;
    private boolean targetAquired = false;
    private float proximity = 0; // from 0 to 1 where 1 is directly in front of the camera and zero being invisible.
    private float staticApproachSpeed = 0.6f;
    private float randTurnSpeed = 0.5f;
    private float dismountSpeed = -0.8f;
    private float variableApproachSpeed = 0;
    private float minSpeed = 0.4f;
    private float maxSpeed = 0.6f;
    private Random rand = new Random();
    // Random choice between -1 and 1.
    private int randomSign = rand.nextBoolean() ? 1 : -1;
    private ScheduledExecutorServiceWithException executor =
            new ScheduledExecutorServiceWithException(1,
                    new ProcessPriorityThreadFactory(Thread.MAX_PRIORITY,
                            "stallEvaluation"));
    private ScheduledFuture<?> mountTimer;
    private ScheduledFuture<?> dismountTimer;
    private ScheduledFuture<?> randTurnTimer;
    private int dismountTime = 2000; // Milliseconds to backup
    private int randTurnTime = 3000; // Milliseconds to turn in a random direction after dismounting
    private boolean missedPuck = false;

    private int visibleFrameCount = 0;
    private int minVisibleFrameCount = 10; // A means to avoid random misclassified distractors

    private int lowCurrentFrameCount = 0;
    private int maxlowCurrentFrameCount = 15; // A means to dismounting when puck current goes low momentarily

    private float chargerCurrent = 0;
    private float coilCurrent = 0;
    private float batteryVoltage = 0;
    private float chargingCurrent = 2.0f;// todo check range of values
    private float chargedVoltage = 4.0f; //todo check range of values

    private ExponentialMovingAverage batteryVoltageLP = new ExponentialMovingAverage(0.1f);
    private ExponentialMovingAverage chargingVoltageLP = new ExponentialMovingAverage(0.1f);
    private ExponentialMovingAverage coilVoltageLP = new ExponentialMovingAverage(0.1f);

    private boolean stalledShutdown = false;


    private int maxSearchSameSpeedCnt = 20; // Number of loops to search using same wheel speeds. Prevents fast jerky movement that makes it hard to detect pucks. Multiple by time step (100 ms here to get total time)
    private int searchSameSpeedCnt = 0;

    @Override
    public void startController() {
        state = State.SEARCHING;
        flipToArms();
        stuckDetector.startTimer(15000);
        usageStats.onStateChange("Charging_" + state.name());
        qrCodePublisher.setFace(Face.CHARGING_SEARCHING);
        super.startController();
    }

    public void run(){
        if (stuckDetector.isStuck()){
            Log.d("ChargeController", "I'm Stuck! ChargeController Logic");
            getFree();
            state = State.SEARCHING;
            flipToArms();
            stuckDetector.startTimer(15000);
            usageStats.onStateChange("Charging_" + state.name());
            qrCodePublisher.setFace(Face.CHARGING_SEARCHING);
        }
        Log.d("ChargeController", "state: " + state);
        if (targetAquired){
            switch (state){
                case SEARCHING:
                    state = State.DECIDING;
                    stuckDetector.startTimer(5000);
                    usageStats.onStateChange("Charging_" + state.name());
                    qrCodePublisher.setFace(Face.CHARGING_DECIDING);
                    break;
                case DECIDING:
                    Log.d("controller", "visibleFrameCount: "+ visibleFrameCount);
                    if (visibleFrameCount > minVisibleFrameCount){
                        state = State.MOUNTING;
                        stuckDetector.startTimer(15000);
                        usageStats.onStateChange("Charging_" + state.name());
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
                    flipToArms();
                    stuckDetector.startTimer(15000);
                    usageStats.onStateChange("Charging_" + state.name());
                    qrCodePublisher.setFace(Face.CHARGING_SEARCHING);
                case SEARCHING:
                    search();
                    break;
                case MOUNTING:
                    mount();
                    if (chargerCurrent > chargingCurrent){
                        Log.d("controller", "puck mounted, charging");
                        state = State.CHARGING;
                        stuckDetector.startTimer(70000);
                        usageStats.onStateChange("Charging_" + state.name());
                        qrCodePublisher.setFace(Face.CHARGING_CHARGING);
                        charge();
                    }
                    break;
                case CHARGING:
                    Log.d("controller", "charge pin: " + chargerCurrent);
                    Log.d("controller", "battery voltage: " + batteryVoltage);
                    if (chargerCurrent < chargingCurrent){
                        if (lowCurrentFrameCount > maxlowCurrentFrameCount){
                            state = State.DISMOUNTING;
                            stuckDetector.startTimer(5000);
                            usageStats.onStateChange("Charging_" + state.name());
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
                    flipToArms();
                    state = State.SEARCHING;
                    flipToArms();
                    stuckDetector.startTimer(15000);
                    usageStats.onStateChange("Charging_" + state.name());
                    qrCodePublisher.setFace(Face.CHARGING_SEARCHING);
                    break;
            }
        }
    }

    public void setQrCodePublisher(QRCodePublisher publisher){
        this.qrCodePublisher = publisher;
    }

    public void setUsageStats(UsageStats usageStats){
        this.usageStats = usageStats;
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

    private void getFree(){
        // Hard coded getFree sequence
        try {
            //1st aggressive motion
            randomSign = rand.nextBoolean() ? 1 : -1;
            float outputLeft = (float) randomSign;
            randomSign = rand.nextBoolean() ? 1 : -1;
            float outputRight = (float) randomSign;
            setOutput(outputLeft, outputRight);
            Thread.sleep(getFreeTime);
            //2nd aggressive motion
            randomSign = rand.nextBoolean() ? 1 : -1;
            outputLeft = (float) randomSign;
            randomSign = rand.nextBoolean() ? 1 : -1;
            outputRight = (float) randomSign;
            setOutput(outputLeft, outputRight);
            Thread.sleep(getFreeTime);
            //3rd aggressive motion
            randomSign = rand.nextBoolean() ? 1 : -1;
            outputLeft = (float) randomSign;
            randomSign = rand.nextBoolean() ? 1 : -1;
            outputRight = (float) randomSign;
            setOutput(outputLeft, outputRight);
            Thread.sleep(getFreeTime);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Just a way of generating new random speeds on the wheels.
     */
    private void startNewSearch(){
//        float maxSpeed = this.maxSpeed * batteryVoltage;
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

    private void flipToArms(){
        // Hard coded flip to arms sequence
        float[] slowGrad = new float[]{-1.0f, -0.9f, -0.8f, -0.7f, -0.6f, -0.5f, -0.4f, -0.3f};
        try {
            setOutput(1, 1);
            Thread.sleep(300);
            for (float speed:slowGrad){
                setOutput(speed,speed);
                Log.d("ChargeController", "Flip Sequence");
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void mount(){
        float outputLeft = -(phi * p_phi) + (staticApproachSpeed);
        float outputRight = (phi * p_phi) + (staticApproachSpeed);
        Log.d("ChargeController", "phi: " + phi);
        Log.d("ChargeController", "output: " + outputLeft);
        setOutput(outputLeft, outputRight);
    }

    private void dismount(){
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
    protected void setTarget(boolean targetAquired, float phi, float proximity, float leftWheelMultiplier, float rightWheelMultiplier){
        this.targetAquired = targetAquired;
        this.phi = phi;
        this.proximity = proximity;
        this.leftWheelMultiplier = leftWheelMultiplier;
        this.rightWheelMultiplier = rightWheelMultiplier;
    }


    @Override
    protected synchronized void setOutput(float left, float right) {
        // Scale output based on battery voltage so wheels to slow down too much. Normalized to full battery around 3.3.
        // wheelMultipliers are an attempt to compensate for motor wear
        Log.d("Multiplier", "leftWheelMultiplier: " + leftWheelMultiplier);
        Log.d("Multiplier", "rightWheelMultiplier: " + rightWheelMultiplier);
        left = (left / (this.batteryVoltage / 3.3f)) * leftWheelMultiplier;
        right = (right / (this.batteryVoltage / 3.3f)) * rightWheelMultiplier;
        float finalLeft = left;
        float finalRight = right;
        executor.schedule(new StallChecker(stuckDetector, left, right, this, usageStats),
                stallDelay, TimeUnit.MILLISECONDS);
        usageStats.onSetOutput(left, right);
        super.setOutput(left, right);
    }

    public void stalledShutdownRequest(){
    }

    @Override
    public void onWheelDataUpdate(long timestamp, int wheelCountL, int wheelCountR,
                                  double wheelDistanceL, double wheelDistanceR,
                                  double wheelSpeedInstantL, double wheelSpeedInstantR,
                                  double wheelSpeedBufferedL, double wheelSpeedBufferedR,
                                  double wheelSpeedExpAvgL, double wheelSpeedExpAvgR) {
        this.wheelSpeedL = wheelSpeedBufferedL;
        this.wheelSpeedR = wheelSpeedBufferedR;
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