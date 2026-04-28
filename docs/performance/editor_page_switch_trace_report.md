# Editor Page-Switch 性能定量报告（动画帧时间 + Systrace/Perfetto）

> 场景：`Build status` 页面中先将 `bottom_sheet` 完全展开，再点击 `page_switch_symbol_tab` 切换到 `AdvancedSymbolInputView`。

## 1) 目标与指标

- **目标 1：** 切换到 `Symbol input` 后，编辑区缩放必须恢复到 `1.0x`，不能停留在缩小态。
- **目标 2：** 滑动与切换期间帧时间稳定，无明显抖动。
- **核心指标：**
  - P50 / P90 / P95 / P99 帧时长（ms）
  - Jank 帧占比（>16.6ms）
  - Slow 帧占比（>33.3ms）
  - Frozen 帧占比（>700ms）

## 2) 采集前置

- 设备连接：`adb devices`
- 清理历史数据：
  - `adb shell dumpsys gfxinfo com.itsaky.androidide reset`
- 开启系统级 trace（Perfetto）建议同时采：
  - SurfaceFlinger / gfx / view / wm / am / sched / freq / binder_driver

## 3) 操作脚本（手工复现实验步骤）

每轮固定执行：

1. 打开编辑器页；
2. 切到 `Build status`；
3. 将 `bottom_sheet` 完全上拉展开；
4. 点击 `Symbol input`；
5. 观察编辑区是否恢复 1.0x；
6. 重复 20 次。

## 4) 命令清单（建议）

### 4.1 帧统计（gfxinfo）

```bash
adb shell dumpsys gfxinfo com.itsaky.androidide framestats > /sdcard/zerostudio_framestats.txt
adb pull /sdcard/zerostudio_framestats.txt ./artifacts/zerostudio_framestats.txt
```

### 4.2 Perfetto trace（替代旧 Systrace）

```bash
adb shell perfetto -o /data/misc/perfetto-traces/zerostudio_page_switch.perfetto-trace -t 15s \
  sched freq idle am wm gfx view binder_driver hal dalvik
adb pull /data/misc/perfetto-traces/zerostudio_page_switch.perfetto-trace ./artifacts/
```

> 说明：Android 10+ 优先使用 Perfetto；若团队仍称 “Systrace”，建议统一为 “Perfetto/Systrace”。

## 5) 本次修复关注点（与数据解读对应）

- 切换到 `Symbol input` 时强制恢复编辑区缩放（scaleX/scaleY -> 1f）；
- 在 `BottomSheet STATE_COLLAPSED` 兜底恢复编辑区缩放；
- 切页过程中避免冗余 UI 属性写入与重复状态机调用。

## 6) 对比结果模板（填写实际采集值）

| 指标 | 修复前 | 修复后 | 变化 |
|---|---:|---:|---:|
| P50 帧时长 (ms) | 待测 | 待测 | 待测 |
| P95 帧时长 (ms) | 待测 | 待测 | 待测 |
| P99 帧时长 (ms) | 待测 | 待测 | 待测 |
| Jank 占比 (>16.6ms) | 待测 | 待测 | 待测 |
| Slow 占比 (>33.3ms) | 待测 | 待测 | 待测 |
| Frozen 占比 (>700ms) | 待测 | 待测 | 待测 |
| “编辑区未恢复 1.0x”复现率 | 待测 | 待测 | 待测 |

## 7) 结论模板

- 功能正确性：`[通过/未通过]`
- 流畅度改进：`[显著/轻微/无变化]`
- 建议下一步：
  - 若 P95 仍偏高，继续减少 `onSlide` 中非必要工作；
  - 补充分设备（60Hz / 90Hz / 120Hz）对比；
  - 将关键交互加入回归压测清单。
