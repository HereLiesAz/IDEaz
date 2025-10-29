// IBuildCallback.aidl
package com.hereliesaz.peridiumide;

interface IBuildCallback {
    void onSuccess(String apkPath);
    void onFailure(String message);
}
