package com.cbrl.pixelblaze.presentation

sealed class Screen(val route: String) {
    data object DeviceList: Screen("device_list")
    data object SegmentSelect: Screen("segment_selection")
    data object HelmetController: Screen("helmet_controller")
}
