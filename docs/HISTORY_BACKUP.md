# 通知历史备份 / Notification history backup

在“历史”页面，自动保留时间设置下方可以选择“导出历史”或“导入历史”。

## 导出

点击“导出历史”，在 Android 系统文件选择器中选择保存位置。默认文件名为 `FluidCapsule-history-yyyyMMdd-HHmmss.json`，时间使用手机本地时间。

文件包含数据库中全部现存记录，包括历史页面最近 250 条之外的记录。它保存当前已记录的完整文字、来源应用、发布时间、捕获时间和处理结果；已删除或过期清理的记录不能恢复。一次活跃通知的更新仍然替换原来的历史内容，备份不包含每次更新的旧版本。

文件是带缩进的 UTF-8 JSON。它包含未经脱敏的通知正文，可能包含私人消息和验证码，请选择适当的保存位置。应用不会自动上传备份；系统文件选择器中的云盘位置由用户选择的文件提供器管理。

## 导入

点击“导入历史”，选择由此功能导出的 JSON 文件。应用先完整校验文件，再显示条数、当前保留期限和过期记录数量，确认之前不会写入数据库。

- 没有过期记录时：选择“导入”或“取消”。
- 有过期记录时：“仅导入期限内记录”跳过旧记录；“改为永久并导入全部”恢复旧记录并将本机保留时间改为永久；也可取消。
- 如果确认前保留期限发生变化，应用重新显示确认内容。

导入与已有历史合并，标识或内容指纹重复的记录跳过，已有正文不被覆盖。同一个备份可以重复导入。导入保留原有时间，重新分配本机记录编号，并将记录视为不再活跃的存档；它不会发布胶囊或开启通知记录。完成后显示新增、重复跳过和过期跳过的数量。

格式不符、版本不支持、字段错误或文件损坏时拒绝整份备份。写入失败会回滚整批导入。选择文件或确认弹窗时取消，不改变数据或保留设置。

单条记录必须能完整放入当前设备的 Android 数据库读取窗口；超出时拒绝整份文件，避免恢复后历史页无法打开。解析器还限制单个 JSON 字符串的编码长度为 16 Mi 个字符，防止损坏文件耗尽内存。这些限制允许超过 64 KiB 的正常通知正文，不截断内容。

“改为永久”的设置在通知事务提交前同步写入并检查结果，写入失败时回滚导入和恢复原期限。若系统在两次提交之间强制结束进程，通知事务只会整批提交或回滚，永久期限可能已经保存；重启后可以重新导入同一备份，重复记录仍会跳过。

## 文件协议 v1

```json
{
  "format": "fluidcapsule.notification-history",
  "schemaVersion": 1,
  "appVersion": "1.3.0",
  "exportedAtMillis": 1790776800000,
  "entries": [
    {
      "eventIdentity": "synthetic-event-1",
      "fingerprint": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "sourcePackage": "test.only.example",
      "sourceLabel": "合成应用",
      "title": "TEST ONLY",
      "primaryText": "TEST ONLY 正文",
      "combinedText": "TEST ONLY\nTEST ONLY 正文",
      "postedAtMillis": 1790776700000,
      "capturedAtMillis": 1790776701000,
      "decision": "SKIPPED",
      "decisionDetail": "合成处理结果"
    }
  ]
}
```

所有示例内容均为合成数据。时间字段是非负的 Unix 毫秒整数。所有展示的字段均为必需；文字字段必须是字符串，空标题或正文允许为空字符串。`eventIdentity` 是非空的不透明标识，兼容旧数据库的指纹标识；`fingerprint` 为 64 位小写十六进制 SHA-256 字符串。未知附加字段被忽略，重复 JSON 字段、错误类型及尾部附加内容被拒绝。

不导出 `id`、系统通知 key 或活跃状态，不复制通知图片、图标、按钮、应用规则或设置。v1 只接受此备份协议，不接受 [ADB CLI](CLI.md) 的分页 JSON 响应。现有 ADB 接口保持原样。

数据库使用 WAL，文件传输期间历史列表读取可以继续；保留期限修改和删除操作会暂时禁用，受保护的 ADB 冲突操作会返回“正在处理”，不会等待长事务阻塞界面。

## English

Use **Export history** and **Import history** on the History page. Export creates a complete, readable JSON snapshot using the system document picker. Import validates the whole file, asks for confirmation, merges without overwriting existing rows, and skips matching event identities or fingerprints. Imported rows retain their original text and timestamps and are inactive archives, so they never trigger capsules or enable recording.

If a backup contains expired rows, choose to import only records within the current retention period, or to keep everything and switch retention to forever. Cancelling does not modify history or settings. Backup files contain sensitive, unredacted notification text; only choose a destination you intend to use.
