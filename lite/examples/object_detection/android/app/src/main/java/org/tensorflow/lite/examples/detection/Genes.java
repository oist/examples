package org.tensorflow.lite.examples.detection;

import android.util.Log;

import com.karlotoy.perfectune.instance.PerfectTune;

import java.util.Arrays;
import java.util.Random;

import jp.oist.abcvlib.util.ProcessPriorityThreadFactory;
import jp.oist.abcvlib.util.ScheduledExecutorServiceWithException;

public class Genes implements Runnable{
    private ScheduledExecutorServiceWithException toneExecutor = new ScheduledExecutorServiceWithException(1, new ProcessPriorityThreadFactory(Thread.NORM_PRIORITY, "TonePlayer"));
    private int[] genes = new int[10];
    private int g = 0;
    private Random random = new Random();
    private int maxMutation = 4;
    private PerfectTune perfectTune = new PerfectTune();
    private Random rand = new Random();
    private float[] freqs = new float[16];
    private float baseFreq = 440;

    public Genes(){
        freqs[0] = baseFreq;
        for (int i = 1; i < freqs.length - 1; i++){
            freqs[i] = freqs[i-1] * (float) Math.pow(2.0f, (1.0f / 12.0f));
        }
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

        if (g < genes.length - 2){
            g++;
            int mutation = rand.nextInt(maxMutation);
            this.genes[g] = genes[g-1] + mateGenesInt[g-1] + mutation;
        }
        toneExecutor.execute(this);
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        for (int gene:genes){
            if (gene != 0){
                // Choose freq from list of freqs
                float freq = freqs[gene % freqs.length];
                Log.d("Genes", "freq:" + freq);
                perfectTune.setTuneFreq(freq);
                perfectTune.playTune();
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                perfectTune.stopTune();
            }
        }
    }
}