package com.kupuproxy.shared.design

import androidx.compose.ui.unit.dp

/**
 * Единая палитра KupuProxy.
 *
 * Раньше проект держал два несовместимых набора цветов: Compose-схему из Material Theme Builder
 * (глубокий teal #006C61) и XML-тему, которая соответствует иконке приложения (teal #00C2A8).
 * Значения ниже взяты из XML/иконки — это и есть фактический бренд — и теперь разложены
 * на аккуратную шкалу. Единственный источник правды для Android и десктопа.
 */
object KupuColors {
    // Брендовые акценты
    const val Teal = 0xFF00C2A8
    const val TealDark = 0xFF009B87
    const val TealLight = 0xFF3DDFC8
    const val Indigo = 0xFF5B6CFF
    const val IndigoLight = 0xFFA8B2FF
    const val Coral = 0xFFFF7A59
    const val CoralLight = 0xFFFF9B82

    // Фоны и поверхности
    const val BackgroundLight = 0xFFF4F7FB
    const val BackgroundDark = 0xFF0B1220
    const val SurfaceLight = 0xFFFFFFFF
    const val SurfaceDark = 0xFF121A2B
    const val SurfaceVariantLight = 0xFFE8EEF5
    const val SurfaceVariantDark = 0xFF1A2438
    const val OnSurfaceLight = 0xFF0F172A
    const val OnSurfaceDark = 0xFFE8EEF7
    const val OnSurfaceVariantLight = 0xFF5B6B7C
    const val OnSurfaceVariantDark = 0xFF9AABC0
    const val OutlineLight = 0xFFC9D4E0
    const val OutlineDark = 0xFF2C3A52

    // Контейнеры брендовых цветов
    const val PrimaryContainerLight = 0xFFD4F7F1
    const val PrimaryContainerDark = 0xFF0A3D36
    const val OnPrimaryContainerLight = 0xFF003730
    const val OnPrimaryContainerDark = 0xFFD4F7F1
    const val SecondaryContainerLight = 0xFFE0E4FF
    const val SecondaryContainerDark = 0xFF2A3370
    const val OnSecondaryContainerLight = 0xFF121848
    const val OnSecondaryContainerDark = 0xFFE0E4FF

    // Ошибки
    const val ErrorLight = 0xFFBA1A1A
    const val ErrorDark = 0xFFFFB4AB
    const val OnErrorLight = 0xFFFFFFFF
    const val OnErrorDark = 0xFF690005

    // Индикаторы задержки — вынесены из неиспользуемого colors.xml в реальный UI.
    const val PingExcellentLight = 0xFF12B76A
    const val PingExcellentDark = 0xFF32D583
    const val PingGoodLight = 0xFFF79009
    const val PingGoodDark = 0xFFFDB022
    const val PingSlowLight = 0xFFF04438
    const val PingSlowDark = 0xFFF97066

    const val OnPrimaryLight = 0xFFFFFFFF
    const val OnPrimaryDark = 0xFF003730
    const val OnSecondaryLight = 0xFFFFFFFF
    const val OnSecondaryDark = 0xFF121848
}

/**
 * Единая шкала отступов и размеров. Заменяет разброс 12/14/16/18/20 dp, который накопился
 * в интерфейсе за предыдущие версии.
 */
object KupuSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Горизонтальный отступ страницы — одинаковый на мобильном и десктопе. */
    val pageGutter = lg
    /** Вертикальный отступ списка. */
    val pageVertical = md
    /** Зазор между элементами списка. */
    val listGap = md
    /** Внутренний отступ карточки. */
    val cardPadding = lg
    /** Зазор внутри карточки между блоками. */
    val cardGap = md
}

/** Радиусы скругления. Единая шкала вместо пяти inline-вызовов RoundedCornerShape. */
object KupuRadius {
    val small = 12.dp
    val medium = 18.dp
    val large = 24.dp
    val xl = 30.dp
    val pill = 999.dp
}

/** Иконки в шапке и списках — один размер на все приложения. */
object KupuIconSize {
    val small = 18.dp
    val medium = 22.dp
    val large = 28.dp
    val hero = 36.dp
    val logo = 48.dp
}

/** Высоты интерактивных элементов — единые для обеих платформ. */
object KupuControl {
    val buttonHeight = 52.dp
    val compactButtonHeight = 44.dp
    val touchTarget = 48.dp
    val iconButton = 40.dp
}

/**
 * Схема анимаций. Раньше переходы были мгновенными: диалоги, смена избранного и — главное —
 * появление результатов скана в списке происходили без перехода.
 */
object KupuMotion {
    /** Быстрый отклик на смену состояния (звезда избранного, чекбокс, статус). */
    const val stateFastMs = 150
    /** Стандартный переход: появление элемента списка, раскрытие панели. */
    const val standardMs = 250
    /** Переход между крупными состояниями (диалог, смена экрана). */
    const val emphasizedMs = 400

    /** Пружина для «живых» элементов: кнопка, статус-индикатор. */
    const val springDampingRatio = 0.8f
    const val springStiffness = 380f
}