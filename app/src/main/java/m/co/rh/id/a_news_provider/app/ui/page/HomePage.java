package m.co.rh.id.a_news_provider.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.constants.Shortcuts;
import m.co.rh.id.a_news_provider.app.provider.command.SyncRssCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.DeviceStatusNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.parser.OpmlParser;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.NewRssChannelSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.RssChannelListSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.RssItemListSV;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.app.workmanager.ConstantsKey;
import m.co.rh.id.a_news_provider.app.workmanager.OpmlParseWorker;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.provider.FileProvider;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnBackPressed;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class HomePage extends StatefulView<Activity> implements RequireNavigator, NavOnBackPressed {
    private static final String TAG = HomePage.class.getName();

    private transient INavigator mNavigator;
    private AppBarSV mAppBarSV;
    private boolean mIsDrawerOpen;
    private boolean mIsNewRssChannelDialogShow;
    private transient AlertDialog mDialog;
    private transient Runnable mPendingDialogCmd;
    private RssItemListSV mRssItemListSV;
    private NewRssChannelSV mNewRssChannelSV;
    private RssChannelListSV mRssChannelListSV;
    private RssChannel mSelectedRssChannel;
    private boolean mLastOnlineStatus;
    private transient long mLastBackPressMilis;
    private transient RxDisposer mRxDisposer;

    @Override
    public void provideNavigator(INavigator navigator) {
        if (mAppBarSV == null) {
            mAppBarSV = new AppBarSV(navigator);
        } else {
            mAppBarSV.provideNavigator(navigator);
        }
        mNavigator = navigator;
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        mRssItemListSV = new RssItemListSV();
        mNewRssChannelSV = new NewRssChannelSV();
        mRssChannelListSV = new RssChannelListSV();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.page_home, container, false);
        Provider provider = BaseApplication.of(activity).getProvider();
        prepareDisposer(provider);
        ILogger logger = provider.get(ILogger.class);
        SyncRssCmd syncRssCmd = provider.get(SyncRssCmd.class);
        View menuSettings = view.findViewById(R.id.menu_settings);
        menuSettings.setOnClickListener(view12 -> mNavigator.push(Routes.SETTINGS_PAGE));
        DrawerLayout drawerLayout = view.findViewById(R.id.drawer);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                mIsDrawerOpen = true;
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                mIsDrawerOpen = false;
            }
        });
        mAppBarSV.setMenu(R.menu.home, item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_sync_feed) {
                syncRssCmd.execute();
                return true;
            } else if (itemId == R.id.menu_export_opml) {
                Context appContext = activity.getApplicationContext();
                Single<File> fileSingle =
                        Single.fromCallable(() -> provider.get(OpmlParser.class).exportOpml())
                                .subscribeOn(Schedulers.from(provider.get(ExecutorService.class)))
                                .observeOn(AndroidSchedulers.mainThread());
                mRxDisposer.add("asyncExportOpml", fileSingle.subscribe(
                        file -> UiUtils.shareFile(activity, file, activity.getString(R.string.share_opml)),
                        throwable -> provider.get(ILogger.class)
                                .e(TAG, appContext.getString(R.string.error_exporting_opml),
                                        throwable)
                ));
            }
            return false;
        });
        mAppBarSV.setTitle(activity.getString(R.string.appbar_title_home));
        mAppBarSV.setNavigationOnClickListener(view1 -> {
            if (!drawerLayout.isOpen()) {
                drawerLayout.open();
            }
        });
        if (mIsDrawerOpen) {
            drawerLayout.open();
        }
        mRxDisposer.add("syncRssCmd.syncedRss",
                syncRssCmd.syncedRss()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssModels -> {
                                    if (!rssModels.isEmpty()) {
                                        Toast.makeText(activity,
                                                activity.getString(R.string.feed_sync_success)
                                                , Toast.LENGTH_LONG).show();
                                    }
                                },
                                throwable ->
                                        logger.e(TAG,
                                                activity.getString(R.string.error_feed_sync_failed),
                                                throwable)
                        )
        );
        RssChangeNotifier rssChangeNotifier = provider.get(RssChangeNotifier.class);
        if (mSelectedRssChannel != null) {
            rssChangeNotifier.selectRssChannel(mSelectedRssChannel);
        }
        mRxDisposer.add("rssChangeNotifier.selectedRssChannel",
                rssChangeNotifier.selectedRssChannel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssChannelOptional -> {
                            if (rssChannelOptional.isPresent()) {
                                if (drawerLayout.isOpen()) {
                                    drawerLayout.close();
                                }
                                mSelectedRssChannel = rssChannelOptional.get();
                            }
                        })
        );
        mRxDisposer.add("rssChangeNotifier.newRssModel",
                rssChangeNotifier.liveNewRssModel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssModelOptional ->
                                rssModelOptional
                                        .ifPresent(rssModel ->
                                                logger.i(TAG,
                                                        activity.getString(
                                                                R.string.feed_added,
                                                                rssModel
                                                                        .getRssChannel()
                                                                        .feedName)))
                        ));
        DeviceStatusNotifier deviceStatusNotifier = provider.get(DeviceStatusNotifier.class);
        mRxDisposer.add("deviceStatusNotifier.onlineStatus",
                deviceStatusNotifier.onlineStatus().subscribe(isOnline -> {
                    if (!isOnline) {
                        // only show when there are changes on online status
                        if (mLastOnlineStatus != isOnline) {
                            Snackbar.make(container,
                                    R.string.device_status_offline,
                                    Snackbar.LENGTH_SHORT)
                                    .setBackgroundTint(Color.RED)
                                    .setTextColor(Color.WHITE)
                                    .show();
                        }
                    }
                    mLastOnlineStatus = isOnline;
                }));
        ViewGroup containerChannelList = view.findViewById(R.id.container_list_channel);
        containerChannelList.addView(mRssChannelListSV.buildView(activity, containerChannelList));

        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));

        ViewGroup containerListNews = view.findViewById(R.id.container_list_news);
        containerListNews.addView(mRssItemListSV.buildView(activity, container));

        FloatingActionButton fab = view.findViewById(R.id.fab);
        fab.setOnClickListener(view1 -> showNewRssChannelDialog(activity, container));
        if (mDialog != null) {
            mDialog.dismiss();
        }
        if (mIsNewRssChannelDialogShow) {
            fab.performClick();
        }
        SwipeRefreshLayout swipeRefreshLayout = view.findViewById(R.id.container_swipe_refresh);
        swipeRefreshLayout.setOnRefreshListener(() -> mRssItemListSV.refresh());
        if (mRssItemListSV.observeRssItems() != null) {
            mRxDisposer.add("mRssItemListSV.observeRssItems",
                    mRssItemListSV.observeRssItems()
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(rssItems -> swipeRefreshLayout.setRefreshing(false))
            );
        }

        // Handle shortcut
        Intent intent = activity.getIntent();
        String intentAction = intent.getAction();
        if (Shortcuts.NEW_RSS_CHANNEL_ACTION.equals(intentAction)) {
            fab.performClick();
        } else if (Intent.ACTION_SEND.equals(intentAction)) {
            String sharedText = activity.getIntent()
                    .getStringExtra(Intent.EXTRA_TEXT);
            mNewRssChannelSV.setFeedUrl(sharedText);
            fab.performClick();
        } else if (Intent.ACTION_VIEW.equals(intentAction)) {
            Uri fileData = intent.getData();
            provider.get(ExecutorService.class)
                    .execute(() -> {
                        try {
                            File file = provider.get(FileProvider.class)
                                    .createTempFile("Feed.opml", fileData);
                            OneTimeWorkRequest oneTimeWorkRequest =
                                    new OneTimeWorkRequest.Builder(OpmlParseWorker.class)
                                            .setInputData(new Data.Builder()
                                                    .putString(ConstantsKey.KEY_FILE_ABSOLUTE_PATH,
                                                            file.getAbsolutePath())
                                                    .build())
                                            .build();
                            provider.get(WorkManager.class)
                                    .enqueue(oneTimeWorkRequest);
                        } catch (Throwable throwable) {
                            provider.get(ILogger.class)
                                    .e(TAG, activity.getString(R.string.error_failed_to_open_file)
                                            , throwable);
                        }
                    });
        }
        return view;
    }

    private void prepareDisposer(Provider provider) {
        if (mRxDisposer != null) {
            mRxDisposer.dispose();
        }
        mRxDisposer = provider.get(RxDisposer.class);
    }

    private void showNewRssChannelDialog(Activity activity, ViewGroup container) {
        // queue guard to ensure only one dialog can be displayed at a time
        if (mDialog != null) {
            mPendingDialogCmd = () -> {
                showNewRssChannelDialog(activity, container);
                mPendingDialogCmd = null;
            };
            return;
        }
        mIsNewRssChannelDialogShow = true;
        View dialogView = mNewRssChannelSV.buildView(activity, container);
        MaterialAlertDialogBuilder alertBuilder = new MaterialAlertDialogBuilder(activity);
        alertBuilder.setView(dialogView);
        alertBuilder.setPositiveButton(R.string.add, (dialogInterface, i) -> {
            //leave blank
        });
        alertBuilder.setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {
            mNewRssChannelSV.clearText(dialogView);
        });
        alertBuilder.setOnCancelListener(dialog -> {
            mIsNewRssChannelDialogShow = false;
            mDialog = null;
            if (mPendingDialogCmd != null) {
                mPendingDialogCmd.run();
            }
        });
        alertBuilder.setOnDismissListener(dialog -> {
            mIsNewRssChannelDialogShow = false;
            mDialog = null;
            if (mPendingDialogCmd != null) {
                mPendingDialogCmd.run();
            }
        });
        mDialog = alertBuilder.create();
        mDialog.show();
        Button positiveButton = mDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(v -> {
            if (mNewRssChannelSV.isValid()) {
                mNewRssChannelSV.addNewFeed();
                mDialog.dismiss();
                mDialog = null;
                mIsNewRssChannelDialogShow = false;
            }
        });
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mPendingDialogCmd = null;
        if (mDialog != null) {
            mDialog.dismiss();
        }
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        mRssItemListSV.dispose(activity);
        mRssItemListSV = null;
        if (mSelectedRssChannel != null) {
            mSelectedRssChannel = null;
        }
        if (mRxDisposer != null) {
            mRxDisposer.dispose();
            mRxDisposer = null;
        }
    }

    @Override
    public void onBackPressed(View currentView, Activity activity, INavigator navigator) {
        DrawerLayout drawerLayout = currentView.findViewById(R.id.drawer);
        if (drawerLayout.isOpen()) {
            drawerLayout.close();
        } else {
            long currentMilis = System.currentTimeMillis();
            if ((currentMilis - mLastBackPressMilis) < 1000) {
                navigator.finishActivity(null);
            } else {
                mLastBackPressMilis = currentMilis;
                BaseApplication.of(activity)
                        .getProvider()
                        .get(ILogger.class)
                        .i(TAG, activity.getString(R.string.toast_back_press_exit));
            }
        }
    }
}
