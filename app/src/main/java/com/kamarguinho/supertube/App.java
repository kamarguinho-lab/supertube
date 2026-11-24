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

        // Idioma do app (português ou inglês) aplicado a cada tela que abre
        com.kamarguinho.supertube.util.Lang.update(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPreCreated(@androidx.annotation.NonNull final android.app.Activity a,
                                             final android.os.Bundle b) {
                com.kamarguinho.supertube.util.Lang.update(a);
            }

            @Override
            public void onActivityCreated(@androidx.annotation.NonNull final android.app.Activity a,
                                          final android.os.Bundle b) {
                com.kamarguinho.supertube.util.Lang.update(a);
            }

            @Override
            public void onActivityStarted(@androidx.annotation.NonNull final android.app.Activity a) {
            }

            @Override
            public void onActivityResumed(@androidx.annotation.NonNull final android.app.Activity a) {
            }

            @Override
            public void onActivityPaused(@androidx.annotation.NonNull final android.app.Activity a) {
            }

            @Override
            public void onActivityStopped(@androidx.annotation.NonNull final android.app.Activity a) {
            }

            @Override
            public void onActivitySaveInstanceState(
                    @androidx.annotation.NonNull final android.app.Activity a,
                    @androidx.annotation.NonNull final android.os.Bundle b) {
            }

            @Override
            public void onActivityDestroyed(@androidx.annotation.NonNull final android.app.Activity a) {
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationChannel channel = new NotificationChannel(CHANNEL_PLAYER,
                    getString(R.string.notification_channel_player),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}
