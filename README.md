# DiskFlow

免 Root 的 Android 外置存储助手：真实检测 SD 卡与 USB/OTG 设备，配置 FAT32 / exFAT / NTFS / ext4 格式化方案，并引导至系统工具执行。

## 能力边界

Android 普通应用没有通用块设备格式化权限。DiskFlow 不伪造格式化进度，也不宣称系统执行结果：

- 应用内负责：设备检测、容量统计、格式方案配置、兼容性建议、系统入口跳转
- 系统负责：真正执行格式化，可选项与结果由手机厂商的存储设置决定

## 功能

- 存储设备总览：内置存储、SD 卡、USB/OTG，挂载状态、容量、文件系统识别
- USB 硬件信息：产品名、厂商、VID/PID、设备类别，按需申请 USB 权限
- 格式化方案向导：目标设备 → 使用场景 → 文件系统 → 卷标与分配单元
- 格式知识支持：FAT32 单文件 4 GiB − 1 限制、卷标规则、分配单元建议
- 大文件限制检查：输入文件大小即时判断目标格式是否可容纳
- SAF 目录授权：通过 Storage Access Framework 持久访问用户选择的目录
- 本地操作记录：仅记录真实动作（建方案、跳系统设置、用户确认、取消），最多 100 条
- 深色科技风 Compose UI，容量条与列表动画

应用不申请网络权限，不包含广告或统计上传，也不申请全盘存储访问权限。

## 环境

- JDK 17
- Android SDK Platform 35、Build-Tools 35.0.0
- Min SDK 26（Android 8.0），Target SDK 35

## 构建

```bash
# 在项目根目录创建 local.properties，写入 SDK 路径
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew testDebugUnitTest lintDebug assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug 构建使用调试签名，可直接安装测试；正式分发请自行配置发布签名。

## 测试

```bash
./gradlew testDebugUnitTest
```

包含 15 项单元测试，覆盖：

- 四种文件系统的场景推荐与老设备兼容分支
- FAT32 4 GiB − 1 字节边界
- 卷标字符、长度与 ext4 的 UTF-8 字节限制
- 分配单元选项与容量计算
- 无外置设备时禁止生成方案
- 不产生虚假成功状态，仅记录真实事件

## 目录结构

```
app/src/main/java/com/diskflow/app/
├── MainActivity.kt
├── data/
│   ├── local/        # DataStore 草稿、授权 URI、事件记录
│   └── storage/      # StorageManager / UsbManager / SAF 系统集成
├── domain/
│   ├── FormatPolicy.kt
│   └── model/
└── ui/
    ├── DiskFlowViewModel.kt
    ├── components/
    ├── navigation/
    ├── screens/
    └── theme/
```

## 已知限制

- 文件系统类型仅在被系统公开或可从挂载信息可靠识别时显示，否则标注为“由 Android 系统管理”
- 部分手机不支持 U 盘格式化，或仅支持 SD 卡格式化
- 未在实体 OTG / SD 卡设备上完成真机验收
- 不提供 Root、Shizuku、JNI 底层调用、安全抹除、SMART 健康度或数据恢复
