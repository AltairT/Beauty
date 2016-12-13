package com.dante.girls.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.blankj.utilcode.utils.ConvertUtils;
import com.blankj.utilcode.utils.FileUtils;
import com.dante.girls.R;
import com.dante.girls.base.App;
import com.dante.girls.utils.AppUtils;
import com.dante.girls.utils.SPUtil;
import com.dante.girls.utils.UI;

import java.io.File;
import java.util.List;


/**
 * the view in setting activity.
 */
public class SettingFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {
    public static final String CLEAR_CACHE = "clear_cache";
    public static final String FEED_BACK = "feedback";
    public static final String APP_VERSION = "check_version";
    public static final String ORIGINAL_SPLASH = "original_splash";
    public static final String SECRET_MODE = "secret_mode";
    public static final String ABOUT = "about";
    private static final long DURATION = 300;

    private Preference clearCache;
    private Preference feedback;
    private Preference version;
    private Preference splash;
    private Preference about;

    private View rootView;
    private long startTime;
    private boolean first = true;
    private int secretIndex;

    public static boolean isIntentSafe(Intent intent) {
        PackageManager packageManager = App.context.getPackageManager();
        List activities = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return activities.size() > 0;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        clearCache = findPreference(CLEAR_CACHE);
        feedback = findPreference(FEED_BACK);
        about = findPreference(ABOUT);
        version = findPreference(APP_VERSION);
        splash = findPreference(ORIGINAL_SPLASH);
        refreshCache();
        splash.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                secretStepOne();
                return true;
            }
        });
        version.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Log.i("test", secretIndex + ">>>>");
                secretStepTwo();
                return true;
            }
        });
        about.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(getActivity().getApplicationContext(), AboutActivity.class));
                return true;
            }
        });
        clearCache.setOnPreferenceClickListener(this);
        feedback.setOnPreferenceClickListener(this);
    }

    private void refreshCache() {
        String cache = String.format(getString(R.string.set_current_cache) + " %s", getDataSize());
        clearCache.setSummary(cache);
    }

    private void secretStepTwo() {
        if (System.currentTimeMillis() - startTime < DURATION * (secretIndex + 1)) {
            if (secretIndex > 2) {
                Log.i("test", "splash " + secretIndex);
                secretIndex++;
            }
        }
        if (secretIndex == 6) {
            if (SPUtil.getBoolean(SECRET_MODE)) {
                SPUtil.save(SECRET_MODE, false);
                secretIndex = 0;
                UI.showSnack(rootView, R.string.secret_mode_closed);
            } else {
                SPUtil.save(SECRET_MODE, true);
                secretIndex = 0;
                UI.showSnackLong(rootView, R.string.secret_mode_opened);
            }
            secretIndex++;
        }
    }

    private void secretStepOne() {
        if (first) {
            startTime = System.currentTimeMillis();
            first = false;
        }
        if (System.currentTimeMillis() - startTime < DURATION * (secretIndex + 1)) {
            if (secretIndex < 3) {
                secretIndex++;
            }

        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (null == rootView) {
            rootView = super.onCreateView(inflater, container, savedInstanceState);
        }
        return rootView;

    }

    private String getDataSize() {
        File file = getActivity().getApplicationContext().getCacheDir();
        long size = AppUtils.folderSize(file);
        if (size==0){
            return getString(R.string.empty);
        }

        return ConvertUtils.byte2FitSize(size);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        switch (key) {
            case CLEAR_CACHE:
                FileUtils.deleteDir(App.context.getCacheDir());
                refreshCache();
                Snackbar.make(rootView, R.string.clear_finished, Snackbar.LENGTH_LONG)
                        .setAction(R.string.deep_clean, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                AppUtils.openAppInfo(getActivity());
                            }
                        }).show();
                break;
            case FEED_BACK:
                sendEmailFeedback();
                break;
        }
        return true;
    }

    private void sendEmailFeedback() {
        Intent email = new Intent(Intent.ACTION_SENDTO);
        if (isIntentSafe(email)) {
            email.setData(Uri.parse("mailto:danteandroi@gmail.com"));
            email.putExtra(Intent.EXTRA_SUBJECT, "'Girls' Feedback");
            email.putExtra(Intent.EXTRA_TEXT, "Hi，");
            startActivity(email);
        } else {
            UI.showSnack(rootView, R.string.email_not_install);
        }
    }

}
