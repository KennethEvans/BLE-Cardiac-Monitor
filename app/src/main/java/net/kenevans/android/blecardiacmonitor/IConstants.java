//Copyright (c) 2011 Kenneth Evans
//
//Permission is hereby granted, free of charge, to any person obtaining
//a copy of this software and associated documentation files (the
//"Software"), to deal in the Software without restriction, including
//without limitation the rights to use, copy, modify, merge, publish,
//distribute, sublicense, and/or sell copies of the Software, and to
//permit persons to whom the Software is furnished to do so, subject to
//the following conditions:
//
//The above copyright notice and this permission notice shall be included
//in all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
//EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
//MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
//IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
//CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
//TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
//SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package net.kenevans.android.blecardiacmonitor;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Holds constant values used by several classes in the application.
 */
public interface IConstants {
    /**
     * Used for SharedPreferences
     */
    String MAIN_ACTIVITY = "DeviceMonitorActivity";
    /**
     * Tag to associate with log messages.
     */
    String TAG = "BCM";
    /**
     * Name of the package for this application.
     */
    String PACKAGE_NAME = "net.kenevans.android.blecardiacmonitor";
    /**
     * Prefix for session names. Will be followed by a date and time.
     */
    String SESSION_NAME_PREFIX = "BCM-";

    /**
     * Key for information URL sent to InfoActivity.
     */
    String INFO_URL = "InformationURL";

    /**
     * Notification ID for managing notifications.
     */
    int NOTIFICATION_ID = 1;

    /**
     * Initial start time for RR values.
     */
    long INITIAL_RR_START_TIME = 0;

    // Base
    /**
     * Base string for standard UUIDS. These UUIDs differ in characters 4-7.
     */
    String BASE_UUID = "00000000-0000-1000-8000-00805f9b34fb";

    // Services
    UUID UUID_BATTERY_SERVICE = UUID
            .fromString("0000180f-0000-1000-8000-00805f9b34fb");
    UUID UUID_DEVICE_INFORMATION_SERVICE = UUID
            .fromString("0000180a-0000-1000-8000-00805f9b34fb");
    UUID UUID_HEART_RATE_SERVICE = UUID
            .fromString("0000180d-0000-1000-8000-00805f9b34fb");
    UUID UUID_HXM_CUSTOM_DATA_SERVICE = UUID
            .fromString("befdff10-c979-11e1-9b21-0800200c9a66");

    // Characteristics
    UUID UUID_BATTERY_LEVEL = UUID
            .fromString("00002a19-0000-1000-8000-00805f9b34fb");
    UUID UUID_CUSTOM_MEASUREMENT = UUID
            .fromString("befdff11-c979-11e1-9b21-0800200c9a66");
    UUID UUID_DEVICE_NAME = UUID
            .fromString("00002a00-0000-1000-8000-00805f9b34fb");
    UUID UUID_HEART_RATE_MEASUREMENT = UUID
            .fromString("00002a37-0000-1000-8000-00805f9b34fb");
    UUID UUID_TEST_MODE = UUID
            .fromString("befdffb1-c979-11e1-9b21-0800200c9a66");
    UUID UUID_BODY_SENSOR_LOCATION = UUID
            .fromString("00002a38-0000-1000-8000-00805f9b34fb");
    UUID UUID_MODEL_NUMBER_STRING = UUID
            .fromString("00002a24-0000-1000-8000-00805f9b34fb");
    UUID UUID_FIRMWARE_REVISION_STRING = UUID
            .fromString("00002a26-0000-1000-8000-00805f9b34fb");
    UUID UUID_APPEARANCE = UUID
            .fromString("00002a01-0000-1000-8000-00805f9b34fb");

    UUID UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID
            .fromString("00002902-0000-1000-8000-00805f9b34fb");

    /**
     * Directory on the SD card where the database is stored
     */
    String SD_CARD_DB_DIRECTORY = "BLE Cardiac Monitor";

    // Preferences
    String PREF_MONITOR_HR = "monitorHrPreference";
    String PREF_MONITOR_CUSTOM = "monitorCustomPreference";
    String PREF_READ_BATTERY = "readBatteryPreference";
    String PREF_PLOT_HR = "plotHrPreference";
    String PREF_PLOT_RR = "plotRrPreference";
    String PREF_PLOT_ACT = "plotActPreference";
    String PREF_PLOT_PA = "plotPaPreference";
    String PREF_PLOT_INTERVAL = "plotIntervalPreference";
    String PREF_MANUALLY_DISCONNECTED = "manuallyDisconnected";
    String PREF_TREE_URI = "tree_uri";

    // Session
    int SESSION_IDLE = 0;
    int SESSION_WAITING_BAT = 1;
    int SESSION_WAITING_HR = 2;
    int SESSION_WAITING_CUSTOM = 3;

    /**
     * Flag for specifying to do Battery Level.
     */
    int DO_NOTHING = 0;
    /**
     * Flag for specifying to do Battery Level.
     */
    int DO_BAT = 1;
    /**
     * Flag for specifying to do HR.
     */
    int DO_HR = 2;
    /**
     * Flag for specifying to do Custom.
     */
    int DO_CUSTOM = 4;

    /**
     * Value for a database date value indicating invalid.
     */
    long INVALID_DATE = Long.MIN_VALUE;
    /**
     * Value for a database int indicating invalid.
     */
    int INVALID_INT = -1;
    /**
     * Value for a database String indicating invalid.
     */
    String INVALID_STRING = "Invalid";

    // Timers
    /**
     * Timer timeout for accumulating characteristics (ms).
     */
    long CHARACTERISTIC_TIMER_TIMEOUT = 100;
    /**
     * Timer interval for accumulating characteristics (ms).
     */
    long CHARACTERISTIC_TIMER_INTERVAL = 1;
    /**
     * Timer timeout for getting custom value (ms).
     */
    long CUSTOM_NOTIFY_TIMER_TIMEOUT = 500;

    // Database
    /**
     * Simple name of the database.
     */
    String DB_NAME = "BCMMonitor.db";
    /**
     * Simple name of the data table.
     */
    String DB_DATA_TABLE = "data";
    /**
     * The database version
     */
    int DB_VERSION = 1;
    /**
     * Database column for the id. Identifies the row.
     */
    String COL_ID = "_id";
    /**
     * Database column for the date.
     */
    String COL_DATE = "date";
    /**
     * Database column for the start date.
     */
    String COL_START_DATE = "startdate";
    /**
     * Database column for the end date.
     */
    String COL_END_DATE = "MAX(" + COL_DATE + ")";
    /**
     * Database column for the heart rate.
     */
    String COL_HR = "hr";
    /**
     * Database column for the R-R.
     */
    String COL_RR = "rr";
    // /** Database column for the temporary flag. */
    //  String COL_TMP = "temporary";

    String saveDatabaseTemplate = "BCMMonitor.%s.db";
    /**
     * Prefix for the file name for saving the database.
     */
    String SAVE_DATABASE_FILENAME_PREFIX = "BCMDatabase";
    /**
     * Suffix for the file name for saving the database.
     */
    String SAVE_DATABASE_FILENAME_SUFFIX = ".csv";
    /**
     * Template for creating the file name for saving the database.
     */
    String SAVE_DATABASE_FILENAME_TEMPLATE = SAVE_DATABASE_FILENAME_PREFIX
            + ".%s" + SAVE_DATABASE_FILENAME_SUFFIX;
    /**
     * Name of the file that will be restored. It would typically be a file that
     * was previously saved and then renamed.
     */
    String RESTORE_FILE_NAME = "restore"
            + SAVE_DATABASE_FILENAME_SUFFIX;
    /**
     * Delimiter for saving session files.
     */
    String SAVE_SESSION_DELIM = ",";
    /**
     * Delimiter for saving the database.
     */
    String SAVE_DATABASE_DELIM = ",";

    /**
     * SQL sort command for date ascending
     */
    String SORT_ASCENDING = COL_DATE + " ASC";
    /**
     * SQL sort command for date ascending
     */
    String SORT_DESCENDING = COL_DATE + " DESC";

    /**
     * Default scan period for device scan.
     */
    long DEVICE_SCAN_PERIOD = 10000;

    // Request codes
    /**
     * Code for requesting all needed permissions for DeviceScanActivity.
     */
    int REQ_DEVICE_SCAN_MULTIPLE = 0;
    /**
     * Request code for all permissions.
     */
    int REQ_ACCESS_PERMISSIONS = 1;
    /**
     * Request code for ACCESS_COARSE_LOCATION permission.
     */
    int REQ_ACCESS_COARSE_LOCATION = 2;
    /**
     * Request code for ACCESS_FINE_LOCATION permission.
     */
    int REQ_ACCESS_FINE_LOCATION = 3;
    /**
     * Request code for BLUETOOTH_CONNECT permission.
     */
    int REQ_ACCESS_BLUETOOTH_CONNECT = 4;
    /**
     * Request code for BLUETOOTH_SCAN permission.
     */
    int REQ_ACCESS_BLUETOOTH_SCAN = 5;
    /**
     * Request code for selecting a device.
     */
    int REQ_SELECT_DEVICE_CODE = 10;
    /**
     * Request code for enabling Bluetooth.
     */
    int REQ_ENABLE_BT_CODE = 11;
    /**
     * Request code for test.
     */
    int REQ_TEST_CODE = 12;
    /**
     * Request code for plotting.
     */
    int REQ_PLOT_CODE = 13;
    /**
     * Request code for the session manager.
     */
    int REQ_SESSION_MANAGER_CODE = 14;
    /**
     * Request code for settings.
     */
    int REQ_SETTINGS_CODE = 15;
    /**
     * Request code for ACTION_OPEN_DOCUMENT_TREE.
     */
    int REQ_GET_TREE = 20;

    // Intent codes
    /**
     * The intent code for extra data.
     */
    String EXTRA_DATA = PACKAGE_NAME + ".extraData";
    /**
     * The intent code for UUID.
     */
    String EXTRA_UUID = PACKAGE_NAME + ".extraUuid";
    /**
     * The intent code for the date.
     */
    String EXTRA_DATE = PACKAGE_NAME + ".extraDate";
    /**
     * The intent code for the heart rate.
     */
    String EXTRA_HR = PACKAGE_NAME + ".extraHr";
    /**
     * The intent code for the R-R values.
     */
    String EXTRA_RR = PACKAGE_NAME + ".extraRr";
    /**
     * The intent code for the battery level.
     */
    String EXTRA_BAT = PACKAGE_NAME + ".extraBattery";
    /**
     * The intent code for a message.
     */
    String EXTRA_MSG = PACKAGE_NAME + ".extraMessage";
    /**
     * The intent code for device name.
     */
    String DEVICE_NAME_CODE = PACKAGE_NAME + ".deviceName";
    /**
     * The intent code for device address.
     */
    String DEVICE_ADDRESS_CODE = PACKAGE_NAME
            + "deviceAddress";
    /**
     * Intent code for a message.
     */
    String MSG_CODE = PACKAGE_NAME + ".MessageCode";
    /**
     * Intent code for plotting a session of current.
     */
    String PLOT_SESSION_CODE = PACKAGE_NAME
            + ".PlotSessionCode";
    /**
     * Intent code for plotting session start time.
     */
    String PLOT_SESSION_START_TIME_CODE = PACKAGE_NAME
            + ".PlotSessionStartTimeCode";
    /**
     * Intent code for plotting session end time.
     */
    String PLOT_SESSION_END_TIME_CODE = PACKAGE_NAME
            + ".PlotSessionEndTimeCode";
    /**
     * Intent code for showing settings.
     */
    String SETTINGS_CODE = PACKAGE_NAME + ".SettingsCode";

    // Result codes
    /**
     * Result code for an error.
     */
    int RESULT_ERROR = 1001;

    // Plotting
    /**
     * Maximum item age for real-time plot, in ms.
     */
    int PLOT_MAXIMUM_AGE = 300000;

    // Formatters
    /**
     * The static formatter to use for formatting dates for file names.
     */
    SimpleDateFormat fileNameFormatter = new SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss", Locale.US);

    /**
     * The static formatter to use for formatting dates to ms level.
     */
    SimpleDateFormat sessionSaveFormatter = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    /**
     * The static long formatter to use for formatting dates.
     */
    SimpleDateFormat longFormatter = new SimpleDateFormat(
            "MMM dd, yyyy HH:mm:ss Z", Locale.US);

    /**
     * The static formatter to use for formatting dates.
     */
    SimpleDateFormat mediumFormatter = new SimpleDateFormat(
            "MMM dd, yyyy HH:mm:ss", Locale.US);

    // /** The static short formatter to use for formatting dates. */
    //  SimpleDateFormat shortFormatter = new
    // SimpleDateFormat(
    // "M/d/yy h:mm a", Locale.US);
    //
    // /** The static second time formatter to use for formatting dates. */
    //  SimpleDateFormat secondTimeFormater = new
    // SimpleDateFormat(
    // "hh:mm:ss", Locale.US);

    /**
     * The static millisecond time formatter to use for formatting dates. Don't
     * use this for time differences as it will subtract the local GMT offset.
     */
    SimpleDateFormat millisecTimeFormater = new SimpleDateFormat(
            "hh:mm.ss.SSS", Locale.US);
}
