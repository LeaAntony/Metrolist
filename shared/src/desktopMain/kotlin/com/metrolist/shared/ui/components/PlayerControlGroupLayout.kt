package com.metrolist.shared.ui.components

internal enum class ControlActionRole {
    STANDARD,
    VOLUME,
    DEVICE_CONNECT,
}

internal data class ControlActionEntry<out T>(
    val action: T,
    val role: ControlActionRole,
)

internal fun <T> buildPlayerControlGroups(
    leftActions: List<T>,
    volumeAction: T,
    deviceConnectAction: T?,
    rightActions: List<T>,
    sectioned: Boolean,
): List<List<ControlActionEntry<T>>> {
    val leftEntries = leftActions.map { ControlActionEntry(it, ControlActionRole.STANDARD) }
    val volumeEntry = ControlActionEntry(volumeAction, ControlActionRole.VOLUME)
    val rightEntries =
        buildList<ControlActionEntry<T>> {
            if (deviceConnectAction != null) {
                add(ControlActionEntry(deviceConnectAction, ControlActionRole.DEVICE_CONNECT))
            }
            rightActions.forEach { add(ControlActionEntry(it, ControlActionRole.STANDARD)) }
        }
    return if (sectioned) {
        listOf(leftEntries, listOf(volumeEntry) + rightEntries)
    } else {
        listOf(leftEntries + volumeEntry + rightEntries)
    }
}

internal fun <T> resolveDisplayedControlEntries(
    rawEntries: List<ControlActionEntry<T>>,
    rawLastVisibleIndex: Int,
    prioritizeDeviceConnect: Boolean,
    sectioned: Boolean,
): List<ControlActionEntry<T>> {
    if (!prioritizeDeviceConnect || sectioned) return rawEntries
    val volumeIndex = rawEntries.indexOfFirst { it.role == ControlActionRole.VOLUME }
    val deviceConnectIndex = rawEntries.indexOfFirst { it.role == ControlActionRole.DEVICE_CONNECT }
    if (volumeIndex < 0 || deviceConnectIndex < 0) return rawEntries
    return promoteAdjacentControlActions(
        actions = rawEntries,
        lastVisibleIndex = rawLastVisibleIndex,
        primaryIndex = volumeIndex,
        secondaryIndex = deviceConnectIndex,
    )
}

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
    // Mirror Material 3 ButtonGroupMeasurePolicy, which subtracts overflowWidth (without trailing spacing)
    // before packing visible items left-to-right.
    var remaining = availableWidth - overflowWidth
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
