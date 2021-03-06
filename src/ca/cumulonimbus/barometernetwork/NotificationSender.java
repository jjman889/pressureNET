package ca.cumulonimbus.barometernetwork;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.widget.Toast;
import ca.cumulonimbus.pressurenetsdk.CbCurrentCondition;
import ca.cumulonimbus.pressurenetsdk.CbService;

public class NotificationSender extends BroadcastReceiver {

	Context mContext;
	public static final int NOTIFICATION_ID = 101325;
	
	private long lastNearbyConditionReportNotification = System.currentTimeMillis() 
			- (1000 * 60 * 60);
	private long lastConditionsSubmit = System.currentTimeMillis() 
			- (1000 * 60 * 60 * 4);
	
	public NotificationSender() {
		super();
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		mContext = context;
		if(intent.getAction().equals(CbService.LOCAL_CONDITIONS_ALERT)) {
			log("app received intent local conditions alert");
			// potentially notify about nearby conditions
			SharedPreferences sharedPreferences = PreferenceManager
					.getDefaultSharedPreferences(mContext);
			boolean isOkayToDeliver = sharedPreferences.getBoolean("send_condition_notifications", false);
			if(isOkayToDeliver) {
				if(intent.hasExtra("ca.cumulonimbus.pressurenetsdk.conditionNotification")) {
					CbCurrentCondition receivedCondition = (CbCurrentCondition) intent.getSerializableExtra("ca.cumulonimbus.pressurenetsdk.conditionNotification");
					if(receivedCondition != null) {
						deliverConditionNotification(receivedCondition);
					}
				} else {
					log("local conditions intent not sent, doesn't have extra");
				}
			} else {
				log("not delivering conditions notification, disabled in prefs");
			}
			
		} else if(intent.getAction().equals(CbService.PRESSURE_CHANGE_ALERT)) {
			log("app received intent pressure change alert");
			if(intent.hasExtra("ca.cumulonimbus.pressurenetsdk.tendencyChange")) {
				String tendencyChange = intent.getStringExtra("ca.cumulonimbus.pressurenetsdk.tendencyChange");
				deliverNotification(tendencyChange);
			} else {
				log("pressure change intent not sent, doesn't have extra");
			}
			
		} else if(intent.getAction().equals(CbService.PRESSURE_SENT_TOAST)) {
			log("app received intent pressure sent toast");
			if(intent.hasExtra("ca.cumulonimbus.pressurenetsdk.pressureSent")) {
				double pressureSent = intent.getDoubleExtra("ca.cumulonimbus.pressurenetsdk.pressureSent", 0.0);
				Toast.makeText(context, "Sent " + displayPressureValue(pressureSent), Toast.LENGTH_SHORT).show();
			} else {
				log("pressure sent intent not sent, doesn't have extra");
			}
			
		} else if(intent.getAction().equals(CbService.CONDITION_SENT_TOAST)) {
			log("app received intent pressure sent toast");
			if(intent.hasExtra("ca.cumulonimbus.pressurenetsdk.conditionSent")) {
				String conditionSent = intent.getStringExtra("ca.cumulonimbus.pressurenetsdk.conditionSent");
				Toast.makeText(context, "Sent " + conditionSent, Toast.LENGTH_SHORT).show();
			} else {
				log("condition sent intent not sent, doesn't have extra");
			}
			
		} else {
			log("no matching code for " + intent.getAction());
		}	
	}
	
	/**
	 * Check the Android SharedPreferences for important values. Save relevant
	 * ones to CbSettings for easy access in submitting readings
	 */
	public String getUnitPreference() {
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		return sharedPreferences.getString("units", "millibars");
	}
	
	private String displayPressureValue(double value) {
		String preferencePressureUnit = getUnitPreference();
		DecimalFormat df = new DecimalFormat("####.0");
		PressureUnit unit = new PressureUnit(preferencePressureUnit);
		unit.setValue(value);
		unit.setAbbreviation(preferencePressureUnit);
		double pressureInPreferredUnit = unit.convertToPreferredUnit();
		return df.format(pressureInPreferredUnit) + " " + unit.fullToAbbrev();
	}
	
	/**
	 * Send an Android notification to the user about nearby users
	 * reporting current conditions.
	 * 
	 * @param tendencyChange
	 */
	private void deliverConditionNotification(CbCurrentCondition condition) {
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		long now = System.currentTimeMillis();
		// don't deliver if recently interacted with
		lastConditionsSubmit = sharedPreferences.getLong(
				"lastConditionsSubmit", System.currentTimeMillis()
				- (1000 * 60 * 60 * 10));
		
		lastNearbyConditionReportNotification = sharedPreferences.getLong(
				"lastConditionTime", System.currentTimeMillis()
						- (1000 * 60 * 60 * 10));
		
		long waitDiff = 1000 * 60 * 60 * 1;
		
		if(now - lastConditionsSubmit < waitDiff) {
			log("bailing on conditions notifications, recently submitted one");
			return;
		}
		if (now - lastNearbyConditionReportNotification < waitDiff) {
			log("bailing on conditions notification, not 1h wait yet");
			return;
		}

		String deliveryMessage = "What's it like where you are?";
		
		// feed it with the initial condition
		// clear, fog, cloud, precip, thunderstorm
		String initial = "";
		int icon = R.drawable.ic_launcher;
		String politeReportText = condition.getGeneral_condition();
		if(condition.getGeneral_condition().equals(mContext.getString(R.string.sunny))) {
			initial = "clear";
			// pick the right clear icon
			icon = getResIdForClearIcon(condition);
		} else if(condition.getGeneral_condition().equals(mContext.getString(R.string.foggy))) {
			initial = "fog";
			icon = R.drawable.ic_wea_on_fog1;
		} else if(condition.getGeneral_condition().equals(mContext.getString(R.string.cloudy))) {
			initial = "cloud";
			icon = R.drawable.ic_wea_on_cloud;
		} else if(condition.getGeneral_condition().equals(mContext.getString(R.string.precipitation))) {
			initial = "precip";
			if(condition.getPrecipitation_type().equals(mContext.getString(R.string.rain))) {
				icon = R.drawable.ic_wea_on_rain1;
				politeReportText = "Rain";
			} else if (condition.getPrecipitation_type().equals(mContext.getString(R.string.snow))) {
				icon = R.drawable.ic_wea_on_snow1;
				politeReportText = "Snow";
			} else {
				icon = R.drawable.ic_wea_on_precip;
			}
			
		} else if(condition.getGeneral_condition().equals(mContext.getString(R.string.thunderstorm))) {
			initial = "thunderstorm";
			icon = R.drawable.ic_wea_on_lightning1;
		}
		
	
		Notification.Builder mBuilder = new Notification.Builder(
				mContext).setSmallIcon(icon)
				.setContentTitle("Someone reported: " + politeReportText).setContentText(deliveryMessage);
		// Creates an explicit intent for an activity
		Intent resultIntent = new Intent(mContext,
				CurrentConditionsActivity.class);
		// Current Conditions activity likes to know the location in the Intent
		double notificationLatitude = 0.0;
		double notificationLongitude = 0.0;
		try {
			LocationManager lm = (LocationManager) mContext
					.getSystemService(Context.LOCATION_SERVICE);
			Location loc = lm
					.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
			if (loc.getLatitude() != 0) {
				notificationLatitude = loc.getLatitude();
				notificationLongitude = loc.getLongitude();
			}
		} catch (Exception e) {

		}

		resultIntent.putExtra("latitude", notificationLatitude);
		resultIntent.putExtra("longitude", notificationLongitude);
		resultIntent.putExtra("cancelNotification", true);
		resultIntent.putExtra("initial", initial);

		TaskStackBuilder stackBuilder = TaskStackBuilder
				.create(mContext);

		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
				PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(resultPendingIntent);
		NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
		// mId allows you to update the
		// notification later on.
		mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());

		// save the time
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putLong("lastConditionTime", now);
		editor.commit();

	}
	

	/**
	 * Send an Android notification to the user with a notice of pressure
	 * tendency change.
	 * 
	 * @param tendencyChange
	 */
	private void deliverNotification(String tendencyChange) {
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(mContext);

		long lastNotificationTime = sharedPreferences.getLong(
				"lastNotificationTime", System.currentTimeMillis()
						- (1000 * 60 * 60 * 10));
		long now = System.currentTimeMillis();
		long waitDiff = 1000 * 60 * 60 * 6;
		if (now - lastNotificationTime < waitDiff) {
			log("bailing on notification, not 6h wait yet");
			return;
		}

		String deliveryMessage = "";
		if (!tendencyChange.contains(",")) {
			// not returning to directional values? don't deliver notification
			return;
		}

		String first = tendencyChange.split(",")[0];
		String second = tendencyChange.split(",")[1];

		if ((first.contains("Rising")) && (second.contains("Falling"))) {
			deliveryMessage = "The pressure is dropping";
		} else if ((first.contains("Steady")) && (second.contains("Falling"))) {
			deliveryMessage = "The pressure is dropping";
		} else if ((first.contains("Steady")) && (second.contains("Rising"))) {
			deliveryMessage = "The pressure is rising";
		} else if ((first.contains("Falling")) && (second.contains("Rising"))) {
			deliveryMessage = "The pressure is rising";
		} else {
			deliveryMessage = "The pressure is steady";
			// don't deliver this message
			log("bailing on notification, pressure is steady");
			return;
		}

		Notification.Builder mBuilder = new Notification.Builder(
				mContext).setSmallIcon(R.drawable.ic_launcher)
				.setContentTitle("pressureNET").setContentText(deliveryMessage);
		// Creates an explicit intent for an activity
		Intent resultIntent = new Intent(mContext,
				CurrentConditionsActivity.class);
		// Current Conditions activity likes to know the location in the Intent
		double notificationLatitude = 0.0;
		double notificationLongitude = 0.0;
		try {
			LocationManager lm = (LocationManager) mContext
					.getSystemService(Context.LOCATION_SERVICE);
			Location loc = lm
					.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
			if (loc.getLatitude() != 0) {
				notificationLatitude = loc.getLatitude();
				notificationLongitude = loc.getLongitude();
			}
		} catch (Exception e) {

		}

		resultIntent.putExtra("latitude", notificationLatitude);
		resultIntent.putExtra("longitude", notificationLongitude);
		resultIntent.putExtra("cancelNotification", true);

		TaskStackBuilder stackBuilder = TaskStackBuilder
				.create(mContext);

		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
				PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(resultPendingIntent);
		NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
		// mId allows you to update the
		// notification later on.
		mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());

		// save the time
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putLong("lastNotificationTime", now);
		editor.commit();

	}
	
	/**
	 * Given a condition, 
	 * @param condition
	 * @return
	 */
	private int getResIdForClearIcon(CbCurrentCondition condition) {
		int moonNumber = getMoonPhaseIndex();
		int sunDrawable = R.drawable.ic_wea_on_sun;
		try {
			if (!CurrentConditionsActivity.isDaytime(condition.getLocation()
					.getLatitude(), condition.getLocation().getLongitude())) {
				switch (moonNumber) {
				case 1:
					sunDrawable = R.drawable.ic_wea_on_moon1;
					break;
				case 2:
					sunDrawable = R.drawable.ic_wea_on_moon2;
					break;
				case 3:
					sunDrawable = R.drawable.ic_wea_on_moon3;
					break;
				case 4:
					sunDrawable = R.drawable.ic_wea_on_moon4;
					break;
				case 5:
					sunDrawable = R.drawable.ic_wea_on_moon5;
					break;
				case 6:
					sunDrawable = R.drawable.ic_wea_on_moon6;
					break;
				case 7:
					sunDrawable = R.drawable.ic_wea_on_moon7;
					break;
				case 8:
					sunDrawable = R.drawable.ic_wea_on_moon8;
					break;
				default:
					sunDrawable = R.drawable.ic_wea_on_moon2;
					break;
				}
			}
		} catch (NullPointerException npe) {
			
		}
		return sunDrawable;
	}
	
	/**
	 * Moon phase info
	 */
	private int getMoonPhaseIndex() {
		MoonPhase mp = new MoonPhase(Calendar.getInstance());
		return mp.getPhaseIndex();
	}
	
	private void log(String message) {
		System.out.println(message);
	}
}
