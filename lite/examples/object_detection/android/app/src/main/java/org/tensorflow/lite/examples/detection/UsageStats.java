package org.tensorflow.lite.examples.detection;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.core.inputs.microcontroller.BatteryDataSubscriber;
import jp.oist.abcvlib.core.inputs.microcontroller.WheelDataSubscriber;
import jp.oist.abcvlib.util.ErrorHandler;
import jp.oist.abcvlib.util.ProcessPriorityThreadFactory;
import jp.oist.abcvlib.util.ScheduledExecutorServiceWithException;

/**
 * A class to collect usage stats for the robot. Records battery level, controller state, and wheel distance over time
 * Battery level recorded to assess how long the controller will survive on its own. Controller state and wheel distance to
 * retrospectively assess errors (stuck, etc.).
 */
public class UsageStats implements BatteryDataSubscriber, WheelDataSubscriber, DefaultLifecycleObserver, Runnable{
    private String TAG = "UsageStats";

    private int sampling_rate = 1; // once per 1 seconds data is sampled from streams
    private int write_rate = 5; // once per 5 seconds samples are written to disk

    private File dir;
    private File file;
    private FileWriter fileWriter;
    private String startTime;
    private DecimalFormat format = new DecimalFormat("#.##");

    private ScheduledExecutorServiceWithException executorSampling =
            new ScheduledExecutorServiceWithException(1,
                    new ProcessPriorityThreadFactory(Thread.NORM_PRIORITY,
                            "UsageStatsSampling"));

    private ScheduledExecutorServiceWithException executorWriter =
            new ScheduledExecutorServiceWithException(1,
                    new ProcessPriorityThreadFactory(Thread.NORM_PRIORITY,
                            "UsageStatsWriter"));

    // Data of interest
    private double batteryLevel = 0;
    private ExponentialMovingAverage batteryLevelLP = new ExponentialMovingAverage(0.1f);
    private int state;
    private double wheelDistanceL;
    private double wheelDistanceR;
    private double wheelSpeedExpAvgL;
    private double wheelSpeedExpAvgR;

    private String header = "BatteryLevel,State,DistanceL,DistanceR,SpeedL,SpeedR" + System.getProperty("line.separator");

    public UsageStats(Context context){
        dir = context.getFilesDir();
        startTime = Long.toString(System.currentTimeMillis());

        try{
            file = new File(dir + File.separator + startTime + ".csv");
            if (!file.exists()){
                boolean created = file.createNewFile();
                Log.v(TAG, "Created new file? " + file.getAbsolutePath() + " " + created);
                fileWriter = new FileWriter(file.getAbsolutePath(),false);
            }
            fileWriter.write(header);
        }catch(Exception e){
            ErrorHandler.eLog(TAG, "Error when saving data to file", e, true);
        }
    }

    public void start(){
        executorSampling.scheduleAtFixedRate(this, 0, sampling_rate, TimeUnit.SECONDS);
        executorWriter.scheduleAtFixedRate((Runnable) () -> {
            try {
                fileWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        try {
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        writeLine();
    }

    private void writeLine(){
        String string = format.format(batteryLevel) + ",";
        string += state + ",";
        string += format.format(wheelDistanceL) + ",";
        string += format.format(wheelDistanceR) + ",";
        string += format.format(wheelSpeedExpAvgL) + ",";
        string += format.format(wheelSpeedExpAvgR) + System.lineSeparator();
        Log.d(TAG, string);
        try {
            fileWriter.write(string);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onStateChange(String state){
        switch(state){
            case "Mating_SEARCHING":
                this.state = 0;
                break;
            case "Mating_DECIDING":
                this.state = 1;
                break;
            case "Mating_APPROACHING":
                this.state = 2;
                break;
            case "Mating_WAITING":
                this.state = 3;
                break;
            case "Mating_FLEEING":
                this.state = 4;
                break;
            case "Charging_SEARCHING":
                this.state = 5;
                break;
            case "Charging_MOUNTING":
                this.state = 6;
                break;
            case "Charging_CHARGING":
                this.state = 7;
                break;
            case "Charging_DISMOUNTING":
                this.state = 8;
                break;
            case "Charging_DECIDING":
                this.state = 9;
                break;
        }
    }

    @Override
    public void onBatteryVoltageUpdate(double voltage, long timestamp) {
        this.batteryLevel = batteryLevelLP.average((float) voltage);
    }

    @Override
    public void onChargerVoltageUpdate(double chargerVoltage, double coilVoltage, long timestamp) {

    }

    @Override
    public void onWheelDataUpdate(long timestamp, int wheelCountL, int wheelCountR,
                                  double wheelDistanceL, double wheelDistanceR,
                                  double wheelSpeedInstantL, double wheelSpeedInstantR,
                                  double wheelSpeedBufferedL, double wheelSpeedBufferedR,
                                  double wheelSpeedExpAvgL, double wheelSpeedExpAvgR) {
        this.wheelDistanceL = wheelDistanceL;
        this.wheelDistanceR = wheelDistanceR;
        this.wheelSpeedExpAvgL = wheelSpeedExpAvgL;
        this.wheelSpeedExpAvgR = wheelSpeedExpAvgR;
    }
}