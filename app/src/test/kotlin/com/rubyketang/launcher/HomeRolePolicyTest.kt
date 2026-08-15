package com.rubyketang.launcher

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeRolePolicyTest {
    @Test fun `支持且尚未持有默认桌面角色时请求`() {
        assertTrue(HomeRolePolicy.shouldRequest(isAvailable = true, isHeld = false))
    }

    @Test fun `已是默认桌面或系统不支持时不重复请求`() {
        assertFalse(HomeRolePolicy.shouldRequest(isAvailable = true, isHeld = true))
        assertFalse(HomeRolePolicy.shouldRequest(isAvailable = false, isHeld = false))
    }
}
