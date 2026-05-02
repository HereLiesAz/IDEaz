// IBuildCallback.aidl
package com.hereliesaz.ideaz;

interface IBuildCallback {
    oneway void onLog(String message);
    oneway void onSuccess(String apkPath);
    oneway void onFailure(String message);
}
