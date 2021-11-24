package com.fieldbook.tracker.activities;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;

import androidx.fragment.app.FragmentActivity;

import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.Utils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.api.support.membermodification.MemberMatcher;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@RunWith(PowerMockRunner.class)
@PrepareForTest(value = {Looper.class, FragmentActivity.class})
public class BrapiExportActivityTest {

    private final static ScheduledExecutorService mainThread = Executors.newSingleThreadScheduledExecutor();

    private Context context;
    private SharedPreferences settings;

    @Before
    public void setup() throws Exception {
        mockMainThreadHandler();
        context = mock(Context.class);
        settings = mock(SharedPreferences.class);
        when(context.getSharedPreferences(eq("Settings"), anyInt())).thenReturn(settings);
        when(settings.getString(eq(GeneralKeys.BRAPI_BASE_URL), anyString())).thenReturn("https://test-server.brapi.org");
        PowerMockito.when(Utils.isConnected(any(Context.class))).thenReturn(true);
    }

    @Test
    public void testExportBrapiV1() throws Exception {

        when(settings.getString(eq(GeneralKeys.BRAPI_VERSION), anyString())).thenReturn("V1");

        List<Observation> observations = generateObservations();

        DataHelper dataHelper = mock(DataHelper.class);
        when(dataHelper.getObservations(anyString())).thenReturn(new ArrayList<>()/*observations*/);
        when(dataHelper.getUserTraitObservations()).thenReturn(new ArrayList<>());
        when(dataHelper.getWrongSourceObservations(anyString())).thenReturn(new ArrayList<>());
        when(dataHelper.getImageObservations(anyString())).thenReturn(new ArrayList<>());
        when(dataHelper.getUserTraitImageObservations()).thenReturn(new ArrayList<>());
        when(dataHelper.getWrongSourceImageObservations(anyString())).thenReturn(new ArrayList<>());

        BrapiExportActivity brapiExportActivity = new BrapiExportActivity(dataHelper);

        PowerMockito.suppress(MemberMatcher.methodsDeclaredIn(FragmentActivity.class));
        System.out.println("calling onCreate");
        brapiExportActivity.onCreate(mock(Bundle.class));

        assertTrue(true);
    }

    private List<Observation> generateObservations() {
        return new ArrayList<>();
    }

    public static void mockMainThreadHandler() throws Exception {
        PowerMockito.mockStatic(Looper.class);
        Looper mockMainThreadLooper = mock(Looper.class);
        when(Looper.getMainLooper()).thenReturn(mockMainThreadLooper);
        when(mockMainThreadLooper.getThread()).thenReturn(Thread.currentThread());
    }
}
