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
	/** Tag to associate with log messages. */
	public static final String TAG = "BCM Monitor";
	/** Name of the package for this application. */
	public static final String PACKAGE_NAME = "net.kenevans.android.blecardiacmonitor";
	/** Prefix for session names. Will be followed by a date and time. */
	public static final String SESSION_NAME_PREFIX = "BCM-";

	/** Notification ID for managing notifications. */
	public static final int NOTIFICATION_ID = 1;

	/** Initial start time for RR values. */
	public static final long INITIAL_RR_START_TIME = 0;

	// Base
	/** Base string for standard UUIDS. These UUIDs differ in characters 4-7. */
	public static final String BASE_UUID = "00000000-0000-1000-8000-00805f9b34fb";

	// Services
	public static final UUID UUID_BATTERY_SERVICE = UUID
			.fromString("0000180f-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_DEVICE_INFORMATION_SERVICE = UUID
			.fromString("0000180a-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_HEART_RATE_SERVICE = UUID
			.fromString("0000180d-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_HXM_CUSTOM_DATA_SERVICE = UUID
			.fromString("befdff10-c979-11e1-9b21-0800200c9a66");

	// Characteristics
	public static final UUID UUID_BATTERY_LEVEL = UUID
			.fromString("00002a19-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_CUSTOM_MEASUREMENT = UUID
			.fromString("befdff11-c979-11e1-9b21-0800200c9a66");
	public static final UUID UUID_DEVICE_NAME = UUID
			.fromString("00002a00-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_HEART_RATE_MEASUREMENT = UUID
			.fromString("00002a37-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_TEST_MODE = UUID
			.fromString("befdffb1-c979-11e1-9b21-0800200c9a66");
	public static final UUID UUID_BODY_SENSOR_LOCATION = UUID
			.fromString("00002a38-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_MODEL_NUMBER_STRING = UUID
			.fromString("00002a24-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_FIRMWARE_REVISION_STRING = UUID
			.fromString("00002a26-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_APPEARANCE = UUID
			.fromString("00002a01-0000-1000-8000-00805f9b34fb");

	public static final UUID UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID
			.fromString("00002902-0000-1000-8000-00805f9b34fb");

	/** Directory on the SD card where the database is stored */
	public static final String SD_CARD_DB_DIRECTORY = "BLE Cardiac Monitor";

	// Preferences
	public static final String PREF_DATA_DIRECTORY = "dataDirectoryPreference";
	public static final String PREF_MONITOR_HR = "monitorHrPreference";
	public static final String PREF_MONITOR_CUSTOM = "monitorCustomPreference";
	public static final String PREF_READ_BATTERY = "readBatteryPreference";
	public static final String PREF_PLOT_HR = "plotHrPreference";
	public static final String PREF_PLOT_RR = "plotRrPreference";
	public static final String PREF_PLOT_INTERVAL = "plotIntervalPreference";
	public static final String PREF_MANUALLY_DISCONNECTED = "manuallyDisconnected";

	// Session
	public static final int SESSION_IDLE = 0;
	public static final int SESSION_WAITING_BAT = 1;
	public static final int SESSION_WAITING_HR = 2;
	public static final int SESSION_WAITING_CUSTOM = 3;

	/** Flag for specifying to do Battery Level. */
	public static final int DO_NOTHING = 0;
	/** Flag for specifying to do Battery Level. */
	public static final int DO_BAT = 1;
	/** Flag for specifying to do HR. */
	public static final int DO_HR = 2;
	/** Flag for specifying to do Custom. */
	public static final int DO_CUSTOM = 4;

	/** Value for a database date value indicating invalid. */
	public static final long INVALID_DATE = Long.MIN_VALUE;
	/** Value for a database int indicating invalid. */
	public static final int INVALID_INT = -1;
	/** Value for a database String indicating invalid. */
	public static final String INVALID_STRING = "Invalid";

	// Timers
	/** Timer timeout for accumulating characteristics (ms). */
	public static final long CHARACTERISTIC_TIMER_TIMEOUT = 100;
	/** Timer interval for accumulating characteristics (ms). */
	public static final long CHARACTERISTIC_TIMER_INTERVAL = 1;
	/** Timer timeout for getting custom value (ms). */
	public static final long CUSTOM_NOTIFY_TIMER_TIMEOUT = 500;

	// Database
	/** Simple name of the database. */
	public static final String DB_NAME = "BCMMonitor.db";
	/** Simple name of the data table. */
	public static final String DB_DATA_TABLE = "data";
	/** The database version */
	public static final int DB_VERSION = 1;
	/** Database column for the id. Identifies the row. */
	public static final String COL_ID = "_id";
	/** Database column for the date. */
	public static final String COL_DATE = "date";
	/** Database column for the start date. */
	public static final String COL_START_DATE = "startdate";
	/** Database column for the end date. */
	public static final String COL_END_DATE = "MAX(" + COL_DATE + ")";
	/** Database column for the heart rate. */
	public static final String COL_HR = "hr";
	/** Database column for the R-R. */
	public static final String COL_RR = "rr";
	// /** Database column for the temporary flag. */
	// public static final String COL_TMP = "temporary";
	/** Prefix for the file name for saving the database. */
	public static final String SAVE_DATABASE_FILENAME_PREFIX = "BCMDatabase";
	/** Suffix for the file name for saving the database. */
	public static final String SAVE_DATABASE_FILENAME_SUFFIX = ".csv";
	/** Template for creating the file name for saving the database. */
	public static final String SAVE_DATABASE_FILENAME_TEMPLATE = SAVE_DATABASE_FILENAME_PREFIX
			+ ".%s" + SAVE_DATABASE_FILENAME_SUFFIX;
	/**
	 * Name of the file that will be restored. It would typically be a file that
	 * was previously saved and then renamed.
	 */
	public static final String RESTORE_FILE_NAME = "restore"
			+ SAVE_DATABASE_FILENAME_SUFFIX;
	/** Delimiter for saving session files. */
	public static final String SAVE_SESSION_DELIM = ",";
	/** Delimiter for saving the database. */
	public static final String SAVE_DATABASE_DELIM = ",";

	/** SQL sort command for date ascending */
	public static final String SORT_ASCENDING = COL_DATE + " ASC";
	/** SQL sort command for date ascending */
	public static final String SORT_DESCENDING = COL_DATE + " DESC";

	/** Default scan period for device scan. */
	public static final long DEVICE_SCAN_PERIOD = 10000;

	// Messages
	/** Request code for selecting a device. */
	public static final int REQUEST_SELECT_DEVICE_CODE = 10;
	/** Request code for enabling Bluetooth. */
	public static final int REQUEST_ENABLE_BT_CODE = 11;
	/** Request code for test. */
	public static final int REQUEST_TEST_CODE = 12;
	/** Request code for plotting. */
	public static final int REQUEST_PLOT_CODE = 13;
	/** Request code for the session manager. */
	public static final int REQUEST_SESSION_MANAGER_CODE = 14;
	/** Request code for settings. */
	public static final int REQUEST_SETTINGS_CODE = 15;

	// Intent codes
	/** The intent code for extra data. */
	public final static String EXTRA_DATA = PACKAGE_NAME + ".extraData";
	/** The intent code for UUID. */
	public final static String EXTRA_UUID = PACKAGE_NAME + ".extraUuid";
	/** The intent code for the date. */
	public final static String EXTRA_DATE = PACKAGE_NAME + ".extraDate";
	/** The intent code for the heart rate. */
	public final static String EXTRA_HR = PACKAGE_NAME + ".extraHr";
	/** The intent code for the R-R values. */
	public final static String EXTRA_RR = PACKAGE_NAME + ".extraRr";
	/** The intent code for the battery level. */
	public final static String EXTRA_BAT = PACKAGE_NAME + ".extraBattery";
	/** The intent code for a message. */
	public final static String EXTRA_MSG = PACKAGE_NAME + ".extraMessage";
	/** The intent code for device name. */
	public static final String DEVICE_NAME_CODE = PACKAGE_NAME + ".deviceName";
	/** The intent code for device address. */
	public static final String DEVICE_ADDRESS_CODE = PACKAGE_NAME
			+ "deviceAddress";
	/** Intent code for a message. */
	public static final String MSG_CODE = PACKAGE_NAME + ".MessageCode";
	/** Intent code for plotting a session of current. */
	public static final String PLOT_SESSION_CODE = PACKAGE_NAME
			+ ".PlotSessionCode";
	/** Intent code for plotting session start time. */
	public static final String PLOT_SESSION_START_TIME_CODE = PACKAGE_NAME
			+ ".PlotSessionStartTimeCode";
	/** Intent code for plotting session end time. */
	public static final String PLOT_SESSION_END_TIME_CODE = PACKAGE_NAME
			+ ".PlotSessionEndTimeCode";
	/** Intent code for showing settings. */
	public static final String SETTINGS_CODE = PACKAGE_NAME + ".SettingsCode";

	// Result codes
	/** Result code for an error. */
	public static final int RESULT_ERROR = 1001;

	// Plotting
	/** Maximum item age for real-time plot, in ms. */
	public static final int PLOT_MAXIMUM_AGE = 300000;

	// Formatters
	/** The static formatter to use for formatting dates for file names. */
	public static final SimpleDateFormat fileNameFormatter = new SimpleDateFormat(
			"yyyy-MM-dd-HH-mm-ss", Locale.US);

	/** The static formatter to use for formatting dates to ms level. */
	public static final SimpleDateFormat sessionSaveFormatter = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

	/** The static long formatter to use for formatting dates. */
	public static final SimpleDateFormat longFormatter = new SimpleDateFormat(
			"MMM dd, yyyy HH:mm:ss Z", Locale.US);

	/** The static formatter to use for formatting dates. */
	public static final SimpleDateFormat mediumFormatter = new SimpleDateFormat(
			"MMM dd, yyyy HH:mm:ss", Locale.US);

	// /** The static short formatter to use for formatting dates. */
	// public static final SimpleDateFormat shortFormatter = new
	// SimpleDateFormat(
	// "M/d/yy h:mm a", Locale.US);
	//
	// /** The static second time formatter to use for formatting dates. */
	// public static final SimpleDateFormat secondTimeFormater = new
	// SimpleDateFormat(
	// "hh:mm:ss", Locale.US);

	/**
	 * The static millisecond time formatter to use for formatting dates. Don't
	 * use this for time differences as it will subtract the local GMT offset.
	 */
	public static final SimpleDateFormat millisecTimeFormater = new SimpleDateFormat(
			"hh:mm.ss.SSS", Locale.US);

}
