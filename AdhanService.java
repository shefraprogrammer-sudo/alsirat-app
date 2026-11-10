package com.shefra.alsirat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

public class AdhanService extends Service {
    private static final String CHANNEL = "adhan_playback_v1";
    private static final int NOTIF_ID = 7001;
    static final String ACTION_STOP = "com.shefra.alsirat.STOP";

    private MediaPlayer player;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String name = intent != null ? intent.getStringExtra("name") : null;
        String key = intent != null ? intent.getStringExtra("key") : null;
        if (name == null) name = "";

        createChannel();
        Notification n = buildNotification(name);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
        play(key);
        return START_NOT_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "الأذان", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("تنبيهات وقت الصلاة وتشغيل الأذان");
        ch.setSound(null, null); // الصوت يُشغَّل عبر MediaPlayer
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String name) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, AdhanService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("حان الآن موعد أذان " + name)
                .setContentText("الصلاة خير من النوم لمن يؤذّن للفجر • اضغط إيقاف لإسكات الأذان")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_media_pause, "إيقاف", stopPi)
                .build();
    }

    private Uri resolveSound(String key) {
        String pkg = getPackageName();
        int res = 0;
        if ("fajr".equals(key)) res = getResources().getIdentifier("adhan_fajr", "raw", pkg);
        if (res == 0) res = getResources().getIdentifier("adhan", "raw", pkg);
        if (res != 0) return Uri.parse("android.resource://" + pkg + "/" + res);
        // لم يوضع ملف أذان: نستخدم نغمة المنبه الافتراضية حتى لا يفشل التشغيل
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    }

    @SuppressWarnings("deprecation")
    private void play(String key) {
        releasePlayer();
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(this, resolveSound(key));
            player.setOnCompletionListener(mp -> stopSelf());
            player.setOnErrorListener((mp, w, e) -> { stopSelf(); return true; });
            player.prepare();
            player.start();
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDestroy() {
        releasePlayer();
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.abandonAudioFocus(null);
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
