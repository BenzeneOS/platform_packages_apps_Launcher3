/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.allapps.search;

import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_EMPTY_SEARCH;
import static com.android.launcher3.model.data.AppsListData.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.AnyThread;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.search.SearchAlgorithm;
import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.search.StringMatcherUtility;
import com.android.launcher3.util.LooperExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The default search implementation.
 */
public class DefaultAppSearchAlgorithm implements SearchAlgorithm<AdapterItem> {

    private static final int MAX_RESULTS_COUNT = 5;

    private final LauncherAppState mAppState;
    private final Context mContext;
    private final Handler mResultHandler;
    private final boolean mAddNoResultsMessage;
    private final int mMaxResultsCount;

    public DefaultAppSearchAlgorithm(Context context, LooperExecutor uiExecutor) {
        this(context, uiExecutor, false, MAX_RESULTS_COUNT);
    }

    public DefaultAppSearchAlgorithm(
            Context context, LooperExecutor uiExecutor, boolean addNoResultsMessage) {
        this(context, uiExecutor, addNoResultsMessage, MAX_RESULTS_COUNT);
    }

    public DefaultAppSearchAlgorithm(Context context, LooperExecutor uiExecutor,
            boolean addNoResultsMessage, int maxResultsCount) {
        mAppState = LauncherAppState.getInstance(context);
        mContext = context.getApplicationContext();
        mResultHandler = new Handler(uiExecutor.getLooper());
        mAddNoResultsMessage = addNoResultsMessage;
        mMaxResultsCount = Math.max(1, maxResultsCount);
    }

    @Override
    public void cancel(boolean interruptActiveRequests) {
        if (interruptActiveRequests) {
            mResultHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void doSearch(String query, SearchCallback<AdapterItem> callback) {
        mAppState.getModel().enqueueModelUpdateTask((taskController, dataModel, apps) ->  {
            boolean privateProfileQuiet =
                    apps.hasFlags(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED);
            ArrayList<AdapterItem> result = getTitleMatchResult(apps.data, query,
                    appInfo -> shouldShowAppInSearch(appInfo, privateProfileQuiet),
                    mMaxResultsCount);
            if (mAddNoResultsMessage && result.isEmpty()) {
                result.add(getEmptyMessageAdapterItem(query));
            }
            mResultHandler.post(() -> callback.onSearchResult(query, result));
        });
    }

    private static AdapterItem getEmptyMessageAdapterItem(String query) {
        AdapterItem item = new AdapterItem(VIEW_TYPE_EMPTY_SEARCH);
        // Add a place holder info to propagate the query
        AppInfo placeHolder = new AppInfo();
        placeHolder.title = query;
        item.itemInfo = placeHolder;
        return item;
    }

    /**
     * Filters {@link AppInfo}s matching specified query
     */
    @AnyThread
    public static ArrayList<AdapterItem> getTitleMatchResult(List<AppInfo> apps, String query) {
        return getTitleMatchResult(apps, query, appInfo -> true, MAX_RESULTS_COUNT);
    }

    @AnyThread
    private static ArrayList<AdapterItem> getTitleMatchResult(List<AppInfo> apps, String query,
            Predicate<AppInfo> appFilter, int maxResultsCount) {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        final String queryTextLower = query.toLowerCase();
        final ArrayList<AdapterItem> result = new ArrayList<>();
        StringMatcherUtility.StringMatcher matcher =
                StringMatcherUtility.StringMatcher.getInstance();

        int resultCount = 0;
        int total = apps.size();
        for (int i = 0; i < total && resultCount < maxResultsCount; i++) {
            AppInfo info = apps.get(i);
            if (appFilter.test(info)
                    && StringMatcherUtility.matches(queryTextLower, info.title.toString(),
                            matcher)) {
                result.add(AdapterItem.asApp(info));
                resultCount++;
            }
        }
        return result;
    }

    private boolean shouldShowAppInSearch(AppInfo info, boolean privateProfileQuiet) {
        if (!UserCache.INSTANCE.get(mContext).getUserInfo(info.user).isPrivate()) {
            return true;
        }
        return !privateProfileQuiet;
    }
}
