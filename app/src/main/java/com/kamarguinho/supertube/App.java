package com.kamarguinho.supertube;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;

import java.util.Locale;

public class App extends Application {

    public static final String CHANNEL_PLAYER = "player";

    @Override
    public void onCreate() {
        super.onCreate();

        DownloaderImpl.init();
        final Locale locale = Locale.getDefault();
        final String country = locale.getCountry().isEmpty() ? "IE" : locale.getCountry();
        NewPipe.init(DownloaderImpl.getInstance(),
                Localization.fromLocale(locale),
                new ContentCountry(country));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationChannel channel = new NotificationChannel(CHANNEL_PLAYER,
                    getString(R.string.notification_channel_player),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}
