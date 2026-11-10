package com.shefra.alsirat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        long time = intent.getLongExtra("time", 0);
        // لو وصل المنبه متأخر أكثر من 10 دقائق (مثلاً بعد إعادة تشغيل) لا نشغل الأذان
        boolean fresh = time == 0 || System.currentTimeMillis() - time < 10 * 60 * 1000L;
        if (fresh) {
            Intent svc = new Intent(ctx, AdhanService.class);
            svc.putExtra("name", intent.getStringExtra("name"));
            svc.putExtra("key", intent.getStringExtra("key"));
            ContextCompat.startForegroundService(ctx, svc);
        }
        AdhanScheduler.scheduleNext(ctx);
    }
}
