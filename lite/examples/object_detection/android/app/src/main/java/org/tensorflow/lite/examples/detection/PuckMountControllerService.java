package org.tensorflow.lite.examples.detection;

import android.content.Intent;
import android.graphics.RectF;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.tensorflow.lite.examples.detection.tflite.Detector;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.core.AbcvlibLooper;
import jp.oist.abcvlib.core.AbcvlibService;
import jp.oist.abcvlib.core.IOReadyListener;
import jp.oist.abcvlib.core.inputs.PublisherManager;
import jp.oist.abcvlib.core.inputs.microcontroller.BatteryData;
import jp.oist.abcvlib.core.inputs.microcontroller.WheelData;

public class PuckMountControllerService extends AbcvlibService implements IOReadyListener{
    private float minimumConfidence = 0.6f;
    private CenterPuckController centerPuckController;
    private float center = 320f / 2f;

    // Binder given to clients
    private final IBinder binder = new LocalBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        PuckMountControllerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return PuckMountControllerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
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
        // Determine which target to approach
        // if puck
        if (centerPuckController != null){
            if (target_puck.getBBArea() > 0){
                float phi = (center - target_puck.getLocation().centerX()) / center;
                //todo hardcoded 320 here. Need to set dynamically somehow
                float proximity = target_puck.getBBArea() / (320 * 320); // Area of bounding box relative to full image.
                centerPuckController.setTarget(true, phi, proximity);
            }else{
                centerPuckController.setTarget(false, 0, 0);
            }
        }
    }

    public void setCenter(float center){
        this.center = center;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setIoReadyListener(this);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onIOReady(AbcvlibLooper abcvlibLooper) {
        centerPuckController = (CenterPuckController) new CenterPuckController().setInitDelay(0)
                .setName("centerPuckController").setThreadCount(1)
                .setThreadPriority(Thread.NORM_PRIORITY).setTimestep(100)
                .setTimeUnit(TimeUnit.MILLISECONDS);

        PublisherManager publisherManager = new PublisherManager();
        new WheelData.Builder(this, publisherManager, abcvlibLooper).build().addSubscriber(centerPuckController);
        new BatteryData.Builder(this, publisherManager, abcvlibLooper).build().addSubscriber(centerPuckController);
        publisherManager.initializePublishers();
        publisherManager.startPublishers();

        // Start your custom controller
        centerPuckController.startController();
        // Adds your custom controller to the compounding master controller.
        getOutputs().getMasterController().addController(centerPuckController);
        // Start the master controller after adding and starting any customer controllers.
        getOutputs().startMasterController();

    }
}