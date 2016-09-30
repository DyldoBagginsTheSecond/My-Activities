package cs.umass.edu.myactivitiestoolkit.steps;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import cs.umass.edu.myactivitiestoolkit.processing.Filter;

/**
 * This class is responsible for detecting steps from the accelerometer sensor.
 * All {@link OnStepListener step listeners} that have been registered will
 * be notified when a step is detected.
 */
public class StepDetector implements SensorEventListener {
    /**
     * Used for debugging purposes.
     */
    @SuppressWarnings("unused")

    private static final String TAG = StepDetector.class.getName();

    /**
     * Maintains the set of listeners registered to handle step events.
     **/
    private ArrayList<OnStepListener> mStepListeners;

    private TreeMap<Long, float[]> mEventBuffer;
    /**
     * The number of steps taken.
     */
    private int stepCount;
    private Filter mFilter;

    //tweakable values
    private static final float minimumRange = 0.8f; //noise with a occurs within a bound that occurs <minimumRange> around the center
    private static final int smoothingFactor = 7; //smoothing factor for the filter within the StepDetector
    private static final double window = 1.0; //window of analysis in seconds
    private static final double cooldown = 0.5; //cooldown between step increments
    private long timestampOfLast = 0; //timestamp of the end of the last window
    public StepDetector() {
        mStepListeners = new ArrayList<>();
        mEventBuffer = new TreeMap<>();
        stepCount = 0;
        mFilter = new Filter(smoothingFactor);

    }

    /**
     * Registers a step listener for handling step events.
     *
     * @param stepListener defines how step events are handled.
     */
    public void registerOnStepListener(final OnStepListener stepListener) {
        mStepListeners.add(stepListener);
    }

    /**
     * Unregisters the specified step listener.
     *
     * @param stepListener the listener to be unregistered. It must already be registered.
     */
    public void unregisterOnStepListener(final OnStepListener stepListener) {
        mStepListeners.remove(stepListener);
    }

    /**
     * Unregisters all step listeners.
     */
    public void unregisterOnStepListeners() {
        mStepListeners.clear();
    }

    /**
     * Here is where you will receive accelerometer readings, buffer them if necessary
     * and run your step detection algorithm. When a step is detected, call
     * {@link #onStepDetected(long, float[])} to notify all listeners.
     * <p>
     * Recall that human steps tend to take anywhere between 0.5 and 2 seconds.
     *
     * @param event sensor reading
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            //TODO: Detect steps! Call onStepDetected(...) when a step is detected.
            mEventBuffer.put(event.timestamp, event.values); //time bounded buffer
            if (timestampOfLast == 0) {
                timestampOfLast = event.timestamp;
            }

            long minimumTimestamp = event.timestamp - (long) (window * Math.pow(10, 9)); // dumps data that is a older than <window> seconds
            Object actualMinTimestamp = mEventBuffer.floorKey(minimumTimestamp); //no match returns null
            if (null != actualMinTimestamp) {
                Map<Long, float[]> newBuffer = mEventBuffer.subMap((long) actualMinTimestamp, mEventBuffer.lastKey());
                mEventBuffer.clear();
                mEventBuffer.putAll(newBuffer);
            }
            Log.d(TAG, "onSensorChanged: " + mEventBuffer.size());
            //algorithm
            if ((event.timestamp > (cooldown * Math.pow(10, 9)) + timestampOfLast) && mEventBuffer.size() > 3) {

                //data set of <cooldown> or fewer seconds is not a sufficient sample size
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
                        timestampOfLast = event.timestamp;
                        mEventBuffer.clear(); //dump current window to prevent further analysis on that set of data
                    }
                }
                map.clear(); //gc
            }
        }
    }

    private long getKeyByValue(float value, Map<Long, Float> map) {
        long result = -1;
        for (long key :
                map.keySet()) {
            if (map.get(key).equals(value)) {
                result = key;
            }
        }
        return result;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // do nothing
    }

    /**
     * This method is called when a step is detected. It updates the current step count,
     * notifies all listeners that a step has occurred and also notifies all listeners
     * of the current step count.
     */
    private void onStepDetected(long timestamp, float[] values) {
        stepCount++;
        for (OnStepListener stepListener : mStepListeners) {
            stepListener.onStepDetected(timestamp, values);
            stepListener.onStepCountUpdated(stepCount);
        }
    }
}
