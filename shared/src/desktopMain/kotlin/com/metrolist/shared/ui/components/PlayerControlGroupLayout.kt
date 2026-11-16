package com.metrolist.shared.ui.components

internal fun playerControlLastVisibleIndex(
    availableWidth: Float,
    itemCount: Int,
    itemWidth: Float,
    spacing: Float,
    overflowWidth: Float,
): Int =
    playerControlLastVisibleIndex(
        availableWidth = availableWidth,
        itemWidths = List(itemCount) { itemWidth },
        spacing = spacing,
        overflowWidth = overflowWidth,
    )

internal fun playerControlLastVisibleIndex(
    availableWidth: Float,
    itemWidths: List<Float>,
    spacing: Float,
    overflowWidth: Float,
): Int {
    if (itemWidths.isEmpty()) return -1
    if (availableWidth <= 0f) return itemWidths.lastIndex
    val desiredWidth = itemWidths.sum() + (itemWidths.size - 1).coerceAtLeast(0) * spacing
    if (desiredWidth <= availableWidth) return itemWidths.lastIndex

    var count = 0
    var remaining = availableWidth - overflowWidth - spacing
    while (count < itemWidths.size && itemWidths[count] <= remaining) {
        remaining -= itemWidths[count] + spacing
        count++
    }
    return count - 1
}

internal fun <T> promoteAdjacentControlActions(
    actions: List<T>,
    lastVisibleIndex: Int,
    primaryIndex: Int,
    secondaryIndex: Int,
): List<T> {
    if (lastVisibleIndex <= 0 || primaryIndex !in actions.indices || secondaryIndex !in actions.indices) {
        return actions
    }
    if (secondaryIndex <= lastVisibleIndex || primaryIndex >= secondaryIndex) {
        return actions
    }
    val targetPrimaryIndex = minOf(primaryIndex, lastVisibleIndex - 1)
    val displaced =
        actions.subList(targetPrimaryIndex, secondaryIndex).filterIndexed { offset, _ ->
            targetPrimaryIndex + offset != primaryIndex
        }
    return buildList(actions.size) {
        addAll(actions.subList(0, targetPrimaryIndex))
        add(actions[primaryIndex])
        add(actions[secondaryIndex])
        addAll(displaced)
        addAll(actions.subList(secondaryIndex + 1, actions.size))
    }
}
