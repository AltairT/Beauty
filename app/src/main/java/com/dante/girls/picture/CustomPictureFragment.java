package com.dante.girls.picture;

import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.dante.girls.MainActivity;
import com.dante.girls.R;
import com.dante.girls.base.Constants;
import com.dante.girls.model.DataBase;
import com.dante.girls.model.Image;
import com.dante.girls.net.API;
import com.dante.girls.net.DataFetcher;
import com.dante.girls.utils.SpUtil;
import com.dante.girls.utils.UiUtils;

import java.util.List;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static android.support.design.widget.AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED;
import static android.support.design.widget.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL;
import static com.dante.girls.net.API.TYPE_A_ANIME;
import static com.dante.girls.net.API.TYPE_A_FULI;
import static com.dante.girls.net.API.TYPE_A_HENTAI;
import static com.dante.girls.net.API.TYPE_A_UNIFORM;
import static com.dante.girls.net.API.TYPE_A_ZATU;
import static com.dante.girls.net.API.TYPE_DB_BREAST;
import static com.dante.girls.net.API.TYPE_DB_BUTT;
import static com.dante.girls.net.API.TYPE_DB_LEG;
import static com.dante.girls.net.API.TYPE_DB_RANK;
import static com.dante.girls.net.API.TYPE_DB_SILK;

/**
 * Custom appearance of picture list fragment
 */

public class CustomPictureFragment extends PictureFragment {
    private static final String TAG = "CustomPictureFragment";
    private static final int BUFFER_SIZE = 2;
    boolean isInPost;
    boolean isA;
    private Observable<List<Image>> source;

    public static CustomPictureFragment newInstance(String type) {
        Bundle args = new Bundle();
        args.putString(Constants.TYPE, type);
        CustomPictureFragment fragment = new CustomPictureFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public void setInfo(String info) {
        this.info = info;
        isInPost = true;
    }

    @Override
    protected void initViews() {
        super.initViews();
        imageType = TextUtils.isEmpty(info) ? baseType : info;
    }

    @Override
    protected void onImageClicked(View view, int position) {
        log("isA " + isA, "::: isInpost " + isInPost);
        if (isA && !isInPost) {
            startPost(getImage(position));
            return;
        }
        super.onImageClicked(view, position);
    }

    @Override
    protected int initAdapterLayout() {
        int layout = R.layout.picture_item;
        switch (baseType) {
            case TYPE_A_ANIME:
            case TYPE_A_FULI:
            case TYPE_A_HENTAI:
            case TYPE_A_ZATU:
            case TYPE_A_UNIFORM:
                isA = true;
                layout = R.layout.post_item;
                if (isInPost) {
                    layout = R.layout.picture_item;
                }
        }
        return layout;
    }

    @Override
    public void fetch() {
        if (isFetching) {
            return;
        }
        if (page <= 1) {
            imageList = realm.copyFromRealm(images);
        }
        DataFetcher fetcher;
        switch (baseType) {
            case TYPE_DB_BREAST:
            case TYPE_DB_BUTT:
            case TYPE_DB_LEG:
            case TYPE_DB_SILK:
            case TYPE_DB_RANK:
                url = API.DB_BASE;
                fetcher = new DataFetcher(url, imageType, page);
                source = fetcher.getDouban();
                break;

            case TYPE_A_ANIME:
            case TYPE_A_FULI:
            case TYPE_A_HENTAI:
            case TYPE_A_ZATU:
            case TYPE_A_UNIFORM:
                url = API.A_BASE;
                fetcher = new DataFetcher(url, imageType, page);
                source = isInPost ? fetcher.getPicturesOfPost(info) : fetcher.getAPosts();
                break;

            default://imageType = 0, 代表GANK
                url = API.GANK;
                if (page <= 1) {
                    LOAD_COUNT = LOAD_COUNT_LARGE;
                }
                fetcher = new DataFetcher(url, imageType, page);
                source = fetcher.getGank();
                break;
        }
        fetchImages(source);
    }

    //预加载Image，然后刷新列表
    protected void fetchImages(final Observable<List<Image>> source) {
        subscription = source
                .observeOn(Schedulers.io())
                .flatMap(new Func1<List<Image>, Observable<Image>>() {
                    @Override
                    public Observable<Image> call(List<Image> images) {
                        return Observable.from(images);
                    }
                })
                .map(image -> {
                    if (!isA || isInPost) {
                        //不是A区，需要预加载
                        return Image.getFixedImage(context, image, imageType, page);
                    }
                    return image;
                })
                .buffer(BUFFER_SIZE)
                .compose(applySchedulers())
                .subscribe(new Subscriber<List<Image>>() {
                    int oldSize;
                    int newSize;

                    @Override
                    public void onStart() {
                        oldSize = images.size();
                        log("old size", oldSize);
                    }

                    @Override
                    public void onCompleted() {
                        images = DataBase.findImages(realm, imageType);
                        newSize = images.size();
                        int add = newSize - oldSize;
                        changeState(false);

                        sortData(add);
                        if (add == 0) {
                            log("onCompleted: old new size are the same");
                            if (isInPost) adapter.loadMoreEnd(true);
                            if (page != 1) adapter.loadMoreFail();

                        } else {
                            log("newsize ", newSize);
                            if (page <= 1 && !isInPost) {
                                //每次刷新第一页的时候给图片排序
                                sortData(add);
                            } else {
                                //获取到数据了，下一页
                                log("save page" + page);
                                SpUtil.save(imageType + Constants.PAGE, page);
                                adapter.notifyItemRangeChanged(oldSize, add);
                            }
                            adapter.loadMoreComplete();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        changeState(false);
                        adapter.loadMoreComplete();
                        if (page != 1) adapter.loadMoreFail();
                        UiUtils.showSnack(rootView, R.string.load_fail);
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(List<Image> list) {
                        if (imageList != null) {
                            if (imageList.containsAll(list)) return;
                            imageList.addAll(0, list);//新数据加到0的位置
                        }
                        DataBase.save(realm, list);
                    }
                });
        compositeSubscription.add(subscription);
    }

    private void sortData(final int added) {
        if (imageList.size() == 0) {
            return;
        }
        Log.i(TAG, "execute: before sort " + images.first().url);
        realm.executeTransactionAsync(realm -> {
            for (int i = 0; i < imageList.size(); i++) {
                Image image = imageList.get(i);
                Image data = realm.where(Image.class).equalTo(Constants.URL, image.url).findFirst();
                if (data != null) {
                    data.setId(i);//id作为序号: 1, 2, 3 ...
                    Log.i(TAG, "sortData: id: " + i + "   url:" + data.url);
                }
            }
        }, () -> {
            images.sort(Constants.ID);
            Log.i(TAG, "execute: after sort " + images.first().url);
            adapter.notifyItemRangeInserted(0, added);
            Log.i(TAG, "onSuccess: sortData " + added + " inserted");
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isInPost && images.size() > 8) {
            log("10 image ", images.get(7).title);
        }
    }

    @Override
    protected void onCreateView() {
        super.onCreateView();
        if (isA) recyclerView.setBackgroundColor(getColor(R.color.cardview_dark_background));
        setupToolbar();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);//fragment被show或者hide时调用
        if (!hidden) {
            setupToolbar();
        }
    }


    private void setupToolbar() {
        //在A区帖子中，改变toolbar的样式
        AppBarLayout.LayoutParams p = (AppBarLayout.LayoutParams) toolbar.getLayoutParams();
        if (isInPost) {
            p.setScrollFlags(0);
            ((MainActivity) context).changeNavigator(false);
            context.setToolbarTitle(title);
        } else {
            p.setScrollFlags(SCROLL_FLAG_SCROLL | SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED);
            ((MainActivity) context).changeNavigator(true);
            context.setToolbarTitle(((MainActivity) context).getCurrentMenuTitle());
        }
        toolbar.setLayoutParams(p);
    }

    private void startPost(Image image) {
        FragmentTransaction transaction = context.getSupportFragmentManager().beginTransaction();
        CustomPictureFragment fragment = CustomPictureFragment.newInstance(imageType);
        fragment.setInfo(image.info);//帖子地址，也是imageType
        fragment.setTitle(image.title);//用于toolbar的标题
        transaction.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right
                , android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        transaction.hide(this);
        transaction.add(R.id.container, fragment, "aPost");
        transaction.addToBackStack(null);
        transaction.commit();
    }

    @Override
    protected void initData() {
        super.initData();
        adapter.setOnLoadMoreListener(() -> {
            page = SpUtil.getInt(imageType + Constants.PAGE, 1);
            page++;
            log("loadmore ", page);
            fetch();
        });
        if (images.isEmpty()) {
            page = 1;
            fetch();
            changeState(true);
        }
    }

}
