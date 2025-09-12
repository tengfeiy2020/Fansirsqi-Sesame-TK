package fansirsqi.xposed.sesame.model

import kotlin.jvm.java

object ModelOrder {
    private val array = arrayOf(
        BaseModel::class.java,       // 基础设置
//        Antinvoice::class.java,      // 蚂蚁发票
    )

    val allConfig: List<Class<out Model>> = array.toList()
}
