package moe.hx030.unsafevolume;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class UnsafeVolumeService extends Service {
    private final static String TAG = "030-UnsafeVol";
    private static final int ALARM_REQUEST_CODE = 69;
    private static final long INTERVAL_MILLIS = 600 * 1000;
    private final IBinder binder = new Binder() {
        UnsafeVolumeService getService() {
            return UnsafeVolumeService.this;
        }
    };

    private Handler handler;
    private boolean first = true;

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "UnsafeVolumeServiceChannel";
    private static boolean started = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        handler = new Handler(Looper.getMainLooper());
        start();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        start();
        return START_STICKY;
    }

    private void start() {
        Log.d(TAG, "start()");
        started = true;
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Unsafe Volume Service")
                .setContentText("Running...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.drawable.ic_launcher_foreground))
                .setContentIntent(pi)
                .setChannelId(CHANNEL_ID)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "failed to start svc", e);
            throw e;
        } finally {
            scheduleServiceAlarm(this);
        }
        handler.post(this::unsafeVolLoop);
    }

    public void unsafeVolLoop() {
        Log.d(TAG, "unsafeVolLoop()");

        try {
            if (checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "030: ERR_NO_PERMISSION", Toast.LENGTH_SHORT).show();
            } else {
                unsafeVol();
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }

        scheduleNextRun();
    }

    public void unsafeVol() {
        int current = -1, state = -1;
        try {
            current = Settings.Global.getInt(getContentResolver(), "audio_safe_csd_current_value");
            state = Settings.Global.getInt(getContentResolver(), "audio_safe_volume_state");
        } catch (Settings.SettingNotFoundException ignore) {}

        Log.d(TAG, String.format("unsafeVol(): current=%d, state=%d", current, state));
        Settings.Global.putInt(getContentResolver(), "audio_safe_csd_current_value", 0);
        Settings.Global.putInt(getContentResolver(), "audio_safe_volume_state", 1);
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Unsafe Volume Service Channel",
                NotificationManager.IMPORTANCE_MIN
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    private void scheduleNextRun() {
        Intent intent = new Intent(this, ServiceAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 30000, pendingIntent);
    }

    public static void scheduleServiceAlarm(Context ctx) {
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ctx, ServiceAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                ctx, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerAt = System.currentTimeMillis() + INTERVAL_MILLIS;

        if (alarmManager != null && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms())) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
            );
        }

        if (!started) {
            started = true;
            Intent serviceIntent = new Intent(ctx, UnsafeVolumeService.class);
            ctx.startForegroundService(serviceIntent);
        }
    }
}
