package com.shefra.alsirat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** يعيد جدولة الأذان بعد إعادة تشغيل الجهاز أو تحديث التطبيق أو تغيير الوقت/المنطقة الزمنية. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        AdhanScheduler.scheduleNext(ctx);
    }
}
