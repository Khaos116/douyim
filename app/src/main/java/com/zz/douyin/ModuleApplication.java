package com.zz.douyin;

import android.app.Application;
import android.util.Log;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class ModuleApplication extends Application
        implements XposedServiceHelper.OnServiceListener {
    private static final Set<ServiceStateListener> LISTENERS =
            new CopyOnWriteArraySet<>();
    private static volatile XposedService service;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            XposedServiceHelper.registerListener(this);
        } catch (Throwable failed) {
            Log.w("DouyinImmersive",
                    "[LSPatch] service helper unavailable;"
                            + " settings stay read-only",
                    failed);
        }
    }

    @Override
    public void onServiceBind(XposedService boundService) {
        service = boundService;
        notifyListeners(boundService);
    }

    @Override
    public void onServiceDied(XposedService deadService) {
        if (service == deadService) {
            service = null;
        }
        notifyListeners(null);
    }

    static void addServiceStateListener(
            ServiceStateListener listener,
            boolean notifyImmediately
    ) {
        LISTENERS.add(listener);
        if (notifyImmediately) {
            listener.onServiceStateChanged(service);
        }
    }

    static void removeServiceStateListener(ServiceStateListener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyListeners(XposedService current) {
        for (ServiceStateListener listener : LISTENERS) {
            listener.onServiceStateChanged(current);
        }
    }

    interface ServiceStateListener {
        void onServiceStateChanged(XposedService service);
    }
}
