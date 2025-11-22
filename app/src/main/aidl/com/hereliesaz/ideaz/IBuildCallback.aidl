// IBuildCallback.aidl
package com.hereliesaz.ideaz;

interface IBuildCallback {
    oneway void onLog(String message);
    void onSuccess(String apkPath);
    void onFailure(String message);
}
