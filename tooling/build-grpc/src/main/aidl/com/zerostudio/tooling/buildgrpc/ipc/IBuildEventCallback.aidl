package com.zerostudio.tooling.buildgrpc.ipc;

interface IBuildEventCallback {
    oneway void onBuildEvent(String eventJson);
    oneway void onBuildFinished(String buildId, boolean success, String summary);
}
