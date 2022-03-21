package net.kenevans.android.blecardiacmonitor;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

/**
 * For a given BLE device, this Activity provides the user interface to connect,
 * display data, and display GATT services and characteristics supported by the
 * device. The Activity communicates with {@code BCMBleService}, which in turn
 * interacts with the Bluetooth LE API.
 */
public class DeviceMonitorActivity extends AppCompatActivity implements IConstants {
    private TextView mConnectionState;
    private TextView mBat;
    private TextView mHr;
    private TextView mRr;
    private TextView mStatus;
    private String mDeviceName;
    private String mDeviceAddress;
    private BCMBleService mBcmBleService;
    private boolean mConnected = false;
    private BCMDbAdapter mDbAdapter;
    private BluetoothGattCharacteristic mCharBat;
    private BluetoothGattCharacteristic mCharHr;

    private boolean mServiceBound;
    private boolean mAllPermissionsAsked;

    // Launcher for enabling Bluetooth
    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Log.d(TAG, "enableBluetoothLauncher: result" +
                                ".getResultCode()=" + result.getResultCode());
                        if (result.getResultCode() != RESULT_OK) {
                            Utils.warnMsg(this, "This app will not work with " +
                                    "Bluetooth disabled");
                        }
                    });

    // Launcher for PREF_TREE_URI
    private final ActivityResultLauncher<Intent> openDocumentTreeLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Log.d(TAG, "openDocumentTreeLauncher: result" +
                                ".getResultCode()=" + result.getResultCode());
                        // Find the UID for this application
                        Log.d(TAG, "URI=" + UriUtils.getApplicationUid(this));
                        Log.d(TAG,
                                "Current permissions (initial): "
                                        + UriUtils.getNPersistedPermissions(this));
                        try {
                            if (result.getResultCode() == RESULT_OK) {
                                // Get Uri from Storage Access Framework.
                                Uri treeUri = result.getData().getData();
                                SharedPreferences.Editor editor =
                                        getPreferences(MODE_PRIVATE)
                                                .edit();
                                if (treeUri == null) {
                                    editor.putString(PREF_TREE_URI, null);
                                    editor.apply();
                                    Utils.errMsg(this, "Failed to get " +
                                            "persistent " +
                                            "access permissions");
                                    return;
                                }
                                // Persist access permissions.
                                try {
                                    this.getContentResolver().takePersistableUriPermission(treeUri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                    // Save the current treeUri as PREF_TREE_URI
                                    editor.putString(PREF_TREE_URI,
                                            treeUri.toString());
                                    editor.apply();
                                    // Trim the persisted permissions
                                    UriUtils.trimPermissions(this, 1);
                                } catch (Exception ex) {
                                    String msg = "Failed to " +
                                            "takePersistableUriPermission for "
                                            + treeUri.getPath();
                                    Utils.excMsg(this, msg, ex);
                                }
                                Log.d(TAG,
                                        "Current permissions (final): "
                                                + UriUtils.getNPersistedPermissions(this));
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Error in openDocumentTreeLauncher: " +
                                    "startActivityForResult", ex);
                        }
                    });

    // Launcher for select device
    private final ActivityResultLauncher<Intent> selectDeviceLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Log.d(TAG, this.getClass().getSimpleName()
                                + ": selectDeviceLauncher");
                        Intent intent = result.getData();
                        if (intent == null) {
                            Log.d(TAG, "  intent=null");
                            return;
                        }
                        int resultCode = result.getResultCode();
                        if (resultCode != RESULT_OK) {
                            Log.d(TAG, "  resultCode="
                                    + ActivityResult.resultCodeToString(resultCode));
                            return;
                        }
                        String deviceName =
                                intent.getStringExtra(DEVICE_NAME_CODE);
                        String deviceAddress =
                                intent.getStringExtra(DEVICE_ADDRESS_CODE);
                        // Do nothing if it is the same device
                        if (deviceAddress.equals(mDeviceAddress)) {
                            return;
                        }
                        if (mConnected) {
                            Log.d(TAG, mDeviceName + " is currently " +
                                    "connected, disconnecting");
                            disconnect();
                            // This should really be set in the callback in the
                            // broadcast receiver. However, we are currently
                            // in the state where the old device is in the
                            // process of being disconnected but not
                            // disconnected. This makes the Connect/Disconnect
                            // button temporarily not apply to the new device.
                            // So force the options menu to change.
                            mConnected = false;
                        }
                        mDeviceName = deviceName;
                        mDeviceAddress = deviceAddress;
                        // Reset the data views
                        resetDataViews();
                        // Reset the name, address, and connection status;
                        // Use this instead of getPreferences to be
                        // application-wide
                        SharedPreferences.Editor editor =
                                PreferenceManager
                                        .getDefaultSharedPreferences(this).edit();
                        editor.putString(DEVICE_NAME_CODE, mDeviceName);
                        editor.putString(DEVICE_ADDRESS_CODE,
                                mDeviceAddress);
                        editor.apply();
                        ((TextView) findViewById(R.id.device_name))
                                .setText(mDeviceName);
                        ((TextView) findViewById(R.id.device_address))
                                .setText(mDeviceAddress);
                        invalidateOptionsMenu();
                    });

    /**
     * Manages the service lifecycle.
     */
    private final ServiceConnection mServiceConnection = new
            ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName,
                                               IBinder service) {
                    Log.d(TAG, "onServiceConnected: " + mDeviceName + " "
                            + mDeviceAddress);
                    mBcmBleService = ((BCMBleService.LocalBinder)
                            service).getService();
                    if (!mBcmBleService.initialize()) {
                        String msg = "Unable to initialize Bluetooth";
                        Log.e(TAG, msg);
                        Utils.errMsg(DeviceMonitorActivity.this, msg);
                        return;
                    }
                    if (mDbAdapter != null) {
                        mBcmBleService.startDatabase(mDbAdapter);
                    }
                    // Automatically connects to the device upon successful
                    // start-up
                    // initialization.
                    SharedPreferences prefs = PreferenceManager
                            .getDefaultSharedPreferences(DeviceMonitorActivity.this);
                    boolean manuallyDisconnected = prefs.getBoolean(
                            PREF_MANUALLY_DISCONNECTED, false);
                    if (!manuallyDisconnected) {
                        boolean res =
                                mBcmBleService.connect(mDeviceAddress);
                        Log.d(TAG, "Connect mBcmBleService result=" +
                                res);
                        if (res) {
                            setManuallyDisconnected(false);
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    Log.d(TAG, "onServiceDisconnected");
                    mBcmBleService = null;
                }
            };

    /**
     * Handles various events fired by the Service.
     * <br>
     * <br>
     * ACTION_GATT_CONNECTED: connected to a GATT server.<br>
     * ACTION_GATT_DISCONNECTED: disconnected from a GATT server.<br>
     * ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.<br>
     * ACTION_DATA_AVAILABLE: received data from the device. This can be a
     * result of read or notification operations.<br>
     */
    private final BroadcastReceiver mGattUpdateReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (BCMBleService.ACTION_GATT_CONNECTED.equals(action)) {
                        Log.d(TAG, "onReceive: " + action);
                        mConnected = true;
                        updateConnectionState();
                        invalidateOptionsMenu();
                    } else if (BCMBleService.ACTION_GATT_DISCONNECTED.equals
                            (action)) {
                        Log.d(TAG, "onReceive: " + action);
                        mConnected = false;
                        resetDataViews();
                        updateConnectionState();
                        invalidateOptionsMenu();
                    } else if (BCMBleService.ACTION_GATT_SERVICES_DISCOVERED
                            .equals(action)) {
                        Log.d(TAG, "onReceive: " + action);
                        onServicesDiscovered(mBcmBleService.getSupportedGattServices());
                    } else if (BCMBleService.ACTION_DATA_AVAILABLE.equals(action)) {
                        // Log.d(TAG, "onReceive: " + action);
                        displayData(intent);
                    } else if (BCMBleService.ACTION_STATUS.equals(action)) {
                        Log.d(TAG, "onReceive: " + action);
                        displayStatus(intent);
                    }
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Capture global exceptions
        Thread.setDefaultUncaughtExceptionHandler((paramThread,
                                                   paramThrowable) -> {
            Log.e(TAG, "Unexpected exception :", paramThrowable);
            // Any non-zero exit code
            System.exit(2);
        });

        setContentView(R.layout.activity_device_monitor);

        // Initialize the preferences if not already done
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Use this instead of getPreferences to be application-wide
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        mDeviceName = prefs.getString(DEVICE_NAME_CODE, null);
        mDeviceAddress = prefs.getString(DEVICE_ADDRESS_CODE, null);
        Log.d(TAG, this.getClass().getSimpleName() + ": onCreate: "
                + mDeviceName + " " + mDeviceAddress);

        ((TextView) findViewById(R.id.device_name)).setText(mDeviceName);
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = findViewById(R.id.connection_state);
        mBat = findViewById(R.id.bat_value);
        mHr = findViewById(R.id.hr_value);
        mRr = findViewById(R.id.rr_value);
        mStatus = findViewById(R.id.status_value);
        resetDataViews();

        // Use this check to determine whether BLE is supported on the device.
        // Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            String msg = getString(R.string.ble_not_supported);
            Utils.warnMsg(this, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }

        // Initializes a Bluetooth bluetoothAdapter. For API level 18 and
        // above, get a reference to BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager = (BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            String msg = getString(R.string.error_bluetooth_manager);
            Utils.errMsg(this, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }

        // Open the database
        mDbAdapter = new BCMDbAdapter(this);
        mDbAdapter.open();

        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        // Checks if Bluetooth is supported on the device
        if (bluetoothAdapter == null) {
            String msg = getString(R.string.bluetooth_not_supported);
            Utils.errMsg(this, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }

        // Ask for needed permissions
        requestPermissions();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onResume: mConnected="
                + mConnected + " mBcmBleService="
                + Utils.getHashCode(mBcmBleService)
                + " isAllPermissionsGranted=" + isAllPermissionsGranted(this)
                + " mAllPermissionsAsked=" + mAllPermissionsAsked
        );
        super.onResume();
        if (!isAllPermissionsGranted(this)) {
            if (!mAllPermissionsAsked) {
                mAllPermissionsAsked = true;
                Utils.warnMsg(this, getString(R.string.permission_not_granted));
            } else {
                return;
            }
        }

        if (!mServiceBound) {
            Intent gattServiceIntent = new Intent(this,
                    BCMBleService.class);
            bindService(gattServiceIntent, mServiceConnection,
                    BIND_AUTO_CREATE);
            mServiceBound = true;
        }
        // Get the settings
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        boolean manuallyDisconnected = prefs.getBoolean(
                PREF_MANUALLY_DISCONNECTED, false);
        Log.d(TAG, "Starting registerReceiver");
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (!manuallyDisconnected && mDeviceAddress != null
                && mBcmBleService != null) {
            Log.d(TAG, "Starting mBcmBleService.connect");
            final boolean res = mBcmBleService.connect(mDeviceAddress);
            Log.d(TAG, "mBcmBleService.connect: result=" + res);
            if (res) {
                setManuallyDisconnected(false);
            }
        }
        // Set whether connected form the service in case the broadcast
        // receiver was not connected for some events.
        boolean serviceConnected = getIsConnectedFromService();
        if (mConnected != serviceConnected) {
            Log.d(TAG, "getIsConnectedFromService: service: "
                    + serviceConnected + " activity: " + mConnected);
        }
        mConnected = serviceConnected;
        updateConnectionState();
        invalidateOptionsMenu();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onPause");
        super.onPause();
        try {
            unregisterReceiver(mGattUpdateReceiver);
        } catch (Exception ex) {
            // Happens because it was not registered. Do nothing.
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[]
            permissions, @NonNull int[] grantResults) {
        Log.d(TAG, this.getClass().getSimpleName()
                + ": onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions,
                grantResults);
        if (requestCode == REQ_ACCESS_PERMISSIONS) {// All (Handle multiple)
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.
                        permission.ACCESS_COARSE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: COARSE_LOCATION " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: COARSE_LOCATION " +
                                "denied");
                    }
                } else if (permissions[i].equals(Manifest.
                        permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: FINE_LOCATION " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: FINE_LOCATION " +
                                "denied");
                    }
                } else if (permissions[i].equals(Manifest.
                        permission.BLUETOOTH_SCAN)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_SCAN " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_SCAN " +
                                "denied");
                    }
                } else if (permissions[i].equals(Manifest.
                        permission.BLUETOOTH_CONNECT)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_CONNECT" +
                                " " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_CONNECT" +
                                " " +
                                "denied");
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onDestroy");
        super.onDestroy();
        if (mServiceBound) {
            unbindService(mServiceConnection);
        }
        mBcmBleService = null;
        if (mDbAdapter != null) {
            mDbAdapter.close();
            mDbAdapter = null;
        }
    }

    @Override
    public void onBackPressed() {
        // This seems to be necessary with Android 12
        // Otherwise onDestroy is not called
        Log.d(TAG, this.getClass().getSimpleName() + ": onBackPressed");
        finish();
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_device_monitor, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_connect) {
            connect();
            return true;
        } else if (item.getItemId() == R.id.menu_disconnect) {
            disconnect();
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.menu_select_device) {
            selectDevice();
            return true;
        } else if (item.getItemId() == R.id.menu_session_manager) {
            startSessionManager();
            return true;
        } else if (item.getItemId() == R.id.menu_plot) {
            plot();
            return true;
        } else if (item.getItemId() == R.id.menu_read_battery_level) {
            readBatteryLevel();
            return true;
        } else if (item.getItemId() == R.id.info) {
            info();
            return true;
        } else if (item.getItemId() == R.id.menu_save_database) {
            saveDatabase();
            return true;
        } else if (item.getItemId() == R.id.menu_replace_database) {
            checkReplaceDatabase();
            return true;
        } else if (item.getItemId() == R.id.choose_data_directory) {
            chooseDataDirectory();
            return true;
        } else if (item.getItemId() == R.id.help) {
            showHelp();
            return true;
        } else if (item.getItemId() == R.id.menu_settings) {
            showSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Sets the current data directory
     */
    private void chooseDataDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION &
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        openDocumentTreeLauncher.launch(intent);
    }

    /**
     * Updates the connection state view on the UI thread.
     */
    private void updateConnectionState() {
        int string = mConnected ? R.string.connected : R.string.disconnected;
        runOnUiThread(() -> mConnectionState.setText(string));
    }

    /**
     * Gets whether connected or not from the service. Used in case the
     * broadcast receiver was not connected during a change.
     */
    private boolean getIsConnectedFromService() {
//        Log.d(TAG, this.getClass().getSimpleName() + ": " +
//                "getIsConnectedFromService");
        boolean newVal = mConnected;
        if (mDeviceAddress == null) return newVal;
        if (!mServiceBound || mBcmBleService == null) return newVal;
        String address = mBcmBleService.getDeviceAddress();
        if (address == null) return newVal;
        int connectionState = mBcmBleService.getConnectionState();
        boolean connected =
                (connectionState == BluetoothProfile.STATE_CONNECTED);
        if (mDeviceAddress.equals(address)) {
            newVal = connected;
        }
        return newVal;
    }

    /**
     * Connects the current device.
     */
    private void connect() {
        Log.d(TAG, this.getClass().getSimpleName() + ": connect");
        mStatus.setText("");
        if (mServiceBound) {
            mBcmBleService.connect(mDeviceAddress);
            setManuallyDisconnected(false);
        }
    }

    /**
     * Disconnects the current device.
     */
    private void disconnect() {
        Log.d(TAG, this.getClass().getSimpleName() + ": disconnect");
        mStatus.setText("");
        if (mServiceBound) {
            mBcmBleService.disconnect();
            setManuallyDisconnected(true);
        }
    }

    /**
     * Calls an activity to select the device.
     */
    public void selectDevice() {
        // Scan doesn't find current device if it is connected
        Intent intent = new Intent(DeviceMonitorActivity.this,
                DeviceScanActivity.class);
        selectDeviceLauncher.launch(intent);
    }

    public void readBatteryLevel() {
        if (mBcmBleService != null) {
            mBcmBleService.readBatteryLevel();
        }
    }

    /**
     * Calls the plot activity.
     */
    public void plot() {
        Intent intent = new Intent(DeviceMonitorActivity.this,
                PlotActivity.class);
        // Plot the current data, not a session
        intent.putExtra(PLOT_SESSION_CODE, false);
        startActivity(intent);
    }

    /**
     * Displays info about the current configuration
     */
    private void info() {
        try {
            StringBuilder info = new StringBuilder();
            info.append("Device Name: ").append(mDeviceName).append("\n");
            info.append("Device Address: ").append(mDeviceAddress).append("\n");
            info.append("Connected: ").append(mConnected).append("\n");
            info.append("Battery: ").append(mBat.getText()).append("\n");
            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            info.append(UriUtils.getRequestedPermissionsInfo(this));
            String treeUriStr = prefs.getString(PREF_TREE_URI, null);
            if (treeUriStr == null) {
                info.append("Data Directory: Not set");
            } else {
                Uri treeUri = Uri.parse(treeUriStr);
                if (treeUri == null) {
                    info.append("Data Directory: Not set");
                } else {
                    info.append("Data Directory: ").append(treeUri.getPath());
                }
            }
            Utils.infoMsg(this, info.toString());
        } catch (Throwable t) {
            Utils.excMsg(this, "Error showing info", t);
            Log.e(TAG, "Error showing info", t);
        }
    }


    /**
     * Show the help.
     */
    private void showHelp() {
        try {
            // Start theInfoActivity
            Intent intent = new Intent();
            intent.setClass(this, InfoActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra(INFO_URL, "file:///android_asset/bcm.html");
            startActivity(intent);
        } catch (Exception ex) {
            Utils.excMsg(this, getString(R.string.help_show_error), ex);
        }
    }

    /**
     * Calls the settings activity.
     */
    public void showSettings() {
        Intent intent = new Intent(DeviceMonitorActivity.this,
                SettingsActivity.class);
        intent.putExtra(SETTINGS_CODE, false);
        startActivity(intent);
    }

    /**
     * Calls the session manager activity.
     */
    public void startSessionManager() {
        Intent intent = new Intent(DeviceMonitorActivity.this,
                SessionManagerActivity.class);
        startActivity(intent);
    }

    /**
     * Displays the data from an ACTION_DATA_AVAILABLE callback.
     *
     * @param intent The Intent.
     */
    private void displayData(Intent intent) {
        String uuidString;
        UUID uuid;
        String value;
        try {
            uuidString = intent.getStringExtra(EXTRA_UUID);
            if (uuidString == null) {
                mStatus.setText(R.string.null_uuid_msg);
                return;
            }
            uuid = UUID.fromString(uuidString);
            if (uuid.equals(UUID_HEART_RATE_MEASUREMENT)) {
                value = intent.getStringExtra(EXTRA_HR);
                if (value == null) {
                    mHr.setText(R.string.not_available);
                } else {
                    mHr.setText(value);
                }
                value = intent.getStringExtra(EXTRA_RR);
                if (value == null) {
                    mRr.setText(R.string.not_available);
                } else if (value.equals(INVALID_STRING)) {
                    mRr.setText("");
                } else {
                    mRr.setText(value);
                }
            } else if (uuid.equals(UUID_BATTERY_LEVEL)) {
                value = intent.getStringExtra(EXTRA_BAT);
                if (value == null) {
                    mBat.setText(R.string.not_available);
                } else {
                    mBat.setText(value);
                }
            }
        } catch (Exception ex) {
            Log.d(TAG, "Error displaying data", ex);
            mStatus.setText(this.getString(R.string.exception_msg_format,
                    ex.getMessage()));
            // Don't use Utils here as there may be many
            // Utils.excMsg(this, "Error displaying message", ex);
        }
    }

    /**
     * Displays the error from an ACTION_STATUS callback.
     *
     * @param intent The Intent with the message for the error.
     */
    private void displayStatus(Intent intent) {
        Log.d(TAG, this.getClass().getSimpleName() + ": displayStatus");
        String msg = null;
        try {
            msg = intent.getStringExtra(EXTRA_MSG);
            if (msg == null) {
                mStatus.setText(R.string.null_error_msg);
                Log.d(TAG, getString(R.string.null_error_msg));
                return;
            }
            mStatus.setText(msg);
        } catch (Exception ex) {
            Log.d(TAG, "Error displaying error", ex);
            mStatus.setText(this.getString(R.string.exception_msg_format,
                    ex.getMessage()));
            Utils.excMsg(this, msg, ex);
        }
    }

    /**
     * Sets the PREF_MANUALLY_DISCONNECTED preference in PreferenceManager
     * .getDefaultSharedPreferences.
     *
     * @param state The value for PREF_MANUALLY_DISCONNECTED.
     */
    private void setManuallyDisconnected(boolean state) {
        SharedPreferences.Editor editor = PreferenceManager
                .getDefaultSharedPreferences(this).edit();
        editor.putBoolean(PREF_MANUALLY_DISCONNECTED, state);
        editor.apply();
    }

    /**
     * Starts a session using the proper starting Characteristics
     * depending on
     * mDoBat, mDoHr, and mDoCustom.
     *
     * @return Whether the service started the session successfully.
     */
    boolean startSession() {
        Log.d(TAG, "  mCharBat=" + mCharBat + " mCharHr=" + mCharHr);
        return mBcmBleService.startSession(mCharBat, mCharHr);
    }

    /**
     * Resets the data views to show default values for battery, hr, rr,
     * and status.
     */
    public void resetDataViews() {
        mBat.setText(R.string.not_available);
        mHr.setText(R.string.not_available);
        mRr.setText(R.string.not_available);
        mStatus.setText("");
    }

    private void saveDatabase() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        try {
            String format = "yyyy-MM-dd-HHmmss";
            SimpleDateFormat df = new SimpleDateFormat(format, Locale.US);
            Date now = new Date();
            String fileName = String.format(saveDatabaseTemplate,
                    df.format(now));
            Uri treeUri = Uri.parse(treeUriStr);
            String treeDocumentId =
                    DocumentsContract.getTreeDocumentId(treeUri);
            Uri docTreeUri =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri,
                            treeDocumentId);
            ContentResolver resolver = this.getContentResolver();
            Uri docUri = DocumentsContract.createDocument(resolver, docTreeUri,
                    "application/vnd.sqlite3", fileName);
            if (docUri == null) {
                Utils.errMsg(this, "Could not create document Uri");
                return;
            }
            ParcelFileDescriptor pfd = getContentResolver().
                    openFileDescriptor(docUri, "rw");
            File src = new File(getExternalFilesDir(null), DB_NAME);
            Log.d(TAG, "saveDatabase: docUri=" + docUri);
            try (FileChannel in =
                         new FileInputStream(src).getChannel();
                 FileChannel out =
                         new FileOutputStream(pfd.getFileDescriptor()).getChannel()) {
                out.transferFrom(in, 0, in.size());
            } catch (Exception ex) {
                String msg = "Error copying source database from "
                        + docUri.getLastPathSegment() + " to "
                        + src.getPath();
                Log.e(TAG, msg, ex);
                Utils.excMsg(this, msg, ex);
            }
            Utils.infoMsg(this, "Wrote " + docUri.getLastPathSegment());
        } catch (Exception ex) {
            String msg = "Error saving to SD card";
            Utils.excMsg(this, msg, ex);
            Log.e(TAG, msg, ex);
        }
    }

    /**
     * Does the preliminary checking for restoring the database, prompts if
     * it is OK to delete the current one, and call restoreDatabase to
     * actually
     * do the replace.
     */
    private void checkReplaceDatabase() {
        Log.d(TAG, "checkReplaceDatabase");
        // Find the .db files in the data directory
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no tree Uri set");
            return;
        }
        Uri treeUri = Uri.parse(treeUriStr);
        final List<UriUtils.UriData> children =
                UriUtils.getChildren(this, treeUri, ".db");
        final int len = children.size();
        if (len == 0) {
            Utils.errMsg(this, "There are no .db files in the data directory");
            return;
        }
        // Sort them by date with newest first
        Collections.sort(children,
                (data1, data2) -> Long.compare(data2.modifiedTime,
                        data1.modifiedTime));

        // Prompt for the file to use
        final CharSequence[] items = new CharSequence[children.size()];
        String displayName;
        UriUtils.UriData uriData;
        for (int i = 0; i < len; i++) {
            uriData = children.get(i);
            displayName = uriData.displayName;
            if (displayName == null) {
                displayName = uriData.uri.getLastPathSegment();
            }
            items[i] = displayName;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getText(R.string.select_replace_database));
        builder.setSingleChoiceItems(items, 0,
                (dialog, item) -> {
                    dialog.dismiss();
                    if (item < 0 || item >= len) {
                        Utils.errMsg(DeviceMonitorActivity.this,
                                "Invalid item");
                        return;
                    }
                    // Confirm the user wants to delete all the current data
                    new AlertDialog.Builder(DeviceMonitorActivity.this)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle(R.string.confirm)
                            .setMessage(R.string.delete_prompt)
                            .setPositiveButton(R.string.ok,
                                    (dialog1, which) -> {
                                        dialog1.dismiss();
                                        Log.d(TAG, "Calling replaceDatabase: " +
                                                "uri="
                                                + children.get(item).uri);
                                        replaceDatabase(children.get(item).uri);
                                    })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Replaces the database without prompting.
     *
     * @param uri The Uri.
     */
    private void replaceDatabase(Uri uri) {
        Log.d(TAG, this.getClass().getSimpleName() + ": replaceDatabase: uri="
                + uri.getLastPathSegment());
        if (uri == null) {
            Log.d(TAG, "replaceDatabase: Source database is null");
            Utils.errMsg(this, "Source database is null");
            return;
        }
        String lastSeg = uri.getLastPathSegment();
        if (!UriUtils.exists(this, uri)) {
            String msg = "Source database does not exist " + lastSeg;
            Log.d(TAG, "replaceDatabase: " + msg);
            Utils.errMsg(this, msg);
            return;
        }
        // Copy the data base to app storage
        File dest = null;
        try {
            String destFileName = UriUtils.getFileNameFromUri(uri);
            dest = new File(getExternalFilesDir(null), destFileName);
            dest.createNewFile();
            ParcelFileDescriptor pfd = getContentResolver().
                    openFileDescriptor(uri, "rw");
            try (FileChannel in =
                         new FileInputStream(pfd.getFileDescriptor()).getChannel();
                 FileChannel out =
                         new FileOutputStream(dest).getChannel()) {
                out.transferFrom(in, 0, in.size());
            } catch (Exception ex) {
                String msg = "Error copying source database from "
                        + uri.getLastPathSegment() + " to "
                        + dest.getPath();
                Log.e(TAG, msg, ex);
                Utils.excMsg(this, msg, ex);
            }
        } catch (Exception ex) {
            String msg = "Error getting source database" + uri;
            Log.e(TAG, msg, ex);
            Utils.excMsg(this, msg, ex);
        }
        try {
            // Replace (Use null for default alias)
            mDbAdapter.replaceDatabase(dest.getPath(), null);
            Utils.infoMsg(this,
                    "Restored database from " + uri.getLastPathSegment());
        } catch (Exception ex) {
            String msg = "Error replacing data from " + dest.getPath();
            Log.e(TAG, msg, ex);
            Utils.excMsg(this, msg, ex);
        }
    }

    /**
     * Sets up read or notify for this characteristic if possible.
     *
     * @param characteristic The Characteristic found.
     */
    private void onCharacteristicFound(
            BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, "onCharacteristicFound: " + characteristic.getUuid());
        if (characteristic.getUuid().equals(UUID_HEART_RATE_MEASUREMENT)
                || characteristic.getUuid().equals(UUID_BATTERY_LEVEL)
                || characteristic.getUuid().equals(UUID_CUSTOM_MEASUREMENT)) {
            if (characteristic.getUuid().equals(UUID_HEART_RATE_MEASUREMENT)) {
                mCharHr = characteristic;
            } else if (characteristic.getUuid().equals(UUID_BATTERY_LEVEL)) {
                mCharBat = characteristic;
            }
            startSession();
        }
    }

    /**
     * Make an IntentFilter for the actions in which we are interested.
     *
     * @return The IntentFilter.
     */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BCMBleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BCMBleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BCMBleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BCMBleService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BCMBleService.ACTION_STATUS);
        return intentFilter;
    }

    /**
     * Called when services are discovered.
     *
     * @param gattServices The list of Gatt services.
     */
    private void onServicesDiscovered(List<BluetoothGattService> gattServices) {
        if (gattServices == null) {
            return;
        }
        // Loop through available GATT Services
        UUID serviceUuid;
        UUID charUuid;
        mCharBat = null;
        mCharHr = null;
        boolean hrFound = false, batFound = false;
        for (BluetoothGattService gattService : gattServices) {
            serviceUuid = gattService.getUuid();
            if (serviceUuid.equals(UUID_HEART_RATE_SERVICE)) {
                hrFound = true;
                // Loop through available Characteristics
                for (BluetoothGattCharacteristic characteristic : gattService
                        .getCharacteristics()) {
                    charUuid = characteristic.getUuid();
                    if (charUuid.equals(UUID_HEART_RATE_MEASUREMENT)) {
                        mCharHr = characteristic;
                        onCharacteristicFound(characteristic);
                    }
                }
            } else if (serviceUuid.equals(UUID_BATTERY_SERVICE)) {
                batFound = true;
                // Loop through available Characteristics
                for (BluetoothGattCharacteristic characteristic : gattService
                        .getCharacteristics()) {
                    charUuid = characteristic.getUuid();
                    if (charUuid.equals(UUID_BATTERY_LEVEL)) {
                        mCharBat = characteristic;
                        onCharacteristicFound(characteristic);
                    }
                }
            }
        }
        if (!hrFound || !batFound || mCharHr == null || mCharBat == null) {
            String info = "Services and Characteristics not found:" + "\n";
            if (!hrFound) {
                info += "  Heart Rate" + "\n";
            } else if (mCharHr == null) {
                info += "    Heart Rate Measurement" + "\n";
            } else if (!batFound) {
                info += "  Battery" + "\n";
            } else if (mCharBat == null) {
                info += "    Battery Level" + "\n";
            }
            Utils.warnMsg(this, info);
            Log.d(TAG, "onServicesDiscovered: " + info);
        }
    }

    /**
     * Determines if either COARSE or FINE location permission is granted.
     *
     * @return If granted.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isAllPermissionsGranted(Context ctx) {
        boolean granted;
        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12 (S)
            granted = ctx.checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED |
                    ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                            PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 6 (M)
            granted = ctx.checkSelfPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED |
                    ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED;
        }
        return granted;
    }

    public void requestPermissions() {
        Log.d(TAG, "requestPermissions");
        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();
            if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent =
                        new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                enableBluetoothLauncher.launch(enableBtIntent);
            }
        }

        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12 (S)
            this.requestPermissions(new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT},
                    REQ_ACCESS_PERMISSIONS);
        } else {
            // Android 6 (M)
            this.requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_ACCESS_PERMISSIONS);
        }
    }
}
