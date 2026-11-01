package org.schabi.newpipe.fragments.list.videos

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.extractor.stream.StreamInfo

class RelatedItemsFragmentTest {
    private val fragmentClass = RelatedItemsFragment::class.java

    @Test
    fun `fragment inherits Bridge save and restore from BaseFragment`() {
        assertTrue(BaseFragment::class.java.isAssignableFrom(fragmentClass))
    }

    @Test
    fun `state saver is generated so streamInfo is saved through Bridge`() {
        val saver = Class.forName(
            "${fragmentClass.name}\$\$StateSaver",
            false,
            fragmentClass.classLoader
        )

        assertEquals(fragmentClass.`package`, saver.`package`)
    }

    @Test
    fun `streamInfo is a public instance field the state saver can reach`() {
        val field = fragmentClass.getField("streamInfo")

        assertEquals(StreamInfo::class.java, field.type)
        assertTrue(!Modifier.isStatic(field.modifiers))
    }
}
