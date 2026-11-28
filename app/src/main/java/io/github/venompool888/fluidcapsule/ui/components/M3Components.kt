package io.github.venompool888.fluidcapsule.ui.components

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.venompool888.fluidcapsule.rules.RulePack
import io.github.venompool888.fluidcapsule.rules.RuleRuntime
import io.github.venompool888.fluidcapsule.settings.AppRule
import io.github.venompool888.fluidcapsule.ui.state.RuleSandboxEvaluator
import io.github.venompool888.fluidcapsule.ui.state.SandboxResult
import io.github.venompool888.fluidcapsule.ui.theme.FluidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StatusTone {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    SUCCESS,
    WARNING,
    ERROR,
    NEUTRAL,
}

data class RuleChipSpec(
    val label: String,
    val highlighted: Boolean = false,
    val warning: Boolean = false,
)

object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(96)

    fun get(packageName: String): ImageBitmap? = synchronized(cache) { cache.get(packageName) }

    fun put(packageName: String, bitmap: ImageBitmap) {
        synchronized(cache) { cache.put(packageName, bitmap) }
    }
}

object RelativeTimeFormatter {
    fun format(
        timestampMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        emptyLabel: String = "尚无记录",
    ): String {
        if (timestampMillis <= 0L) return emptyLabel
        val elapsedMillis = (nowMillis - timestampMillis).coerceAtLeast(0L)
        val minutes = elapsedMillis / 60_000L
        return when {
            minutes == 0L -> "刚刚"
            minutes < 60L -> "$minutes 分钟前"
            minutes < 1_440L -> "${minutes / 60L} 小时前"
            minutes < 7 * 1_440L -> "${minutes / 1_440L} 天前"
            else -> SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(timestampMillis))
        }
    }
}

fun summarizeRuleChips(rule: AppRule, otpOnly: Boolean): List<RuleChipSpec> {
    val chips = mutableListOf<RuleChipSpec>()
    chips += RuleChipSpec(
        label = if (rule.ttlMinutes == 0) "跟随全局时长" else "胶囊 ${rule.ttlMinutes} 分钟",
        highlighted = rule.ttlMinutes > 0,
    )
    val priorityLabel = when (rule.priority.coerceIn(-1, 1)) {
        -1 -> "低优先级"
        1 -> "高优先级"
        else -> "普通优先级"
    }
    chips += RuleChipSpec(
        label = priorityLabel,
        highlighted = rule.priority != 0,
    )
    if (otpOnly) {
        chips += RuleChipSpec(label = "仅识别验证码", highlighted = true)
    }
    when (rule.contentMode) {
        AppRule.CONTENT_SHOW -> chips += RuleChipSpec(label = "始终显示正文", highlighted = true)
        AppRule.CONTENT_HIDE -> chips += RuleChipSpec(label = "始终隐藏正文", highlighted = true)
    }
    if (rule.maxBodyChars != AppRule.DEFAULT_MAX_BODY_CHARS) {
        chips += RuleChipSpec(label = "上限 ${rule.maxBodyChars} 字", highlighted = true)
    }
    val includeCount = AppRule.keywordList(rule.includeKeywords).size
    val excludeCount = AppRule.keywordList(rule.excludeKeywords).size
    if (includeCount > 0 || excludeCount > 0) {
        val label = when {
            includeCount > 0 && excludeCount > 0 -> "关键词 含$includeCount/排$excludeCount"
            includeCount > 0 -> "包含关键词 $includeCount"
            else -> "排除关键词 $excludeCount"
        }
        chips += RuleChipSpec(label = label, highlighted = true)
    }
    if (!rule.recordHistory) {
        chips += RuleChipSpec(label = "不记历史", warning = true)
    }
    return chips
}

@Composable
fun SectionLabel(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun TonalSectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: Shape = RoundedCornerShape(22.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun AppIconBadge(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    cornerRadius: Dp = 14.dp,
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(AppIconCache.get(packageName)) }

    LaunchedEffect(packageName) {
        if (bitmap == null && packageName.isNotBlank()) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    val width = drawable.intrinsicWidth.takeIf { it in 1..144 } ?: 96
                    val height = drawable.intrinsicHeight.takeIf { it in 1..144 } ?: 96
                    drawable.toBitmap(width = width, height = height).asImageBitmap()
                }.getOrNull()
            }
            if (loaded != null) {
                AppIconCache.put(packageName, loaded)
                bitmap = loaded
            }
        }
    }

    val shape = RoundedCornerShape(cornerRadius)
    val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString()
        ?: packageName.trim().firstOrNull()?.uppercaseChar()?.toString()
        ?: "?"
    val accessibleName = label.ifBlank { packageName }

    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = accessibleName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = accessibleName },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    tone: StatusTone = StatusTone.SECONDARY,
    modifier: Modifier = Modifier,
    leadingDot: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val semantic = FluidTheme.semanticColors
    val (container, content) = when (tone) {
        StatusTone.PRIMARY -> scheme.primaryContainer to scheme.onPrimaryContainer
        StatusTone.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        StatusTone.TERTIARY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        StatusTone.SUCCESS -> semantic.successContainer to semantic.onSuccessContainer
        StatusTone.WARNING -> semantic.warningContainer to semantic.onWarningContainer
        StatusTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
        StatusTone.NEUTRAL -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    StatusBadge(
        text = text,
        containerColor = container,
        contentColor = content,
        modifier = modifier,
        leadingDot = leadingDot,
    )
}

@Composable
fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    leadingDot: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (leadingDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(contentColor),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleSummaryChips(
    rule: AppRule,
    otpOnly: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val chips = remember(rule, otpOnly) { summarizeRuleChips(rule, otpOnly) }
    val semantic = FluidTheme.semanticColors
    val scheme = MaterialTheme.colorScheme
    val clickModifier = if (onClick != null) {
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    } else {
        Modifier
    }

    FlowRow(
        modifier = modifier.then(clickModifier),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { chip ->
            val containerColor = when {
                chip.warning -> semantic.warningContainer
                chip.highlighted -> scheme.primaryContainer
                else -> scheme.surfaceContainerLowest
            }
            val contentColor = when {
                chip.warning -> semantic.onWarningContainer
                chip.highlighted -> scheme.onPrimaryContainer
                else -> scheme.onSurfaceVariant
            }
            Surface(
                shape = CircleShape,
                color = containerColor,
                contentColor = contentColor,
            ) {
                Text(
                    text = chip.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (chip.highlighted || chip.warning) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Medium
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    switchContentDescription: String = title,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = switchContentDescription
            },
        )
    }
}

@Composable
fun ConfirmAlertDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String = "取消",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissLabel)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleSandboxCard(
    rule: AppRule,
    otpOnly: Boolean,
    sampleText: String,
    onSampleTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    rulePack: RulePack? = null,
) {
    val context = LocalContext.current
    val activeRulePack = rulePack ?: remember(context) {
        runCatching { RuleRuntime.current(context) }.getOrDefault(RulePack.EMPTY)
    }
    val evaluation = remember(sampleText, rule, otpOnly, activeRulePack) {
        RuleSandboxEvaluator.evaluate(
            sampleText = sampleText,
            rule = rule,
            otpOnly = otpOnly,
            rulePack = activeRulePack,
        )
    }
    val semantic = FluidTheme.semanticColors
    val scheme = MaterialTheme.colorScheme
    val (resultBg, resultFg) = when (evaluation) {
        is SandboxResult.MatchedOtp -> semantic.successContainer to semantic.onSuccessContainer
        is SandboxResult.MatchedVerificationLink -> scheme.primaryContainer to scheme.onPrimaryContainer
        is SandboxResult.MatchedWhitelist -> scheme.primaryContainer to scheme.onPrimaryContainer
        is SandboxResult.BlockedByExcludeKeyword -> scheme.errorContainer to scheme.onErrorContainer
        is SandboxResult.BlockedByIncludeKeyword -> semantic.warningContainer to semantic.onWarningContainer
        is SandboxResult.SkippedNotOtp,
        is SandboxResult.EmptyInput,
        -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }

    TonalSectionCard(
        modifier = modifier,
        containerColor = scheme.surfaceContainer,
    ) {
        Text(
            text = "规则试测沙盒（实时验证）",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.primary,
        )
        Text(
            text = "输入示例通知正文，保存前立即确认关键词拦截与验证码提取效果。",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        OutlinedTextField(
            value = sampleText,
            onValueChange = onSampleTextChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            placeholder = { Text("输入测试通知正文…") },
            trailingIcon = {
                if (sampleText.isNotEmpty()) {
                    IconButton(onClick = { onSampleTextChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "清空测试文本",
                        )
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
        )
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RuleSandboxEvaluator.DEFAULT_SAMPLES.forEach { (label, text) ->
                AssistChip(
                    onClick = { onSampleTextChange(text) },
                    label = { Text(text = "示例：$label") },
                )
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            shape = RoundedCornerShape(14.dp),
            color = resultBg,
            contentColor = resultFg,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = evaluation.message,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (evaluation is SandboxResult.MatchedWhitelist && evaluation.truncated) {
                    Text(
                        text = "预览截断：${evaluation.previewText}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = resultFg.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}
