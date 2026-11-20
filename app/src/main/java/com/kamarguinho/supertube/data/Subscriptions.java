package com.kamarguinho.supertube.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Inscrições guardadas só no celular: sem conta, sem Google. */
public final class Subscriptions {

    public static final class Channel {
        public final int serviceId;
        public final String url;
        public final String name;
        @Nullable
        public final String avatar;

        public Channel(final int serviceId, final String url, final String name,
                       @Nullable final String avatar) {
            this.serviceId = serviceId;
            this.url = url;
            this.name = name;
            this.avatar = avatar;
        }
    }

    private static final String PREFS = "subscriptions";
    private static final String KEY = "channels";

    private final SharedPreferences prefs;

    public Subscriptions(final Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Channel> all() {
        final List<Channel> list = new ArrayList<>();
        try {
            final JSONArray array = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                final JSONObject o = array.getJSONObject(i);
                list.add(new Channel(o.optInt("service", 0), o.getString("url"),
                        o.optString("name", ""),
                        o.isNull("avatar") ? null : o.optString("avatar", null)));
            }
        } catch (final JSONException ignored) {
            // lista corrompida: começa vazia
        }
        return list;
    }

    public boolean isSubscribed(final String url) {
        for (final Channel c : all()) {
            if (c.url.equals(url)) {
                return true;
            }
        }
        return false;
    }

    public void add(final Channel channel) {
        final List<Channel> list = all();
        for (final Channel c : list) {
            if (c.url.equals(channel.url)) {
                return;
            }
        }
        list.add(channel);
        save(list);
    }

    public void remove(final String url) {
        final List<Channel> list = all();
        final List<Channel> kept = new ArrayList<>();
        for (final Channel c : list) {
            if (!c.url.equals(url)) {
                kept.add(c);
            }
        }
        save(kept);
    }

    private void save(final List<Channel> list) {
        final JSONArray array = new JSONArray();
        try {
            for (final Channel c : list) {
                final JSONObject o = new JSONObject();
                o.put("service", c.serviceId);
                o.put("url", c.url);
                o.put("name", c.name);
                o.put("avatar", c.avatar == null ? JSONObject.NULL : c.avatar);
                array.put(o);
            }
        } catch (final JSONException ignored) {
            return;
        }
        prefs.edit().putString(KEY, array.toString()).apply();
    }
}
