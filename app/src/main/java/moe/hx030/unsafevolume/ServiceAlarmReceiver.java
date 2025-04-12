package moe.hx030.unsafevolume;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class ServiceAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("ServiceAlarmReceiver", "Alarm received, starting service...");

        Intent serviceIntent = new Intent(context, UnsafeVolumeService.class);
        context.startForegroundService(serviceIntent);
    }
}
