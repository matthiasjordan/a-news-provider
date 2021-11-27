package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProviderModule;
import m.co.rh.id.a_news_provider.app.provider.command.RenameRssFeedCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

public class RssChannelItemSV extends StatefulView<Activity> implements View.OnClickListener, View.OnLongClickListener {

    private transient BehaviorSubject<Map.Entry<RssChannel, Integer>> mRssChannelCountSubject;
    private transient BehaviorSubject<Boolean> mEditModeSubject;
    private transient BehaviorSubject<Optional<String>> mImageUrlSubject;
    private transient BehaviorSubject<String> mEditNameSubject;
    private transient Provider mSvProvider;
    private transient RssChangeNotifier mRssChangeNotifier;

    public void setRssChannelCount(Map.Entry<RssChannel, Integer> rssChannelCount) {
        if (mRssChannelCountSubject == null) {
            mRssChannelCountSubject = BehaviorSubject.createDefault(rssChannelCount);
        } else {
            mRssChannelCountSubject.onNext(rssChannelCount);
        }
        if (mEditModeSubject != null) {
            mEditModeSubject.onNext(false);
        }
    }


    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mRssChannelCountSubject == null) {
            mRssChannelCountSubject = BehaviorSubject.create();
        }
        if (mEditModeSubject == null) {
            mEditModeSubject = BehaviorSubject.createDefault(false);
        }
        if (mImageUrlSubject == null) {
            mImageUrlSubject = BehaviorSubject.createDefault(Optional.empty());
        }
        if (mEditNameSubject == null) {
            mEditNameSubject = BehaviorSubject.createDefault("");
        }
        View view = activity.getLayoutInflater().inflate(R.layout.list_item_rss_channel, container, false);
        NetworkImageView networkImageViewIcon = view.findViewById(R.id.network_image_view_icon);
        TextView textName = view.findViewById(R.id.text_name);
        TextView textCount = view.findViewById(R.id.text_count);
        EditText editName = view.findViewById(R.id.input_text_name);
        Button buttonRename = view.findViewById(R.id.button_rename);
        Button buttonDelete = view.findViewById(R.id.button_delete);
        Button buttonCancel = view.findViewById(R.id.button_cancel);
        Button buttonLink = view.findViewById(R.id.button_link);

        Provider provider = BaseApplication.of(activity).getProvider();
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = Provider.createProvider(activity.getApplicationContext(), new StatefulViewProviderModule(activity));
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
        view.setOnClickListener(this);
        view.setLongClickable(true);
        view.setOnLongClickListener(this);
        editName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String s = editable.toString();
                mEditNameSubject.onNext(s);
                mSvProvider.get(RenameRssFeedCmd.class).validName(s);
            }
        });
        buttonRename.setOnClickListener(this);
        buttonDelete.setOnClickListener(this);
        buttonCancel.setOnClickListener(this);
        buttonLink.setOnClickListener(this);
        mSvProvider.get(RxDisposer.class).add("mRenameRssFeedCmd.getNameValidation",
                mSvProvider.get(RenameRssFeedCmd.class).liveNameValidation()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                editName.setError(s);
                            } else {
                                editName.setError(null);
                            }
                        })
        );
        mSvProvider.get(RxDisposer.class).add("mRenameRssFeedCmd.getRssChannel",
                mSvProvider.get(RenameRssFeedCmd.class).liveRssChannel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssChannel -> Toast
                                        .makeText(activity,
                                                activity.getString(R.string.rename_feed_to, rssChannel.feedName)
                                                , Toast.LENGTH_LONG).show(),
                                throwable -> Toast
                                        .makeText(activity,
                                                throwable.getMessage()
                                                , Toast.LENGTH_LONG).show())
        );
        mSvProvider.get(RxDisposer.class).add("mEditModeSubject", mEditModeSubject.subscribe(editMode -> {
                    if (editMode) {
                        networkImageViewIcon.setVisibility(View.GONE);
                        textName.setVisibility(View.GONE);
                        textCount.setVisibility(View.GONE);
                        editName.setVisibility(View.VISIBLE);
                        buttonRename.setVisibility(View.VISIBLE);
                        buttonDelete.setVisibility(View.VISIBLE);
                        buttonCancel.setVisibility(View.VISIBLE);
                        buttonLink.setVisibility(View.VISIBLE);
                    } else {
                        networkImageViewIcon.setVisibility(View.VISIBLE);
                        textName.setVisibility(View.VISIBLE);
                        textCount.setVisibility(View.VISIBLE);
                        editName.setVisibility(View.GONE);
                        buttonRename.setVisibility(View.GONE);
                        buttonDelete.setVisibility(View.GONE);
                        buttonCancel.setVisibility(View.GONE);
                        buttonLink.setVisibility(View.GONE);
                    }
                })
        );
        mSvProvider.get(RxDisposer.class).add("rssChannelUiChange", Flowable.combineLatest(
                Flowable.fromObservable(mRssChannelCountSubject, BackpressureStrategy.BUFFER)
                        .debounce(100, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread()),
                mRssChangeNotifier.selectedRssChannel()
                        .observeOn(AndroidSchedulers.mainThread()),
                (rssChannelCountEntry, rssChannelOptional) -> {
                    RssChannel rssChannel = rssChannelCountEntry.getKey();
                    if (rssChannel.title != null) {
                        textName.setText(rssChannel.feedName);
                        editName.setText(rssChannel.feedName);
                    }
                    if (rssChannel.imageUrl != null) {
                        mImageUrlSubject.onNext(Optional.of(rssChannel.imageUrl));
                        networkImageViewIcon.setVisibility(View.VISIBLE);
                    } else {
                        mImageUrlSubject.onNext(Optional.empty());
                        networkImageViewIcon.setVisibility(View.GONE);
                    }
                    textCount.setText(rssChannelCountEntry.getValue().toString());

                    int selectedColor = UiUtils.getColorFromAttribute(activity, R.attr.colorOnPrimary);
                    if (rssChannelOptional.isPresent()) {
                        if (rssChannelOptional.get().id.equals(rssChannelCountEntry.getKey().id)) {
                            selectedColor = activity.getResources().getColor(R.color.daynight_gray_300_gray_600);
                        }
                    }
                    view.setBackgroundColor(selectedColor);
                    return true;
                }).subscribe(aBoolean -> {
                })
        );
        mSvProvider.get(RxDisposer.class).add("setImageUrl",
                mImageUrlSubject.debounce(100, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(imageUrlOpt -> imageUrlOpt.ifPresent(s ->
                                networkImageViewIcon.setImageUrl(s,
                                        provider.get(ImageLoader.class)))));
        return view;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.root_layout) {
            Boolean editMode = mEditModeSubject.getValue();
            if (editMode == null || !editMode) {
                Map.Entry<RssChannel, Integer> entry = mRssChannelCountSubject.getValue();
                if (entry != null) {
                    mRssChangeNotifier.selectRssChannel(entry.getKey());
                }
            }
        } else if (viewId == R.id.button_rename) {
            String feedName = mEditNameSubject.getValue();
            if (mSvProvider.get(RenameRssFeedCmd.class).validName(feedName)) {
                Map.Entry<RssChannel, Integer> rssChannelCount = mRssChannelCountSubject.getValue();
                if (rssChannelCount != null) {
                    mSvProvider.get(RenameRssFeedCmd.class).execute(rssChannelCount.getKey().id, feedName);
                }
            }
            mEditModeSubject.onNext(!mEditModeSubject.getValue());
        } else if (viewId == R.id.button_delete) {
            Map.Entry<RssChannel, Integer> rssChannelCount = mRssChannelCountSubject.getValue();
            if (rssChannelCount != null) {
                mRssChangeNotifier.deleteRssChannel(rssChannelCount.getKey());
            }
            mEditModeSubject.onNext(!mEditModeSubject.getValue());
        } else if (viewId == R.id.button_cancel) {
            mEditModeSubject.onNext(!mEditModeSubject.getValue());
        } else if (viewId == R.id.button_link) {
            Context context = view.getContext();
            MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(context);
            materialAlertDialogBuilder.setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss());
            materialAlertDialogBuilder.setTitle(context.getString(R.string.url).toUpperCase());
            materialAlertDialogBuilder.setMessage(mRssChannelCountSubject.getValue().getKey().url);
            materialAlertDialogBuilder.create().show();
        }
    }

    @Override
    public boolean onLongClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.root_layout) {
            mEditModeSubject.onNext(!mEditModeSubject.getValue());
            return true;
        }
        return false;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mRssChannelCountSubject != null) {
            mRssChannelCountSubject.onComplete();
            mRssChannelCountSubject = null;
        }
        if (mEditModeSubject != null) {
            mEditModeSubject.onComplete();
            mEditModeSubject = null;
        }
        if (mImageUrlSubject != null) {
            mImageUrlSubject.onComplete();
            mImageUrlSubject = null;
        }
        mRssChangeNotifier = null;
    }
}
