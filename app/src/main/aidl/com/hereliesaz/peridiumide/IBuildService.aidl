// IBuildService.aidl
package com.hereliesaz.peridiumide;

import com.hereliesaz.peridiumide.IBuildCallback;

interface IBuildService {
    void startBuild(String projectPath, IBuildCallback callback);
}
