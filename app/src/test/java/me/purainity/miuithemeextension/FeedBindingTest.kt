package me.purainity.miuithemeextension

import me.purainity.miuithemeextension.hook.FeedBinding
import org.junit.Assert.*
import org.junit.Test

class FeedBindingTest {
    class UIProduct
    class Wrapper(@Suppress("unused") val product: UIProduct)
    class Plain(@Suppress("unused") val name: String)

    private val product = UIProduct::class.java

    @Test fun directProductParameterWins() {
        val slot = FeedBinding.productSlot(arrayOf(product, Int::class.javaPrimitiveType!!), product)
        assertEquals(0, slot?.first)
        assertNull(slot?.second)
    }
    @Test fun directProductAllowsExtraIntParameters() {
        val slot = FeedBinding.productSlot(
            arrayOf(product, Int::class.javaPrimitiveType!!, Int::class.javaObjectType), product)
        assertEquals(0, slot?.first)
        assertNull(slot?.second)
    }
    @Test fun wrapperParameterExposesProductField() {
        val slot = FeedBinding.productSlot(arrayOf(Wrapper::class.java, Int::class.javaPrimitiveType!!), product)
        assertEquals(0, slot?.first)
        assertEquals(Wrapper::class.java.getDeclaredField("product"), slot?.second)
    }
    @Test fun rejectsForeignObjectAlongsideDirectProduct() {
        assertNull(FeedBinding.productSlot(arrayOf(product, Plain::class.java), product))
    }
    @Test fun rejectsMultipleObjectParameters() {
        assertNull(FeedBinding.productSlot(arrayOf(Wrapper::class.java, Plain::class.java), product))
    }
    @Test fun rejectsObjectWithoutProductField() {
        assertNull(FeedBinding.productSlot(arrayOf(Plain::class.java, Int::class.javaPrimitiveType!!), product))
    }
    @Test fun rejectsEmptyParameters() {
        assertNull(FeedBinding.productSlot(emptyArray(), product))
    }
}
