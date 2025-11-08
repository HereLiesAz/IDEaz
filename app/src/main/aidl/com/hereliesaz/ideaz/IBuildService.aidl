// IBuildService.aidl
package com.hereliesaz.ideaz;

import com.hereliesaz.ideaz.IBuildCallback;

interface IBuildService {
    void startBuild(String projectPath, IBuildCallback callback);
    void updateNotification(String message);
}
