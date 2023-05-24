package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.utilities.HttpUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This preference fragment handles all Gos related shared preferences.
 * <p>
 * If using oidc flow, changing the base url will change the oidc url to match. If the user
 * explicitly changes the oidc url, then the automatic change will not occur anymore.
 * <p>
 * This will call the Gos Authentication activity to handle authentication s.a OIDC or basic.
 * Auth token is saved in the preferences, or set to null when logging out.
 */
public class GosPreferencesFragment extends PreferenceFragmentCompat {

    private static final String DIALOG_FRAGMENT_TAG = "com.tracker.fieldbook.preferences.GOS_DIALOG_FRAGMENT";

    private Context context;
    private PreferenceManager prefMgr;
    private Preference gosAuthButton;

    private BetterEditTextPreference gosBaseUrl;

    private BetterEditTextPreference gosPort;

    private BetterEditTextPreference gosUsername;

    private BetterEditTextPreference gosPassword;

    private ExecutorService executorService;

    @Override
    public void onAttach(@NonNull Context context) {
        System.out.println("onAttach");
        super.onAttach(context);
        GosPreferencesFragment.this.context = context;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        System.out.println("onCreatePreferences");
        executorService = Executors.newSingleThreadExecutor();
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(GeneralKeys.SHARED_PREF_FILE_NAME);
        setPreferencesFromResource(R.xml.preferences_gos, rootKey);
        gosAuthButton = findPreference("authorizeGos");
        gosBaseUrl = findPreference(GeneralKeys.GOS_BASE_URL);
        gosPort = findPreference(GeneralKeys.GOS_PORT);
        gosUsername = findPreference(GeneralKeys.GOS_USERNAME);
        gosPassword = findPreference(GeneralKeys.GOS_PASSWORD);
        if (gosBaseUrl != null) {
            gosBaseUrl.setOnBindEditTextListener(editText -> {
                editText.setHint(getString(R.string.gos_input_base_url));
            });
            gosBaseUrl.setOnPreferenceChangeListener((preference, newValue) -> {
                String value = (String) newValue;
                if (value.contains(" ")) {
                    Toast.makeText(context, getString(R.string.gos_contain_space), Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            });
            gosBaseUrl.setSummaryProvider(preference -> {
                String value = (String) preference.getSharedPreferences().getString(preference.getKey(), "");
                if (value.length() > 0) {
                    return value;
                } else {
                    return getString(R.string.gos_input_base_url);
                }
            });
        }
        if (gosPort != null) {
            gosPort.setOnBindEditTextListener(editText -> {
                editText.setHint(getString(R.string.gos_input_port));
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            });
            gosPort.setOnPreferenceChangeListener((preference, newValue) -> {
                String value = (String) newValue;
                if (value.contains(" ")) {
                    Toast.makeText(context, getString(R.string.gos_contain_space), Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            });
            gosPort.setSummaryProvider(preference -> {
                String value = (String) preference.getSharedPreferences().getString(preference.getKey(), "");
                if (value.length() > 0) {
                    return value;
                } else {
                    return getString(R.string.gos_input_port);
                }
            });
        }
        if (gosUsername != null) {
            gosUsername.setOnBindEditTextListener(editText -> {
                editText.setHint(getString(R.string.gos_input_username));
            });
            gosUsername.setOnPreferenceChangeListener((preference, newValue) -> {
                String value = (String) newValue;
                if (value.contains(" ")) {
                    Toast.makeText(context, getString(R.string.gos_contain_space), Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            });
            gosUsername.setSummaryProvider(preference -> {
                String value = (String) preference.getSharedPreferences().getString(preference.getKey(), "");
                if (value.length() > 0) {
                    return value;
                } else {
                    return getString(R.string.gos_input_username);
                }
            });
        }
        if (gosPassword != null) {
            gosPassword.setOnBindEditTextListener(editText -> {
                editText.setHint(getString(R.string.gos_input_password));
                editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            });
            //在保存时检查是否有空格，有则提示用户并且保存失败
            gosPassword.setOnPreferenceChangeListener((preference, newValue) -> {
                String value = (String) newValue;
                if (value.contains(" ")) {
                    Toast.makeText(context, getString(R.string.gos_contain_space), Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            });
            //summary显示密文
            gosPassword.setSummaryProvider(preference -> {
                String value = (String) preference.getSharedPreferences().getString(preference.getKey(), "");
                if (value.length() > 0) {
                    //密文长度为明文长度
                    StringBuilder result = new StringBuilder();
                    for (int i = 0; i < value.length(); i++) {
                        result.append("*");
                    }
                    return result;
                } else {
                    return getString(R.string.gos_input_password);
                }
            });
        }
        if (gosAuthButton != null) {
            gosAuthButton.setOnPreferenceClickListener(preference -> {
                testGos();
                return true;
            });
        }
    }

    public void testGos() {
        System.out.println("testGos");
        int result = 0;
        String gosUrl = prefMgr.getSharedPreferences().getString(GeneralKeys.GOS_BASE_URL, null);
        String gosPort = prefMgr.getSharedPreferences().getString(GeneralKeys.GOS_PORT, null);
        String username = prefMgr.getSharedPreferences().getString(GeneralKeys.GOS_USERNAME, null);
        String password = prefMgr.getSharedPreferences().getString(GeneralKeys.GOS_PASSWORD, null);
        if (gosUrl.length() == 0) {
            result = -1;
        }
        if (gosPort.length() == 0) {
            result = -2;
        }
        if (username.length() == 0) {
            result = -3;
        }
        if (password.length() == 0) {
            result = -4;
        }
        if(result<0){
            toastUi(result);
            return;
        }
        password = HttpUtil.md5Password(password);
        String finalPassword = password;
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    // 请求头

                    HttpUtil httpUtil = new HttpUtil(context,gosUrl + ":" + gosPort + "/web/login","application/x-www-form-urlencoded");
                    // post参数
                    httpUtil.addFormField("username", username);
                    //md5加密
                    httpUtil.addFormField("password", finalPassword);
                    httpUtil.addFormField("captchaVerification", "sLkNRVNRCVNyxglc3qusQwdREkmuGy3JZJ51q69cQKZyKSyRBwsBpTQpCx5ZrIkS");
                    // 返回信息
                    JsonObject jsonObject=httpUtil.finish();
                    if(jsonObject!=null) {
                        toastUi(0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

    }

    public void toastUi(int result){
        Handler uiThread = new Handler(Looper.getMainLooper());
        uiThread.post(new Runnable() {
            @Override
            public void run() {
                switch (result){
                    case 0:
                        Toast.makeText(context,  getString(R.string.gos_authorize_200), Toast.LENGTH_SHORT).show();
                        break;
                    case -1:
                        Toast.makeText(context, getString(R.string.gos_input_base_url), Toast.LENGTH_SHORT).show();
                        break;
                    case -2:
                        Toast.makeText(context, getString(R.string.gos_input_port), Toast.LENGTH_SHORT).show();
                        break;
                    case -3:
                        Toast.makeText(context, getString(R.string.gos_input_username), Toast.LENGTH_SHORT).show();
                        break;
                    case -4:
                        Toast.makeText(context, getString(R.string.gos_input_password), Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        });
    }
    @Override
    public void onResume() {
        System.out.println("onResume");
        super.onResume();
    }

    @Override
    public void onPause() {
        System.out.println("onPause");
        if(!executorService.isShutdown()){
            executorService.shutdownNow();
        }
        super.onPause();
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        System.out.println("onDisplayPreferenceDialog");
        super.onDisplayPreferenceDialog(preference);
    }


}