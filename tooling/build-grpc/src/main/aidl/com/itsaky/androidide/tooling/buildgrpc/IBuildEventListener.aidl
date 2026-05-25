package com.itsaky.androidide.tooling.buildgrpc;

interface IBuildEventListener {
  void onBuildEvent(String buildEventJson);
  void onBuildStreamClosed(String requestId, boolean successful);
}
