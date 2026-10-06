package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.anjeib.margin.ai.Forward

/** Who wrote a forwarded text, read from the words themselves. */
class ForwardParseTest {
    @Test fun senders() {
        Forward.sender("---------- Forwarded message ---------\nFrom: Марина Петрова <marina@studio.ru>\nDate: Tue, 6 Oct\n\nПравки пришлю до среды").let {
            assertEquals("Марина Петрова", it?.name); assertEquals("marina@studio.ru", it?.address)
        }
        Forward.sender("-------- Пересылаемое сообщение --------\n06.10.2026, 17:42, \"Виктор\" <v@arenda.ru>:\nОплату жду до 10-го").let {
            assertEquals("Виктор", it?.name); assertEquals("v@arenda.ru", it?.address)
        }
        Forward.sender("От кого: ООО «ПакетПро» <sale@paketpro.ru>\nСчёт на оплату").let { assertEquals("sale@paketpro.ru", it?.address) }
        Forward.sender("Марина Петрова, [6 окт. 2026 г. в 17:42]:\nПришлю правки до среды").let { assertEquals("Марина Петрова", it?.name) }
        Forward.sender("[06.10.2026 17:42] Лена: скину график").let { assertEquals("Лена", it?.name) }
        Forward.sender("Счёт направляем. С уважением, ООО «ПакетПро»").let { assertEquals("ООО «ПакетПро»", it?.company) }
        assertNull(Forward.sender("Созвон перенесли на пятницу, 15:00"))
    }
}
