// IBuildService.aidl
package com.hereliesaz.ideaz;

import com.hereliesaz.ideaz.IBuildCallback;

interface IBuildService {
    oneway void startBuild(String projectPath, IBuildCallback callback);
    oneway void downloadDependencies(String projectPath, IBuildCallback callback);
    oneway void updateNotification(String message);
    oneway void cancelBuild();
}
