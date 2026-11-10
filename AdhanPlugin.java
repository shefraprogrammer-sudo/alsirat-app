package com.shefra.alsirat;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Adhan")
public class AdhanPlugin extends Plugin {

    @PluginMethod
    public void schedule(PluginCall call) {
        JSArray times = call.getArray("times");
        if (times == null) { call.reject("times is required"); return; }
        AdhanScheduler.save(getContext(), times);
        AdhanScheduler.scheduleNext(getContext());
        call.resolve();
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        AdhanScheduler.cancel(getContext());
        call.resolve();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        Context c = getContext();
        JSObject r = new JSObject();
        r.put("notifications", NotificationManagerCompat.from(c).areNotificationsEnabled());
        boolean ignored = true;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            ignored = pm.isIgnoringBatteryOptimizations(c.getPackageName());
        }
        r.put("batteryIgnored", ignored);
        r.put("manufacturer", Build.MANUFACTURER.toLowerCase());
        r.put("nextTime", AdhanScheduler.nextTime(c));
        call.resolve(r);
    }

    /** يفتح شاشة استثناءات توفير البطارية (لا تحتاج إذن خاص ومقبولة في Google Play). */
    @PluginMethod
    public void openBatterySettings(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Exception e) {
            openAppDetails();
        }
        call.resolve();
    }

    private static final String[][] AUTOSTART = {
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
            {"com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"}
    };

    @PluginMethod
    public void openAutostart(PluginCall call) {
        Context c = getContext();
        boolean opened = false;
        for (String[] pair : AUTOSTART) {
            try {
                Intent i = new Intent().setComponent(new ComponentName(pair[0], pair[1]));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                opened = true;
                break;
            } catch (ActivityNotFoundException | SecurityException ignored) { }
        }
        if (!opened) openAppDetails();
        JSObject r = new JSObject();
        r.put("opened", opened);
        call.resolve(r);
    }

    private void openAppDetails() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
    }

    @PluginMethod
    public void test(PluginCall call) {
        Context c = getContext();
        Intent svc = new Intent(c, AdhanService.class);
        svc.putExtra("name", "(تجربة)");
        svc.putExtra("key", "dhuhr");
        ContextCompat.startForegroundService(c, svc);
        call.resolve();
    }
}
