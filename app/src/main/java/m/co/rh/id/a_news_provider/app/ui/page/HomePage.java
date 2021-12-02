package m.co.rh.id.a_news_provider.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.component.AppSharedPreferences;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.constants.Shortcuts;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.SyncRssCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.DeviceStatusNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.parser.OpmlParser;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.NewRssChannelSVDialog;
import m.co.rh.id.a_news_provider.app.ui.component.rss.RssChannelListSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.RssItemListSV;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.app.workmanager.ConstantsKey;
import m.co.rh.id.a_news_provider.app.workmanager.OpmlParseWorker;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnBackPressed;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class HomePage extends StatefulView<Activity> implements Externalizable, RequireNavigator, RequireComponent<Provider>, NavOnBackPressed, Toolbar.OnMenuItemClickListener, SwipeRefreshLayout.OnRefreshListener, DrawerLayout.DrawerListener, View.OnClickListener {
    private static final String TAG = HomePage.class.getName();

    private transient INavigator mNavigator;
    private AppBarSV mAppBarSV;
    private boolean mIsDrawerOpen;
    private transient Runnable mPendingDialogCmd;
    private RssItemListSV mRssItemListSV;
    private RssChannelListSV mRssChannelListSV;
    private RssChannel mSelectedRssChannel;
    private boolean mLastOnlineStatus;
    private transient long mLastBackPressMilis;

    // component
    private transient ExecutorService mExecutorService;
    private transient FileHelper mFileHelper;
    private transient ILogger mLogger;
    private transient WorkManager mWorkManager;
    private transient DeviceStatusNotifier mDeviceStatusNotifier;
    private transient AppSharedPreferences mAppSharedPreferences;
    private transient RssChangeNotifier mRssChangeNotifier;
    private transient Provider mSvProvider;

    // View related
    private transient DrawerLayout mDrawerLayout;
    private transient Runnable mOnNavigationClicked;

    @Override
    public void provideNavigator(INavigator navigator) {
        if (mAppBarSV == null) {
            mAppBarSV = new AppBarSV(navigator, R.menu.home);
        } else {
            mAppBarSV.provideNavigator(navigator);
        }
        mNavigator = navigator;
    }

    @Override
    public void provideComponent(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mFileHelper = provider.get(FileHelper.class);
        mLogger = provider.get(ILogger.class);
        mWorkManager = provider.get(WorkManager.class);
        mDeviceStatusNotifier = provider.get(DeviceStatusNotifier.class);
        mAppSharedPreferences = provider.get(AppSharedPreferences.class);
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = provider.get(StatefulViewProvider.class);
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        mRssItemListSV = new RssItemListSV();
        mRssChannelListSV = new RssChannelListSV();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        int layoutId = R.layout.page_home;
        if (mAppSharedPreferences.isOneHandMode()) {
            layoutId = R.layout.one_hand_mode_page_home;
        }
        View view = activity.getLayoutInflater().inflate(layoutId, container, false);
        View menuSettings = view.findViewById(R.id.menu_settings);
        menuSettings.setOnClickListener(view12 -> mNavigator.push(Routes.SETTINGS_PAGE));
        mDrawerLayout = view.findViewById(R.id.drawer);
        mDrawerLayout.addDrawerListener(this);
        if (mOnNavigationClicked == null) {
            mOnNavigationClicked = () -> {
                if (!mDrawerLayout.isOpen()) {
                    mDrawerLayout.open();
                }
            };
        }
        mAppBarSV.setMenuItemListener(this);
        mAppBarSV.setTitle(activity.getString(R.string.appbar_title_home));
        mAppBarSV.setNavigationOnClick(mOnNavigationClicked);
        if (mIsDrawerOpen) {
            mDrawerLayout.open();
        }
        String feedSyncSuccess = activity.getString(R.string.feed_sync_success);
        String feedSyncError = activity.getString(R.string.error_feed_sync_failed);
        mSvProvider.get(RxDisposer.class).add("syncRssCmd.syncedRss",
                mSvProvider.get(SyncRssCmd.class).syncedRss()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssModels -> {
                                    if (!rssModels.isEmpty()) {
                                        Toast.makeText(mSvProvider.getContext(),
                                                feedSyncSuccess
                                                , Toast.LENGTH_LONG).show();
                                    }
                                },
                                throwable ->
                                        mLogger.e(TAG, feedSyncError, throwable)
                        )
        );
        if (mSelectedRssChannel != null) {
            mRssChangeNotifier.selectRssChannel(mSelectedRssChannel);
        }
        mSvProvider.get(RxDisposer.class).add("rssChangeNotifier.selectedRssChannel",
                mRssChangeNotifier.selectedRssChannel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssChannelOptional -> {
                            if (rssChannelOptional.isPresent()) {
                                if (mDrawerLayout.isOpen()) {
                                    mDrawerLayout.close();
                                }
                                mSelectedRssChannel = rssChannelOptional.get();
                            }
                        })
        );
        mSvProvider.get(RxDisposer.class).add("rssChangeNotifier.newRssModel",
                mRssChangeNotifier.liveNewRssModel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssModelOptional ->
                                rssModelOptional
                                        .ifPresent(rssModel ->
                                                mLogger.i(TAG,
                                                        mSvProvider.getContext()
                                                                .getString(
                                                                        R.string.feed_added,
                                                                        rssModel
                                                                                .getRssChannel()
                                                                                .feedName)))
                        ));
        mSvProvider.get(RxDisposer.class).add("deviceStatusNotifier.onlineStatus",
                mDeviceStatusNotifier.onlineStatus().subscribe(isOnline -> {
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
        fab.setOnClickListener(this);
        SwipeRefreshLayout swipeRefreshLayout = view.findViewById(R.id.container_swipe_refresh);
        swipeRefreshLayout.setOnRefreshListener(this);
        if (mRssItemListSV.observeRssItems() != null) {
            mSvProvider.get(RxDisposer.class).add("mRssItemListSV.observeRssItems",
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
            mNavigator.push((args, activity1) ->
                    new NewRssChannelSVDialog((String) args), sharedText);
        } else if (Intent.ACTION_VIEW.equals(intentAction)) {
            Uri fileData = intent.getData();
            String errorMessage = activity.getString(R.string.error_failed_to_open_file);
            mExecutorService
                    .execute(() -> {
                        try {
                            File file = mFileHelper
                                    .createTempFile("Feed.opml", fileData);
                            OneTimeWorkRequest oneTimeWorkRequest =
                                    new OneTimeWorkRequest.Builder(OpmlParseWorker.class)
                                            .setInputData(new Data.Builder()
                                                    .putString(ConstantsKey.KEY_FILE_ABSOLUTE_PATH,
                                                            file.getAbsolutePath())
                                                    .build())
                                            .build();
                            mWorkManager
                                    .enqueue(oneTimeWorkRequest);
                        } catch (Throwable throwable) {
                            mLogger
                                    .e(TAG, errorMessage
                                            , throwable);
                        }
                    });
        }
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mPendingDialogCmd = null;
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        mRssItemListSV.dispose(activity);
        mRssItemListSV = null;
        if (mSelectedRssChannel != null) {
            mSelectedRssChannel = null;
        }
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mDrawerLayout = null;
        mOnNavigationClicked = null;
        mExecutorService = null;
        mFileHelper = null;
        mLogger = null;
        mWorkManager = null;
        mDeviceStatusNotifier = null;
        mAppSharedPreferences = null;
        mRssChangeNotifier = null;
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
                mLogger.i(TAG,
                        activity.getString(R.string.toast_back_press_exit));
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_sync_feed) {
            mSvProvider.get(SyncRssCmd.class).execute();
            return true;
        } else if (itemId == R.id.menu_export_opml) {
            Context context = mSvProvider.getContext();
            Single<File> fileSingle =
                    Single.fromCallable(() -> mSvProvider.get(OpmlParser.class).exportOpml())
                            .subscribeOn(Schedulers.from(mExecutorService))
                            .observeOn(AndroidSchedulers.mainThread());
            mSvProvider.get(RxDisposer.class).add("asyncExportOpml", fileSingle.subscribe(
                    file -> UiUtils.shareFile(context, file, context.getString(R.string.share_opml)),
                    throwable -> mLogger
                            .e(TAG, context.getString(R.string.error_exporting_opml),
                                    throwable)
            ));
        }
        return false;
    }

    @Override
    public void onRefresh() {
        mRssItemListSV.refresh();
    }

    @Override
    public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
        // Leave blank
    }

    @Override
    public void onDrawerOpened(@NonNull View drawerView) {
        mIsDrawerOpen = true;
    }

    @Override
    public void onDrawerClosed(@NonNull View drawerView) {
        mIsDrawerOpen = false;
    }

    @Override
    public void onDrawerStateChanged(int newState) {
        // Leave blank
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.fab) {
            mNavigator.push((args, activity1) ->
                    new NewRssChannelSVDialog());
        }
    }

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        super.writeExternal(objectOutput);
        objectOutput.writeObject(mAppBarSV);
        objectOutput.writeBoolean(mIsDrawerOpen);
        objectOutput.writeObject(mRssItemListSV);
        objectOutput.writeObject(mRssChannelListSV);
        objectOutput.writeObject(mSelectedRssChannel);
        objectOutput.writeBoolean(mLastOnlineStatus);
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws ClassNotFoundException, IOException {
        super.readExternal(objectInput);
        mAppBarSV = (AppBarSV) objectInput.readObject();
        mIsDrawerOpen = objectInput.readBoolean();
        mRssItemListSV = (RssItemListSV) objectInput.readObject();
        mRssChannelListSV = (RssChannelListSV) objectInput.readObject();
        mSelectedRssChannel = (RssChannel) objectInput.readObject();
        mLastOnlineStatus = objectInput.readBoolean();
    }
}
