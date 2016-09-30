package cs.umass.edu.myactivitiestoolkit.steps;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import cs.umass.edu.myactivitiestoolkit.processing.Filter;

/**
 * This class is responsible for detecting steps from the accelerometer sensor.
 * All {@link OnStepListener step listeners} that have been registered will
 * be notified when a step is detected.
 */
public class StepDetector implements SensorEventListener {
    /** Used for debugging purposes. */
    @SuppressWarnings("unused")

    private static final String TAG = StepDetector.class.getName();

    /** Maintains the set of listeners registered to handle step events. **/
    private ArrayList<OnStepListener> mStepListeners;

    private TreeMap<Long, float[]> mEventBuffer;
    /**
     * The number of steps taken.
     */
    private int stepCount;
    private Filter mFilter;

    //tweakable values
    private float minimumRange = 0.3f; //noise with a occurs within a bound that occurs <minimumRange> around the center
    private int smoothingFactor = 5; //smoothing factor for the filter within the StepDetector
    private double window = 1.0; //window

    public StepDetector(){
        mStepListeners = new ArrayList<>();
        mEventBuffer = new TreeMap<>();
        stepCount = 0;
        mFilter = new Filter(smoothingFactor);

    }

    /**
     * Registers a step listener for handling step events.
     * @param stepListener defines how step events are handled.
     */
    public void registerOnStepListener(final OnStepListener stepListener){
        mStepListeners.add(stepListener);
    }

    /**
     * Unregisters the specified step listener.
     * @param stepListener the listener to be unregistered. It must already be registered.
     */
    public void unregisterOnStepListener(final OnStepListener stepListener){
        mStepListeners.remove(stepListener);
    }

    /**
     * Unregisters all step listeners.
     */
    public void unregisterOnStepListeners(){
        mStepListeners.clear();
    }

    /**
     * Here is where you will receive accelerometer readings, buffer them if necessary
     * and run your step detection algorithm. When a step is detected, call
     * {@link #onStepDetected(long, float[])} to notify all listeners.
     *
     * Recall that human steps tend to take anywhere between 0.5 and 2 seconds.
     *
     * @param event sensor reading
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            //TODO: Detect steps! Call onStepDetected(...) when a step is detected.
            mEventBuffer.put(event.timestamp, event.values); //time bounded buffer

            long minimumTimestamp = event.timestamp - (long) (window * Math.pow(10, 9));
//            int upperRange = getNearestTimestampMatch(minimumTimestamp);
            Object actualminTimestamp = mEventBuffer.floorKey(minimumTimestamp);
            if (null != actualminTimestamp) {

                Map<Long, float[]> newBuffer = mEventBuffer.subMap((long) actualminTimestamp, mEventBuffer.lastKey());
                mEventBuffer.clear();
                mEventBuffer.putAll(newBuffer);
            }
//            if (upperRange != -1) // If getNearestTimestampMatch() doesn't get a match
//            {
//                mEventBuffer.removeAll(mEventBuffer.subList(0, getNearestTimestampMatch(minimumTimestamp))); // dumps data that is a older than 1.5 seconds
//            }


            //algorithm
            if (mEventBuffer.size() > 3) {

                //data set of 3 or fewer is not a sufficient sample size
                TreeMap<Long, Float> map = new TreeMap<>();

                //math function converts the three waveforms to a single signal
                for (Long key : mEventBuffer.keySet()) {
                    double[] fValues = mFilter.getFilteredValues(mEventBuffer.get(key));
                    for (int i = 0; i < fValues.length; i++) {
                        fValues[i] = Math.pow(fValues[i], 2);
                    }
                    double combined = Math.sqrt(fValues[0] + fValues[1] + fValues[2]);
                    map.put(key, (float) combined);
                }

                Collection<Float> list = map.values();
                float upper = Collections.max(list);
                float lower = Collections.min(list);
                float center = (upper + lower) / 2;

                // disregard noise at about center value
                if (!(upper < center + minimumRange && lower > center - minimumRange)) {
                    long top = getKeyByValue(upper, map);
                    long bottom = getKeyByValue(lower, map);
                    // down turn of a wave where the slope is negative
                    if (top < bottom) {
                        onStepDetected(bottom, event.values); // send step signal
                        mEventBuffer.clear(); //dump current window to prevent further analysis on that set of data
                    }
                }
                map.clear(); //gc
            }
        }
    }
    private long getKeyByValue(float value,Map<Long,Float> map){
        long result = -1;
        for (long key :
                map.keySet()) {
            if (map.get(key).equals(value)) {
                result = key;
            }
        }
        return result;
    }

    /*
    *   returns index of nearest timestamp that is below the specified
    *   returns -1 if none found
    *
    * */
//    private int getNearestTimestampMatch(long timestamp)
//    {
//        int result = -1;
//        long[] timestampArray = new long[mEventBuffer.size()];
//        TreeSet<Long> set = new TreeSet<>();
//        String out = "";
//        for (int i = 0; i < mEventBuffer.size(); i++) {
//            long stamp = mEventBuffer.get(i).timestamp;
//            timestampArray[i] = stamp;
//            set.add(stamp);
//            out += " " + stamp;
//        }
//        Object item = set.floor(timestamp);
//        if (null == item) {
//            return result;
//        }
//        result = Arrays.binarySearch(timestampArray, (Long) item);
//        set.clear();
//        return result;
//    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // do nothing
    }

    /**
     * This method is called when a step is detected. It updates the current step count,
     * notifies all listeners that a step has occurred and also notifies all listeners
     * of the current step count.
     */
    private void onStepDetected(long timestamp, float[] values){
        stepCount++;
        for (OnStepListener stepListener : mStepListeners){
            stepListener.onStepDetected(timestamp, values);
            stepListener.onStepCountUpdated(stepCount);
        }
    }
}
