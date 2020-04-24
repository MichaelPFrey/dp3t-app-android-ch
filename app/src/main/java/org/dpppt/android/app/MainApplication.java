/*
 * Created by Ubique Innovation AG
 * https://www.ubique.ch
 * Copyright (c) 2020. All rights reserved.
 */
package org.dpppt.android.app;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.dpppt.android.app.storage.SecureStorage;
import org.dpppt.android.sdk.DP3T;
import org.dpppt.android.sdk.InfectionStatus;
import org.dpppt.android.sdk.TracingStatus;
import org.dpppt.android.sdk.backend.models.ApplicationInfo;
import org.dpppt.android.sdk.internal.database.models.MatchedContact;
import org.dpppt.android.sdk.internal.util.ProcessUtil;


public class MainApplication extends Application {

	private static final String NOTIFICATION_CHANNEL_ID = "contact-channel";
	public static final int NOTIFICATION_ID = 42;

	private SecureStorage secureStorage;

	@Override
	public void onCreate() {
		super.onCreate();
		if (ProcessUtil.isMainProcess(this)) {
			secureStorage = SecureStorage.getInstance(getApplicationContext());
			registerReceiver(broadcastReceiver, DP3T.getUpdateIntentFilter());
			DP3T.init(this, new ApplicationInfo("dp3t-app", BuildConfig.REPORT_URL, BuildConfig.BUCKET_URL));
		}
	}

	@Override
	public void onTerminate() {
		if (ProcessUtil.isMainProcess(this)) {
			unregisterReceiver(broadcastReceiver);
		}
		super.onTerminate();
	}

	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			TracingStatus status = DP3T.getStatus(context);
			if (status.getInfectionStatus() == InfectionStatus.EXPOSED) {
				MatchedContact newestContact = null;
				long dateNewest = 0;
				for (MatchedContact contact : status.getMatchedContacts()) {
					if (contact.getReportDate() > dateNewest) {
						newestContact = contact;
						dateNewest = contact.getReportDate();
					}
				}
				if (newestContact != null && secureStorage.getLastShownContactId() != newestContact.getId()) {
					createNewContactNotifaction(newestContact.getId());
				}
			}
		}
	};

	private void createNewContactNotifaction(int contactId) {
		Context context = getApplicationContext();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel();
		}

		Intent resultIntent = new Intent(context, MainActivity.class);
		resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		resultIntent.setAction(MainActivity.ACTION_GOTO_REPORTS);

		PendingIntent pendingIntent =
				PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		Notification notification =
				new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
						.setContentTitle(context.getString(R.string.push_exposed_title))
						.setContentText(context.getString(R.string.push_exposed_text))
						.setPriority(NotificationCompat.PRIORITY_MAX)
						.setSmallIcon(R.drawable.ic_begegnungen)
						.setContentIntent(pendingIntent)
						.setAutoCancel(true)
						.build();

		NotificationManager notificationManager =
				(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(NOTIFICATION_ID, notification);

		secureStorage.setHotlineCalled(false);
		secureStorage.setLastShownContactId(contactId);
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	private void createNotificationChannel() {
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		String channelName = getString(R.string.app_name);
		NotificationChannel channel =
				new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
		channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
		notificationManager.createNotificationChannel(channel);
	}

}
