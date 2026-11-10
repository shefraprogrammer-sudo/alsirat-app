package com.shefra.alsirat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * يحتفظ بقائمة المواقيت، ويجدول منبهًا واحدًا فقط للأذان القادم.
 * عند إطلاقه يجدول AdhanReceiver الأذان الذي يليه (سلسلة).
 * نستخدم setAlarmClock لأنه معفى من Doze ولا يحتاج إذن SCHEDULE_EXACT_ALARM.
 */
public class AdhanScheduler {
    private static final String PREFS = "adhan_prefs";
    private static final String KEY = "times";
    static final String ACTION = "com.shefra.alsirat.ADHAN";

    public static void save(Context c, JSONArray arr) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply();
    }

    public static JSONArray load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try { return new JSONArray(p.getString(KEY, "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private static JSONObject findNext(Context c) {
        JSONArray arr = load(c);
        long now = System.currentTimeMillis();
        JSONObject next = null;
        long best = Long.MAX_VALUE;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            long t = o.optLong("time", 0);
            if (t > now + 1000 && t < best) { best = t; next = o; }
        }
        return next;
    }

    public static long nextTime(Context c) {
        JSONObject n = findNext(c);
        return n == null ? 0 : n.optLong("time", 0);
    }

    private static PendingIntent pending(Context c, JSONObject o) {
        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACTION);
        if (o != null) {
            i.putExtra("name", o.optString("name"));
            i.putExtra("key", o.optString("key"));
            i.putExtra("time", o.optLong("time"));
        }
        return PendingIntent.getBroadcast(c, 1001, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        JSONObject next = findNext(c);
        if (next == null) { am.cancel(pending(c, null)); return; }
        Intent show = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent showPi = PendingIntent.getActivity(c, 1002, show, PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(next.optLong("time"), showPi), pending(c, next));
    }

    public static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(c, null));
        save(c, new JSONArray());
    }
}
