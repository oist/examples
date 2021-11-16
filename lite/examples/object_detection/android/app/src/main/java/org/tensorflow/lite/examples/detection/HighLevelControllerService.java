package org.tensorflow.lite.examples.detection;

import android.content.Intent;
import android.graphics.RectF;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

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
import jp.oist.abcvlib.core.inputs.phone.QRCodeData;

public class HighLevelControllerService extends AbcvlibService implements IOReadyListener, BatteryDataSubscriber, LifecycleOwner {

    private LifecycleRegistry lifecycleRegistry;

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
    private LowLevelController lowLevelController;
    private MatingController matingController;
    private float center = 320f * 0.2f; // As camera is offcenter, this is not exactly half of frame
    private float batteryVoltage = 0;
    private ExponentialMovingAverage batteryVoltageLP = new ExponentialMovingAverage(0.1f);
    private float minMatingVoltage = 2.9f;
    private float maxChargingVoltage = 3.0f;

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
//        lifecycleRegistry.markState(Lifecycle.State.STARTED);
        return super.onStartCommand(intent, flags, startId);
    }



    /** method for clients */
    public void onNewResults(List<Detector.Recognition> results) {
        Detector.Recognition target_puck = new Detector.Recognition(null, "puck_red", 0.0f, new RectF(0,0,0,0));
        Detector.Recognition target_robot = new Detector.Recognition(null, "robot_front", 0.0f, new RectF(0,0,0,0));;
        Log.i("Results", results.size() + " new results");
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
        Log.i("Results", "Target puck center: " + target_puck.getLocation().centerX());
        Log.i("Results", "Target robot location: " + target_robot.getLocation());
        if (lowLevelController != null){
            updateState();
            sendControl(target_robot, target_puck);
        }
    }

    public void updateState(){
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

    public void sendControl(Detector.Recognition target_robot, Detector.Recognition target_puck){
        switch (state){
            case MATING:
                if (matingController.isRunning()){
                    //Do stuff
                }else{
                    Log.i("HighLevel", "Mating Selected");
                    lowLevelController.stopController();
                    matingController.startController();
                }
                break;
            case CHARGING:
                if (lowLevelController.isRunning()){
                    if (target_puck.getBBArea() > 0){
                        float phi = (center - target_puck.getLocation().centerX()) / (320f/2f);
                        //todo hardcoded 320 here. Need to set dynamically somehow
                        float proximity = target_puck.getBBArea() / (320 * 320); // Area of bounding box relative to full image.
                        lowLevelController.setTarget(true, phi, proximity);
                    }else{
                        lowLevelController.setTarget(false, 0, 0);
                    }
                }else{
                    Log.i("HighLevel", "Charging Selected");
                    lowLevelController.startController();
                    matingController.stopController();
                }
                break;
        }
    }

    public void setCenter(float center){
        this.center = center;
    }

    @Override
    public void onIOReady(AbcvlibLooper abcvlibLooper) {
        lowLevelController = (LowLevelController) new LowLevelController().setInitDelay(0)
                .setName("lowLevelController").setThreadCount(1)
                .setThreadPriority(Thread.NORM_PRIORITY).setTimestep(100)
                .setTimeUnit(TimeUnit.MILLISECONDS);

        matingController = (MatingController) new MatingController().setInitDelay(0)
                .setName("matingController").setThreadCount(1)
                .setThreadPriority(Thread.NORM_PRIORITY).setTimestep(100)
                .setTimeUnit(TimeUnit.MILLISECONDS);

        PublisherManager publisherManager = new PublisherManager();
        new WheelData.Builder(this, publisherManager, abcvlibLooper).build().addSubscriber(lowLevelController).addSubscriber(matingController);
        new BatteryData.Builder(this, publisherManager, abcvlibLooper).build().addSubscriber(lowLevelController).addSubscriber(this);
        new QRCodeData.Builder(this, publisherManager, this).build().addSubscriber(matingController);
        publisherManager.initializePublishers();
        publisherManager.startPublishers();

        // Start your custom controller
        lowLevelController.startController();
        // Adds your custom controller to the compounding master controller.
        getOutputs().getMasterController().addController(lowLevelController);
        // Start the master controller after adding and starting any customer controllers.
        getOutputs().startMasterController();

        lifecycleRegistry = new LifecycleRegistry(this);
//        lifecycleRegistry.markState(Lifecycle.State.CREATED);
    }

    @Override
    public void onBatteryVoltageUpdate(double voltage, long timestamp) {
        this.batteryVoltage = batteryVoltageLP.average((float) voltage);
    }

    @Override
    public void onChargerVoltageUpdate(double chargerVoltage, double coilVoltage, long timestamp) {
    }
}