package org.tensorflow.lite.examples.detection;

import static android.graphics.ImageFormat.YUV_420_888;
import static android.graphics.ImageFormat.YUV_422_888;
import static android.graphics.ImageFormat.YUV_444_888;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.Image;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ImageProxy;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jetbrains.annotations.NotNull;
import org.tensorflow.lite.examples.detection.tflite.Detector;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
import jp.oist.abcvlib.core.inputs.phone.QRCodeDataSubscriber;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

public class HighLevelControllerService extends AbcvlibService implements IOReadyListener, BatteryDataSubscriber, LifecycleOwner {

    private LifecycleRegistry lifecycleRegistry;
    private QRCodePublisher qrCodePublisher;
    private CountDownLatch countDownLatch;

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }

    private enum State {
        CHARGING, MATING
    }
    private HighLevelControllerService.State state = State.CHARGING;

    private float minimumConfidence = 0.8f;
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
        countDownLatch = new CountDownLatch(1);
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
        if (chargeController != null && matingController != null){
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
        countDownLatch.countDown();
    }

    public void sendControl(Detector.Recognition target_robot, Detector.Recognition target_puck){
        switch (state){
            case MATING:
                if (matingController.isRunning()){
                    Log.i("HighLevel", "Mating");
                    if (target_robot.getBBArea() > 0){
                        float phi = (center - target_robot.getLocation().centerX()) / (320f/2f);
                        //todo hardcoded 320 here. Need to set dynamically somehow
                        float proximity = target_robot.getBBArea() / (320 * 320); // Area of bounding box relative to full image.
                        matingController.setTarget(true, phi, proximity);
                    }else{
                        matingController.setTarget(false, 0, 0);
                    }
//                    qrCodePublisher.turnOnQRCode(new int[]{1,2,3});
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
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        chargeController = (ChargeController) new ChargeController().setInitDelay(0)
                .setName("chargeController").setThreadCount(1)
                .setThreadPriority(Thread.NORM_PRIORITY).setTimestep(100)
                .setTimeUnit(TimeUnit.MILLISECONDS);

        matingController = (MatingController) new MatingController().setInitDelay(0)
                .setName("matingController").setThreadCount(1)
                .setThreadPriority(Thread.NORM_PRIORITY).setTimestep(100)
                .setTimeUnit(TimeUnit.MILLISECONDS);

        matingController.setContext(this);
        matingController.setQrCodePublisher(qrCodePublisher);

        publisherManager = new PublisherManager();
        wheelData = new WheelData.Builder(this, publisherManager, abcvlibLooper).build();
        wheelData.addSubscriber(chargeController).addSubscriber(matingController);
        batteryData = new BatteryData.Builder(this, publisherManager, abcvlibLooper).build();
        batteryData.addSubscriber(chargeController).addSubscriber(this);
//        qrCodeData = new QRCodeData.Builder(this, publisherManager, this).build();
//        qrCodeData.addSubscriber(matingController);

        Log.d("race", "init publishers start");
        publisherManager.initializePublishers();
        Log.d("race", "init publishers end");
        Log.d("race", "start publishers start");
        publisherManager.startPublishers();
        Log.d("race", "start publishers end");

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

    private int[] convertRGB2YUV(int color) {
        ColorMatrix cm = new ColorMatrix();
        cm.setRGB2YUV();
        final float[] yuvArray = cm.getArray();

        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        int[] result = new int[3];

        // Adding a 127 U and V.
        result[0] = floatToByte(yuvArray[0] * r + yuvArray[1] * g + yuvArray[2] * b);
        result[1] = floatToByte(yuvArray[5] * r + yuvArray[6] * g + yuvArray[7] * b) + 127;
        result[2] = floatToByte(yuvArray[10] * r + yuvArray[11] * g + yuvArray[12] * b) + 127;
        return result;
    }

    private int floatToByte(float x) {
        int n = java.lang.Math.round(x);
        return n;
    }

    public void analyze(int width, int height, @NonNull @NotNull int[] rgbbytes, Matrix frameToCropTransform) {
        if (matingController != null){
            String qrDecodedData = "";
            int cropSize = 320;
            Bitmap rgbFrameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            rgbFrameBitmap.setPixels(rgbbytes, 0, width, 0, 0, width, height);
            Bitmap croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(croppedBitmap);
            canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
            int[] cropedpixels = new int[cropSize*cropSize];
            croppedBitmap.getPixels(cropedpixels, 0, cropSize, 0, 0, cropSize, cropSize);

            RGBLuminanceSource source = new RGBLuminanceSource(cropSize, cropSize, cropedpixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            try {
                Result result = new QRCodeReader().decode(binaryBitmap);
                qrDecodedData = result.getText();
                Log.v("qrcode", "QR Code found: " + qrDecodedData);
                matingController.onQRCodeDetected(qrDecodedData);
            } catch (FormatException e) {
                Log.v("qrcode", "QR Code cannot be decoded");
            } catch (ChecksumException e) {
                Log.v("qrcode", "QR Code error correction failed");
                e.printStackTrace();
            } catch (NotFoundException e) {
                Log.v("qrcode", "QR Code not found");
            }
        }
    }
}