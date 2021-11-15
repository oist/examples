package org.tensorflow.lite.examples.detection;

import android.content.Intent;
import android.graphics.RectF;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.tensorflow.lite.examples.detection.tflite.Detector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.core.AbcvlibLooper;
import jp.oist.abcvlib.core.AbcvlibService;
import jp.oist.abcvlib.core.IOReadyListener;
import jp.oist.abcvlib.tests.BackAndForthController;

public class PuckMountControllerService extends AbcvlibService implements IOReadyListener{
    private float minimumConfidence = 0.6f;

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
        Log.i("Results", "Target puck location: " + target_puck.getLocation());
        Log.i("Results", "Target robot location: " + target_robot.getLocation());
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setIoReadyListener(this);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onIOReady(AbcvlibLooper abcvlibLooper) {
        float speed = 0.5f;
        // Customizing ALL build params. You can remove any or all. This object not used, but here for reference.
        BackAndForthController backAndForthController = (BackAndForthController) new BackAndForthController(speed).setInitDelay(0)
                .setName("BackAndForthController").setThreadCount(1)
                .setThreadPriority(Thread.NORM_PRIORITY).setTimestep(1000)
                .setTimeUnit(TimeUnit.MILLISECONDS);

        // Start your custom controller
        backAndForthController.startController();
        // Adds your custom controller to the compounding master controller.
        getOutputs().getMasterController().addController(backAndForthController);
        // Start the master controller after adding and starting any customer controllers.
        getOutputs().startMasterController();
    }
}