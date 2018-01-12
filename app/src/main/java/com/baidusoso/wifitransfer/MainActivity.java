package com.baidusoso.wifitransfer;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.hwangjr.rxbus.RxBus;
import com.hwangjr.rxbus.annotation.Subscribe;
import com.hwangjr.rxbus.annotation.Tag;
import com.hwangjr.rxbus.thread.EventThread;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements Animator.AnimatorListener {
    Unbinder mUnbinder;
    @BindView( R.id.toolbar )
    Toolbar mToolbar;
    @BindView( R.id.fab )
    FloatingActionButton mFab;
    @BindView( R.id.recyclerview )
    RecyclerView mAppList;
    @BindView( R.id.content_main )
    SwipeRefreshLayout mSwipeRefreshLayout;

    List<InfoModel> mApps = new ArrayList<>();
    AppshelfAdapter mAppshelfAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUnbinder = ButterKnife.bind(this);
        setSupportActionBar(mToolbar);

        //menu item点击事件监听
        mToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch ( item.getItemId() ) {
                    case R.id.test_menu1:
                        if ( !mApps.isEmpty() ) {
                            showDialog();
                        } else {
                            Toast.makeText(MainActivity.this, "暂无可删内容", Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
                return false;
            }
        });
        Timber.plant(new Timber.DebugTree());
        RxBus.get().register(this);
        initRecyclerView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.item_menu, menu);//加载menu布局
        return true;
    }

    @OnClick( R.id.fab )
    public void onClick(View view) {
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(mFab, "translationY", 0, mFab.getHeight() * 2).setDuration(200L);
        objectAnimator.setInterpolator(new AccelerateInterpolator());
        objectAnimator.addListener(this);
        objectAnimator.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WebService.stop(this);
        if ( mUnbinder != null ) {
            mUnbinder.unbind();
        }
        RxBus.get().unregister(this);
    }

    @Subscribe( tags = {@Tag( Constants.RxBusEventType.POPUP_MENU_DIALOG_SHOW_DISMISS )} )
    public void onPopupMenuDialogDismiss(Integer type) {
        if ( type == Constants.MSG_DIALOG_DISMISS ) {
            WebService.stop(this);
            ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(mFab, "translationY", mFab.getHeight() * 2, 0).setDuration(200L);
            objectAnimator.setInterpolator(new AccelerateInterpolator());
            objectAnimator.start();
        }
    }

    //显示确认对话框
    private void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("温馨提示:");
        builder.setMessage("确定全部删除吗？");
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteAll();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.show();
    }

    @Override
    public void onAnimationStart(Animator animation) {
        WebService.start(this);
        new PopupMenuDialog(this).builder().setCancelable(false)
                .setCanceledOnTouchOutside(false).show();
    }

    @Override
    public void onAnimationEnd(Animator animation) {
    }

    @Override
    public void onAnimationCancel(Animator animation) {
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }

    void initRecyclerView() {
        mAppshelfAdapter = new AppshelfAdapter();
        mAppList.setHasFixedSize(true);
        mAppList.setLayoutManager(new LinearLayoutManager(this));
        mAppList.setAdapter(mAppshelfAdapter);
        RxBus.get().post(Constants.RxBusEventType.LOAD_BOOK_LIST, 0);

        mSwipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
        mAppList.addItemDecoration(new ItemButtomDecoration(this, 10));
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                RxBus.get().post(Constants.RxBusEventType.LOAD_BOOK_LIST, 0);
            }
        });

    }

    //获取apk信息
    private void handleApk(String path, long length, List<InfoModel> list) {
        InfoModel infoModel = new InfoModel();
        String archiveFilePath = "";
        archiveFilePath = path;
        PackageManager pm = getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(archiveFilePath, 0);

        if ( info != null ) {
            ApplicationInfo appInfo = info.applicationInfo;
            appInfo.sourceDir = archiveFilePath;
            appInfo.publicSourceDir = archiveFilePath;
            String packageName = appInfo.packageName;  //得到安装包名称
            String version = info.versionName;       //得到版本信息
            Drawable icon = pm.getApplicationIcon(appInfo);
            String appName = pm.getApplicationLabel(appInfo).toString();
            if ( TextUtils.isEmpty(appName) ) {
                appName = getApplicationName(packageName);
            }
            if ( icon == null ) {
                icon = getIconFromPackageName(packageName, this); // 获得应用程序图标
            }
            infoModel.setName(appName);
            infoModel.setPackageName(packageName);
            infoModel.setPath(path);
            infoModel.setSize(getFileSize(length));
            infoModel.setVersion(version);
            infoModel.setIcon(icon);
            infoModel.setInstalled(isAvilible(this, packageName));
            if ( list == null )
                mApps.add(infoModel);
            else
                list.add(infoModel);
        }
    }

    public synchronized static Drawable getIconFromPackageName(String packageName, Context context) {
        PackageManager pm = context.getPackageManager();
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1 ) {
            try {
                PackageInfo pi = pm.getPackageInfo(packageName, 0);
                Context otherAppCtx = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY);
                int displayMetrics[] = {DisplayMetrics.DENSITY_XXXHIGH, DisplayMetrics.DENSITY_XXHIGH, DisplayMetrics.DENSITY_XHIGH, DisplayMetrics.DENSITY_HIGH, DisplayMetrics.DENSITY_TV};
                for ( int displayMetric : displayMetrics ) {
                    try {
                        Drawable d = otherAppCtx.getResources().getDrawableForDensity(pi.applicationInfo.icon, displayMetric);
                        if ( d != null ) {
                            return d;
                        }
                    } catch ( Resources.NotFoundException e ) {
                        continue;
                    }
                }
            } catch ( Exception e ) {
                // Handle Error here
            }
        }
        ApplicationInfo appInfo = null;
        try {
            appInfo = pm.getApplicationInfo(packageName, 0);
        } catch ( PackageManager.NameNotFoundException e ) {
            return null;
        }
        return appInfo.loadIcon(pm);
    }

    private String getFileSize(long length) {
        DecimalFormat df = new DecimalFormat("######0.00");
        double d1 = 3.23456;
        double d2 = 0.0;
        double d3 = 2.0;
        df.format(d1);
        df.format(d2);
        df.format(d3);
        long l = length / 1000;//KB
        if ( l < 1024 ) {
            return df.format(l) + "KB";
        } else if ( l < 1024 * 1024.f ) {
            return df.format((l / 1024.f)) + "MB";
        }
        return df.format(l / 1024.f / 1024.f) + "GB";
    }

    @Subscribe( thread = EventThread.IO, tags = {@Tag( Constants.RxBusEventType.LOAD_BOOK_LIST )} )
    public void loadAppList(Integer type) {
        Timber.d("loadAppList:" + Thread.currentThread().getName());
        List<InfoModel> listArr = new ArrayList<>();
        File dir = Constants.DIR;
        if ( dir.exists() && dir.isDirectory() ) {
            File[] fileNames = dir.listFiles();
            if ( fileNames != null ) {
                for ( File fileName : fileNames ) {
                    handleApk(fileName.getAbsolutePath(), fileName.length(), listArr);
                }
            }
        }
        runOnUiThread(() -> {
            mSwipeRefreshLayout.setRefreshing(false);
            mApps.clear();
            mApps.addAll(listArr);
            mAppshelfAdapter.notifyDataSetChanged();
        });
    }

    public String getApplicationName(String packageName) {
        PackageManager packageManager = null;
        ApplicationInfo applicationInfo = null;
        try {
            packageManager = getApplicationContext().getPackageManager();
            applicationInfo = packageManager.getApplicationInfo(packageName, 0);
        } catch ( PackageManager.NameNotFoundException e ) {
            applicationInfo = null;
        }
        if ( packageManager != null && applicationInfo != null ) {
            String applicationName =
                    ( String ) packageManager.getApplicationLabel(applicationInfo);
            return applicationName;
        }
        return packageName;
    }


    @Deprecated
    private void loadAppList() {
        Observable.create(new Observable.OnSubscribe<List<InfoModel>>() {
            @Override
            public void call(Subscriber<? super List<InfoModel>> subscriber) {
                List<InfoModel> list = new ArrayList<>();
                File dir = Constants.DIR;
                if ( dir.exists() && dir.isDirectory() ) {
                    File[] fileNames = dir.listFiles();
                    if ( fileNames != null ) {
                        for ( File fileName : fileNames ) {
                            handleApk(fileName.getAbsolutePath(), fileName.length(), list);
                        }
                    }
                }
                subscriber.onNext(list);
                subscriber.onCompleted();
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<List<InfoModel>>() {
            @Override
            public void onCompleted() {
                mAppshelfAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(Throwable e) {
                mAppshelfAdapter.notifyDataSetChanged();
            }

            @Override
            public void onNext(List<InfoModel> books) {
                mApps.clear();
                mApps.addAll(books);
            }
        });
    }

    class AppshelfAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if ( viewType == 1 ) {
                View view = inflater.inflate(R.layout.empty_view, parent, false);
                return new EmptyViewHolder(view);
            } else {
                return new MyViewHolder(LayoutInflater.from(
                        MainActivity.this).inflate(R.layout.layout_book_item, parent,
                        false));
            }
        }


        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder holder1, int position) {
            if ( holder1 instanceof MyViewHolder ) {
                MyViewHolder holder = ( MyViewHolder ) holder1;
                InfoModel infoModel = mApps.get(position);
                holder.mTvAppName.setText(infoModel.getName() + "(v" + infoModel.getVersion() + ")");

//            holder.mTvAppInstall.setText(infoModel.getName());

                holder.mTvAppSize.setText(infoModel.getSize());
                holder.mTvAppPath.setText(infoModel.getPath());
                holder.ivIcon.setImageDrawable(infoModel.getIcon());

                holder.mTvAppInstall.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        installApkFile(MainActivity.this, new File(infoModel.getPath()));
                    }
                });
                holder.mTvAppDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        delete(MainActivity.this, infoModel.getPackageName());
                    }
                });

                if ( infoModel.isInstalled() ) {
                    holder.mTvAppDelete.setVisibility(View.VISIBLE);
                } else {
                    holder.mTvAppDelete.setVisibility(View.GONE);
                }
            }
        }

        class EmptyViewHolder extends RecyclerView.ViewHolder {

            public EmptyViewHolder(View itemView) {
                super(itemView);
            }
        }

        @Override
        public int getItemCount() {
            return mApps.size() > 0 ? mApps.size() : 1;

        }

        class MyViewHolder extends RecyclerView.ViewHolder {
            TextView mTvAppName;
            TextView mTvAppSize;
            TextView mTvAppInstall;
            TextView mTvAppDelete;
            TextView mTvAppPath;
            ImageView ivIcon;

            public MyViewHolder(View view) {
                super(view);
                mTvAppName = ( TextView ) view.findViewById(R.id.tv_name);
                mTvAppSize = ( TextView ) view.findViewById(R.id.tv_size);
                mTvAppInstall = ( TextView ) view.findViewById(R.id.tv_install);
                mTvAppPath = ( TextView ) view.findViewById(R.id.tv_path);
                mTvAppDelete = ( TextView ) view.findViewById(R.id.tv_delete);
                ivIcon = ( ImageView ) view.findViewById(R.id.iv_icon);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if ( mApps.size() == 0 ) {
                return 1;
            }
            return super.getItemViewType(position);
        }
    }

    /**
     * 判断相对应的APP是否存在
     *
     * @param context
     * @param packageName(包名)(若想判断QQ，则改为com.tencent.mobileqq，若想判断微信，则改为com.tencent.mm)
     * @return
     */
    public boolean isAvilible(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();

        //获取手机系统的所有APP包名，然后进行一一比较
        List<PackageInfo> pinfo = packageManager.getInstalledPackages(0);
        for ( int i = 0; i < pinfo.size(); i++ ) {
            if ( (( PackageInfo ) pinfo.get(i)).packageName
                    .equalsIgnoreCase(packageName) )
                return true;
        }
        return false;
    }

    //安装
    public static void installApkFile(Context context, File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        //兼容7.0
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if ( context.getPackageManager().queryIntentActivities(intent, 0).size() > 0 ) {
            context.startActivity(intent);
        }
    }

    //卸载
    public static void delete(Context context, String packageName) {
        Uri uri = Uri.fromParts("package", packageName, null);
        Intent intent = new Intent(Intent.ACTION_DELETE, uri);
        context.startActivity(intent);
    }

    //删除所有文件
    private void deleteAll() {
        File dir = Constants.DIR;
        if ( dir.exists() && dir.isDirectory() ) {
            File[] fileNames = dir.listFiles();
            if ( fileNames != null ) {
                for ( File fileName : fileNames ) {
                    fileName.delete();
                }
            }
        }
        RxBus.get().post(Constants.RxBusEventType.LOAD_BOOK_LIST, 0);
    }
}
