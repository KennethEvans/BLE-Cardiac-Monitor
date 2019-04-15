package net.kenevans.android.blecardiacmonitor;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PanZoom;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeriesFormatter;

import java.io.File;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

/**
 * @author evans
 */
public class PlotActivity extends AppCompatActivity implements IConstants,
        PlotterListener {
    private static final String TAG = "BCMPlot";
    private XYPlot mPlot;

    private boolean mPlotHr = true;
    private boolean mPlotRr = true;
    private int mPlotInterval = PLOT_MAXIMUM_AGE;
    private BCMDbAdapter mDbAdapter;
    private File mDataDir;
    private long mPlotStartTime = INVALID_DATE;
    private long mPlotSessionStart = INVALID_DATE;
    private boolean mIsSession = false;
    private long mLastRrUpdateTime = INVALID_DATE;
    private long mLastRrTime = INVALID_DATE;

    private double RR_SCALE = .1;  // to 100 ms to use same axis

    private Context context;
    private XYSeriesFormatter hrFormatter;
    private XYSeriesFormatter rrFormatter;
    private SimpleXYSeries hrSeries;
    private SimpleXYSeries rrSeries;


    /**
     * Handles various events fired by the Service.
     * <p/>
     * <br>
     * <br>
     * ACTION_GATT_CONNECTED: connected to a GATT server.<br>
     * ACTION_GATT_DISCONNECTED: disconnected from a GATT server.<br>
     * ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.<br>
     * ACTION_DATA_AVAILABLE: received data from the device. This can be a
     * result of read or notification operations.<br>
     */
    private final BroadcastReceiver mGattUpdateReceiver = new
            BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (BCMBleService.ACTION_DATA_AVAILABLE.equals(action)) {
//                        Log.d(TAG, "mGattUpdateReceiver:onReceive: " +
//                                action);
                        addValues(intent);
                    } else if (BCMBleService.ACTION_ERROR.equals(action)) {
//                        Log.d(TAG, "mGattUpdateReceiver:onReceive: " +
//                                action);
                        displayError(intent);
                    }
                }
            };

    /**
     * Make an IntentFilter for the actions in which we are interested.
     *
     * @return The IntentFilter.
     */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        // intentFilter.addAction(BCMBleService.ACTION_GATT_CONNECTED);
        // intentFilter.addAction(BCMBleService.ACTION_GATT_DISCONNECTED);
        // intentFilter
        // .addAction(BCMBleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BCMBleService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, this.getClass().getSimpleName() + ": onCreate");
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_activity_plot);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
        setContentView(R.layout.activity_plot);
        mPlot = findViewById(R.id.plot);

        // Get whether to plot a session or current
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mIsSession = extras.getBoolean(PLOT_SESSION_CODE, false);
            if (mIsSession) {
                mPlotSessionStart = extras.getLong(
                        PLOT_SESSION_START_TIME_CODE, INVALID_DATE);
            }
        }
        if (mIsSession && (mPlotSessionStart == INVALID_DATE)) {
            Utils.errMsg(this, "Plotting a session but got invalid "
                    + "values for the start time");
            return;
        }

        // Get the database name from the default preferences
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        String prefString = prefs.getString(PREF_DATA_DIRECTORY, null);
        if (prefString == null) {
            Utils.errMsg(this, "Cannot find the name of the data directory");
            return;
        }

        // Open the database
        mDataDir = new File(prefString);
        if (!mDataDir.exists()) {
            Utils.errMsg(this, "Cannot find database directory: " + mDataDir);
            mDataDir = null;
            return;
        }
        mDbAdapter = new BCMDbAdapter(this, mDataDir);
        mDbAdapter.open();

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onResume: " + "mPlot="
                + mPlot + " hrSeries=" + hrSeries + " rrSeries="
                + rrSeries);
        super.onResume();
        // Get the settings
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        mPlotHr = prefs.getBoolean(PREF_PLOT_HR, true);
        mPlotRr = prefs.getBoolean(PREF_PLOT_RR, true);
        String stringVal = prefs.getString(PREF_PLOT_INTERVAL, null);
        if (stringVal == null) {
            mPlotInterval = PLOT_MAXIMUM_AGE;
        } else {
            try {
                mPlotInterval = 60000 * Integer.parseInt(stringVal);
            } catch (Exception ex) {
                mPlotInterval = PLOT_MAXIMUM_AGE;
            }
        }
        if (mPlot != null) {
            createPlot();
        } else {
            Log.d(TAG, getClass().getSimpleName() + ".onResume: mPlot is null");
            returnResult(RESULT_ERROR, "mPlot is null");
        }
        // If mIsSession is true it uses mPlotSessionStart, set in onCreate
        // If mIsSession is false it uses mPlotStartTime, set here
        if (!mIsSession) {
            Log.d(TAG, "onResume: Starting registerReceiver");
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            mPlotStartTime = new Date().getTime() - mPlotInterval;
            // Make it keep the screen on
            getWindow()
                    .addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            // Make it not keep the screen on
            getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        // Create the datasets and fill them
        refresh();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onPause");
        super.onPause();
        if (!mIsSession) {
            unregisterReceiver(mGattUpdateReceiver);
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onDestroy");
        super.onDestroy();
        if (mDbAdapter != null) {
            mDbAdapter.close();
            mDbAdapter = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_plot, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_zoom_reset:
                if (mPlot != null) {
                    mPlot.setRangeBoundaries(50, 100,
                            BoundaryMode.AUTO);
                    mPlot.setDomainBoundaries(0, 24 * 3600 * 1000,
                            BoundaryMode.AUTO);
                    update();
                }
                return true;
            case R.id.menu_refresh:
                refresh();
                return true;
            case R.id.get_view_info:
                Utils.infoMsg(this, getPlotInfo());
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Sets the result code to send back to the calling Activity.
     *
     * @param resultCode The result code to send.
     */
    private void returnResult(int resultCode, String msg) {
        Intent data = new Intent();
        if (msg != null) {
            data.putExtra(MSG_CODE, msg);
        }
        setResult(resultCode, data);
        finish();
    }

    /**
     * Displays the error from an ACTION_ERROR callback.
     *
     * @param intent The Intent used to display the error.
     */
    private void displayError(Intent intent) {
        String msg = null;
        try {
            msg = intent.getStringExtra(EXTRA_MSG);
            if (msg == null) {
                Utils.errMsg(this, "Received null error message");
                return;
            }
            Utils.errMsg(this, msg);
        } catch (Exception ex) {
            Log.d(TAG, this.getClass().getSimpleName() + ": displayError: " +
                    "Error displaying error", ex);
            Utils.excMsg(this, msg, ex);
        }
    }

    /**
     * Refreshes the mPlot by getting new data.
     */
    private void refresh() {
        // The logic for whether it is a session or not is in this method
        createSeries();
    }

    private void createPlot() {
        Log.d(TAG, this.getClass().getSimpleName() + ": createPlot");
        hrFormatter = new LineAndPointFormatter(Color.RED,
                null, null, null);
        hrFormatter.setLegendIconEnabled(false);
        rrFormatter = new LineAndPointFormatter(Color.rgb(0, 153, 255),
                null, null, null);
        rrFormatter.setLegendIconEnabled(false);

        mPlot.setRangeBoundaries(50, 100,
                BoundaryMode.AUTO);
        mPlot.setDomainBoundaries(0, 360000,
                BoundaryMode.AUTO);
        // Range labels will increment by 10
        mPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 10);
//        mPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 60000); // 1 min
        mPlot.setDomainStep(StepMode.SUBDIVIDE, 5);
        // Make left labels be an integer (no decimal places)
        mPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).
                setFormat(new DecimalFormat("#"));
        mPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new Format() {
            private final SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "HH:mm", Locale.US);

            @Override
            public StringBuffer format(Object obj,
                                       @NonNull StringBuffer toAppendTo,
                                       @NonNull FieldPosition pos) {
                int yearIndex = (int) Math.round(((Number) obj).doubleValue());
                return dateFormat.format(yearIndex, toAppendTo, pos);
            }

            @Override
            public Object parseObject(String source,
                                      @NonNull ParsePosition pos) {
                return null;
            }
        });

//        // This adds sub-grid lines in the default color 180,180,180 (#646464)
//        mPlot.setLinesPerRangeLabel(2);
//        // No resource to set this, and this makes them disappear
//        mPlot.getGraph().setRangeSubGridLinePaint(new Paint(Color.rgb(90, 90,
//                90)));


        // Pan and Zoom
        PanZoom.attach(mPlot, PanZoom.Pan.BOTH, PanZoom.Zoom.STRETCH_BOTH);
    }

    /**
     * Creates the data sets and series.
     */
    private void createSeries() {
        Log.d(TAG, "Creating series");
        if (!mPlotHr && !mPlotRr) {
            Utils.errMsg(this, "Neither HR nor RR is selected to be plotted");
        }
        if (mPlotHr) {
            hrSeries = new SimpleXYSeries("HR");
//            if (!mIsSession) {
//                mHrSeries.setMaximumItemAge(mPlotInterval);
//            }
        } else {
            hrSeries = null;
        }
        if (mPlotRr) {
            rrSeries = new SimpleXYSeries("RR");
//            if (!mIsSession) {
//                mRrSeries.setMaximumItemAge(mPlotInterval);
//            }
        } else {
            rrSeries = null;
        }
        mLastRrTime = INVALID_DATE;
        mLastRrUpdateTime = INVALID_DATE;
        Cursor cursor = null;
        int nHrItems = 0, nRrItems = 0;
        int nErrors = 0;
        boolean res;
        try {
            if (mDbAdapter != null) {
                if (mIsSession) {
                    cursor = mDbAdapter
                            .fetchAllHrRrDateDataForStartDate
                                    (mPlotSessionStart);
                } else {
                    cursor = mDbAdapter
                            .fetchAllHrRrDateDataStartingAtDate
                                    (mPlotStartTime);
                }
                int indexDate = cursor.getColumnIndex(COL_DATE);
                int indexHr = mPlotHr ? cursor.getColumnIndex(COL_HR) : -1;
                int indexRr = mPlotRr ? cursor.getColumnIndex(COL_RR) : -1;

                // Loop over items
                cursor.moveToFirst();
                long date;
                double hr;
                String rrString;
                while (!cursor.isAfterLast()) {
                    date = cursor.getLong(indexDate);
                    if (indexHr > -1) {
                        hr = cursor.getInt(indexHr);
                        if (hr == INVALID_INT) {
                            hr = Double.NaN;
                        }
                        hrSeries.addLast(date, hr);
                        nHrItems++;
                    }
                    if (indexRr > -1) {
                        rrString = cursor.getString(indexRr);
                        if (nRrItems == 0) {
                            mLastRrUpdateTime = date;
                            mLastRrTime = date - INITIAL_RR_START_TIME;
                        }
                        res = addRrValues(date, rrString);
                        nRrItems++;
                        if (!res) {
                            nErrors++;
                        }
                    }
                    cursor.moveToNext();
                }
            }
        } catch (Exception ex) {
            Utils.excMsg(this, "Error creating datasets", ex);
        } finally {
            try {
                if (cursor != null) cursor.close();
            } catch (Exception ex) {
                // Do nothing
            }
        }
        if (nErrors > 0) {
            Utils.errMsg(this, nErrors + " creating RR series");
        }
        if (mPlotHr) {
            Log.d(TAG, "HR series created with " + nHrItems + " items");
            mPlot.addSeries(hrSeries, hrFormatter);
        }
        if (mPlotRr) {
            Log.d(TAG, "RR series created with " + nRrItems
                    + " items with nErrors=" + nErrors);
            mPlot.addSeries(rrSeries, rrFormatter);
        }
    }

    /**
     * Add new values to the plot when received from the Gatt broadcast
     * receiver.  This only happens when not in a session.
     *
     * @param intent Intent from the mGattUpdateReceiver.
     */
    public void addValues(Intent intent) {
//        Log.d(TAG, this.getClass().getSimpleName() + ": addValues: mPlotHr="
//                + mPlotHr + " mPlotRr=" + mPlotRr);
        String strValue;
        double value;
        long date = intent.getLongExtra(EXTRA_DATE, INVALID_DATE);
        if (date == INVALID_DATE) {
            Log.d(TAG, this.getClass().getSimpleName() + ": addValues: " +
                    "INVALID_DATE");
            return;
        }
        //
        if (mPlotHr) {
            strValue = intent.getStringExtra(EXTRA_HR);
            if (strValue != null && strValue.length() > 0) {
                try {
                    value = Double.parseDouble(strValue);
                } catch (NumberFormatException ex) {
                    value = Double.NaN;
                }
                if (value == INVALID_INT) {
                    value = Double.NaN;
                }
                hrSeries.addLast(date, value);
            }
        }
        if (mPlotRr) {
            if (mLastRrUpdateTime == INVALID_DATE) {
                mLastRrUpdateTime = date;
                mLastRrTime = date;
            }
            strValue = intent.getStringExtra(EXTRA_RR);
            if (strValue != null && strValue.length() > 0) {
                // boolean res = addRrValues(mRrSeries, date, strValue);
                // Don't check for errors here to avoid error storms
                addRrValues(date, strValue);
            }

            if (mLastRrUpdateTime == INVALID_DATE) {
                mLastRrUpdateTime = date;
                mLastRrTime = date;
            }
        }
        update();
    }

    /**
     * Parses the RR String and adds items to the series at the appropriate
     * times.
     *
     * @param updateTime The time of this update.
     * @param strValue   The RR String from the database.
     * @return If the operation was successful.
     */
    private boolean addRrValues(long updateTime,
                                String strValue) {
        if (strValue.length() == 0) {
            // Do nothing
            return true;
        }
        if (strValue.equals(INVALID_STRING)) {
            mLastRrUpdateTime = updateTime;
            mLastRrTime = updateTime - INITIAL_RR_START_TIME;
            return true;
        }
        String[] tokens;
        tokens = strValue.trim().split("\\s+");
        int nRrValues = tokens.length;
        if (nRrValues == 0) {
            return false;
        }
        long[] times = new long[nRrValues];
        double[] values = new double[nRrValues];
        long lastRrTime = mLastRrTime;
        double val;
        for (int i = 0; i < nRrValues; i++) {
            try {
                val = Double.parseDouble(tokens[i]);
            } catch (NumberFormatException ex) {
                return false;
            }
            lastRrTime += val;
            times[i] = lastRrTime;
            values[i] = val / 1.024;
        }
        // Make first rr time be >= mLastRrUpdateTime
        long deltaTime;
        long firstTime = times[0];
        if (firstTime < mLastRrUpdateTime) {
            deltaTime = mLastRrUpdateTime - firstTime;
            for (int i = 0; i < nRrValues; i++) {
                times[i] += deltaTime;
            }
        }
        // Make all times be <= updateTime. Overrides previous if necessary.
        long lastTime = times[nRrValues - 1];
        if (times[nRrValues - 1] > updateTime) {
            deltaTime = lastTime - updateTime;
            for (int i = 0; i < nRrValues; i++) {
                times[i] -= deltaTime;
            }
        }
        double rr;
        for (int i = 0; i < nRrValues; i++) {
            rr = RR_SCALE * values[i];
            rrSeries.addLast(times[i], rr);
        }
        mLastRrUpdateTime = updateTime;
        mLastRrTime = times[nRrValues - 1];
        return true;
    }

    /**
     * Gets info about the view.
     */
    private String getPlotInfo() {
        final String LF = "\n";
        StringBuilder sb = new StringBuilder();
        if (mPlot == null) {
            sb.append("View is null");
            return sb.toString();
        }
        sb.append("Title=").append(mPlot.getTitle().getText()).append(LF);
        sb.append("Range Title=").append(mPlot.getRangeTitle().getText()).append(LF);
        sb.append("Domain Title=").append(mPlot.getDomainTitle().getText()).append(LF);
        sb.append("Range Origin=").append(mPlot.getRangeOrigin()).append(LF);
        long timeVal = mPlot.getDomainOrigin().longValue();
        Date date = new Date(timeVal);
        sb.append("Domain Origin=").append(date.toString()).append(LF);
        sb.append("Range Step Value=").append(mPlot.getRangeStepValue()).append(LF);
        sb.append("Domain Step Value=").append(mPlot.getDomainStepValue()).append(LF);
        sb.append("Graph Width=").append(mPlot.getGraph().getSize().getWidth().getValue()).append(LF);
        sb.append("Graph Height=").append(mPlot.getGraph().getSize().getHeight().getValue()).append(LF);
        if (hrSeries != null) {
            if (hrSeries.getxVals() != null) {
                sb.append("hrSeries Size=").append(hrSeries.getxVals().size()).append(LF);
            }
        } else {
            sb.append("hrSeries=Null").append(LF);
        }
        if (rrSeries != null) {
            if (rrSeries.getxVals() != null) {
                sb.append("rrSeries Size=").append(rrSeries.getxVals().size()).append(LF);
            }
        } else {
            sb.append("rrSeries=Null").append(LF);
        }
        return sb.toString();
    }

    public void update() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPlot.redraw();
            }
        });
    }
}
