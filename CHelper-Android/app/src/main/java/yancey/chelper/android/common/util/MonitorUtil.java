/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.android.common.util;

import android.app.Application;

import com.efs.sdk.net.OkHttpInterceptor;
import com.efs.sdk.net.OkHttpListener;
import com.umeng.analytics.MobclickAgent;
import com.umeng.commonsdk.UMConfigure;
import com.umeng.umcrash.UMCrash;

import okhttp3.OkHttpClient;
import yancey.chelper.BuildConfig;

public class MonitorUtil {

    private static Application application;
    private static boolean isInit = false;

    public static void init(Application application) {
        if (BuildConfig.DEBUG) {
            return;
        }
        MonitorUtil.application = application;
        if (PolicyGrantManager.INSTANCE.getState() == PolicyGrantManager.State.AGREE) {
            UMConfigure.init(application, "6836aa2bbc47b67d8374e464", "official", UMConfigure.DEVICE_TYPE_PHONE, "");
            isInit = true;
        } else {
            UMConfigure.preInit(application, "6836aa2bbc47b67d8374e464", "official");
            isInit = false;
        }
        MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.MANUAL);
    }

    public static void onAgreePolicyGrant() {
        if (BuildConfig.DEBUG) {
            return;
        }
        if (isInit) {
            return;
        }
        UMConfigure.init(application, "6836aa2bbc47b67d8374e464", "official", UMConfigure.DEVICE_TYPE_PHONE, "");
        isInit = true;
    }

    public static void generateCustomLog(Throwable e, String type) {
        if (BuildConfig.DEBUG) {
            return;
        }
        UMCrash.generateCustomLog(e, type);
    }

    public static void monitHttp(OkHttpClient.Builder builder) {
        if (BuildConfig.DEBUG) {
            return;
        }
        builder.eventListenerFactory(OkHttpListener.get())
                .addNetworkInterceptor(new OkHttpInterceptor());
    }

}
