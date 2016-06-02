package net.kenevans.android.blecardiacmonitor;

import android.bluetooth.BluetoothGattCharacteristic;

public class HeartRateValues implements IConstants {
    long date;
    private int hr = INVALID_INT;
    int sensorContact = INVALID_INT;
    int ee = INVALID_INT;
    private String rr = INVALID_STRING;
    private String info;

    public HeartRateValues(BluetoothGattCharacteristic characteristic, long
            date) {
        this.date = date;
        if (!characteristic.getUuid().equals(UUID_HEART_RATE_MEASUREMENT)) {
            return;
        }
        String string = "";
        int flag = characteristic.getProperties();
        int format;
        int offset = 1;
        if ((flag & 0x01) != 0) {
            format = BluetoothGattCharacteristic.FORMAT_UINT16;
            offset += 2;
        } else {
            format = BluetoothGattCharacteristic.FORMAT_UINT8;
            offset += 1;
        }
        hr = characteristic.getIntValue(format, 1);
        string += "Heart Rate: " + hr;
        // Sensor Contact
        sensorContact = (flag >> 1) & 0x11;
        switch (sensorContact) {
            case 0:
            case 1:
                string += "\nSensor contact not supported";
                break;
            case 2:
                string += "\nSensor contact not detected";
                break;
            case 3:
                string += "\nSensor contact detected";
                break;
        }
        // Energy Expended
        if ((flag & 0x08) != 0) {
            offset += 2;
            ee = characteristic.getIntValue(
                    BluetoothGattCharacteristic.FORMAT_UINT16, offset);
            string += "\nEnergy Expended: " + ee;
        } else {
            string += "\nEnergy Expended: NA";
        }
        // R-R
        if ((flag & 0x10) != 0) {
            int len = characteristic.getValue().length;
            // There may be more than 1 R-R value
            int iVal;
            String rrString = "";
            while (offset < len) {
                iVal = characteristic.getIntValue(
                        BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                offset += 2;
                rrString += " " + iVal;
            }
            rr = rrString.trim();
            string += "\nR-R: " + rrString;
        } else {
            string += "\nR-R: NA";
        }
        // // DEBUG
        // final byte[] data = characteristic.getValue();
        // if (data != null && data.length > 0) {
        // final StringBuilder stringBuilder = new StringBuilder(data.length);
        // for (byte byteChar : data) {
        // stringBuilder.append(String.format("%02X ", byteChar));
        // }
        // info += "\n" + stringBuilder.toString();
        // }
        this.info = string;
    }

    /**
     * Gets the data value.
     *
     * @return The date.
     */
    @SuppressWarnings("unused")
    public long getDate() {
        return date;
    }

    /**
     * Gets the heart rate.
     *
     * @return The heart rate.
     */
    @SuppressWarnings("unused")
    public int getHr() {
        return hr;
    }

    /**
     * Gets the sensor contact
     *
     * @return The sensor contact.
     */
    @SuppressWarnings("unused")
    public int getSensorContact() {
        return sensorContact;
    }

    /**
     * Gets the energy expended.
     *
     * @return The energy expended.
     */
    @SuppressWarnings("unused")
    public int getEe() {
        return ee;
    }

    /**
     * Gets the R-R values.
     *
     * @return The R-R values.
     */
    public String getRr() {
        return rr;
    }

    /**
     * Gets info on the data in the characteristic.
     *
     * @return The info.
     */
    public String getInfo() {
        return info;
    }

}
