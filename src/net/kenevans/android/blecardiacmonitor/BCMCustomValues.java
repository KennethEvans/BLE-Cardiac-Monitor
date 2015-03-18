package net.kenevans.android.blecardiacmonitor;

import android.bluetooth.BluetoothGattCharacteristic;

public class BCMCustomValues implements IConstants {
	long date = INVALID_DATE;
	private int activity = INVALID_INT;
	private int pa = INVALID_INT;
	private String info;

	public BCMCustomValues(BluetoothGattCharacteristic characteristic, long date) {
		this.date = date;
		if (!characteristic.getUuid().equals(UUID_CUSTOM_MEASUREMENT)) {
			return;
		}
		int offset = 0;
		int flag = characteristic.getIntValue(
				BluetoothGattCharacteristic.FORMAT_UINT8, offset);
		offset += 1;
		if ((flag & 0x01) != 0) {
			activity = characteristic.getIntValue(
					BluetoothGattCharacteristic.FORMAT_UINT16, offset);
			offset += 2;
			info += "Activity: " + activity;
		} else {
			info += "Activity: NA";
		}
		if ((flag & 0x02) != 0) {
			pa = characteristic.getIntValue(
					BluetoothGattCharacteristic.FORMAT_UINT16, offset);
			offset += 2;
			info += "\nPeak Acceleration: " + pa;
		} else {
			info += "\nPeak Acceleration: NA";
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
	}

	/**
	 * Gets the data value.
	 * 
	 * @return
	 */
	public long getDate() {
		return date;
	}

	/**
	 * Gets the activity.
	 * 
	 * @return
	 */
	public int getActivity() {
		return activity;
	}

	/**
	 * Gets the peak acceleration.
	 * 
	 * @return
	 */
	public int getPa() {
		return pa;
	}

	/**
	 * Gets info on the data in the characteristic.
	 * 
	 * @return
	 */
	public String getInfo() {
		return info;
	}

}
