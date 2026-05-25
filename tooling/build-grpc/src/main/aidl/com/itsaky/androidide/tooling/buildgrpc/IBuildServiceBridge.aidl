package com.itsaky.androidide.tooling.buildgrpc;

interface IBuildServiceBridge {
  String initialize(String initializeBuildRequestJson);
  String queryTargets(String queryBuildTargetsRequestJson);
  String executeBuild(String executeBuildRequestJson);
  String getBuildResult(String executeBuildRequestJson);
}
