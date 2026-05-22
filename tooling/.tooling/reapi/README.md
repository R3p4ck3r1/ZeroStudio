# REAPI vendoring workspace

This directory (under tooling/.tooling) is the local source mirror location for bazelbuild/remote-apis files used by
Iteration2 integrated transport stack (AIDL + gRPC + REAPI).

## Current status

- Network policy in this environment currently blocks cloning from GitHub (`403 CONNECT tunnel`).
- To keep Iteration2 moving, we bootstrap required proto structure and placeholders here.
- When network access is available, run:

```bash
git clone https://github.com/bazelbuild/remote-apis /tmp/remote-apis
rsync -a /tmp/remote-apis/ tooling/.tooling/reapi/remote-apis/
```

## Required upstream paths

- `build/bazel/remote/execution/v2/remote_execution.proto`
- `build/bazel/semver/semver.proto`
- `google/bytestream/bytestream.proto`
- `google/longrunning/operations.proto`
- `google/rpc/status.proto` (transitive)

These placeholders are intentionally non-production and exist only to unblock repo structure
and follow-up code integration.


## Java + Proto requirement

Iteration2 requires both upstream proto definitions and Java-oriented source/config conversion inputs from remote-apis.
Use `tooling/.tooling/reapi/sync-remote-apis.sh` to mirror the full repository locally, then consume the slices listed in `required-slices/paths.txt`.
