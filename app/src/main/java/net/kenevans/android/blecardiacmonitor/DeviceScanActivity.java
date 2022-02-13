package net.kenevans.android.blecardiacmonitor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
@SuppressLint("InflateParams")
public class DeviceScanActivity extends AppCompatActivity implements IConstants {
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;
    private boolean mFineDenied;
    private boolean mConnectDenied;
    private boolean mScanDenied;
    private ListView mListView;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, this.getClass().getSimpleName() + ": onCreate");
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_activity_devices);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
        setContentView(R.layout.list_view);
        mHandler = new Handler();
        mListView = findViewById(R.id.mainListView);
        mListView.setOnItemClickListener((parent, view, position, id) ->
                onListItemClick(mListView, view, position, id));

        // Use this check to determine whether BLE is supported on the device.
        // Then you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            String msg = getString(R.string.ble_not_supported);
            Utils.errMsg(this, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            finish();
        }

        // Initializes a Bluetooth adapter. For API level 18 and above, get a
        // reference to BluetoothAdapter through BluetoothManager.
        mBluetoothAdapter = null;
        final BluetoothManager bluetoothManager = (BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Toast.makeText(this, R.string.error_bluetooth_manager,
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            mBluetoothAdapter = bluetoothManager.getAdapter();
        } catch (NullPointerException ex) {
            Toast.makeText(this, R.string.error_bluetooth_adapter,
                    Toast.LENGTH_LONG).show();
        }

        // Checks if Bluetooth is supported on the device
        if (mBluetoothAdapter == null) {
            String msg = getString(R.string.bluetooth_not_supported);
            Utils.errMsg(this, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

//        // Seems to be necessary
//        ActivityCompat.requestPermissions(this, new String[]
//                {
//                        Manifest.permission.ACCESS_FINE_LOCATION,
//                }, REQ_ACCESS_FINE_LOCATION);

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_device_scan, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (mLeDeviceListAdapter == null) return false;
        if (item.getItemId() == R.id.menu_scan) {
            mLeDeviceListAdapter.clear();
            startScan();
        } else if (item.getItemId() == R.id.menu_stop) {
            endScan();
        }
        return true;
    }

    @Override
    protected void onResume() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onResume:"
                + " mBluetoothAdapter.isEnabled()=" + mBluetoothAdapter.isEnabled()
                + " mLeDeviceListAdapter=" + Utils.getHashCode(mLeDeviceListAdapter));
        super.onResume();

        // Check location
        if (!checkFineLocationPermission(this)) return;

        if (Build.VERSION.SDK_INT >= 31) {
            // Check Scan
            if (!checkScanPermission(this)) return;

            // Check Connect
            if (!checkConnectPermission(this)) return;
        }

        // Ensure Bluetooth is enabled on the device. If Bluetooth is not
        // currently enabled,
        // fire an intent to display a dialog asking the user to grant
        // permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(intent);
        } else {
            // Initialize the list view adapter
            if (mLeDeviceListAdapter == null) {
                mLeDeviceListAdapter = new LeDeviceListAdapter();
                mListView.setAdapter(mLeDeviceListAdapter);
                startScan();
            }
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onPause");
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[]
            permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions,
                grantResults);
        Log.d(TAG, "onRequestPermissionsResult: nPermissions="
                + permissions.length);
        for (int i = 0; i < permissions.length; i++) {
            Log.d(TAG, "    " + permissions[i] + " "
                    + (grantResults[i] == PackageManager.PERMISSION_GRANTED
                    ? "Granted" : "Denied"));
        }
        if (requestCode == REQ_ACCESS_FINE_LOCATION) {
            // FINE_LOCATION
            if (grantResults.length > 0 && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, this.getClass().getSimpleName()
                        + " onRequestPermissionsResult");
                mFineDenied = false;
                startScan();
            } else if (grantResults.length > 0 && grantResults[0] ==
                    PackageManager.PERMISSION_DENIED) {
                mFineDenied = true;
                String msg = "Scanning for devices cannot be done " +
                        "without LOCATION permission. You can do "
                        + getString(R.string.menu_select_device)
                        + " to be prompted for the permission again."
                        + " You can change the permission in Settings if"
                        + " you have selected \"don't ask again\".";
                Utils.warnMsg(this, msg);
            }
        }
        if (requestCode == REQ_ACCESS_COARSE_LOCATION) {
            // COARSE_LOCATION
            if (grantResults.length > 0 && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
                mFineDenied = false;
                startScan();
            } else if (grantResults.length > 0 && grantResults[0] ==
                    PackageManager.PERMISSION_DENIED) {
                mFineDenied = true;
                String msg = "Scanning for devices cannot be done " +
                        "without LOCATION permission. You can do "
                        + getString(R.string.menu_select_device)
                        + " to be prompted for the permission again."
                        + " You can change the permission in Settings if"
                        + " you have selected \"don't ask again\".";
                Utils.warnMsg(this, msg);
            }
        }
        if (requestCode == REQ_ACCESS_BLUETOOTH_CONNECT) {
            // BLUETOOTH_CONNECT
            if (grantResults.length > 0 && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
                mConnectDenied = false;
                startScan();
            } else if (grantResults.length > 0 && grantResults[0] ==
                    PackageManager.PERMISSION_DENIED) {
                mConnectDenied = true;
                mFineDenied = true;
                String msg = "Scanning for devices cannot be done " +
                        "without BLUETOOTH_CONNECT permission. You can do "
                        + getString(R.string.menu_select_device)
                        + " to be prompted for the permission again."
                        + " You can change the permission in Settings if"
                        + " you have selected \"don't ask again\".";
                Utils.warnMsg(this, msg);
            }
        }
        if (requestCode == REQ_ACCESS_BLUETOOTH_SCAN) {
            // BLUETOOTH_SCAN
            if (grantResults.length > 0 && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
                mScanDenied = false;
                startScan();
            } else if (grantResults.length > 0 && grantResults[0] ==
                    PackageManager.PERMISSION_DENIED) {
                mFineDenied = true;
                String msg = "Scanning for devices cannot be done " +
                        "without BLUETOOTH_SCAN permission. You can do "
                        + getString(R.string.menu_select_device)
                        + " to be prompted for the permission again."
                        + " You can change the permission in Settings if"
                        + " you have selected \"don't ask again\".";
                Utils.warnMsg(this, msg);
            }
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean checkFineLocationPermission(Activity activity) {
        // Check location
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            if (!mFineDenied) {
                Log.d(TAG, "checkFineLocationPermission:"
                        + " ACCESS_FINE_LOCATION not granted,"
                        + " requesting permission");
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQ_ACCESS_FINE_LOCATION);
            }
            return false;
        }
        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean checkScanPermission(Activity activity) {
        if (Build.VERSION.SDK_INT <= 31) return true;
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            if (!mScanDenied) {
                Log.d(TAG, "checkScanPermission: BLUETOOTH_SCAN not granted,"
                        + " requesting permission");
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.BLUETOOTH_SCAN},
                        REQ_ACCESS_BLUETOOTH_SCAN);
            }
            return false;
        }
        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean checkConnectPermission(Activity activity) {
        if (Build.VERSION.SDK_INT <= 31) return true;
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            if (!mConnectDenied) {
                Log.d(TAG, "checkConnectPermission: BLUETOOTH_SCAN not granted,"
                        + " requesting permission");
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        REQ_ACCESS_BLUETOOTH_SCAN);
            }
            return false;
        }
        return true;
    }

    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) {
            return;
        }
        // Stop scanning
        if (mScanning) {
            if (Build.VERSION.SDK_INT >= 31) {
                // Check Scan
                if (!checkScanPermission(this)) return;
            }
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
            mScanning = false;
        }
        final Intent data = new Intent();
        data.putExtra(DEVICE_NAME_CODE, device.getName());
        data.putExtra(DEVICE_ADDRESS_CODE, device.getAddress());
        setResult(RESULT_OK, data);
        finish();
    }

    private void endScan() {
        Log.d(TAG,
                this.getClass().getSimpleName() + " endScan: mScanning="
                        + mScanning);
        // Stop scanning
        if (mScanning) {
            if (Build.VERSION.SDK_INT >= 31) {
                if (!checkScanPermission(this)) return;
            }
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
        }
        mScanning = false;
        invalidateOptionsMenu();
    }

    private void startScan() {
        Log.d(TAG,
                this.getClass().getSimpleName() + " startScan: mScanning="
                        + mScanning);
        // Check for coarse location permission
        if (!checkFineLocationPermission(this)) return;
        if (!mScanning) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(() -> {
                Log.d(TAG, this.getClass().getSimpleName()
                        + ": startScan: Scanning timed out");
                mScanning = false;
                mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
                invalidateOptionsMenu();
            }, DEVICE_SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.getBluetoothLeScanner().startScan(null,
                    new ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .setReportDelay(1000)
                            .build(),
                    mLeScanCallback);
        }
        invalidateOptionsMenu();
    }


    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private final ArrayList<BluetoothDevice> mLeDevices;
        private final LayoutInflater mInflator;

        private LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        private void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        private BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        private void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = view
                        .findViewById(R.id.device_address);
                viewHolder.deviceName = view
                        .findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            if (Build.VERSION.SDK_INT >= 31) {
                if (!checkConnectPermission(DeviceScanActivity.this)) {
                    return view;
                }
            }
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.deviceName.setText(deviceName);
                viewHolder.deviceAddress.setText(device.getAddress());
            } else {
                viewHolder.deviceName.setText(R.string.unknown_device);
                viewHolder.deviceAddress.setText("");
            }
            return view;
        }
    }

    // Device scan callback.
    private final ScanCallback mLeScanCallback = new
            ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    Log.d(TAG, this.getClass().getSimpleName() + ": " +
                            "onScanResult");
                    final BluetoothDevice device = result.getDevice();
                    String deviceAddress = device.getAddress();
                    runOnUiThread(() -> {
                        mLeDeviceListAdapter.addDevice(device);
                        mLeDeviceListAdapter.notifyDataSetChanged();
                    });
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    Log.d(TAG, "ScanCallback: onBatchScanResults"
                            + " nResults=" + results.size());
                    // Results is non-null
                    for (ScanResult result : results) {
                        final BluetoothDevice device = result.getDevice();
                        if (Build.VERSION.SDK_INT >= 31) {
                            if (!checkConnectPermission(DeviceScanActivity.this)) {
                                return;
                            }
                        }
                        Log.d(TAG, "    device=" + device.getName()
                                + " " + device.getAddress());
                        runOnUiThread(() -> {
                            mLeDeviceListAdapter.addDevice(device);
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        });
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    String msg = "Unknown";
                    if (errorCode == SCAN_FAILED_ALREADY_STARTED) {
                        msg = "SCAN_FAILED_ALREADY_STARTED";
                    } else if (errorCode == SCAN_FAILED_APPLICATION_REGISTRATION_FAILED) {
                        msg = "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED";
                    } else if (errorCode == SCAN_FAILED_FEATURE_UNSUPPORTED) {
                        msg = "SCAN_FAILED_FEATURE_UNSUPPORTED";
                    } else if (errorCode == SCAN_FAILED_INTERNAL_ERROR) {
                        msg = "SCAN_FAILED_INTERNAL_ERROR";
                    }
                    Log.d(TAG, "ScanCallback:  onScanFailed " + msg);
                }
            };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}