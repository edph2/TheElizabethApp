package uk.elizabeth.aac.core.model

/** Splits content into fixed-size pages, because scrolling is hard with limited motor control. */
object Paging {
    fun pageCount(itemCount: Int, pageSize: Int): Int =
        if (pageSize <= 0) 1 else maxOf(1, (itemCount + pageSize - 1) / pageSize)

    fun <T> page(items: List<T>, pageIndex: Int, pageSize: Int): List<T> {
        if (pageSize <= 0) return items
        val index = pageIndex.coerceIn(0, pageCount(items.size, pageSize) - 1)
        return items.drop(index * pageSize).take(pageSize)
    }
}
