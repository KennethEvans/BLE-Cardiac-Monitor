package net.kenevans.android.blecardiacmonitor;

import android.widget.CheckBox;

public class Session implements IConstants {
	private String name;
	private long startDate = INVALID_DATE;
	private long endDate = INVALID_DATE;
	private boolean checked = false;
	private CheckBox checkBox;

	public Session(String name, long startDate, long endDate) {
		this.name = name;
		this.startDate = startDate;
		this.endDate = endDate;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getStartDate() {
		return startDate;
	}

	public void setStartDate(long startDate) {
		this.startDate = startDate;
	}

	public long getEndDate() {
		return endDate;
	}

	public void setEndDate(long endDate) {
		this.endDate = endDate;
	}

	public long getDuration() {
		return endDate - startDate;
	}

	public boolean isChecked() {
		return checked;
	}

	public void setChecked(boolean checked) {
		this.checked = checked;
	}

	public CheckBox getCheckBox() {
		return checkBox;
	}

	public void setCheckBox(CheckBox checkBox) {
		this.checkBox = checkBox;
	}

}
