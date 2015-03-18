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

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.util.Log;

public class Utils implements IConstants {
	/**
	 * General alert dialog.
	 * 
	 * @param context
	 * @param title
	 * @param msg
	 */
	public static void alert(Context context, String title, String msg) {
		try {
			AlertDialog alertDialog = new AlertDialog.Builder(context)
					.setTitle(title)
					.setMessage(msg)
					.setPositiveButton(context.getText(R.string.ok),
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									dialog.cancel();
								}
							}).create();
			alertDialog.show();
		} catch (Throwable t) {
			Log.e(getContextTag(context), "Error using " + title
					+ "AlertDialog\n" + t + "\n" + t.getMessage());
		}
	}

	/**
	 * Error message dialog.
	 * 
	 * @param context
	 * @param msg
	 */
	public static void errMsg(Context context, String msg) {
		Log.e(TAG, getContextTag(context) + msg);
		alert(context, context.getText(R.string.error).toString(), msg);
	}

	/**
	 * Error message dialog.
	 * 
	 * @param context
	 * @param msg
	 */
	public static void warnMsg(Context context, String msg) {
		Log.w(TAG, getContextTag(context) + msg);
		alert(context, context.getText(R.string.warning).toString(), msg);
	}

	/**
	 * Info message dialog.
	 * 
	 * @param context
	 * @param msg
	 */
	public static void infoMsg(Context context, String msg) {
		Log.i(TAG, getContextTag(context) + msg);
		alert(context, context.getText(R.string.info).toString(), msg);
	}

	/**
	 * Exception message dialog. Displays message plus the exception and
	 * exception message.
	 * 
	 * @param context
	 * @param msg
	 * @param t
	 */
	public static void excMsg(Context context, String msg, Throwable t) {
		String fullMsg = msg += "\n"
				+ context.getText(R.string.exception).toString() + ": " + t
				+ "\n" + t.getMessage();
		Log.e(TAG, getContextTag(context) + msg);
		alert(context, context.getText(R.string.exception).toString(), fullMsg);
	}

	/**
	 * Utility method to get a tag representing the Context to associate with a
	 * log message.
	 * 
	 * @param context
	 * @return
	 */
	public static String getContextTag(Context context) {
		if (context == null) {
			return "<???>: ";
		}
		return "Utils: " + context.getClass().getSimpleName() + ": ";
	}

	/**
	 * Determines whether this is a debug build or not. Can be used to decide
	 * which Maps API key to use. Relies on android:debuggable being set
	 * automatically by the build. If set manually (not necessary with current
	 * tools), needs to be removed for release builds for this to work.
	 * 
	 * @param context
	 * @return
	 */
	public static boolean isDebugBuild(Context context) {
		boolean retVal = false;
		try {
			PackageManager pm = context.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);

			retVal = ((pi.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
		} catch (Exception ex) {
			retVal = false;
		}
		return retVal;
	}

	/**
	 * Sends an SMS message.
	 * 
	 * @param context
	 *            The Context for the sending Activity.
	 * @param cls
	 *            The Class of the sending Activity.
	 * @param phoneNumber
	 *            The number of the receiver.
	 * @param message
	 *            The message to be sent.
	 */
	public static void sendSMS(Context context, Class<?> cls,
			String phoneNumber, String message) {
		PendingIntent pi = PendingIntent.getActivity(context, 0, new Intent(
				context, cls), 0);
		SmsManager sms = SmsManager.getDefault();
		sms.sendTextMessage(phoneNumber, null, message, pi, null);
	}

}
