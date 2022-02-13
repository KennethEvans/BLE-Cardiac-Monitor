package net.kenevans.android.blecardiacmonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

public class SessionManagerActivity extends AppCompatActivity implements IConstants {
    private SessionListAdapter mSessionListAdapter;
    private BCMDbAdapter mDbAdapter;
    private RestoreTask mRestoreTask;
    private ListView mListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_activity_session_manager);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        // Set result OK in case the user backs out
        setResult(Activity.RESULT_OK);

        setContentView(R.layout.list_view);
        mListView = findViewById(R.id.mainListView);

        mDbAdapter = new BCMDbAdapter(this);
        mDbAdapter.open();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onResume");
        super.onResume();
        refresh();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onPause");
        super.onPause();
        if (mSessionListAdapter != null) {
            mSessionListAdapter.clear();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onDestroy");
        super.onDestroy();
        if (mDbAdapter != null) {
            mDbAdapter.close();
            mDbAdapter = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_session_manager, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        if (item.getItemId() == R.id.menu_plot) {
            plot();
            return true;
        } else if (item.getItemId() == R.id.menu_discard) {
            promptToDiscardSession();
            return true;
        } else if (item.getItemId() == R.id.menu_save) {
            saveSessions();
            return true;
        } else if (item.getItemId() == R.id.menu_save_combined) {
            saveCombinedSessions();
            return true;
        } else if (item.getItemId() == R.id.menu_save_gpx) {
            saveSessionsAsGpx();
            return true;
        } else if (item.getItemId() == R.id.menu_refresh) {
            refresh();
            return true;
        } else if (item.getItemId() == R.id.menu_check_all) {
            setAllSessionsChecked(true);
            return true;
        } else if (item.getItemId() == R.id.menu_check_none) {
            setAllSessionsChecked(false);
            return true;
        } else if (item.getItemId() == R.id.menu_save_database_cvs) {
            saveDatabaseAsCsv();
            return true;
        } else if (item.getItemId() == R.id.menu_restore_database_cvs) {
            checkRestoreDatabaseFromCvs();
            return true;
        } else if (item.getItemId() == R.id.menu_save_database) {
            saveDatabase();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Calls the plot activity for the selected sessions.
     */
    public void plot() {
        ArrayList<Session> checkedSessions = mSessionListAdapter
                .getCheckedSessions();
        if (checkedSessions.size() == 0) {
            Utils.errMsg(this, "There are no sessions to plot");
            return;
        }
        if (checkedSessions.size() > 1) {
            Utils.errMsg(this,
                    "Only one session may be checked for this operation");
            return;
        }
        Session session = checkedSessions.get(0);
        long startDate = session.getStartDate();
        // long endDate = session.getEndDate();
        Intent intent = new Intent(SessionManagerActivity.this,
                PlotActivity.class);
        // Plot the session
        intent.putExtra(PLOT_SESSION_CODE, true);
        intent.putExtra(PLOT_SESSION_START_TIME_CODE, startDate);
        // // This is not currently used
        // intent.putExtra(PLOT_SESSION_END_TIME_CODE, endDate);
        startActivity(intent);
    }

    /**
     * Merges the selected sessions.
     */
    public void mergeSessions() {
        Utils.infoMsg(this, "Not implented yet");
    }

    /**
     * Splits the selected sessions.
     */
    public void splitSessions() {
        Utils.infoMsg(this, "Not implented yet");
    }

    /**
     * Saves the selected sessions.
     */
    public void saveSessions() {
        ArrayList<Session> checkedSessions = mSessionListAdapter
                .getCheckedSessions();
        if (checkedSessions.size() == 0) {
            Utils.errMsg(this, "There are no sessions to save");
            return;
        }
        // Get the saved tree Uri
        SharedPreferences prefs = getSharedPreferences(MAIN_ACTIVITY,
                MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        // Get a docTree Uri
        Uri treeUri = Uri.parse(treeUriStr);
        String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        int nErrors = 0;
        int nWriteErrors;
        String errMsg = "Error saving sessions:\n";
        String fileNames = "Saved to:\n";
        String fileName;
        long startDate;
        for (Session session : checkedSessions) {
            try {
                startDate = session.getStartDate();
                fileName = session.getName() + ".csv";
                Uri docTreeUri =
                        DocumentsContract.buildDocumentUriUsingTree(treeUri,
                                treeDocumentId);
                // Get a docUri and ParcelFileDescriptor
                ContentResolver resolver = this.getContentResolver();
                ParcelFileDescriptor pfd;
                // Create the document
                Uri docUri = DocumentsContract.createDocument(resolver,
                        docTreeUri,
                        "test/csv", fileName);
                pfd = getContentResolver().
                        openFileDescriptor(docUri, "w");
                try (FileWriter writer =
                             new FileWriter(pfd.getFileDescriptor());
                     BufferedWriter out = new BufferedWriter(writer)) {
                    // Write the session data
                    nWriteErrors = writeSessionDataToCvsFile(startDate, out);
                    if (nWriteErrors > 0) {
                        nErrors += nWriteErrors;
                        errMsg += "  " + session.getName();
                    }
                    fileNames += "  " + docUri.getLastPathSegment() + "\n";
                }
            } catch (Exception ex) {
                nErrors++;
                errMsg += "  " + session.getName();
            }
        }
        String msg = "";
        if (nErrors > 0) {
            msg += errMsg;
        }
        msg += fileNames;
        if (nErrors > 0) {
            Utils.errMsg(this, msg);
        } else {
            Utils.infoMsg(this, msg);
        }
    }

    /**
     * Saves the selected sessions as a combined session.
     */
    public void saveCombinedSessions() {
        ArrayList<Session> checkedSessions = mSessionListAdapter
                .getCheckedSessions();
        if (checkedSessions.size() == 0) {
            Utils.errMsg(this, "There are no sessions to combine and save");
            return;
        }
        // Get the saved tree Uri
        SharedPreferences prefs = getSharedPreferences(MAIN_ACTIVITY,
                MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        // Get a docTree Uri
        Uri treeUri = Uri.parse(treeUriStr);
        String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        // Need to sort in order of increasing startTime
        Collections.sort(checkedSessions, (lhs, rhs) ->
                Long.compare(lhs.getStartDate(), rhs.getStartDate()));
        int nErrors = 0;
        int nWriteErrors;
        String errMsg = "Error saving combined sessions:\n";
        String fileNames = "Saved to:\n";
        // Use the name of the first session
        String fileName = checkedSessions.get(0).getName() + "-Combined.csv";
        long startDate;
        try {
            Uri docTreeUri =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri,
                            treeDocumentId);
            // Get a docUri and ParcelFileDescriptor
            ContentResolver resolver = this.getContentResolver();
            ParcelFileDescriptor pfd;
            // Create the document
            Uri docUri = DocumentsContract.createDocument(resolver,
                    docTreeUri,
                    "text/csv", fileName);
            pfd = getContentResolver().
                    openFileDescriptor(docUri, "w");
            // Do the writing with a BufferedWriter or other
            try (FileWriter writer =
                         new FileWriter(pfd.getFileDescriptor());
                 BufferedWriter out = new BufferedWriter(writer)) {
                boolean first = true;
                for (Session session : checkedSessions) {
                    startDate = session.getStartDate();
                    // Write a blank line to separate sessions
                    if (first) {
                        first = false;
                    } else {
                        out.write("\n");
                    }
                    // Write the session data
                    nWriteErrors = writeSessionDataToCvsFile(startDate, out);
                    if (nWriteErrors > 0) {
                        nErrors += nWriteErrors;
                        errMsg += "  " + session.getName();
                    }
                }
                fileNames += "  " + docUri.getLastPathSegment() + "\n";
            } catch (Exception ex) {
                nErrors++;
                errMsg += "  " + "Writing combined file";
            }
        } catch (Exception ex) {
            Utils.excMsg(this, "Error saving combined sessions", ex);
        }
        String msg = "";
        if (nErrors > 0) {
            msg += errMsg;
        }
        msg += fileNames;
        if (nErrors > 0) {
            Utils.errMsg(this, msg);
        } else {
            Utils.infoMsg(this, msg);
        }
    }

    /**
     * Writes the session data for the given startDate to the given
     * BufferedWriter.
     *
     * @param startDate The startDate.
     * @param out       The BufferedWRiter.
     * @return The number of errors.
     */
    private int writeSessionDataToCvsFile(long startDate, BufferedWriter out) {
        int nErrors = 0;
        try (Cursor cursor =
                     mDbAdapter.fetchAllHrRrDateDataForStartDate(startDate)) {
            int indexDate = cursor.getColumnIndex(COL_DATE);
            int indexHr = cursor.getColumnIndex(COL_HR);
            int indexRr = cursor.getColumnIndex(COL_RR);
            // Loop over items
            cursor.moveToFirst();
            String dateStr, hrStr, rrStr, line;
            long dateNum;
            while (!cursor.isAfterLast()) {
                dateStr = INVALID_STRING;
                if (indexDate > -1) {
                    dateNum = cursor.getLong(indexDate);
                    dateStr = sessionSaveFormatter.format(new Date(dateNum));
                }
                hrStr = INVALID_STRING;
                if (indexHr > -1) {
                    hrStr = cursor.getString(indexHr);
                }
                rrStr = INVALID_STRING;
                if (indexRr > -1) {
                    rrStr = cursor.getString(indexRr);
                }
                line = dateStr + SAVE_SESSION_DELIM + hrStr
                        + SAVE_SESSION_DELIM + rrStr + "\n";
                out.write(line);
                cursor.moveToNext();
            }
        } catch (Exception ex) {
            nErrors++;
        }
        return nErrors;
    }

    /**
     * Saves the selected sessions as GPX files.
     */
    public void saveSessionsAsGpx() {
        ArrayList<Session> checkedSessions = mSessionListAdapter
                .getCheckedSessions();
        if (checkedSessions.size() == 0) {
            Utils.errMsg(this, "There are no sessions to save");
            return;
        }
        // Get the saved tree Uri
        SharedPreferences prefs = getSharedPreferences(MAIN_ACTIVITY,
                MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        // Get a docTree Uri
        Uri treeUri = Uri.parse(treeUriStr);
        String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        int nErrors = 0;
        String errMsg = "Error saving sessions:\n";
        String fileNames = "Saved to:\n";
        String fileName;
        long startDate;
        String name;
        SimpleDateFormat formatter = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        try {
            PackageManager pm = getPackageManager();
            PackageInfo po = pm.getPackageInfo(this.getPackageName(), 0);
            name = "BLE Cardiac Monitor" + " " + po.versionName;
        } catch (Exception ex) {
            name = "BLE Cardiac Monitor";
        }
        for (Session session : checkedSessions) {
            try {
                startDate = session.getStartDate();
                fileName = session.getName() + ".gpx";
                Uri docTreeUri =
                        DocumentsContract.buildDocumentUriUsingTree(treeUri,
                                treeDocumentId);
                // Get a docUri and ParcelFileDescriptor
                ContentResolver resolver = this.getContentResolver();
                ParcelFileDescriptor pfd;
                // Create the document
                Uri docUri = DocumentsContract.createDocument(resolver,
                        docTreeUri,
                        "application/gpx+xml", fileName);
                pfd = getContentResolver().
                        openFileDescriptor(docUri, "w");
                // Do the writing with a BufferedWriter or other
                try (FileWriter writer =
                             new FileWriter(pfd.getFileDescriptor());
                     BufferedWriter out = new BufferedWriter(writer);
                     Cursor cursor =
                             mDbAdapter.fetchAllHrDateDataForStartDate(startDate)) {
                    // Write the beginning lines
                    out.write(String.format(GPXUtils.GPX_FILE_START_LINES, name,
                            formatter.format(new Date())));
                    int indexDate = cursor.getColumnIndex(COL_DATE);
                    int indexHr = cursor.getColumnIndex(COL_HR);
                    // Loop over items
                    cursor.moveToFirst();
                    String hrStr, line;
                    long dateNum = INVALID_DATE;
                    while (!cursor.isAfterLast()) {
                        if (indexDate > -1) {
                            dateNum = cursor.getLong(indexDate);
                        }
                        hrStr = INVALID_STRING;
                        if (indexHr > -1) {
                            hrStr = cursor.getString(indexHr);
                        }
                        if (hrStr.equals(INVALID_STRING)) {
                            continue;
                        }
                        line = String.format(GPXUtils.GPX_FILE_TRACK_LINES,
                                formatter.format(new Date(dateNum)), hrStr);
                        out.write(line);
                        cursor.moveToNext();
                    }
                    out.write(GPXUtils.GPX_FILE_END_LINES);
                    fileNames += "  " + docUri.getLastPathSegment() + "\n";
                }
            } catch (Exception ex) {
                nErrors++;
                errMsg += "  " + session.getName();
            }
        }
        String msg = "";
        if (nErrors > 0) {
            msg += errMsg;
        }
        msg += fileNames;
        if (nErrors > 0) {
            Utils.errMsg(this, msg);
        } else {
            Utils.infoMsg(this, msg);
        }
    }

    /**
     * Prompts to discards the selected sessions. The method doDiscard will do
     * the actual discarding, if the user confirms.
     *
     * @see #doDiscardSession()
     */
    public void promptToDiscardSession() {
        ArrayList<Session> checkedSessions = mSessionListAdapter
                .getCheckedSessions();
        if (checkedSessions.size() == 0) {
            Utils.errMsg(this, "There are no sessions to discard");
            return;
        }
        String msg = SessionManagerActivity.this.getString(
                R.string.session_delete_prompt, checkedSessions.size());
        new AlertDialog.Builder(SessionManagerActivity.this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.confirm)
                .setMessage(msg)
                .setPositiveButton(R.string.ok,
                        (dialog, which) -> {
                            dialog.dismiss();
                            doDiscardSession();
                        }).setNegativeButton(R.string.cancel, null).show();
    }

    /**
     * Does the actual work of discarding the selected sessions.
     */
    public void doDiscardSession() {
        ArrayList<Session> checkedSessions = mSessionListAdapter
                .getCheckedSessions();
        if (checkedSessions.size() == 0) {
            Utils.errMsg(this, "There are no sessions to discard");
            return;
        }
        long startDate;
        for (Session session : checkedSessions) {
            startDate = session.getStartDate();
            mDbAdapter.deleteAllDataForStartDate(startDate);
        }
        refresh();
    }

    /**
     * Saves the database as a CSV file with a .txt extension.
     */
    private void saveDatabase() {
        SharedPreferences prefs = getSharedPreferences(MAIN_ACTIVITY,
                MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        FileInputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            String format = "yyyy-MM-dd_HH:mm:ss";
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
            Log.d(TAG, "saveDatabase: docUri=" + docUri);
            try {
                // Close the database
                if (mDbAdapter != null) {
                    mDbAdapter.close();
                }
                File file = new File(getExternalFilesDir(null), DB_NAME);
                inputStream = new FileInputStream(file);
                ParcelFileDescriptor pfd = getContentResolver().
                        openFileDescriptor(docUri, "w");
                outputStream =
                        new FileOutputStream(pfd.getFileDescriptor());
                byte[] buff = new byte[1024];
                int read;
                while ((read = inputStream.read(buff, 0, buff.length)) > 0)
                    outputStream.write(buff, 0, read);
            } catch (Exception ex) {
                String msg =
                        "Failed to save database";
                Utils.excMsg(this, msg, ex);
                Log.e(TAG, msg, ex);
            } finally {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
                if (mDbAdapter != null) {
                    mDbAdapter.open();
                }
            }
            Utils.infoMsg(this, "Wrote " + docUri.getLastPathSegment());
        } catch (Exception ex) {
            String msg = "Error saving to SD card";
            Utils.excMsg(this, msg, ex);
            Log.e(TAG, msg, ex);
        }
    }

    /**
     * Saves the database as a CSV file with a .txt extension.
     */
    private void saveDatabaseAsCsv() {
        // Get the saved tree Uri
        SharedPreferences prefs = getSharedPreferences(MAIN_ACTIVITY,
                MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        // Determine the filename
        String format = "yyyy-MM-dd_HH:mm:ss";
        SimpleDateFormat df = new SimpleDateFormat(format, Locale.US);
        Date now = new Date();
        String fileName = String.format(SAVE_DATABASE_FILENAME_TEMPLATE,
                df.format(now));
        // Get a docTree Uri
        Uri treeUri = Uri.parse(treeUriStr);
        String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        try {
            Uri docTreeUri =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri,
                            treeDocumentId);
            // Get a docUri and ParcelFileDescriptor
            ContentResolver resolver = this.getContentResolver();
            ParcelFileDescriptor pfd;
            // Create the document
            Uri docUri = DocumentsContract.createDocument(resolver,
                    docTreeUri,
                    "text/csv", fileName);
            pfd = getContentResolver().
                    openFileDescriptor(docUri, "w");
            try (FileWriter writer =
                         new FileWriter(pfd.getFileDescriptor());
                 BufferedWriter out = new BufferedWriter(writer);
                 Cursor cursor = mDbAdapter.fetchAllData(null)) {
                int indexDate = cursor.getColumnIndex(COL_DATE);
                int indexStartDate = cursor.getColumnIndex(COL_START_DATE);
                int indexHr = cursor.getColumnIndex(COL_HR);
                int indexRr = cursor.getColumnIndex(COL_RR);
                // Loop over items
                cursor.moveToFirst();
                String rr, info;
                long dateNum, startDateNum;
                int hr;
                while (!cursor.isAfterLast()) {
                    dateNum = startDateNum = INVALID_DATE;
                    hr = INVALID_INT;
                    rr = " ";
                    if (indexDate > -1) {
                        try {
                            dateNum = cursor.getLong(indexDate);
                        } catch (Exception ex) {
                            // Do nothing
                        }
                    }
                    if (indexStartDate > -1) {
                        try {
                            startDateNum = cursor.getLong(indexStartDate);
                        } catch (Exception ex) {
                            // Do nothing
                        }
                    }
                    if (indexHr > -1) {
                        try {
                            hr = cursor.getInt(indexHr);
                        } catch (Exception ex) {
                            // Do nothing
                        }
                    }
                    if (indexRr > -1) {
                        try {
                            rr = cursor.getString(indexRr);
                        } catch (Exception ex) {
                            // Do nothing
                        }
                        // Need to do this, or it isn't recognized as a token
                        if (rr.length() == 0) {
                            rr = " ";
                        }
                    }
                    info = String.format(Locale.US, "%d%s%d%s%d%s%s%s\n",
                            dateNum,
                            SAVE_DATABASE_DELIM, startDateNum,
                            SAVE_DATABASE_DELIM,
                            hr, SAVE_DATABASE_DELIM, rr, SAVE_DATABASE_DELIM);
                    out.write(info);
                    cursor.moveToNext();
                }
                Utils.infoMsg(this, "Wrote " + docUri.getLastPathSegment());
            }
        } catch (Exception ex) {
            Utils.excMsg(this, "Error saving database as CSV", ex);
        }
    }

    /**
     * Does the preliminary checking for restoring data, prompts if it is OK to
     * delete the current data, and call restoreData to actually do the delete
     * and restore.
     */
    private void checkRestoreDatabaseFromCvs() {
        // Get the saved tree Uri
        SharedPreferences prefs = getSharedPreferences(MAIN_ACTIVITY,
                MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }

        final List<UriData> uriList;
        try {
            uriList = getUriList(this);
        } catch (Exception ex) {
            Utils.excMsg(this, "Failed to get list of available files", ex);
            return;
        }
        if (uriList == null || uriList.size() == 0) {
            Utils.errMsg(this,
                    "There are no saved database files in the data directory");
            return;
        }

        // Sort them by date with newest first
        final int len = uriList.size();
        Collections.sort(uriList, (data1, data2) ->
                Long.compare(data2.lastModified, data1.lastModified));

        // Prompt for the file to use
        final CharSequence[] items = new CharSequence[uriList.size()];
        for (int i = 0; i < len; i++) {
            items[i] = uriList.get(i).displayName;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getText(R.string.select_restore_file));
        builder.setSingleChoiceItems(items, 0,
                (dialog, item) -> {
                    dialog.dismiss();
                    if (item < 0 || item >= len) {
                        Utils.errMsg(SessionManagerActivity.this,
                                "Invalid item");
                        return;
                    }
                    // Confirm the user wants to delete all the current data
                    new AlertDialog.Builder(SessionManagerActivity.this)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle(R.string.confirm)
                            .setMessage(R.string.delete_prompt)
                            .setPositiveButton(R.string.ok,
                                    (dialog1, which) -> {
                                        dialog1.dismiss();
                                        restoreDatabaseFromCvs(uriList.get(item).uri);
                                    })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Deletes the existing data without prompting and restores the new data.
     */
    private void restoreDatabaseFromCvs(Uri uri) {
        if (mRestoreTask != null) {
            // Don't do anything if we are updating
            Log.d(TAG,
                    this.getClass().getSimpleName()
                            + ": restoreDatabase: restoreTask is not null for "
                            + uri.getLastPathSegment());
            return;
        }

        mRestoreTask = new RestoreTask(this, uri);
        mRestoreTask.execute();
    }

    /**
     * Get the list of available restore files.
     *
     * @param context The context.
     * @return The list.
     */
    public static List<UriData> getUriList(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        SharedPreferences prefs = context.getSharedPreferences(
                "DeviceMonitorActivity", Context.MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(context, "There is no tree Uri set");
            return null;
        }
        Uri treeUri = Uri.parse(treeUriStr);
        Uri childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri));
        List<UriData> uriList = new ArrayList<>();
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        };

        try (Cursor cursor = contentResolver.query(childrenUri, projection,
                null, null, null)) {
            if (cursor == null) return null;
            String documentId;
            Uri documentUri;
            String displayName;
            long lastModified;
            while (cursor.moveToNext()) {
                documentUri = null;
                if (cursor.getColumnIndex(projection[0]) != -1) {
                    documentId = cursor.getString(0);
                    documentUri =
                            DocumentsContract.buildDocumentUriUsingTree(treeUri,
                                    documentId);
                }
                if (documentUri == null) continue;

                displayName = "<NA>";
                if (cursor.getColumnIndex(projection[1]) != -1) {
                    displayName = cursor.getString(1);
                }
                lastModified = -1;
                if (cursor.getColumnIndex(projection[2]) != -1) {
                    lastModified = cursor.getLong(2);
                }
                if (displayName.startsWith(SAVE_DATABASE_FILENAME_PREFIX)
                        && displayName.endsWith(SAVE_DATABASE_FILENAME_SUFFIX)) {
                    uriList.add(new UriData(documentUri, displayName,
                            lastModified));
                }
            }
        }
        return uriList;
    }

    /**
     * Refreshes the sessions by recreating the list adapter.
     */
    public void refresh() {
        // Initialize the list view adapter
        mSessionListAdapter = new SessionListAdapter();
        mListView.setAdapter(mSessionListAdapter);
    }

    /**
     * Class to handle getting the bitmap from the web using a progress
     * bar that can be cancelled.<br>
     * <br>
     * Call with <b>Bitmap bitmap = new MyUpdateTask().execute(String)<b>
     */
    private class RestoreTask extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog dialog;
        private Context mCtx;
        private Uri mUri;
        private int mErrors;
        private int mLineNumber;
        private String mExceptionMsg;

        private RestoreTask(Context context, Uri uri) {
            super();
            this.mCtx = context;
            this.mUri = uri;
        }

        @Override
        protected void onPreExecute() {
            dialog = new ProgressDialog(SessionManagerActivity.this);
            dialog.setMessage(getString(R.string
                    .restoring_database_progress_text));
            dialog.setCancelable(false);
            dialog.setIndeterminate(true);
            dialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... dummy) {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            try {
                // Delete all the data and recreate the table
                mDbAdapter.recreateDataTable();
                try (InputStreamReader inputStreamReader =
                             new InputStreamReader(
                                     mCtx.getContentResolver().openInputStream(mUri));
                     BufferedReader in =
                             new BufferedReader(inputStreamReader)) {
                    // Read the file and get the data to restore
                    String rr;
                    long dateNum, startDateNum;
                    int hr;
                    String[] tokens;
                    String line;
                    while ((line = in.readLine()) != null) {
                        dateNum = startDateNum = INVALID_DATE;
                        mLineNumber++;
                        tokens = line.trim().split(SAVE_DATABASE_DELIM);
                        // Skip blank lines
                        if (line.trim().length() == 0) {
                            continue;
                        }
                        // Skip lines starting with #
                        if (tokens[0].trim().startsWith("#")) {
                            continue;
                        }
                        hr = 0;
                        if (tokens.length < 4) {
                            // Utils.errMsg(this, "Found " + tokens.length
                            // + " tokens for line " + lineNum
                            // + "\nShould be 5 or more tokens");
                            mErrors++;
                            Log.d(TAG, "tokens.length=" + tokens.length
                                    + " @ line " + mLineNumber);
                            Log.d(TAG, line);
                            continue;
                        }
                        try {
                            dateNum = Long.parseLong(tokens[0]);
                        } catch (Exception ex) {
                            Log.d(TAG, "Long.parseLong failed for dateNum @ " +
                                    "line "
                                    + mLineNumber);
                        }
                        try {
                            startDateNum = Long.parseLong(tokens[1]);
                        } catch (Exception ex) {
                            Log.d(TAG,
                                    "Long.parseLong failed for startDateNum @" +
                                            " line "
                                            + mLineNumber);
                        }
                        try {
                            hr = Integer.parseInt(tokens[2]);
                        } catch (Exception ex) {
                            Log.d(TAG, "Integer.parseInt failed for hr @ line "
                                    + mLineNumber);
                        }
                        rr = tokens[3].trim();
                        // Write the row
                        long id = mDbAdapter.createData(dateNum, startDateNum
                                , hr,
                                rr);
                        if (id < 0) {
                            mErrors++;
                        }
                    }
                }
            } catch (Exception ex) {
                mExceptionMsg = "Got Exception restoring at line "
                        + mLineNumber + "\n" + ex.getMessage();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Log.d(TAG, this.getClass().getSimpleName()
                    + ": onPostExecute: result=" + result);
            if (dialog != null) {
                dialog.dismiss();
            }
            mRestoreTask = null;
            String info;
            if (mErrors == 0) {
                info = "Restored " + mLineNumber + " lines from "
                        + mUri.getLastPathSegment();
            } else {
                info = "Got " + mErrors + " errors processing " + mLineNumber
                        + " lines from " + mUri.getLastPathSegment();
                if (mExceptionMsg != null) {
                    info += "\n" + mExceptionMsg;
                }
            }
            Utils.infoMsg(SessionManagerActivity.this, info);
            refresh();
        }
    }

    /**
     * Sets all the sessions to checked or not.
     *
     * @param checked Whether checked or not.
     */
    public void setAllSessionsChecked(Boolean checked) {
        ArrayList<Session> sessions = mSessionListAdapter.getSessions();
        CheckBox cb;
        for (Session session : sessions) {
            session.setChecked(checked);
            cb = session.getCheckBox();
            if (cb != null) {
                cb.setChecked(checked);
            }
        }
    }

    /**
     * Creates a session name from the given date.
     *
     * @param date The date.
     * @return Session name.
     */
    public static String sessionNameFromDate(long date) {
        return SESSION_NAME_PREFIX + fileNameFormatter.format(new Date(date));
    }

    // Adapter for holding sessions
    private class SessionListAdapter extends BaseAdapter {
        private ArrayList<Session> mSessions;
        private LayoutInflater mInflator;

        private SessionListAdapter() {
            super();
            mSessions = new ArrayList<>();
            mInflator = SessionManagerActivity.this.getLayoutInflater();
            Cursor cursor = null;
            int nItems = 0;
            try {
                if (mDbAdapter != null) {
                    cursor = mDbAdapter.fetchAllSessionStartEndData();
                    // // DEBUG
                    // Log.d(TAG,
                    // this.getClass().getSimpleName()
                    // + ": SessionListAdapter: " + "rows="
                    // + cursor.getCount() + " cols="
                    // + cursor.getColumnCount());
                    // String[] colNames = cursor.getColumnNames();
                    // for (String colName : colNames) {
                    // Log.d(TAG, "  " + colName);
                    // }

                    int indexStartDate = cursor
                            .getColumnIndexOrThrow(COL_START_DATE);
                    int indexEndDate = cursor
                            .getColumnIndexOrThrow(COL_END_DATE);
                    // int indexTmp = cursor.getColumnIndexOrThrow(COL_TMP);

                    // Loop over items
                    cursor.moveToFirst();
                    long startDate;
                    long endDate;
                    String name;
                    while (!cursor.isAfterLast()) {
                        nItems++;
                        startDate = cursor.getLong(indexStartDate);
                        endDate = cursor.getLong(indexEndDate);

                        // // DEBUG
                        // double duration = endDate - startDate;
                        // int durationHours = (int) (duration / 3600000.);
                        // int durationMin = (int) (duration / 60000.)
                        // - durationHours * 60;
                        // int durationSec = (int) (duration / 1000.)
                        // - durationHours * 3600 - durationMin * 60;
                        // Log.d(TAG, "duration: " + durationHours + " hr "
                        // + durationMin + " min " + +durationSec + " sec");
                        // name = "Temporary Session ";

                        // String tempStr = cursor.getString(indexTmp);
                        // temp = tempStr.equals("1");
                        // name = "Session ";
                        // if (temp) {
                        // name = "Temporary Session ";
                        // }
                        name = sessionNameFromDate(startDate);
                        addSession(new Session(name, startDate, endDate));
                        cursor.moveToNext();
                    }
                }
                if (cursor != null) cursor.close();
            } catch (Exception ex) {
                Utils.excMsg(SessionManagerActivity.this,
                        "Error getting sessions", ex);
            } finally {
                try {
                    if (cursor != null) cursor.close();
                } catch (Exception ex) {
                    // Do nothing
                }
            }
            Log.d(TAG, "Session list created with " + nItems + " items");
        }

        private void addSession(Session session) {
            if (!mSessions.contains(session)) {
                mSessions.add(session);
            }
        }

        public Session getSession(int position) {
            return mSessions.get(position);
        }

        private void clear() {
            mSessions.clear();
        }

        @Override
        public int getCount() {
            return mSessions.size();
        }

        @Override
        public Object getItem(int i) {
            return mSessions.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            // // DEBUG
            // Log.d(TAG, "getView: " + i);
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_session, viewGroup,
                        false);
                viewHolder = new ViewHolder();
                viewHolder.sessionCheckbox = view
                        .findViewById(R.id.session_checkbox);
                viewHolder.sessionStart = view
                        .findViewById(R.id.session_start);
                viewHolder.sessionDuration = view
                        .findViewById(R.id.session_end);
                view.setTag(viewHolder);

                viewHolder.sessionCheckbox
                        .setOnClickListener(v -> {
                            CheckBox cb = (CheckBox) v;
                            Session session = (Session) cb.getTag();
                            boolean checked = cb.isChecked();
                            session.setChecked(checked);
                            // // DEBUG
                            // Log.d(TAG,
                            // "sessionCheckbox.onClickListener: "
                            // + session.getName() + " "
                            // + session.isChecked());
                        });
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            Session session = mSessions.get(i);
            // Set the name
            final String sessionName = session.getName();
            if (sessionName != null && sessionName.length() > 0) {
                viewHolder.sessionCheckbox.setText(sessionName);
                Date startDate = new Date(session.getStartDate());
                String startStr = mediumFormatter.format(startDate);
                double duration = session.getDuration();
                int durationDays = (int) (duration / (3600000. * 24));

                int durationHours = (int) (duration / 3600000.) - durationDays;
                int durationMin = (int) (duration / 60000.) - durationHours
                        * 60;
                int durationSec = (int) (duration / 1000.) - durationHours
                        * 3600 - durationMin * 60;
                String durString = "";
                if (durationDays > 0) {
                    durString += durationDays + " day ";
                }
                if (durationHours > 0) {
                    durString += durationHours + " hr ";
                }
                if (durationMin > 0) {
                    durString += durationMin + " min ";
                }
                durString += durationSec + " sec";
                viewHolder.sessionStart.setText(startStr);
                viewHolder.sessionDuration.setText(durString);
            } else {
                viewHolder.sessionCheckbox.setText(R.string.unknown_device);
                viewHolder.sessionStart.setText("");
                viewHolder.sessionDuration.setText("");
            }
            // Set the tag for the CheckBox to the session and set its state
            viewHolder.sessionCheckbox.setChecked(session.isChecked());
            viewHolder.sessionCheckbox.setTag(session);
            // And set the associated checkBox for the session
            session.setCheckBox(viewHolder.sessionCheckbox);
            return view;
        }

        /**
         * Get a list of checked sessions.
         *
         * @return List of sessions.
         */
        private ArrayList<Session> getSessions() {
            return mSessions;
        }

        /**
         * Get a list of checked sessions.
         *
         * @return List of sessions.
         */
        private ArrayList<Session> getCheckedSessions() {
            ArrayList<Session> checkedSessions = new ArrayList<>();
            for (Session session : mSessions) {
                if (session.isChecked()) {
                    checkedSessions.add(session);
                }
            }
            return checkedSessions;
        }

    }

    /**
     * Convenience class for managing views for a ListView row.
     */
    static class ViewHolder {
        CheckBox sessionCheckbox;
        TextView sessionStart;
        TextView sessionDuration;
    }

    /**
     * Convenience class for managing Uri information.
     */
    public static class UriData {
        final public Uri uri;
        final public String displayName;
        final public long lastModified;

        UriData(Uri uri, String displayName, long lastModified) {
            this.uri = uri;
            this.displayName = displayName;
            this.lastModified = lastModified;
        }

        @androidx.annotation.NonNull
        @Override
        public String toString() {
            return displayName;
        }
    }
}
