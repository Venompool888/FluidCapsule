# 遗留问题

| 编号 | 状态 | 现象 / 影响 | 下一步 | 证据 |
| --- | --- | --- | --- | --- |
| OI-001 | closed | 2026-11-23 已验收：`./gradlew assembleDebug testDebugUnitTest --offline` 构建成功，136 项测试通过。原记录：完整 Gradle 构建/测试尚未验收；沙箱拒绝 ~/.gradle wrapper 锁写入，临时可写目录下仍拒绝 Gradle 锁协调 socket，均发生在编译前。独立 Kotlin 编译与 JUnitCore 136 项通过不能替代 APK 构建。 | 在允许本地 socket 与 Gradle 缓存写入的环境执行 ./gradlew assembleDebug testDebugUnitTest -q --offline，核对新报告与 APK。 | [2026-11-18.md](2026-11-18.md) |
| OI-002 | open | TODO：AndroidKeyStore 实际密钥生成/令牌恢复、真机设置页与真实服务器接收链路尚未验收；JVM 测试仅覆盖纯协议、URL 和胶囊决策。 | 同账号连接真实 Capsule SMS 和 FluidCapsule，选接收设备，验证 OTP/普通短信、忽略 send_sms、解密失败后继续、断网重连、断开及启动恢复；检查亮/暗色和系统栏。 | [2026-11-18.md](2026-11-18.md) |
