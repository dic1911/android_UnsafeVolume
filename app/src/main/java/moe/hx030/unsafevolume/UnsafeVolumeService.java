package moe.hx030.unsafevolume;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
    private final IBinder binder = new Binder() {
        UnsafeVolumeService getService() {
            return UnsafeVolumeService.this;
        }
    };

    private Handler handler;
    private boolean first = true;

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "UnsafeVolumeServiceChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        handler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Unsafe Volume Service")
                .setContentText("Running...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.drawable.ic_launcher_foreground))
                .setContentIntent(pi)
                .setChannelId(CHANNEL_ID)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        handler.post(this::unsafeVolLoop);
        return START_STICKY;
    }

    public void unsafeVolLoop() {
        if (first) first = false;
        else return;

        try {
            if (checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "030: ERR_NO_PERMISSION", Toast.LENGTH_SHORT).show();
            } else {
                unsafeVol();
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }

        handler.postDelayed(this::unsafeVolLoop, 30000);
    }

    public void unsafeVol() {
        Settings.Global.putInt(getContentResolver(), "audio_safe_volume_state", 2);
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

}
