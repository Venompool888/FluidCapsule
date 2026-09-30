# 通知历史 JSON 导出与导入

用户已确认：全量 JSON 备份；导入合并且跳过重复；超过保留期限时让用户选择。

## Task 1: 备份格式和校验

UTF-8、缩进 JSON，顶层 format=fluidcapsule.notification-history、schemaVersion=1、appVersion、exportedAtMillis、entries。
每条保存 eventIdentity、fingerprint、sourcePackage、sourceLabel、title、primaryText、combinedText、postedAtMillis、capturedAtMillis、decision、decisionDetail。
不备份本机 id、notification_key、active。流式读取，校验完整文件后才允许导入；拒绝错误格式、版本、类型、缺失字段、损坏和尾部多余内容。

## Task 2: SQLite 导出快照与事务合并

读取事务生成临时备份，覆盖全部记录；文件系统复制在事务外进行。新导入记录 inactive、key=null，重新分配 id，保留标识、时间和文字。
eventIdentity 或 fingerprint 重复均跳过，不覆盖本机。批量写入用单事务，失败回滚。自动清理与导入协调，永久选项失败恢复原设置。

## Task 3: 文件选择与界面状态

历史页保留期限设置之后、清空按钮之前放导出/导入按钮，复用样式。系统保存/打开文件选择器，默认名称 FluidCapsule-history-yyyyMMdd-HHmmss.json。
后台读写，防重复操作，页面重建保留操作和结果。导入校验后显示总条数、当前期限、过期条数；有过期记录提供期限内/改为永久并全部/取消，无过期提供导入/取消。
确认时策略变化重新确认。取消不修改设置或数据。完成显示统计，刷新列表、条数和期限。导入不发布胶囊、不启用记录。

## Task 4: 验证与文档

合成数据验证 Unicode/换行/引号、64KiB 以上正文、250 条以上完整快照、空备份、重复与重叠合并、旧记录、全部弹窗路径、失败回滚、文件访问失败、旋转和并发更新。
运行 JVM 全套、Android 新增测试、lint 和构建。更新使用说明及隐私说明。ADB 分页协议保持兼容。不增加 CSV、HTML、筛选或通知内容版本历史。
