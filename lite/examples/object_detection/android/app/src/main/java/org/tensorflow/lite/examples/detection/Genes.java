package org.tensorflow.lite.examples.detection;

import android.util.Log;

import com.karlotoy.perfectune.instance.PerfectTune;

import org.apache.commons.math3.distribution.IntegerDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.util.ProcessPriorityThreadFactory;
import jp.oist.abcvlib.util.ScheduledExecutorServiceWithException;

public class Genes implements Runnable{
    private ScheduledExecutorServiceWithException toneExecutor = new ScheduledExecutorServiceWithException(1, new ProcessPriorityThreadFactory(Thread.NORM_PRIORITY, "TonePlayer"));
    private int[] genes = new int[10];
    private int g = 2; // skip first two random ones
    private int currentGene = 2;
    private int previousGene = 1;
    private Random random = new Random();
    private NormalDistribution normalDistribution = new NormalDistribution(5000, 1000);
    private int maxMutation = 4;
    private PerfectTune perfectTune = new PerfectTune();
    private Random rand = new Random();
    private float[] freqs = new float[16];
    private float baseFreq = 440;
    private ScheduledFuture<?> tonePlayingFuture;

    public Genes(){
        for (int i = 0; i < 3; i++){
            genes[i] = rand.nextInt(maxMutation);
        }
        freqs[0] = baseFreq;
        for (int i = 1; i < freqs.length - 1; i++){
            freqs[i] = freqs[i-1] * (float) Math.pow(2.0f, (1.0f / 12.0f));
        }
        tonePlayingFuture = toneExecutor.schedule(this, 0, TimeUnit.SECONDS);

        // Unit test to test mating with only one robot. Keeping commented for reference.

//        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(new Runnable() {
//            @Override
//            public void run() {
//                exchangeGenes(Arrays.toString(genes));
//            }
//        }, 10, 10, TimeUnit.SECONDS);
    }

    public int[] parseIncomingGenes(String incomingGenes){
        return Arrays.stream(incomingGenes.substring(1, incomingGenes.length() - 1).split(","))
                .map(String::trim).mapToInt(Integer::parseInt).toArray();
    }

    public String genesToString(){
        return Arrays.toString(genes);
    }

    public void exchangeGenes(String mateGenes){
        Log.d("MatingController", "Exhcnaged Genes");
        Log.d("MatingController", "My Genes: " + Arrays.toString(genes));
        Log.d("MatingController", "Mate's Genes: " + mateGenes);

        final int[] mateGenesInt = parseIncomingGenes(mateGenes);

        currentGene = g++ % genes.length;
        int mutation = rand.nextInt(maxMutation);
        this.genes[currentGene] = genes[previousGene] + mateGenesInt[previousGene] + mutation;
        previousGene = currentGene;

        // Wait for a while to give mate a chance to also read QR code on self.
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Cancel existing tone player before starting a new one
        tonePlayingFuture.cancel(true);
        tonePlayingFuture = toneExecutor.schedule(this, 0, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        long songTime = 0;
        try{
            for (int gene:genes){
                if (gene != 0){
                    // Choose freq from list of freqs
                    float freq = freqs[gene % freqs.length];
                    Log.d("Genes", "freq:" + freq);
                    perfectTune.setTuneFreq(freq);
                    perfectTune.playTune();
                    long toneTime = 250 + rand.nextInt(250); // Time to play single tone in ms varied a bit to make it less annoying
                    songTime += toneTime;
                    try {
                        Thread.sleep(toneTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    perfectTune.stopTune();
                }
            }
        } finally {
            tonePlayingFuture = toneExecutor.schedule(this, songTime
                    + Math.round(normalDistribution.sample()), TimeUnit.MILLISECONDS);
        }
    }
}