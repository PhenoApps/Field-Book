package com.fieldbook.tracker.utilities;

import java.io.File;

public interface VersionCheckerListener {
    void onApkDownloaded(File apkFile);
}
