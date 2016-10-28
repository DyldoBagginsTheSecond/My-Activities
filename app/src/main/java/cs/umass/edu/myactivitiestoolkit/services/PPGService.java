package cs.umass.edu.myactivitiestoolkit.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import cs.umass.edu.myactivitiestoolkit.R;
import cs.umass.edu.myactivitiestoolkit.constants.Constants;
import cs.umass.edu.myactivitiestoolkit.ppg.HRSensorReading;
import cs.umass.edu.myactivitiestoolkit.ppg.HeartRateCameraView;
import cs.umass.edu.myactivitiestoolkit.ppg.PPGEvent;
import cs.umass.edu.myactivitiestoolkit.ppg.PPGListener;
import cs.umass.edu.myactivitiestoolkit.ppg.PPGSensorReading;
import cs.umass.edu.myactivitiestoolkit.processing.Filter;
import edu.umass.cs.MHLClient.client.MobileIOClient;

/**
 * Photoplethysmography service. This service uses a {@link HeartRateCameraView}
 * to collect PPG data using a standard camera with continuous flash. This is where
 * you will do most of your work for this assignment.
 * <br><br>
 * <b>ASSIGNMENT (PHOTOPLETHYSMOGRAPHY)</b> :
 * In {@link #onSensorChanged(PPGEvent)}, you should smooth the PPG reading using
 * a {@link Filter}. You should send the filtered PPG reading both to the server
 * and to the {@link cs.umass.edu.myactivitiestoolkit.view.fragments.HeartRateFragment}
 * for visualization. Then call your heart rate detection algorithm, buffering the
 * readings if necessary, and send the bpm measurement back to the UI.
 * <br><br>
 * EXTRA CREDIT:
 *      Follow the steps outlined <a href="http://www.marcoaltini.com/blog/heart-rate-variability-using-the-phones-camera">here</a>
 *      to acquire a cleaner PPG signal. For additional extra credit, you may also try computing
 *      the heart rate variability from the heart rate, as they do.
 *
 * @author CS390MB
 *
 * @see HeartRateCameraView
 * @see PPGEvent
 * @see PPGListener
 * @see Filter
 * @see MobileIOClient
 * @see PPGSensorReading
 * @see Service
 */
public class PPGService extends SensorService implements PPGListener
{
    @SuppressWarnings("unused")
    /** used for debugging purposes */
    private static final String TAG = PPGService.class.getName();

    /* Surface view responsible for collecting PPG data and displaying the camera preview. */
    private HeartRateCameraView mPPGSensor;

    private Filter ppgFilter;
    private TreeMap<Long, Double> mEventBuffer;
    private ArrayList<Long> timestamps;
    private int bpm;
    private long timestampOfLast = 0;

    //tweak
    private static final double window = .1;
    private static final double cooldown = .08;
    private static final double minimumRange = .03;
    private static final int smoothingFactor = 4;





    @Override
    protected void start() {
        Log.d(TAG, "START");
        mPPGSensor = new HeartRateCameraView(getApplicationContext(), null);
        ppgFilter = new Filter(smoothingFactor);
        mEventBuffer = new TreeMap<>();
        timestamps = new ArrayList<>();

        WindowManager winMan = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);

        //surface view dimensions and position specified where service intent is called
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        //display the surface view as a stand-alone window
        winMan.addView(mPPGSensor, params);
        mPPGSensor.setZOrderOnTop(true);

        // only once the surface has been created can we start the PPG sensor
        mPPGSensor.setSurfaceCreatedCallback(new HeartRateCameraView.SurfaceCreatedCallback() {
            @Override
            public void onSurfaceCreated() {
                mPPGSensor.start(); //start recording PPG
            }
        });

        super.start();
    }

    @Override
    protected void onServiceStarted() {
        broadcastMessage(Constants.MESSAGE.PPG_SERVICE_STARTED);
    }

    @Override
    protected void onServiceStopped() {
        if (mPPGSensor != null)
            mPPGSensor.stop();
        if (mPPGSensor != null) {
            ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).removeView(mPPGSensor);
        }
        broadcastMessage(Constants.MESSAGE.PPG_SERVICE_STOPPED);
    }

    @Override
    protected void registerSensors() {
        // TODO: Register a PPG listener with the PPG sensor (mPPGSensor)
        mPPGSensor.registerListener(this);
    }

    @Override
    protected void unregisterSensors() {
        // TODO: Unregister the PPG listener
        mPPGSensor.unregisterListener(this);
    }

    @Override
    protected int getNotificationID() {
        return Constants.NOTIFICATION_ID.PPG_SERVICE;
    }

    @Override
    protected String getNotificationContentText() {
        return getString(R.string.ppg_service_notification);
    }

    @Override
    protected int getNotificationIconResourceID() {
        return R.drawable.ic_whatshot_white_48dp;
    }

    /**
     * This method is called each time a PPG sensor reading is received.
     * <br><br>
     * You should smooth the data using {@link Filter} and then send the filtered data both
     * to the server and the main UI for real-time visualization. Run your algorithm to
     * detect heart beats, calculate your current bpm and send the bmp measurement to the
     * main UI. Additionally, it may be useful for you to send the peaks you detect to
     * the main UI, using {@link #broadcastPeak(long, double)}. The plot is already set up
     * to draw these peak points upon receiving them.
     * <br><br>
     * Also make sure to send your bmp measurement to the server for visualization. You
     * can do this using {@link HRSensorReading}.
     *
     * @param event The PPG sensor reading, wrapping a timestamp and mean red value.
     *
     * @see PPGEvent
     * @see PPGSensorReading
     * @see HeartRateCameraView#onPreviewFrame(byte[], Camera)
     * @see MobileIOClient
     * @see HRSensorReading
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onSensorChanged(PPGEvent event) {
        Log.d(TAG, "PPG onSensorChanged");
        // TODO: Smooth the signal using a Butterworth / exponential smoothing filter
        double filtered[] = ppgFilter.getFilteredValues((float)event.value);
        if(filtered.length > 0) {
            // TODO: send the data to the UI fragment for visualization, using broadcastPPGReading(...)
            broadcastPPGReading(event.timestamp, filtered[0]);
            // TODO: Send the filtered mean red value to the server
            PPGSensorReading reading = new PPGSensorReading(mUserID, "MOBILE", mClient.toString(), event.timestamp, filtered[0]);
            mClient.sendSensorReading(reading);
            // TODO: Buffer data if necessary for your algorithm
            // TODO: Call your heart beat and bpm detection algorithm
            processEvent(new PPGEvent(filtered[0], event.timestamp));

            int beats = timestamps.size();
            long time = 0;
            if (beats > 2) {
                time = ((timestamps.get(beats - 1) - timestamps.get(0)));
                double seconds = time / 1000;
//                Log.i(TAG, "" + time);
//                Log.i(TAG, "" + seconds);

                double bpma = beats / seconds;
                bpma = bpma * 60;
                bpm = (int) bpma;
                Log.i(TAG, "bpm: " + bpm);
                broadcastBPM(bpm);
            }
            if (time > 60000) {
                timestamps.remove(0);
            }
            // TODO: Send your heart rate estimate to the server
        }
    }

    private void processEvent(PPGEvent event) {
        mEventBuffer.put(event.timestamp, event.value);
        if (timestampOfLast == 0) {
            timestampOfLast = event.timestamp;
        }
        long minTimestamp = event.timestamp - (long) (window * Math.pow(10, 4));
        Object actualMinTimestamp = mEventBuffer.floorKey(minTimestamp); //no match returns null

        if (null != actualMinTimestamp) {
            Map<Long, Double> newBuffer = mEventBuffer.subMap((long) actualMinTimestamp, mEventBuffer.lastKey());
            mEventBuffer.clear();
            mEventBuffer.putAll(newBuffer);
            timestampOfLast = (long) actualMinTimestamp;
        }

        if ((event.timestamp > (cooldown * Math.pow(10, 3)) + timestampOfLast) && mEventBuffer.size() > 3) {

            //data set of <cooldown> or fewer seconds is not a sufficient sample size
            TreeMap<Long, Double> map = new TreeMap<>();

            //separate holding map processing map
            for (Long key : mEventBuffer.keySet()) {
                map.put(key, mEventBuffer.get(key));
            }

            Collection<Double> list = map.values();
            double upper = Collections.max(list);
            double lower = Collections.min(list);
            double center = (upper + lower) / 2;

            // disregard noise at about center value
            if (!(upper < center + minimumRange && lower > center - minimumRange)) {
                long top = getKeyByValue(upper, map);
                long bottom = getKeyByValue(lower, map);
                // down turn of a wave where the slope is negative
                if (top < bottom) {
                    timestampOfLast = event.timestamp;
                    timestamps.add(top);
                    broadcastPeak(bottom, lower);
                    mEventBuffer.clear(); //dump current window to prevent further analysis on that set of data
                }
            }
            map.clear(); //gc
        }

    }

    private long getKeyByValue(Double value, Map<Long, Double> map) {
        long result = -1;
        for (long key :
                map.keySet()) {
            if (map.get(key).equals(value)) {
                result = key;
            }
        }
        return result;
    }

    /**
     * Broadcasts the PPG reading to other application components, e.g. the main UI.
     * @param ppgReading the mean red value.
     */
    public void broadcastPPGReading(final long timestamp, final double ppgReading) {
        Log.d(TAG, "broadcasting PPG to UI: " + timestamp + " reading: " + ppgReading);
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.PPG_DATA, ppgReading);
        intent.putExtra(Constants.KEY.TIMESTAMP, timestamp);
        intent.setAction(Constants.ACTION.BROADCAST_PPG);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }

    /**
     * Broadcasts the current heart rate in BPM to other application components, e.g. the main UI.
     * @param bpm the current beats per minute measurement.
     */
    public void broadcastBPM(final int bpm) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.HEART_RATE, bpm);
        intent.setAction(Constants.ACTION.BROADCAST_HEART_RATE);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }

    /**
     * Broadcasts the current heart rate in BPM to other application components, e.g. the main UI.
     * @param timestamp the current beats per minute measurement.
     */
    public void broadcastPeak(final long timestamp, final double value) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.PPG_PEAK_TIMESTAMP, timestamp);
        intent.putExtra(Constants.KEY.PPG_PEAK_VALUE, value);
        intent.setAction(Constants.ACTION.BROADCAST_PPG_PEAK);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }
}