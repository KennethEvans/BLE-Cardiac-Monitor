package net.kenevans.android.blecardiacmonitor;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeriesFormatter;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * @author evans
 */
public class PlotActivity extends AppCompatActivity implements IConstants,
        PlotterListener {
    private static final String TAG = "BCM Plot";
    private XYPlot mPlot;

    //    private AFreeChartView mView;
//    private AFreeChart mChart;
//    private TimeSeriesCollection mHrDataset;
//    private TimeSeriesCollection mRrDataset;
//    private TimeSeriesCollection mActDataset;
//    private TimeSeriesCollection mPaDataset;
//    private TimeSeries mHrSeries;
//    private TimeSeries mRrSeries;
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

    private static final int NVALS = 300;  // 5 min
    private double RR_SCALE = .1;
    // to ms
    private Context context;
    private XYSeriesFormatter hrFormatter;
    private XYSeriesFormatter rrFormatter;
    private SimpleXYSeries hrSeries;
    private SimpleXYSeries rrSeries;
    private Double[] xHrVals = new Double[NVALS];
    private Double[] yHrVals = new Double[NVALS];
    private Double[] xRrVals = new Double[NVALS];
    private Double[] yRrVals = new Double[NVALS];


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
                        // Log.d(TAG, "onReceive: " + action);
                        addValues(intent);
                    } else if (BCMBleService.ACTION_ERROR.equals(action)) {
                        Log.d(TAG, "onReceive: " + action);
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

        // Initialize plots
        Date now = new Date();
        double endTime = now.getTime();
        double startTime = endTime - NVALS * 1000;
        double delta = (endTime - startTime) / (NVALS - 1);

        // Specify initial values to keep it from auto sizing
        for (int i = 0; i < NVALS; i++) {
            xHrVals[i] = new Double(startTime + i * delta);
            yHrVals[i] = new Double(60);
            xRrVals[i] = new Double(startTime + i * delta);
            yRrVals[i] = new Double(100);
        }

        hrFormatter = new LineAndPointFormatter(Color.RED,
                null, null, null);
        hrFormatter.setLegendIconEnabled(false);
        hrSeries = new SimpleXYSeries(Arrays.asList(xHrVals),
                Arrays.asList(yHrVals),
                "HR");

        rrFormatter = new LineAndPointFormatter(Color.BLUE,
                null, null, null);
        rrFormatter.setLegendIconEnabled(false);
        rrSeries = new SimpleXYSeries(Arrays.asList(xRrVals),
                Arrays.asList(yRrVals),
                "HR");

        mPlot.addSeries(hrSeries, hrFormatter);
        mPlot.addSeries(rrSeries, rrFormatter);

        mPlot.setRangeBoundaries(50, 100,
                BoundaryMode.AUTO);
        mPlot.setDomainBoundaries(0, 360000,
                BoundaryMode.AUTO);
        // Left labels will increment by 10
        mPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 10);
        mPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 60000);
        // Make left labels be an integer (no decimal places)
        mPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).
                setFormat(new DecimalFormat("#"));
        // These don't seem to have an effect
        mPlot.setLinesPerRangeLabel(2);


        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);
    }

    @Override
    protected void onResume() {
//        Log.d(TAG, this.getClass().getSimpleName() + ": onResume: " + "mView="
//                + mView + " mHrDataset=" + mHrDataset + " mHrSeries="
//                + mHrSeries);
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
        // If mSession is true it uses mPlotSessionStart, set in onCreate
        // If mSession is false it uses mPlotStartTime, set here
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
            case R.id.menu_refresh:
                refresh();
                return true;
            case R.id.get_view_info:
                Utils.infoMsg(this, getViewInfo());
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
            Log.d(TAG, "Error displaying error", ex);
            Utils.excMsg(this, msg, ex);
        }
    }

    /**
     * Refreshes the mPlot by getting new data.
     */
    private void refresh() {
//        // The logic for whether it is a session or not is in these methods
//        createDatasets();
//        ((XYPlot) mChart.getPlot()).setDataset(1, mHrDataset);
//        ((XYPlot) mChart.getPlot()).setDataset(2, mRrDataset);
//        ((XYPlot) mChart.getPlot()).setDataset(3, mActDataset);
//        ((XYPlot) mChart.getPlot()).setDataset(4, mPaDataset);
    }

    private void createPlot() {
        Date now = new Date();
        double endTime = now.getTime();
        double startTime = endTime - NVALS * 1000;
        double delta = (endTime - startTime) / (NVALS - 1);

        // Specify initial values to keep it from auto sizing
        for (int i = 0; i < NVALS; i++) {
            xHrVals[i] = new Double(startTime + i * delta);
            yHrVals[i] = new Double(60);
            xRrVals[i] = new Double(startTime + i * delta);
            yRrVals[i] = new Double(100);
        }

        hrFormatter = new LineAndPointFormatter(Color.RED,
                null, null, null);
        hrFormatter.setLegendIconEnabled(false);
        hrSeries = new SimpleXYSeries(Arrays.asList(xHrVals),
                Arrays.asList(yHrVals),
                "HR");

        rrFormatter = new LineAndPointFormatter(Color.BLUE,
                null, null, null);
        rrFormatter.setLegendIconEnabled(false);
        rrSeries = new SimpleXYSeries(Arrays.asList(xRrVals),
                Arrays.asList(yRrVals),
                "HR");
    }

    public void addValues(Intent intent) {
        Log.d(TAG, "updateChart");
        String strValue;
        double value;
        long date = intent.getLongExtra(EXTRA_DATE, INVALID_DATE);
        if (date == INVALID_DATE) {
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
                // Reset the series
                for (int i = 0; i < NVALS - 1; i++) {
                    xHrVals[i] = xHrVals[i + 1];
                    yHrVals[i] = yHrVals[i + 1];
                    hrSeries.setXY(xHrVals[i], yHrVals[i], i);
                }
                xHrVals[NVALS - 1] = new Double(date);
                yHrVals[NVALS - 1] = new Double(value);
                hrSeries.setXY(xHrVals[NVALS - 1], yHrVals[NVALS - 1],
                        NVALS - 1);
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
            // TODO
            mLastRrUpdateTime = updateTime;
            mLastRrTime = updateTime - INITIAL_RR_START_TIME;
//            series.addOrUpdate(new FixedMillisecond(updateTime), Double.NaN);
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
        // Make all times be >= mLastRrUpdateTime
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
        // Reset the series
        for (int i = 0; i < NVALS - nRrValues; i++) {
            xRrVals[i] = xRrVals[i + 1];
            yRrVals[i] = yRrVals[i + 1];
            rrSeries.setXY(xRrVals[i], yRrVals[i], i);
        }
        double totalRR = 0;
//        for (int i = 0; i < nRrValues; i++) {
//            totalRR += RR_SCALE * values[i];
//        }
        int index = 0;
        double rr;
        for (int i = NVALS - nRrValues; i < NVALS; i++) {
            rr = RR_SCALE * values[index++];
            xRrVals[i] = new Double(updateTime - totalRR);
            yRrVals[i] = new Double(rr);
//            totalRR -= rr;
            rrSeries.setXY(xRrVals[i], yRrVals[i], i);
        }
        mLastRrUpdateTime = updateTime;
        mLastRrTime = times[nRrValues - 1];
        return true;
    }

    /**
     * Gets info about the view.
     */
    private String getViewInfo() {
//        String info = "";
//        if (mView == null) {
//            info += "View is null";
//            return info;
//        }
//        Dimension size = mView.getSize();
//        RectangleInsets insets = mView.getInsets();
//        RectShape available = new RectShape(insets.getLeft(), insets.getTop(),
//                size.getWidth() - insets.getLeft() - insets.getRight(),
//                size.getHeight() - insets.getTop() - insets.getBottom());
//
//        info += "Size=(" + size.getWidth() + "," + size.getHeight() + ")\n";
//        info += "Available=(" + available.getWidth() + ","
//                + available.getHeight() + ") @ (" + available.getCenterX()
//                + "," + available.getCenterY() + ")\n";
//        int minimumDrawWidth = mView.getMinimumDrawWidth();
//        int maximumDrawWidth = mView.getMaximumDrawWidth();
//        int minimumDrawHeight = mView.getMinimumDrawHeight();
//        int maximumDrawHeight = mView.getMaximumDrawHeight();
//        info += "minimumDrawWidth=" + minimumDrawWidth + " maximumDrawWidth="
//                + maximumDrawWidth + "\n";
//        info += "minimumDrawHeight=" + minimumDrawHeight
//                + " maximumDrawHeight=" + maximumDrawHeight + "\n";
//        double chartScaleX = mView.getChartScaleX();
//        double chartScaleY = mView.getChartScaleY();
//        info += "chartScaleX=" + chartScaleX + " chartScaleY=" + chartScaleY
//                + "\n";
//        Display display = getWindowManager().getDefaultDisplay();
//        Point displaySize = new Point();
//        display.getSize(displaySize);
//        info += "displayWidth=" + displaySize.x + " displayHeight="
//                + displaySize.y + "\n";
//
//        return info;
        return "Not implemented yet";
    }

    /**
     * Creates a chart.
     * <p/>
     * F	 * @return The chart.
     */
//    private AFreeChart createChart() {
//        Log.d(TAG, "createChart");
//        final boolean doLegend = true;
//        AFreeChart chart = ChartFactory.createTimeSeriesChart(null, // title
//                "Time", // x-axis label
//                null, // y-axis label
//                null, // data
//                doLegend, // create legend?
//                true, // generate tooltips?
//                false // generate URLs?
//        );
//
//        SolidColor white = new SolidColor(Color.WHITE);
//        SolidColor black = new SolidColor(Color.BLACK);
//        SolidColor gray = new SolidColor(Color.GRAY);
//        SolidColor ltgray = new SolidColor(Color.LTGRAY);
//        SolidColor hrColor = new SolidColor(Color.argb(255, 255, 50, 50));
//        SolidColor rrColor = new SolidColor(Color.argb(255, 0, 153, 255));
//
//        Font font = new Font("SansSerif", Typeface.NORMAL, 30);
//        Font titleFont = new Font("SansSerif", Typeface.BOLD, 36);
//
//        float strokeSize = 5f;
//
//        // Chart
//        chart.setTitle("BLE Cardiac Monitor");
//        TextTitle title = chart.getTitle();
//        title.setFont(titleFont);
//        title.setPaintType(white);
//        chart.setBackgroundPaintType(black);
//        // chart.setBorderPaintType(white);
//        chart.setBorderVisible(false);
//        // chart.setPadding(new RectangleInsets(10.0, 10.0, 10.0, 10.0));
//
//        // Legend
//        LegendTitle legend = chart.getLegend();
//        legend.setItemFont(font);
//        legend.setBackgroundPaintType(black);
//        legend.setItemPaintType(white);
//
//        // Plot
//        XYPlot mPlot = (XYPlot) chart.getPlot();
//        mPlot.setBackgroundPaintType(black);
//        mPlot.setDomainGridlinePaintType(gray);
//        mPlot.setRangeGridlinePaintType(gray);
//        mPlot.setOutlineVisible(true);
//        mPlot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
//        // TODO Find out what these mean
//        // mPlot.setDomainCrosshairVisible(true);
//        // mPlot.setRangeCrosshairVisible(true);
//        XYItemRenderer renderer = mPlot.getRenderer();
//        if (renderer instanceof XYLineAndShapeRenderer) {
//            XYLineAndShapeRenderer lineShapeRenderer =
//                    (XYLineAndShapeRenderer) renderer;
//            lineShapeRenderer.setBaseShapesVisible(true);
//            lineShapeRenderer.setBaseShapesFilled(true);
//            lineShapeRenderer.setDrawSeriesLineAsPath(true);
//        }
//
//        // X axis
//        DateAxis xAxis = (DateAxis) mPlot.getDomainAxis();
//        xAxis.setDateFormatOverride(new SimpleDateFormat("hh:mm", Locale.US));
//        xAxis.setLabelFont(font);
//        xAxis.setLabelPaintType(white);
//        xAxis.setAxisLinePaintType(white);
//        xAxis.setTickLabelFont(font);
//        xAxis.setTickLabelPaintType(ltgray);
//
//        // HR
//        if (mPlotHr) {
//            final int axisNum = 1;
//            NumberAxis axis = new NumberAxis(null);
//            mPlot.setRangeAxis(axisNum, axis);
//            mPlot.setDataset(axisNum, mHrDataset);
//            mPlot.mapDatasetToRangeAxis(axisNum, axisNum);
//            mPlot.setRangeAxisLocation(axisNum, AxisLocation.BOTTOM_OR_LEFT);
//            XYItemRenderer itemRenderer = new StandardXYItemRenderer();
//            itemRenderer.setSeriesPaintType(0, hrColor);
//            itemRenderer.setBaseStroke(strokeSize);
//            itemRenderer.setSeriesStroke(0, strokeSize);
//            mPlot.setRenderer(axisNum, itemRenderer);
//
//            axis.setAutoRangeIncludesZero(true);
//            axis.setAutoRangeMinimumSize(10);
//            axis.setTickUnit(new NumberTickUnit(5));
//            // yAxis0.setLabelFont(font);
//            // yAxis0.setLabelPaintType(color);
//            axis.setAxisLinePaintType(hrColor);
//            axis.setTickLabelFont(font);
//            axis.setTickLabelPaintType(hrColor);
//        }
//
//        // RR
//        if (mPlotRr) {
//            final int axisNum = 2;
//            NumberAxis axis = new NumberAxis(null);
//            mPlot.setRangeAxis(axisNum, axis);
//            mPlot.setDataset(axisNum, mRrDataset);
//            mPlot.mapDatasetToRangeAxis(axisNum, axisNum);
//            mPlot.setRangeAxisLocation(axisNum, AxisLocation.BOTTOM_OR_RIGHT);
//            XYItemRenderer itemRenderer = new StandardXYItemRenderer();
//            itemRenderer.setSeriesPaintType(0, rrColor);
//            itemRenderer.setBaseStroke(strokeSize);
//            itemRenderer.setSeriesStroke(0, strokeSize);
//            mPlot.setRenderer(axisNum, itemRenderer);
//
//            axis.setAutoRangeIncludesZero(true);
//            axis.setAutoRangeMinimumSize(.25);
//            // axis.setTickUnit(new NumberTickUnit(.1));
//            // yAxis1.setLabelFont(font);
//            // yAxis1.setLabelPaintType(color);
//            axis.setLabelPaintType(rrColor);
//            axis.setAxisLinePaintType(rrColor);
//            axis.setTickLabelFont(font);
//            axis.setTickLabelPaintType(rrColor);
//        }
//        return chart;
//    }

    /**
     * Updates the chart when data is received from the BCMBleService.
     *
     * @param intent THe Intent used.
     */
//    private void updateChart(Intent intent) {
//        Log.d(TAG, "updateChart");
//        String strValue;
//        double value;
//        long date = intent.getLongExtra(EXTRA_DATE, INVALID_DATE);
//        if (date == INVALID_DATE) {
//            return;
//        }
//        if (mPlotHr && mHrSeries != null) {
//            strValue = intent.getStringExtra(EXTRA_HR);
//            if (strValue != null && strValue.length() > 0) {
//                try {
//                    value = Double.parseDouble(strValue);
//                } catch (NumberFormatException ex) {
//                    value = Double.NaN;
//                }
//                if (value == INVALID_INT) {
//                    value = Double.NaN;
//                }
//                mHrSeries.addOrUpdate(new FixedMillisecond(date), value);
//            }
//        }
//        if (mPlotRr && mRrSeries != null) {
//
//            if (mLastRrUpdateTime == INVALID_DATE) {
//                mLastRrUpdateTime = date;
//                mLastRrTime = date;
//            }
//            strValue = intent.getStringExtra(EXTRA_RR);
//            if (strValue != null && strValue.length() > 0) {
//                // boolean res = addRrValues(mRrSeries, date, strValue);
//                // Don't check for errors here to avoid error storms
//                addRrValues(mRrSeries, date, strValue);
//            }
//        }
//    }

    /**
     * Creates the data sets and series.
     */
//    private void createDatasets() {
//        Log.d(TAG, "Creating datasets");
//        if (!mPlotHr && !mPlotRr) {
//            Utils.errMsg(this, "Neither HR nor RR is selected to be plotted");
//        }
//        if (mPlotHr) {
//            mHrSeries = new TimeSeries("HR");
//            if (!mIsSession) {
//                mHrSeries.setMaximumItemAge(mPlotInterval);
//            }
//        } else {
//            mHrSeries = null;
//        }
//        if (mPlotRr) {
//            mRrSeries = new TimeSeries("RR");
//            if (!mIsSession) {
//                mRrSeries.setMaximumItemAge(mPlotInterval);
//            }
//        } else {
//            mRrSeries = null;
//        }
//        mLastRrTime = INVALID_DATE;
//        mLastRrUpdateTime = INVALID_DATE;
//        Cursor cursor = null;
//        int nHrItems = 0, nRrItems = 0;
//        int nErrors = 0;
//        boolean res;
//        try {
//            if (mDbAdapter != null) {
//                if (mIsSession) {
//                    cursor = mDbAdapter
//                            .fetchAllHrRrDateDataForStartDate
//                                    (mPlotSessionStart);
//                } else {
//                    cursor = mDbAdapter
//                            .fetchAllHrRrDateDataStartingAtDate
// (mPlotStartTime);
//                }
//                int indexDate = cursor.getColumnIndex(COL_DATE);
//                int indexHr = mPlotHr ? cursor.getColumnIndex(COL_HR) : -1;
//                int indexRr = mPlotRr ? cursor.getColumnIndex(COL_RR) : -1;
//
//                // Loop over items
//                cursor.moveToFirst();
//                long date;
//                double hr;
//                String rrString;
//                while (!cursor.isAfterLast()) {
//                    date = cursor.getLong(indexDate);
//                    if (indexHr > -1) {
//                        hr = cursor.getInt(indexHr);
//                        if (hr == INVALID_INT) {
//                            hr = Double.NaN;
//                        }
//                        mHrSeries.addOrUpdate(new FixedMillisecond(date), hr);
//                        nHrItems++;
//                    }
//                    if (indexRr > -1) {
//                        rrString = cursor.getString(indexRr);
//                        if (nRrItems == 0) {
//                            mLastRrUpdateTime = date;
//                            mLastRrTime = date - INITIAL_RR_START_TIME;
//                        }
//                        res = addRrValues(mRrSeries, date, rrString);
//                        nRrItems++;
//                        if (!res) {
//                            nErrors++;
//                        }
//                    }
//                    cursor.moveToNext();
//                }
//            }
//        } catch (Exception ex) {
//            Utils.excMsg(this, "Error creating datasets", ex);
//        } finally {
//            try {
//                if (cursor != null) cursor.close();
//            } catch (Exception ex) {
//                // Do nothing
//            }
//        }
//        if (nErrors > 0) {
//            Utils.errMsg(this, nErrors + " creating RR dataset");
//        }
//        if (mPlotHr) {
//            Log.d(TAG, "HR dataset created with " + nHrItems + " items");
//            mHrDataset = new TimeSeriesCollection();
//            mHrDataset.addSeries(mHrSeries);
//        }
//        if (mPlotRr) {
//            Log.d(TAG, "RR dataset created with " + nRrItems
//                    + " items nErrors=" + nErrors);
//            mRrDataset = new TimeSeriesCollection();
//            mRrDataset.addSeries(mRrSeries);
//        }
//    }
    public void update() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPlot.redraw();
            }
        });
    }

}
