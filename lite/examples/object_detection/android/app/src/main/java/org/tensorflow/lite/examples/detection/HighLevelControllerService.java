package org.tensorflow.lite.examples.detection;

import android.content.Intent;
import android.graphics.RectF;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.tensorflow.lite.examples.detection.tflite.Detector;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.core.AbcvlibLooper;
import jp.oist.abcvlib.core.AbcvlibService;
import jp.oist.abcvlib.core.IOReadyListener;
import jp.oist.abcvlib.core.inputs.PublisherManager;
import jp.oist.abcvlib.core.inputs.microcontroller.BatteryData;
import jp.oist.abcvlib.core.inputs.microcontroller.BatteryDataSubscriber;
import jp.oist.abcvlib.core.inputs.microcontroller.WheelData;
import jp.oist.abcvlib.core.inputs.phone.ImageData;
import jp.oist.abcvlib.core.inputs.phone.QRCodeData;

public class HighLevelControllerService extends AbcvlibService implements IOReadyListener, BatteryDataSubscriber, LifecycleOwner {

    private LifecycleRegistry lifecycleRegistry;
    private QRCodePublisher qrCodePublisher;

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }

    private enum State {
        CHARGING, MATING
    }
    private HighLevelControllerService.State state = State.CHARGING;

    private float minimumConfidence = 0.6f;
    private ChargeController chargeController;
    private MatingController matingController;
    private float center = 320f * 0.2f; // As camera is offcenter, this is not exactly half of frame
    private float batteryVoltage = 0;
    private ExponentialMovingAverage batteryVoltageLP = new ExponentialMovingAverage(0.1f);
    private float minMatingVoltage = 2.8f;
    private float maxChargingVoltage = 2.9f;
    private ImageData imageData;
    private PublisherManager publisherManager;
    private WheelData wheelData;
    private BatteryData batteryData;
    private QRCodeData qrCodeData;

    // Binder given to clients
    private final IBinder binder = new LocalBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        HighLevelControllerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return HighLevelControllerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setIoReadyListener(this);
        return super.onStartCommand(intent, flags, startId);
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    public void onCreate() {
        super.onCreate();
        lifecycleRegistry = new LifecycleRegistry(this);
        lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
        lifecycleRegistry.setCurrentState(Lifecycle.State.STARTED);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    /** method for clients */
    public void onNewResults(List<Detector.Recognition> results) {
        Detector.Recognition target_puck = new Detector.Recognition(null, "puck_red", 0.0f, new RectF(0,0,0,0));
        Detector.Recognition target_robot = new Detector.Recognition(null, "robot_front", 0.0f, new RectF(0,0,0,0));;
        for (final Detector.Recognition result : results) {
            if (result.getConfidence() > minimumConfidence){
                float boundingBoxArea = result.getLocation().height() * result.getLocation().height();
                // Update target robot to be the one with the largest bounding box (closest)
                if ((result.getTitle().equals("robot_front") || result.getTitle().equals("robot_back")) && (result.getBBArea() > target_robot.getBBArea())){
                    target_robot = result;
                }
                else if (result.getTitle().equals("puck_red") && (result.getBBArea() > target_puck.getBBArea())){
                    target_puck = result;
                }
            }
        }
        if (chargeController != null){
            updateState();
            sendControl(target_robot, target_puck);
        }
    }

    public void updateState(){
        Log.v("HighLevel", "state:" + state);
        switch (state){
            case MATING:
                if (batteryVoltage < minMatingVoltage){
                    state = State.CHARGING;
                }
                break;
            case CHARGING:
                if (batteryVoltage > maxChargingVoltage){
                    state = State.MATING;
                }
                break;
        }
    }

    public void setQRCodePublisher(QRCodePublisher publisher){
        this.qrCodePublisher = publisher;
    }

    public void sendControl(Detector.Recognition target_robot, Detector.Recognition target_puck){
        switch (state){
            case MATING:
                if (matingController.isRunning()){
                    //Do stuff
                    qrCodePublisher.turnOnQRCode(new int[]{1,2,3});
//                    matingController.turnOnQRCode(new int[]{1,2,3});
                }else{
                    Log.i("HighLevel", "Mating Selected");
                    if (chargeController.isRunning()){
                        chargeController.stopController();
                    }
                    matingController.startController();
                }
                break;
            case CHARGING:
                if (chargeController.isRunning()){
                    Log.i("HighLevel", "Continuing to Charge");
                    if (target_puck.getBBArea() > 0){
                        float phi = (center - target_puck.getLocation().centerX()) / (320f/2f);
                        //todo hardcoded 320 here. Need to set dynamically somehow
                        float proximity = target_puck.getBBArea() / (320 * 320); // Area of bounding box relative to full image.
                        chargeController.setTarget(true, phi, proximity);
                    }else{
                        chargeController.setTarget(false, 0, 0);
                    }
                }else{
                    Log.i("HighLevel", "Charging Selected");
                    chargeController.startController();
                    if (matingController.isRunning()){
                        matingController.stopController();
                    }
                }
                break;
        }
    }

    public void setCenter(float center){
        this.center = center;
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    public void onIOReady(AbcvlibLooper abcvlibLooper) {
        chargeController = (ChargeController) new ChargeController().setInitDelay(0)
                .setName("chargeController").setThreadCount(1)
                .setThreadPriority(Thread.NORM_PRIORITY).setTimestep(100)
                .setTimeUnit(TimeUnit.MILLISECONDS);

        matingController = (MatingController) new MatingController().setInitDelay(0)
                .setName("matingController").setThreadCount(1)
                .setThreadPriority(Thread.NORM_PRIORITY).setTimestep(100)
                .setTimeUnit(TimeUnit.MILLISECONDS);

        matingController.setContext(this);

        publisherManager = new PublisherManager();
        wheelData = new WheelData.Builder(this, publisherManager, abcvlibLooper).build();
        wheelData.addSubscriber(chargeController).addSubscriber(matingController);
        batteryData = new BatteryData.Builder(this, publisherManager, abcvlibLooper).build();
        batteryData.addSubscriber(chargeController).addSubscriber(this);
        qrCodeData = new QRCodeData.Builder(this, publisherManager, this).build();
        qrCodeData.addSubscriber(matingController);
//        imageData = new ImageData.Builder(this, publisherManager, this)
//                .build();
//        imageData.addSubscriber(matingController);

        Log.d("race", "init publishers start");
        publisherManager.initializePublishers();
        Log.d("race", "init publishers end");
        Log.d("race", "start publishers start");
        publisherManager.startPublishers();
        Log.d("race", "start publishers end");

        // Start your custom controller
        chargeController.startController();
        matingController.startController();
        // Adds your custom controller to the compounding master controller.
        getOutputs().getMasterController().addController(chargeController);
        // Adds your custom controller to the compounding master controller.
        getOutputs().getMasterController().addController(matingController);
        // Start the master controller after adding and starting any customer controllers.
        getOutputs().startMasterController();

    }

    @Override
    public void onBatteryVoltageUpdate(double voltage, long timestamp) {
//        Log.v("race", "batt:" + voltage);
        this.batteryVoltage = batteryVoltageLP.average((float) voltage);
    }

    @Override
    public void onChargerVoltageUpdate(double chargerVoltage, double coilVoltage, long timestamp) {
    }
}