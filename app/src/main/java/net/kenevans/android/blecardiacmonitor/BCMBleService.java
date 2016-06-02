/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kenevans.android.blecardiacmonitor;

import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Service for managing connection and data communication with a GATT server
 * hosted on a given Bluetooth LE device.
 */
public class BCMBleService extends Service implements IConstants {
    private final static String TAG = "BCMService";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private BCMDbAdapter mDbAdapter;
    private int mConnectionState = BluetoothProfile.STATE_DISCONNECTED;
    private long mLastHrDate;
    private int mLastBat = INVALID_INT;
    private int mLastHr = INVALID_INT;
    private String mLastRr = INVALID_STRING;

    private BluetoothGattCharacteristic mCharBat;
    private BluetoothGattCharacteristic mCharHr;
    private boolean mSessionInProgress = false;
    private long mSessionStartTime;

    private Queue<BluetoothGattDescriptor> descriptorWriteQueue = new
            LinkedList<>();
    private Queue<BluetoothGattCharacteristic> characteristicReadQueue = new
            LinkedList<>();

    private final IBinder mBinder = new LocalBinder();

    public final static String ACTION_GATT_CONNECTED = PACKAGE_NAME
            + ".ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = PACKAGE_NAME
            + ".ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = PACKAGE_NAME
            + ".ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = PACKAGE_NAME
            + ".ACTION_DATA_AVAILABLE";
    public final static String ACTION_ERROR = PACKAGE_NAME + ".ACTION_ERROR";

    /**
     * Implements callback methods for GATT events.
     */
    private final BluetoothGattCallback mGattCallback = new
            BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int
                        status,
                                                    int newState) {
                    Log.i(TAG, "onConnectionStateChange: status="
                            + (status == BluetoothGatt.GATT_SUCCESS ?
                            "GATT_SUCCESS"
                            : status));
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG,
                                "onConnectionStateChange: Aborting: status is" +
                                        " not " +
                                        "GATT_SUCCESS");
                        return;
                    }
                    String intentAction;
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        intentAction = ACTION_GATT_CONNECTED;
                        mConnectionState = BluetoothProfile.STATE_CONNECTED;
                        // Stop any session
                        stopSession();
                        broadcastUpdate(intentAction);
                        Log.i(TAG, "onConnectionStateChange: Connected to " +
                                "GATT server");
                        // Attempts to discover services after successful
                        // connection.
                        Log.i(TAG,
                                "onConnectionStateChange: Attempting to start" +
                                        " service" +
                                        " discovery: "
                                        + mBluetoothGatt.discoverServices());
                    } else if (newState == BluetoothProfile
                            .STATE_DISCONNECTED) {
                        intentAction = ACTION_GATT_DISCONNECTED;
                        mConnectionState = BluetoothProfile.STATE_DISCONNECTED;
                        Log.i(TAG,
                                "onConnectionStateChange: Disconnected from " +
                                        "GATT " +
                                        "server");
                        // Stop any session
                        stopSession();
                        broadcastUpdate(intentAction);
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int
                        status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                    } else {
                        Log.w(TAG, "onServicesDiscovered received: " + status);
                    }
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic
                                                         characteristic, int
                                                         status) {
                    characteristicReadQueue.remove();
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                    } else {
                        Log.w(TAG, "onCharacteristicRead received: " + status);
                    }
                    if (characteristicReadQueue.size() > 0)
                        mBluetoothGatt.readCharacteristic
                                (characteristicReadQueue
                                        .element());
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt,
                                                    BluetoothGattCharacteristic
                                                            characteristic) {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt,
                                              BluetoothGattDescriptor
                                                      descriptor, int
                                                      status) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "onDescriptorWrite: Error writing GATT " +
                                "Descriptor: "
                                + status);
                    }
                    // Pop the item that we just finishing writing
                    descriptorWriteQueue.remove();
                    // Check if there is more to write
                    if (descriptorWriteQueue.size() > 0)
                        mBluetoothGatt.writeDescriptor(descriptorWriteQueue
                                .element());
                    else if (characteristicReadQueue.size() > 0)
                        mBluetoothGatt.readCharacteristic
                                (characteristicReadQueue
                                        .element());
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        // Post a notification the service is running
        Intent activityIntent = new Intent(this, DeviceMonitorActivity.class);
        PendingIntent viewPendingIntent = PendingIntent.getActivity(this, 0,
                activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notificationBuilder = new
                NotificationCompat.Builder(
                this)
                .setSmallIcon(R.drawable.blecardiacmonitor)
                .setContentTitle(getString(R.string.service_notification_title))
                .setContentText(getString(R.string.service_notification_text))
                .setContentIntent(viewPendingIntent);
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat
                        .from(this);
        notificationManager
                .notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    @Override
    public void onDestroy() {
        // Cancel the notification
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat
                        .from(this);
        notificationManager.cancel(NOTIFICATION_ID);
        super.onDestroy();
    }

    // /**
    // * Broadcast an error using ACTION_ERROR.
    // *
    // * @param msg
    // */
    // private void broadcastError(final String msg) {
    // final Intent intent = new Intent(ACTION_ERROR);
    // intent.putExtra(EXTRA_MSG, msg);
    // sendBroadcast(intent);
    // }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic
                                         characteristic) {
        Date now = new Date();
        long date = now.getTime();
        // // DEBUG
        // Set this to "" to not get the date in the return value
        // String dateStr = " @ " + millisecTimeFormater.format(now);
        String dateStr = "";
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_UUID, characteristic.getUuid().toString());
        intent.putExtra(EXTRA_DATE, date);

        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            HeartRateValues values = new HeartRateValues(characteristic, date);
            mLastHr = values.getHr();
            mLastRr = values.getRr();
            mLastHrDate = date;
            // // DEBUG
            // Log.d(TAG, String.format("Received heart rate measurement: %d",
            // mLastHr));
            if (mDbAdapter != null) {
                mDbAdapter.createData(mLastHrDate, mSessionStartTime, mLastHr,
                        mLastRr);
            }
            intent.putExtra(EXTRA_HR, String.valueOf(values.getHr() + dateStr));
            intent.putExtra(EXTRA_RR, values.getRr() + dateStr);
            intent.putExtra(EXTRA_DATA, values.getInfo());
        } else if (UUID_BATTERY_LEVEL.equals(characteristic.getUuid())) {
            mLastBat = characteristic.getIntValue(
                    BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            Log.d(TAG, String.format("Received battery level: %d", mLastBat));
            intent.putExtra(EXTRA_BAT, String.valueOf(mLastBat) + dateStr);
            intent.putExtra(EXTRA_DATA,
                    String.valueOf("Battery Level: " + mLastBat));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(
                        data.length);
                for (byte byteChar : data) {
                    stringBuilder.append(String.format("%02X ", byteChar));
                }
                intent.putExtra(
                        EXTRA_DATA,
                        BleNamesResolver
                                .resolveCharacteristicName(characteristic
                                        .getUuid().toString())
                                + "\n"
                                + new String(data)
                                + "\n"
                                + stringBuilder.toString());
            } else {
                intent.putExtra(
                        EXTRA_DATA,
                        BleNamesResolver
                                .resolveCharacteristicName(characteristic
                                        .getUuid().toString())
                                + "\n" + ((data == null) ? "null" : "No data"));
            }
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BCMBleService getService() {
            return BCMBleService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        close();
        return super.onUnbind(intent);
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        Log.d(TAG, "initialize");
        // For API level 18 and above, get a reference to BluetoothAdapter
        // through BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context
                    .BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter");
            return false;
        }

        return true;
    }

    /**
     * Starts writing to the database with the given adapter.
     *
     * @param adapter The adapter.
     * @return If successful.
     */
    public boolean startDatabase(BCMDbAdapter adapter) {
        Log.d(TAG, "startDatabase");
        mDbAdapter = adapter;
        return mDbAdapter != null;
    }

    /**
     * Stops writing to the the database.
     */
    public void stopDatabase() {
        Log.d(TAG, "stopDatabase");
        mDbAdapter = null;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The
     * connection result is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth
     * .BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        Log.d(TAG, "connect");
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG,
                    "connect: BluetoothAdapter not initialized or unspecified" +
                            " address");
            return false;
        }

        // Previously connected device. Try to reconnect.
        if (mBluetoothDeviceAddress != null
                && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG,
                    "connect: Trying to use an existing mBluetoothGatt for " +
                            "connection");
            if (mBluetoothGatt.connect()) {
                mConnectionState = BluetoothProfile.STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter
                .getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect");
            return false;
        }
        // We want to directly connect to the device, so we are setting the
        // autoConnect parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection");
        mBluetoothDeviceAddress = address;
        mConnectionState = BluetoothProfile.STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The
     * disconnection result is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth
     * .BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        Log.d(TAG, "disconnect");
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure
     * resources are released properly.
     */
    public void close() {
        Log.d(TAG, "close");
        stopDatabase();
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Enables or disables notification on a given characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification. False otherwise.
     */
    public void setCharacteristicNotification(
            BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        boolean res = mBluetoothGatt.setCharacteristicNotification(
                characteristic, enabled);
        if (!res) {
            Log.d(TAG,
                    "setCharacteristicNotification failed for "
                            + BleNamesResolver
                            .resolveCharacteristicName(characteristic
                                    .getUuid().toString()));
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This
     * should be invoked only after {@code BluetoothGatt#discoverServices()}
     * completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null)
            return null;

        return mBluetoothGatt.getServices();
    }

    /**
     * Returns the connection state.
     *
     * @return The connection state.
     */
    public int getConnectionState() {
        return mConnectionState;
    }

    /**
     * Writes READ, NOTIFY, WRITE properties to the Log. Use for debugging.
     *
     * @param charBat    The BAT characteristic.
     * @param charHr     The HR characteristic.
     * @param charCustom The custom characteristic.
     */
    @SuppressWarnings("unused")
    public void checkPermissions(BluetoothGattCharacteristic charBat,
                                 BluetoothGattCharacteristic charHr,
                                 BluetoothGattCharacteristic charCustom) {
        // DEBUG
        // Check permissions
        if ((charBat.getProperties() & BluetoothGattCharacteristic
                .PROPERTY_READ) == 0) {
            Log.d(TAG, "incrementSessionState: charBat: Not Readable");
        } else {
            Log.d(TAG, "incrementSessionState: charBat: Readable");
        }
        if ((charBat.getProperties() & BluetoothGattCharacteristic
                .PROPERTY_NOTIFY) == 0) {
            Log.d(TAG, "incrementSessionState: charBat: Not Notifiable");
        } else {
            Log.d(TAG, "incrementSessionState: charBat: Notifiable");
        }
        if ((charBat.getProperties() & BluetoothGattCharacteristic
                .PROPERTY_WRITE) == 0) {
            Log.d(TAG, "incrementSessionState: charBat: Not Writable");
        } else {
            Log.d(TAG, "incrementSessionState: charBat: Writable");
        }

        if ((charHr.getProperties() & BluetoothGattCharacteristic
                .PROPERTY_READ) == 0) {
            Log.d(TAG, "incrementSessionState: charHr: Not Readable");
        } else {
            Log.d(TAG, "incrementSessionState: charHr: Readable");
        }
        if ((charHr.getProperties() & BluetoothGattCharacteristic
                .PROPERTY_NOTIFY) == 0) {
            Log.d(TAG, "incrementSessionState: charHr: Not Notifiable");
        } else {
            Log.d(TAG, "incrementSessionState: charHr: Notifiable");
        }
        if ((charHr.getProperties() & BluetoothGattCharacteristic
                .PROPERTY_WRITE) == 0) {
            Log.d(TAG, "incrementSessionState: charHr: Not Writable");
        } else {
            Log.d(TAG, "incrementSessionState: charHr: Writable");
        }

        if ((charCustom.getProperties() & BluetoothGattCharacteristic
                .PROPERTY_READ) == 0) {
            Log.d(TAG, "incrementSessionState: charCustom: Not Readable");
        } else {
            Log.d(TAG, "incrementSessionState: charCustom: Readable");
        }
        if ((charCustom.getProperties() & BluetoothGattCharacteristic
                .PROPERTY_NOTIFY) == 0) {
            Log.d(TAG, "incrementSessionState: charCustom: Not Notifiable");
        } else {
            Log.d(TAG, "incrementSessionState: charCustom: Notifiable");
        }
        if ((charCustom.getProperties() & BluetoothGattCharacteristic
                .PROPERTY_WRITE) == 0) {
            Log.d(TAG, "incrementSessionState: charCustom: Not Writable");
        } else {
            Log.d(TAG, "incrementSessionState: charCustom: Writable");
        }
    }

    /**
     * Returns if a session is in progress
     *
     * @return If in progress.
     */
    public boolean getSessionInProgress() {
        return mSessionInProgress;
    }

    /**
     * Initializes reading the battery level.
     */
    public void readBatteryLevel() {
        if (mCharBat == null) {
            return;
        }
        // Add it to the queueO
        characteristicReadQueue.add(mCharBat);
        // Process the queue if this is the only pending item
        // Otherwise handle it asynchronously
        if (descriptorWriteQueue.size() == 0
                || characteristicReadQueue.size() == 1) {
            mBluetoothGatt
                    .readCharacteristic(characteristicReadQueue.element());
        }
    }

    /**
     * Starts a session.
     *
     * @param charBat The BAT characteristic.
     * @param charHr  The HR characteristic.
     * @return If successful. (Always returns true)
     */
    public boolean startSession(BluetoothGattCharacteristic charBat,
                                BluetoothGattCharacteristic charHr) {
        Log.d(TAG, "startSession");
        // Log.d(TAG, "startSession: mSessionState=" + mSessionState
        // + " mTimeoutTimer=" + mTimeoutTimer);
        // // DEBUG
        // String batVal = mCharBat == null ? "null" : String.format("%8x",
        // mCharBat.hashCode());
        // String hrVal = mCharHr == null ? "null" : String.format("%8x",
        // mCharHr.hashCode());
        // String customVal = mCharCustom == null ? "null" :
        // String.format("%8x",
        // mCharCustom.hashCode());
        // Log.d(TAG, "  mCharBat=" + batVal + " mCharHr=" + hrVal
        // + " mCharCustom=" + customVal);
        // batVal = charBat == null ? "null" : String.format("%8x",
        // charBat.hashCode());
        // hrVal = charHr == null ? "null" : String.format("%8x",
        // charHr.hashCode());
        // customVal = charCustom == null ? "null" : String.format("%8x",
        // charCustom.hashCode());
        // Log.d(TAG, "  charBat=" + batVal + " charHr=" + hrVal +
        // " charCustom="
        // + customVal);
        // Log.d(TAG, "  mDoBat=" + mDoBat + " mDoHr=" + mDoHr + " mDoCustom="
        // + mDoCustom);
        if (!mSessionInProgress) {
            mSessionStartTime = new Date().getTime();
        }

        // // DEBUG Check permissions
        // checkPermissions(charBat, charHr, charCustom);

        // Stop notifying for existing characteristics
        if (mCharHr != null) {
            setCharacteristicNotification(mCharHr, false);
        }

        // Clear any queues
        while (descriptorWriteQueue.size() > 0) {
            descriptorWriteQueue.remove();
        }
        while (characteristicReadQueue.size() > 0) {
            characteristicReadQueue.remove();
        }

        // Initialize for the new values
        mCharBat = charBat;
        mCharHr = charHr;
        mLastBat = INVALID_INT;
        mLastHr = INVALID_INT;
        mLastRr = INVALID_STRING;
        mLastHrDate = new Date().getTime();
        BluetoothGattDescriptor descriptor;
        if (mCharBat != null) {
            characteristicReadQueue.add(mCharBat);
        }
        if (mCharHr != null) {
            descriptor = mCharHr
                    .getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
            descriptor
                    .setValue(BluetoothGattDescriptor
                            .ENABLE_NOTIFICATION_VALUE);
            descriptorWriteQueue.add(descriptor);
            setCharacteristicNotification(mCharHr, true);
        }

        // Start the queues. Do writeDescriptors before any readCharacteristics
        if (descriptorWriteQueue.size() > 0) {
            mBluetoothGatt.writeDescriptor(descriptorWriteQueue.element());
        } else if (characteristicReadQueue.size() > 0) {
            mBluetoothGatt
                    .readCharacteristic(characteristicReadQueue.element());
        }

        mSessionInProgress = true;
        return true;
    }

    /**
     * Stops a session.
     */
    public void stopSession() {
        Log.d(TAG, "stopSession");
        // Clear any queues
        while (descriptorWriteQueue.size() > 0) {
            descriptorWriteQueue.remove();
        }
        while (characteristicReadQueue.size() > 0) {
            characteristicReadQueue.remove();
        }
        // Stop notifying for existing characteristics
        if (mSessionInProgress && mCharHr != null) {
            setCharacteristicNotification(mCharHr, false);
        }
        mCharBat = null;
        mCharHr = null;
        mLastHr = -1;
        mLastRr = null;
        mSessionInProgress = false;
    }

}
