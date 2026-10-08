package dev.zomboidds.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HereOrderTest {
    private fun card(key: String, vararg actions: String) =
        MenuOption(id = key, name = key, enabled = true, key = key,
            children = actions.map { MenuOption(id = "$key.$it", name = it, enabled = true) })

    private val window = card("window", "Open Window")
    private val sink = card("sink", "Drink")
    private val oven = card("oven", "Turn on")
    private val microwave = card("microwave", "Turn on")
    private val disassemble = card("list:Disassemble", "Chrome Toaster").copy(tray = true)
    private val sit = MenuOption(id = "9", name = "Sit on ground", enabled = true, key = "name:Sit on ground")

    private fun names(order: HereOrder, vararg options: MenuOption) =
        order.arrange(ItemMenu("m", options.toList())).objects.map { it.name }

    @Test
    fun `objects keep the order they first showed up in, whatever the game's order`() {
        val order = HereOrder()
        assertEquals(listOf("window", "sink"), names(order, window, sink))
        assertEquals(listOf("window", "sink", "oven"), names(order, oven, sink, window))
    }

    @Test
    fun `what was in front comes first, the most recent on top`() {
        val order = HereOrder()
        names(order, window, sink, oven, microwave)
        order.promote("oven")
        assertEquals(listOf("oven", "window", "sink", "microwave"), names(order, window, sink, oven, microwave))
        order.promote("microwave")
        assertEquals("the oven stays right under it", listOf("microwave", "oven", "window", "sink"),
            names(order, window, sink, oven, microwave))
        order.promote("oven")
        assertEquals("turning back swaps the two, nothing else moves", listOf("oven", "microwave", "window", "sink"),
            names(order, window, sink, oven, microwave))
    }

    @Test
    fun `lists and loose actions go to the tray, in the game's order`() {
        val arranged = HereOrder().arrange(ItemMenu("m", listOf(disassemble, window, sit)))
        assertEquals(listOf("window"), arranged.objects.map { it.name })
        assertEquals(listOf("list:Disassemble", "Sit on ground"), arranged.tray.map { it.name })
    }

    @Test
    fun `a tap on a replaced menu finds the same option by key and name`() {
        val newer = ItemMenu("m2", listOf(sink.copy(id = "1"), window.copy(id = "2",
            children = listOf(MenuOption(id = "2.1", name = "Open Window", enabled = true)))))
        assertEquals("2.1", newer.find("window", listOf("Open Window"))?.id)
        assertNull("gone since", newer.find("oven", listOf("Turn on")))
    }
}
