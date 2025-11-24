// IBuildCallback.aidl
package com.hereliesaz.ideaz;

interface IBuildCallback {
    void onSuccess(String apkPath);
    void onFailure(String message);
    void onLog(String message);
}
