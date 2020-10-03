/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */
package net.mamoe.mirai.event

import kotlinx.coroutines.*
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.subscribeAlways
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse


internal class CancelScopeTest {
    @Test
    fun testCancelScope() {
        val scope = CoroutineScope(SupervisorJob())

        var got = false
        scope.subscribeAlways<TestEvent> {
            got = true
        }

        runBlocking {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            TestEvent().broadcast()
        }
        assertFalse { got }
    }
}