@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kupuproxy.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kupuproxy.desktop.Fluent
import com.kupuproxy.desktop.Severity

// Контролы в стиле Fluent 2 / WinUI 3. Пишем свои, а не тянем зависимость: стандартные
// Material-контролы выглядят как Android, а Fluent-библиотеки для Compose пока нестабильны.

private val ControlShape = RoundedCornerShape(Fluent.controlRadius)
private val CardShape = RoundedCornerShape(Fluent.overlayRadius)

enum class ButtonStyle { ACCENT, STANDARD, SUBTLE }

/** Кнопка Fluent: акцентная (основное действие), стандартная и «тихая» (subtle). */
@Composable
fun FluentButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.STANDARD,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = if (text == null) 8.dp else 12.dp),
) {
    val c = Fluent.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val background = when (style) {
        ButtonStyle.ACCENT -> when {
            !enabled -> c.textDisabled.copy(alpha = 0.22f)
            pressed -> c.accentPressed
            hovered -> c.accentHover
            else -> c.accent
        }
        ButtonStyle.STANDARD -> when {
            !enabled -> c.control.copy(alpha = 0.6f)
            pressed -> c.controlPressed
            hovered -> c.controlHover
            else -> c.control
        }
        ButtonStyle.SUBTLE -> when {
            !enabled -> Color.Transparent
            pressed -> c.subtlePressed
            hovered -> c.subtleHover
            else -> Color.Transparent
        }
    }
    val animatedBg by animateColorAsState(background, tween(83), label = "btnBg")
    val content = when {
        !enabled -> c.textDisabled
        style == ButtonStyle.ACCENT -> c.onAccent
        pressed -> c.textSecondary
        else -> c.textPrimary
    }
    Row(
        modifier
            .height(Fluent.controlHeight)
            .defaultMinSize(minWidth = if (text == null) Fluent.controlHeight else 64.dp)
            .clip(ControlShape)
            .background(animatedBg)
            .then(
                if (style == ButtonStyle.STANDARD) {
                    Modifier.border(BorderStroke(1.dp, c.controlStroke), ControlShape)
                        .drawBehind {
                            // Нижняя грань чуть темнее — характерный «объём» кнопок WinUI.
                            drawRect(c.controlStroke, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
                        }
                } else {
                    Modifier
                },
            )
            .hoverable(interaction, enabled)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = text, tint = content, modifier = Modifier.size(16.dp))
            if (text != null) Spacer(Modifier.width(8.dp))
        }
        if (text != null) {
            Text(text, style = Fluent.type.body, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Иконка-кнопка с подсказкой (ToolTip). */
@Composable
fun FluentIconButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    size: Dp = Fluent.controlHeight,
) {
    FluentTooltip(tooltip) {
        val c = Fluent.colors
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val pressed by interaction.collectIsPressedAsState()
        Box(
            modifier
                .size(size)
                .clip(ControlShape)
                .background(if (pressed) c.subtlePressed else if (hovered && enabled) c.subtleHover else Color.Transparent)
                .hoverable(interaction, enabled)
                .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = tooltip,
                tint = if (!enabled) c.textDisabled else tint ?: c.textPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Подсказка Fluent: всплывает с задержкой, светлая/тёмная карточка с обводкой. */
@Composable
fun FluentTooltip(text: String, content: @Composable () -> Unit) {
    val c = Fluent.colors
    TooltipArea(
        tooltip = {
            Box(
                Modifier
                    .shadow(8.dp, ControlShape)
                    .background(c.flyout, ControlShape)
                    .border(1.dp, c.cardStroke, ControlShape)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(text, style = Fluent.type.caption, color = c.textPrimary)
            }
        },
        delayMillis = 600,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 20.dp)),
    ) { content() }
}

/** Карточка — основной «слой» Fluent: заливка, обводка 1 dp, радиус 8 dp. */
@Composable
fun FluentCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Fluent.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier
            .clip(CardShape)
            .background(if (onClick != null && hovered) c.cardHover else c.card)
            .border(1.dp, c.cardStroke, CardShape)
            .then(
                if (onClick != null) {
                    Modifier.hoverable(interaction).clickable(interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(padding),
        content = content,
    )
}

/** Строка настроек в стиле «Параметров» Windows 11: иконка, заголовок, описание, контрол справа. */
@Composable
fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Fluent.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(ControlShape)
            .background(if (onClick != null && hovered) c.cardHover else c.card)
            .border(1.dp, c.cardStroke, ControlShape)
            .then(
                if (onClick != null) Modifier.hoverable(interaction).clickable(interaction, null, onClick = onClick) else Modifier,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Fluent.type.body, color = c.textPrimary)
            if (!description.isNullOrBlank()) {
                Text(description, style = Fluent.type.caption, color = c.textSecondary)
            }
        }
        Spacer(Modifier.width(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = trailing)
    }
}

/** Заголовок группы настроек. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Fluent.type.bodyStrong,
        color = Fluent.colors.textPrimary,
        modifier = modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

/** Переключатель Fluent (ToggleSwitch) с подписью «Вкл.»/«Откл.» слева, как в Windows 11. */
@Composable
fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    showLabel: Boolean = true,
) {
    val c = Fluent.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val knobOffset by animateDpAsState(if (checked) 22.dp else 4.dp, tween(167), label = "toggleKnob")
    val knobSize by animateDpAsState(if (pressed) 14.dp else if (hovered) 14.dp else 12.dp, tween(83), label = "toggleKnobSize")
    Row(
        Modifier
            .hoverable(interaction, enabled)
            .clickable(interaction, null, enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLabel) {
            Text(
                if (checked) "Вкл." else "Откл.",
                style = Fluent.type.body,
                color = if (enabled) c.textPrimary else c.textDisabled,
                modifier = Modifier.widthIn(min = 44.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier
                .size(width = 40.dp, height = 20.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !enabled -> Color.Transparent
                        checked && hovered -> c.accentHover
                        checked -> c.accent
                        hovered -> c.subtleHover
                        else -> Color.Transparent
                    },
                )
                .then(
                    if (!checked || !enabled) Modifier.border(1.dp, if (enabled) c.controlStrong else c.textDisabled, CircleShape) else Modifier,
                ),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = knobOffset + (12.dp - knobSize) / 2)
                    .size(knobSize)
                    .clip(CircleShape)
                    .background(
                        when {
                            !enabled -> c.textDisabled
                            checked -> c.onAccent
                            else -> c.textSecondary
                        },
                    ),
            )
        }
    }
}

/** Кнопка-переключатель (ToggleButton) — для фильтров протоколов. */
@Composable
fun ToggleChip(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val c = Fluent.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Выбранный фильтр — мягкая акцентная заливка с акцентной обводкой: ряд из нескольких
    // включённых фильтров с полной заливкой выглядел бы слишком «тяжело».
    val bg = when {
        checked -> c.accent.copy(alpha = if (hovered) 0.24f else 0.16f).compositeOver(c.card)
        hovered -> c.controlHover
        else -> c.control
    }
    val fg = if (checked) (if (c.isDark) c.accent else c.accentText) else c.textPrimary
    Row(
        Modifier
            .height(30.dp)
            .clip(ControlShape)
            .background(bg)
            .border(1.dp, if (checked) c.accent.copy(alpha = 0.6f) else c.controlStroke, ControlShape)
            .hoverable(interaction)
            .clickable(interaction, null, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Fluent.type.body, color = fg)
    }
}

/** Поле ввода Fluent: при фокусе — сплошная заливка и акцентная нижняя линия 2 dp. */
@Composable
fun TextBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    onSubmit: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Fluent.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Fluent.type.body.copy(color = c.textPrimary),
        cursorBrush = SolidColor(c.textPrimary),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions.Default,
        keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }, onGo = { onSubmit?.invoke() }),
        modifier = modifier
            .height(Fluent.controlHeight)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .hoverable(interaction)
            .onEnterKey(onSubmit),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Fluent.controlHeight)
                    .clip(ControlShape)
                    .background(if (focused) (if (c.isDark) Color(0xFF1F1F1F) else Color.White) else if (hovered) c.controlHover else c.control)
                    .border(1.dp, c.controlStroke, ControlShape)
                    .drawBehind {
                        val h = if (focused) 2.dp.toPx() else 1.dp.toPx()
                        drawRect(if (focused) c.accent else c.controlStrokeBottom, Offset(0f, size.height - h), Size(size.width, h))
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = Fluent.type.body, color = c.textTertiary, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty() && focused) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Очистить",
                        tint = c.textSecondary,
                        modifier = Modifier.size(14.dp).clickable { onValueChange("") },
                    )
                }
                trailing?.invoke()
            }
        },
    )
}

private fun Modifier.onEnterKey(onSubmit: (() -> Unit)?): Modifier =
    if (onSubmit == null) {
        this
    } else {
        this.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                onSubmit()
                true
            } else {
                false
            }
        }
    }

/** Выпадающий список (ComboBox). */
@Composable
fun <T> ComboBox(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Fluent.colors
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Row(
            Modifier
                .height(Fluent.controlHeight)
                .widthIn(min = 140.dp)
                .clip(ControlShape)
                .background(if (hovered) c.controlHover else c.control)
                .border(1.dp, c.controlStroke, ControlShape)
                .hoverable(interaction)
                .clickable(interaction, null) { expanded = true }
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(selected), style = Fluent.type.body, color = c.textPrimary, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = CardShape,
            containerColor = c.flyout,
            border = BorderStroke(1.dp, c.cardStroke),
            shadowElevation = 8.dp,
            tonalElevation = 0.dp,
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = { Text(label(option), style = Fluent.type.body, color = c.textPrimary) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    leadingIcon = {
                        Box(
                            Modifier.width(3.dp).height(16.dp).clip(CircleShape)
                                .background(if (isSelected) c.accent else Color.Transparent),
                        )
                    },
                    modifier = Modifier.height(36.dp).padding(horizontal = 4.dp).clip(ControlShape)
                        .background(if (isSelected) c.subtleHover else Color.Transparent),
                )
            }
        }
    }
}

/** Ползунок Fluent: тонкая дорожка 4 dp и «кольцевой» бегунок с акцентной точкой. */
@Composable
fun FluentSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    val c = Fluent.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        interactionSource = interaction,
        modifier = modifier.height(32.dp).hoverable(interaction),
        thumb = {
            Box(
                Modifier
                    .size(20.dp)
                    .shadow(1.dp, CircleShape)
                    .background(if (c.isDark) Color(0xFF454545) else Color.White, CircleShape)
                    .border(1.dp, c.controlStroke, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val inner = if (pressed) 10.dp else if (hovered) 14.dp else 12.dp
                Box(Modifier.size(inner).background(c.accent, CircleShape))
            }
        },
        track = { sliderState ->
            val range = sliderState.valueRange
            val fraction = ((sliderState.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(c.controlStrong, size = size, cornerRadius = r)
                drawRoundRect(c.accent, size = Size(size.width * fraction, size.height), cornerRadius = r)
            }
        },
    )
}

/** Полоса прогресса Fluent: 3 dp; без [progress] — неопределённая анимация. */
@Composable
fun ProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val c = Fluent.colors
    if (progress == null) {
        val transition = rememberInfiniteTransition(label = "progress")
        val shift by transition.animateFloat(
            initialValue = -0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "progressShift",
        )
        Canvas(modifier.fillMaxWidth().height(3.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(c.controlStrong.copy(alpha = 0.25f), size = Size(size.width, size.height), cornerRadius = r)
            val start = (shift * size.width).coerceAtLeast(0f)
            val end = ((shift + 0.4f) * size.width).coerceAtMost(size.width)
            if (end > start) drawRoundRect(c.accent, Offset(start, 0f), Size(end - start, size.height), r)
        }
    } else {
        val animated by androidx.compose.animation.core.animateFloatAsState(progress.coerceIn(0f, 1f), tween(250), label = "progressValue")
        Canvas(modifier.fillMaxWidth().height(3.dp)) {
            val r = CornerRadius(size.height / 2)
            val track = 1.dp.toPx()
            drawRoundRect(c.controlStrong, Offset(0f, (size.height - track) / 2), Size(size.width, track), CornerRadius(track / 2))
            drawRoundRect(c.accent, size = Size(size.width * animated, size.height), cornerRadius = r)
        }
    }
}

/** InfoBar Fluent: сообщение с иконкой серьёзности, необязательной кнопкой и закрытием. */
@Composable
fun InfoBar(
    severity: Severity,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    elevated: Boolean = false,
) {
    val c = Fluent.colors
    val (bg, fg, icon) = when (severity) {
        Severity.SUCCESS -> Triple(c.successBg, c.success, Icons.Filled.CheckCircle)
        Severity.WARNING -> Triple(c.cautionBg, c.caution, Icons.Filled.Warning)
        Severity.ERROR -> Triple(c.criticalBg, c.critical, Icons.Filled.Error)
        Severity.INFO -> Triple(c.infoBg, c.accent, Icons.Filled.Info)
    }
    Row(
        modifier
            .then(if (elevated) Modifier.shadow(16.dp, ControlShape) else Modifier)
            .clip(ControlShape)
            .background(if (elevated) bg.compositeOn(c.flyout) else bg)
            .border(1.dp, c.cardStroke, ControlShape)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f, fill = false).padding(vertical = 4.dp)) {
            Text(title, style = Fluent.type.bodyStrong, color = c.textPrimary)
            if (!message.isNullOrBlank()) Text(message, style = Fluent.type.body, color = c.textPrimary)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(12.dp))
            FluentButton(actionLabel, onAction)
        }
        if (onClose != null) {
            Spacer(Modifier.width(4.dp))
            FluentIconButton(Icons.Outlined.Close, "Закрыть", onClose)
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}

private fun Color.compositeOn(base: Color): Color = if (alpha >= 1f) this else compositeOver(base)

/** Тонкий разделитель. */
@Composable
fun FluentDivider(modifier: Modifier = Modifier, vertical: Boolean = false) {
    val c = Fluent.colors
    Box(
        if (vertical) modifier.width(1.dp).fillMaxHeight().background(c.divider)
        else modifier.fillMaxWidth().height(1.dp).background(c.divider),
    )
}

/** Небольшой «бейдж» (InfoBadge / Tag). */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(ControlShape)
            .background(color.copy(alpha = if (Fluent.colors.isDark) 0.22f else 0.13f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, style = Fluent.type.caption, color = if (Fluent.colors.isDark) color else color)
    }
}

/** Заголовок страницы (Title 28) с подзаголовком и действиями справа. */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Fluent.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Fluent.type.title, color = c.textPrimary)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = Fluent.type.body, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Иконка в мягкой акцентной плашке — для карточек метрик. */
@Composable
fun IconPlate(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(6.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun BoxScope.FillLoading() {
    androidx.compose.material3.CircularProgressIndicator(
        color = Fluent.colors.accent,
        strokeWidth = 3.dp,
        modifier = Modifier.align(Alignment.Center).size(32.dp),
    )
}

/** Текст с явным стилем и цветом — сокращение для частых вызовов. */
@Composable
fun FText(
    text: String,
    style: TextStyle = Fluent.type.body,
    color: Color = Fluent.colors.textPrimary,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(text, style = style, color = color, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
