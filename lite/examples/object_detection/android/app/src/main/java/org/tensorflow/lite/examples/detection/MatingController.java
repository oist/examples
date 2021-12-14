package org.tensorflow.lite.examples.detection;

import android.content.Context;
import android.util.Log;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.core.inputs.microcontroller.BatteryDataSubscriber;
import jp.oist.abcvlib.core.inputs.microcontroller.WheelDataSubscriber;
import jp.oist.abcvlib.core.inputs.phone.QRCodeDataSubscriber;
import jp.oist.abcvlib.core.outputs.AbcvlibController;
import jp.oist.abcvlib.util.ProcessPriorityThreadFactory;
import jp.oist.abcvlib.util.ScheduledExecutorServiceWithException;

public class MatingController extends AbcvlibController implements WheelDataSubscriber, QRCodeDataSubscriber, BatteryDataSubscriber, StallAwareController {

    private QRCodePublisher qrCodePublisher;
    private float proximity = 0;
    private float minProximity = 0.3f;
    private String qrDataDecoded;
    private UsageStats usageStats;
    private long getFreeTime = 500;
    private float leftWheelMultiplier = 1;
    private float rightWheelMultiplier = 1;
    private float batteryVoltage = 0;
    private ExponentialMovingAverage batteryVoltageLP = new ExponentialMovingAverage(0.1f);
    private double wheelSpeedL = 0;
    private double wheelSpeedR = 0;
    private boolean isRunning = false;

    public void setStuckDetector(StuckDetector stuckDetector) {
        this.stuckDetector = stuckDetector;
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

    @Override
    public void stalledShutdownRequest() {

    }

    @Override
    public void zeroController(WheelSide wheelSide) {
        switch (wheelSide){
            case LEFT:
                super.setOutput(0, output.right);
                break;
            case RIGHT:
                super.setOutput(output.left, 0);
                break;
        }
    }

    private enum State {
        SEARCHING, DECIDING, APPROACHING, WAITING, FLEEING
    }
    private State state;
    private boolean targetAquired = false;
    private int visibleFrameCount = 0;
    private int minVisibleFrameCount = 10; // A means to avoid random misclassified distractors
    private enum RobotFacing {
        FRONT, BACK, INVISIBLE
    }
    private RobotFacing robotFacing = RobotFacing.INVISIBLE;
    private boolean qrCodeVisible = false;
    private float staticApproachSpeed = 0.4f;
    private float randTurnSpeed = 0.5f;
    private float fleeSpeed = -0.8f;
    private float variableApproachSpeed = 0;
    private float minSpeed = 0.3f;
    private float maxSpeed = 0.5f;
    private Random rand = new Random();
    private int randomSign = rand.nextBoolean() ? 1 : -1;
    private int fleeBackupTime = 1000; // Milliseconds to backup
    private int randTurnTime = 3000; // Milliseconds to turn in a random direction after dismounting
    private int maxSearchSameSpeedCnt = 5; // Number of loops to search using same wheel speeds. Prevents fast jerky movement that makes it hard to detect pucks. Multiple by time step (100 ms here to get total time)
    private int searchSameSpeedCnt = 0;
    private float phi = 0;
    private float p_phi = 0.25f;
    private Context context;
    private Genes genes = new Genes();
    private int speedL = 0;
    private int speedR = 0;
    private StuckDetector stuckDetector;
    private ScheduledExecutorServiceWithException executor =
            new ScheduledExecutorServiceWithException(1,
                    new ProcessPriorityThreadFactory(Thread.MAX_PRIORITY,
                            "stallEvaluation"));
    private long stallDelay; // ms to wait to evaluate if stuck. Should be less than control loop time.

    public void setStallDelay(long stallDelay) {
        this.stallDelay = stallDelay;
    }

    @Override
    public void onWheelDataUpdate(long timestamp, int wheelCountL, int wheelCountR, double wheelDistanceL, double wheelDistanceR, double wheelSpeedInstantL, double wheelSpeedInstantR, double wheelSpeedBufferedL, double wheelSpeedBufferedR, double wheelSpeedExpAvgL, double wheelSpeedExpAvgR) {
        this.speedL = speedL;
        this.speedR = speedR;
    }

    @Override
    public void onQRCodeDetected(String qrDataDecoded) {
        qrCodeVisible = true;
//        Log.d("MatingController", "recvd gene string:" + qrDataDecoded);
        this.qrDataDecoded = qrDataDecoded;
    }

    public void setQrCodePublisher(QRCodePublisher publisher){
        this.qrCodePublisher = publisher;
    }

    public void setUsageStats(UsageStats usageStats){
        this.usageStats = usageStats;
    }

    public void startController() {
        genes.playGenes();
        state = State.SEARCHING;
        flipToArms();
        stuckDetector.startTimer(15000);
        usageStats.onStateChange("Mating_" + state.name());
        qrCodePublisher.setFace(Face.MATE_SEARCHING);
        this.isRunning = true;
    }


    public void stopController() {
        genes.stopPlayingGenes();
        setOutput(0,0);
        this.isRunning = false;
    }

    @Override
    public synchronized boolean isRunning() {
        return this.isRunning;
    }

    @Override
    public void run() {
        Log.d("MatingController", "state: " + state);
        assert qrCodePublisher != null;
        if (stuckDetector.isStuck()){
            // do some predefined aggressive action
            getFree();
            state = State.SEARCHING;
            flipToArms();
            stuckDetector.startTimer(15000);
            usageStats.onStateChange("Mating_" + state.name());
            qrCodePublisher.setFace(Face.MATE_SEARCHING);
        }
        if (targetAquired){
            Log.d("MatingController", "Taget aquired");
            switch (state){
                case SEARCHING:
                    // Turn off QR Code
                    qrCodePublisher.turnOffQRCode();
                    search();
                    state = State.DECIDING;
                    stuckDetector.startTimer(10000);
                    usageStats.onStateChange("Mating_" + state.name());
                    qrCodePublisher.setFace(Face.MATE_DECIDING);
                    break;
                case DECIDING:
                    // Turn off QR Code
                    qrCodePublisher.turnOffQRCode();
                    decide();
                    if (visibleFrameCount > minVisibleFrameCount){
                        state = State.APPROACHING;
                        stuckDetector.startTimer();
                        usageStats.onStateChange("Mating_" + state.name());
                        qrCodePublisher.setFace(Face.MATE_APPROACHING);
                        visibleFrameCount = 0;
                    }else{
                        visibleFrameCount++;
                    }
                    break;
                case APPROACHING:
                    qrCodePublisher.turnOnQRCode(genes.genesToString());
                    approach();
                    Log.d("MatingController", "prox: " + proximity);
                    if (proximity > minProximity){
                        state = State.WAITING;
                        stuckDetector.startTimer(20000);
                        usageStats.onStateChange("Mating_" + state.name());
                        qrCodePublisher.setFace(Face.MATE_WAITING);
                    }
                    break;
                case WAITING:
                    Log.d("MatingController", "Turning on QR Code");
                    qrCodePublisher.turnOnQRCode(genes.genesToString());
                    Log.d("MatingController", "Target Aquired Waiting");
                    waiting();
                    switch (robotFacing){
                        case INVISIBLE:
                            Log.d("MatingController", "Mate invisible");
                            break;
                        case BACK:
                            Log.d("MatingController", "Mate back visible");
                            break;
                        case FRONT:
                            if (qrCodeVisible){
                                Log.d("MatingController", "QRcode visible");
                                genes.exchangeGenes(qrDataDecoded);
                                state = State.FLEEING;
                                stuckDetector.startTimer();
                                usageStats.onStateChange("Mating_" + state.name());
                                qrCodePublisher.setFace(Face.MATE_FLEEING);
                            }
                            break;
                    }
                    Log.d("MatingController", "End of Waiting logic");
                    break;
                case FLEEING:
                    Log.d("MatingController", "Fleeing");
                    // Turn off QR Code
                    qrCodeVisible = false;
                    qrCodePublisher.turnOffQRCode();
                    flee();
                    state = State.SEARCHING;
                    flipToArms();
                    stuckDetector.startTimer();
                    usageStats.onStateChange("Mating_" + state.name());
                    qrCodePublisher.setFace(Face.MATE_SEARCHING);
                    break;
            }
        }else{
            Log.d("MatingController", "Taget not aquired");
            switch (state){
                case SEARCHING:
                    search();
                    break;
                case APPROACHING:
                case WAITING:
                case FLEEING:
                case DECIDING:
                    state = State.SEARCHING;
                    stuckDetector.startTimer(); //todo this restarts the timer every loop rendering it meaningless.
                    usageStats.onStateChange("Mating_" + state.name());
                    qrCodePublisher.setFace(Face.MATE_SEARCHING);
                    // Turn off QR Code
                    qrCodePublisher.turnOffQRCode();
                    break;
            }
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

    private void flipToArms(){
        // Hard coded flip to arms sequence
        float[] slowGrad = new float[]{-1.0f, -0.9f, -0.8f, -0.7f, -0.6f, -0.5f, -0.4f, -0.3f};
        try {
            setOutput(1, 1);
            Thread.sleep(300);
            for (float speed:slowGrad){
                setOutput(speed,speed);
                Log.d("MateController", "Flip Sequence");
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void search(){
        if (searchSameSpeedCnt == 0){
            Log.v("controller", "starting new search");
            startNewSearch();
            searchSameSpeedCnt++;
        }else{
            searchSameSpeedCnt++;
            Log.d("CheckStallEval", "Creating Stall Checker from mating search() on thread:" + Thread.currentThread().getName());
            executor.schedule(new StallChecker(stuckDetector, output.left,
                            output.right,
                            this, usageStats, batteryVoltage),
                    stallDelay, TimeUnit.MILLISECONDS);
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
        // Todo check polarity on these turns. Could be opposite
        float outputLeft = -(phi * p_phi) + (staticApproachSpeed);
        float outputRight = (phi * p_phi) + (staticApproachSpeed);
        setOutput(outputLeft, outputRight);
    }

    private void waiting(){
        setOutput(0, 0);
    }

    private void flee(){
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
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void setContext(Context context) {
        this.context = context;
    }

    protected void setTarget(boolean targetAquired, float phi, float proximity, String robot_face, float leftWheelMultiplier, float rightWheelMultiplier){
        this.targetAquired = targetAquired;
        this.phi = phi;
        this.proximity = proximity;
        this.robotFacing = interpretFace(robot_face);
        this.leftWheelMultiplier = leftWheelMultiplier;
        this.rightWheelMultiplier = rightWheelMultiplier;
    }

    @Override
    protected synchronized void setOutput(float left, float right) {
        // Scale output based on battery voltage so wheels to slow down too much. Normalized to full battery around 3.2.
        // wheelMultipliers are an attempt to compensate for motor wear
        Log.d("Multiplier", "leftWheelMultiplier: " + leftWheelMultiplier);
        Log.d("Multiplier", "rightWheelMultiplier: " + rightWheelMultiplier);
        float leftScalingFactor = leftWheelMultiplier / (this.batteryVoltage / 3.2f);
        float rightScalingFactor = leftWheelMultiplier / (this.batteryVoltage / 3.2f);
        left = left * leftScalingFactor;
        right = right * rightScalingFactor;
        float finalLeft = left;
        float finalRight = right;
        Log.d("CheckStallEval", "Creating Stall Checker from mating setOutputon thread:" + Thread.currentThread().getName());
        executor.schedule(new StallChecker(stuckDetector, finalLeft, finalRight, this, usageStats, batteryVoltage),
                stallDelay, TimeUnit.MILLISECONDS);
        usageStats.onSetOutput(left, right);
        super.setOutput(left, right);
    }

    @Override
    public void onBatteryVoltageUpdate(double voltage, long timestamp) {
        this.batteryVoltage = batteryVoltageLP.average((float) voltage);
    }

    @Override
    public void onChargerVoltageUpdate(double chargerVoltage, double coilVoltage, long timestamp) {

    }

    private RobotFacing interpretFace(String title){
        switch (title){
            case "robot_front":
                return RobotFacing.FRONT;
            case "robot_back":
                return RobotFacing.BACK;
            default:
                return RobotFacing.INVISIBLE;
        }
    }
}
