package org.tensorflow.lite.examples.detection;

import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.Random;

import jp.oist.abcvlib.core.inputs.microcontroller.WheelDataSubscriber;
import jp.oist.abcvlib.core.inputs.phone.QRCodeDataSubscriber;
import jp.oist.abcvlib.core.outputs.AbcvlibController;

public class MatingController extends AbcvlibController implements WheelDataSubscriber, QRCodeDataSubscriber {

    private QRCodePublisher qrCodePublisher;
    private float proximity = 0;
    private float minProximity = 0.2f;
    private String qrDataDecoded;

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
    private int[] genes = new int[10];
    private int g = 0;
    private int[] mateGenes = new int[10];
    private Random random = new Random();
    private int maxMutation = 4;

    @Override
    public void onWheelDataUpdate(long timestamp, int wheelCountL, int wheelCountR, double wheelDistanceL, double wheelDistanceR, double wheelSpeedInstantL, double wheelSpeedInstantR, double wheelSpeedBufferedL, double wheelSpeedBufferedR, double wheelSpeedExpAvgL, double wheelSpeedExpAvgR) {

    }

    @Override
    public void onQRCodeDetected(String qrDataDecoded) {
        qrCodeVisible = true;
//        Log.d("MatingController", "recvd gene string:" + qrDataDecoded);
        this.qrDataDecoded = qrDataDecoded;
        mateGenes = Arrays.stream(qrDataDecoded.substring(1, qrDataDecoded.length() - 1).split(","))
                .map(String::trim).mapToInt(Integer::parseInt).toArray();
    }

    public void setQrCodePublisher(QRCodePublisher publisher){
        this.qrCodePublisher = publisher;
    }

    @Override
    public void run() {
        Log.d("MatingController", "state: " + state);
        assert qrCodePublisher != null;
        if (targetAquired){
            switch (state){
                case SEARCHING:
                    search();
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
                                exchangeGenes();
                                state = State.FLEEING;
                            }
                            break;
                    }
                    break;
                case FLEEING:
                    flee();
                    break;
            }
        }else{
            state = State.SEARCHING;
            search();
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
        Log.d("MatingController", "int genes:" + Arrays.toString(genes));
        Log.d("MatingController", "qrCodePublisher :" + qrCodePublisher.toString());

        qrCodePublisher.turnOnQRCode(genes);

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

    private void exchangeGenes(){
        Log.d("MatingController", "Exhcnaged Genes");
        Log.d("MatingController", "My Genes: " + Arrays.toString(genes));
        Log.d("MatingController", "Mate's Genes: " + Arrays.toString(mateGenes));
        g++;
        if (g < genes.length - 1){
            int mutation = rand.nextInt(maxMutation);
            this.genes[g] = genes[g-1] + mateGenes[g-1] + mutation;
            // Clear to make sure same data not repeatedly used
//            this.qrDataDecoded = null;
        }
        // Just trying to prevent only one robot exchanging
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void setContext(Context context) {
        this.context = context;
    }

    protected void setTarget(boolean targetAquired, float phi, float proximity, String robot_face){
        this.targetAquired = targetAquired;
        this.phi = phi;
        this.proximity = proximity;
        this.robotFacing = interpretFace(robot_face);
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
