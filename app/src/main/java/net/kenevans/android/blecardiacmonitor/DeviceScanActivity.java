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
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

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
    private boolean mCoarseLocationPermissionAsked = false;
    private boolean mAllowScan;
    private ListView mListView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_activity_devices);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
        setContentView(R.layout.list_view);
        mHandler = new Handler();
        mListView = findViewById(R.id.mainListView);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                onListItemClick(mListView, view, position, id);
            }
        });

        // Use this check to determine whether BLE is supported on the device.
        // Then you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            String msg = getString(R.string.ble_not_supported);
            Utils.errMsg(this, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            finish();
        }

        /// Seems to be necessary
        ActivityCompat.requestPermissions(this, new String[]
                {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                }, 0);

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
    public boolean onOptionsItemSelected(MenuItem item) {
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
        Log.d(TAG, this.getClass().getSimpleName() + ": onResume");
        super.onResume();
        // Ensures Bluetooth is enabled on the device. If Bluetooth is not
        // currently enabled,
        // fire an intent to display a dialog asking the user to grant
        // permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQ_ENABLE_BT_CODE);
        } else {
            // Initialize the list view adapter
            mLeDeviceListAdapter = new LeDeviceListAdapter();
            mListView.setAdapter(mLeDeviceListAdapter);
            startScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[]
            permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_ACCESS_COARSE_LOCATION) {
            // COARSE_LOCATION
            if (grantResults.length > 0 && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
                mAllowScan = true;
                startScan();
            } else if (grantResults.length > 0 && grantResults[0] ==
                    PackageManager.PERMISSION_DENIED) {
                mAllowScan = false;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent
            data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQ_ENABLE_BT_CODE
                && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) {
            return;
        }
        if (mScanning) {
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
        Log.d(TAG, "endScan");
        // Stop
        if (mScanning) {
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
        }
        mScanning = false;
        invalidateOptionsMenu();
    }

    private void startScan() {
        Log.d(TAG, this.getClass().getSimpleName() + ": startScan");
        // Check for coarse location permission
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            mAllowScan = false;
            if (!mCoarseLocationPermissionAsked) {
                Log.d(TAG, "startScan: requestPermission");
                mCoarseLocationPermissionAsked = true;
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission
                                .ACCESS_COARSE_LOCATION},
                        PERMISSION_ACCESS_COARSE_LOCATION);
            } else {
                Log.d(TAG, "startScan: infoMsg");
                String msg = getString(R.string.permission_coarse_location);
                if (mCoarseLocationPermissionAsked) {
                    Utils.infoMsg(this, msg);
                }
            }
        } else {
            mAllowScan = true;
        }

        if (mAllowScan) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
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
    private ScanCallback mLeScanCallback = new
            ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    Log.d(TAG, this.getClass().getSimpleName() + ": " +
                            "onScanResult");
                    final BluetoothDevice device = result.getDevice();
                    String deviceAddress = device.getAddress();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLeDeviceListAdapter.addDevice(device);
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    Log.d(TAG, this.getClass().getSimpleName() + ": " +
                            "onBatchScanResults"
                            + " nResults=" + results.size());
                    // Results is non-null
                    for (ScanResult result : results) {
                        final BluetoothDevice device = result.getDevice();
                        Log.d(TAG, "    device=" + device.getName()
                                + " " + device.getAddress());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mLeDeviceListAdapter.addDevice(device);
                                mLeDeviceListAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.d(TAG, this.getClass().getSimpleName() + ": " +
                            "onScanFailed");
                }
            };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }

}