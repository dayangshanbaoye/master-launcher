package com.rubyketang.launcher

/** 默认桌面角色的纯决策，避免在已授权设备上重复打断用户。 */
object HomeRolePolicy {
    fun shouldRequest(isAvailable: Boolean, isHeld: Boolean): Boolean = isAvailable && !isHeld
}
