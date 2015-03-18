package net.kenevans.android.blecardiacmonitor;

import java.io.File;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Simple database access helper class, modified from the Notes example
 * application.
 */
public class BCMDbAdapter implements IConstants {
	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;
	private final Activity mActivity;
	private File mDataDir;

	/** Database creation SQL statement */
	private static final String DB_CREATE_DATA_TABLE = "create table "
			+ DB_DATA_TABLE + " (_id integer primary key autoincrement, "
			+ COL_DATE + " integer not null, " + COL_START_DATE
			+ " integer not null, " + COL_HR + " integer not null, " + COL_RR
			+ " text not null, " + COL_ACT + " integer not null," + COL_PA
			+ " integer not null);";

	/**
	 * Constructor - takes the context to allow the database to be
	 * opened/created
	 * 
	 * @param activity
	 *            The context.
	 * @param dataDir
	 *            The location of the data.
	 */
	public BCMDbAdapter(Activity activity, File dataDir) {
		mActivity = activity;
		mDataDir = dataDir;
	}

	/**
	 * Open the database. If it cannot be opened, try to create a new instance
	 * of the database. If it cannot be created, throw an exception to signal
	 * the failure
	 * 
	 * @return this (self reference, allowing this to be chained in an
	 *         initialization call).
	 * @throws SQLException
	 *             if the database could be neither opened or created.
	 */
	public BCMDbAdapter open() throws SQLException {
		// Make sure the directory exists and is available
		if (mDataDir == null) {
			mActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Utils.errMsg(mActivity, "Cannot access database");
				}
			});
			return null;
		}
		try {
			if (!mDataDir.exists()) {
				mDataDir.mkdirs();
				// Try again
				if (!mDataDir.exists()) {
					mActivity.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Utils.errMsg(mActivity,
									"Unable to create database directory at "
											+ mDataDir);
						}
					});
					return null;
				}
			}
			mDbHelper = new DatabaseHelper(mActivity, mDataDir.getPath()
					+ File.separator + DB_NAME);
			mDb = mDbHelper.getWritableDatabase();
		} catch (final Exception ex) {
			mActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Utils.excMsg(mActivity, "Error opening database at "
							+ mDataDir, ex);
				}
			});
		}
		return this;
	}

	public void close() {
		mDbHelper.close();
	}

	/**
	 * Create new data using the parameters provided. If the data is
	 * successfully created return the new rowId for that entry, otherwise
	 * return a -1 to indicate failure.
	 * 
	 * @param date
	 * @param startDate
	 * @param hr
	 * @param rr
	 * @param activity
	 * @param pa
	 * @return
	 */
	public long createData(long date, long startDate, int hr, String rr,
			int activity, int pa) {
		if (mDb == null) {
			mActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Utils.errMsg(mActivity,
							"Failed to create data. Database is null.");
				}
			});
			return -1;
		}
		ContentValues values = new ContentValues();
		values.put(COL_DATE, date);
		values.put(COL_START_DATE, startDate);
		values.put(COL_HR, hr);
		values.put(COL_RR, rr);
		values.put(COL_ACT, activity);
		values.put(COL_PA, pa);

		return mDb.insert(DB_DATA_TABLE, null, values);
	}

	/**
	 * Delete all the data and recreate the table.
	 * 
	 * @return true if deleted, false otherwise.
	 */
	public void recreateDataTable() {
		mDb.execSQL("DROP TABLE IF EXISTS " + DB_DATA_TABLE);
		mDb.execSQL(DB_CREATE_DATA_TABLE);
	}

	/**
	 * Return a Cursor over the list of all items in the database.
	 * 
	 * @return Cursor over items.
	 */
	public Cursor fetchAllData(String filter) {
		if (mDb == null) {
			return null;
		}
		return mDb.query(DB_DATA_TABLE, new String[] { COL_ID, COL_DATE,
				COL_START_DATE, COL_HR, COL_RR, COL_ACT, COL_PA }, filter,
				null, null, null, SORT_ASCENDING);
	}

	/**
	 * Delete the data with the given rowId.
	 * 
	 * @param rowId
	 *            id of data to delete
	 * @return true if deleted, false otherwise.
	 */
	public boolean deleteData(long rowId) {
		return mDb.delete(DB_DATA_TABLE, COL_ID + "=" + rowId, null) > 0;
	}

	/**
	 * Return a Cursor positioned at the data that matches the given rowId
	 * 
	 * @param rowId
	 *            id of entry to retrieve.
	 * @return Cursor positioned to matching entry, if found.
	 * @throws SQLException
	 *             if entry could not be found/retrieved.
	 */
	public Cursor fetchData(long rowId) throws SQLException {
		Cursor mCursor = mDb.query(true, DB_DATA_TABLE, new String[] { COL_ID,
				COL_DATE, COL_START_DATE, COL_HR, COL_RR, COL_ACT, COL_PA },
				COL_ID + "=" + rowId, null, null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

	/**
	 * Update the data using the details provided. The data to be updated is
	 * specified using the rowId, and it is altered to use the values passed in.
	 * 
	 * @param rowId
	 * @param date
	 * @param startDate
	 * @param hr
	 * @param rr
	 * @param activity
	 * @param pa
	 * @param comment
	 * @return
	 */
	public boolean updateData(long rowId, long date, long startDate, int hr,
			String rr, int activity, int pa) {
		ContentValues values = new ContentValues();
		values.put(COL_DATE, date);
		values.put(COL_START_DATE, startDate);
		values.put(COL_HR, hr);
		values.put(COL_RR, rr);
		values.put(COL_ACT, activity);
		values.put(COL_PA, pa);

		return mDb.update(DB_DATA_TABLE, values, COL_ID + "=" + rowId, null) > 0;
	}

	/**
	 * Return a Cursor over the list of start and ending times, sorted in
	 * reverse order.
	 * 
	 * @param date
	 * @return Cursor over items.
	 */
	public Cursor fetchAllSessionStartEndData() {
		if (mDb == null) {
			return null;
		}
		return mDb.query(DB_DATA_TABLE, new String[] { COL_START_DATE,
				COL_END_DATE }, null, null, COL_START_DATE, null,
				SORT_DESCENDING);
	}

	///////////////////////////////////////////////////////////////////////////
	// Get data for start date only (ForStartDate) ////////////////////////////
	///////////////////////////////////////////////////////////////////////////

	/**
	 * Deletes all data in the database for the interval corresponding to the
	 * given the start date.
	 * 
	 * @param start
	 * @return
	 */
	public boolean deleteAllDataForStartDate(long start) {
		return mDb.delete(DB_DATA_TABLE, COL_START_DATE + "=" + Long.toString(start),
				null) > 0;
	}

	/**
	 * Return a Cursor over the HR items in the database having the given the
	 * start date.
	 * 
	 * @param date
	 * @return Cursor over items.
	 */
	public Cursor fetchAllHrDateDataForStartDate(long date) {
		if (mDb == null) {
			return null;
		}
		return mDb.query(DB_DATA_TABLE, new String[] { COL_DATE, COL_HR },
				COL_START_DATE + "=" + Long.toString(date), null, null, null,
				SORT_ASCENDING);
	}

	/**
	 * Return a Cursor over the HR, RR, Activity, and PA items in the database
	 * having the given the start date
	 * 
	 * @param date
	 * @return Cursor over items.
	 */
	public Cursor fetchAllHrRrActPaDateDataForStartDate(long date) {
		if (mDb == null) {
			return null;
		}
		return mDb.query(DB_DATA_TABLE, new String[] { COL_DATE, COL_HR,
				COL_RR, COL_ACT, COL_PA },
				COL_START_DATE + "=" + Long.toString(date), null, null, null,
				SORT_ASCENDING);
	}

	///////////////////////////////////////////////////////////////////////////
	// Get data for start date through end date (ForDate) /////////////////////
	///////////////////////////////////////////////////////////////////////////

	/**
	 * Return a Cursor over the HR items in the database for a given start and
	 * end times.
	 * 
	 * @param start
	 * @param end
	 * @return Cursor over items.
	 */
	public Cursor fetchAllHrDateDataForDates(long start, long end) {
		if (mDb == null) {
			return null;
		}
		return mDb.query(DB_DATA_TABLE, new String[] { COL_DATE, COL_HR },
				COL_DATE + ">=" + Long.toString(start) + " AND " + COL_DATE
						+ "<=" + Long.toString(end), null, null, null,
				SORT_ASCENDING);
	}

	/**
	 * Return a Cursor over the HR and RR items in the database for a given
	 * start and end times.
	 * 
	 * @param start
	 * @param end
	 * @return Cursor over items.
	 */
	public Cursor fetchAllHrRrDateDataForDates(long start, long end) {
		if (mDb == null) {
			return null;
		}
		return mDb.query(DB_DATA_TABLE,
				new String[] { COL_DATE, COL_HR, COL_RR }, COL_DATE + ">="
						+ Long.toString(start) + " AND " + COL_DATE + "<="
						+ Long.toString(end), null, null, null, SORT_ASCENDING);
	}

	/**
	 * Return a Cursor over the Activity and PA items in the database for a
	 * given start and end times.
	 * 
	 * @param start
	 * @param end
	 * @return Cursor over items.
	 */
	public Cursor fetchAllActPaDateDataForDates(long start, long end) {
		if (mDb == null) {
			return null;
		}
		return mDb.query(DB_DATA_TABLE, new String[] { COL_DATE, COL_ACT,
				COL_PA }, COL_DATE + ">=" + Long.toString(start) + " AND "
				+ COL_DATE + "<=" + Long.toString(end), null, null, null,
				SORT_ASCENDING);
	}

	/**
	 * Return a Cursor over the HR, RR, Activity, and PA items in the database
	 * for a given start and end times.
	 * 
	 * @param start
	 * @param end
	 * @return Cursor over items.
	 */
	public Cursor fetchAllHrRrActPaDateDataForDates(long start, long end) {
		if (mDb == null) {
			return null;
		}
		return mDb.query(DB_DATA_TABLE, new String[] { COL_DATE, COL_HR,
				COL_RR, COL_ACT, COL_PA },
				COL_DATE + ">=" + Long.toString(start) + " AND " + COL_DATE
						+ "<=" + Long.toString(end), null, null, null,
				SORT_ASCENDING);
	}

	///////////////////////////////////////////////////////////////////////////
	// Get data for start date and later (StartingAtDate) /////////////////////
	///////////////////////////////////////////////////////////////////////////

	/**
	 * Return a Cursor over the list of all items in the database for a given
	 * time and later.
	 * 
	 * @param date
	 * @return Cursor over items.
	 */
	public Cursor fetchAllDataStartingAtDate(long date) {
		if (mDb == null) {
			return null;
		}
		return mDb.query(DB_DATA_TABLE, new String[] { COL_ID, COL_DATE,
				COL_START_DATE, COL_HR, COL_RR, COL_ACT, COL_PA }, COL_DATE
				+ ">=" + Long.toString(date), null, null, null, SORT_ASCENDING);
	}

	/**
	 * Return a Cursor over the HR and RR items in the database for a given time
	 * and later.
	 * 
	 * @param date
	 * @return Cursor over items.
	 */
	public Cursor fetchAllHrRrDateDataStartingAtDate(long date) {
		if (mDb == null) {
			return null;
		}
		return mDb
				.query(DB_DATA_TABLE,
						new String[] { COL_DATE, COL_HR, COL_RR }, COL_DATE
								+ ">=" + Long.toString(date), null, null, null,
						SORT_ASCENDING);
	}

	/**
	 * Return a Cursor over the Activity and PA items in the database for a
	 * given time and later.
	 * 
	 * @param date
	 * @return Cursor over items.
	 */
	public Cursor fetchAllActPaDateDataStartingAtDate(long date) {
		if (mDb == null) {
			return null;
		}
		return mDb.query(DB_DATA_TABLE, new String[] { COL_DATE, COL_ACT,
				COL_PA }, COL_DATE + ">=" + Long.toString(date), null, null,
				null, SORT_ASCENDING);
	}

	/**
	 * Return a Cursor over the HR, RR, Activity, and PA items in the database
	 * for a given time and later.
	 * 
	 * @param date
	 * @return Cursor over items.
	 */
	public Cursor fetchAllHrRrActPaDateDataStartingAtDate(long date) {
		if (mDb == null) {
			return null;
		}
		return mDb.query(DB_DATA_TABLE, new String[] { COL_DATE, COL_HR,
				COL_RR, COL_ACT, COL_PA },
				COL_DATE + ">=" + Long.toString(date), null, null, null,
				SORT_ASCENDING);
	}

	/**
	 * A SQLiteOpenHelper helper to help manage database creation and version
	 * management. Extends a custom version that writes to the SD Card instead
	 * of using the Context.
	 */
	private static class DatabaseHelper extends SQLiteOpenHelper {
		public DatabaseHelper(Context context, String dir) {
			super(context, dir, null, DB_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DB_CREATE_DATA_TABLE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// TODO Re-do this so nothing is lost if there is a need to change
			// the version
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
					+ newVersion + ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS " + DB_DATA_TABLE);
			onCreate(db);
		}
	}

}
