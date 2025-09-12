package fansirsqi.xposed.sesame.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.core.type.TypeReference
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.entity.ExtendFunctionItem
import fansirsqi.xposed.sesame.newui.WatermarkView
import fansirsqi.xposed.sesame.newutil.DataStore
import fansirsqi.xposed.sesame.ui.widget.ExtendFunctionAdapter
import fansirsqi.xposed.sesame.util.Detector.getApi
import fansirsqi.xposed.sesame.util.FansirsqiUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil

/**
 * 扩展功能页面
 */
class ExtendActivity : BaseActivity() {
    private val tag = ExtendActivity::class.java.simpleName
    private var debugTips: String? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var extendFunctionAdapter: ExtendFunctionAdapter
    private val extendFunctions = mutableListOf<ExtendFunctionItem>()

    /**
     * 初始化Activity
     *
     * @param savedInstanceState 保存的实例状态
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extend) // 设置布局文件
        debugTips = getString(R.string.debug_tips)
        baseTitle = getString(R.string.extended_func)
        setupRecyclerView()
        populateExtendFunctions()
        WatermarkView.install(this)
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView_extend_functions)
        recyclerView.layoutManager = LinearLayoutManager(this)
        extendFunctionAdapter = ExtendFunctionAdapter(extendFunctions)
        recyclerView.adapter = extendFunctionAdapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun populateExtendFunctions() {

        extendFunctions.add(
            ExtendFunctionItem(getString(R.string.clear_photo)) {
                // 取出当前条数
                val currentCount = DataStore
                    .getOrCreate("guangPanPhoto", object : TypeReference<List<Map<String, String>>>() {})
                    .size

                AlertDialog.Builder(this)
                    .setTitle(R.string.clear_photo)
                    .setMessage("确认清空 $currentCount 组光盘行动图片？")
                    .setPositiveButton(R.string.ok) { _, _ ->
                        // 直接从持久化里删掉 key
                        DataStore.remove("guangPanPhoto")
                        ToastUtil.showToast(this, "光盘行动图片清空成功")
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        )
        //调试功能往里加
        if (BuildConfig.DEBUG) {
            extendFunctions.add(
                ExtendFunctionItem("写入光盘") {
                    AlertDialog.Builder(this)
                        .setTitle("Test")
                        .setMessage("xxxx")
                        .setPositiveButton(R.string.ok) { _, _ ->
                            val newPhotoEntry = mapOf(
                                "before" to "before${FansirsqiUtil.getRandomString(10)}",
                                "after" to "after${FansirsqiUtil.getRandomString(10)}"
                            )

                            // 取出已有列表（空时返回空 MutableList）
                            val existingPhotos = DataStore.getOrCreate(
                                "guangPanPhoto",
                                object : TypeReference<MutableList<Map<String, String>>>() {})
                            existingPhotos.add(newPhotoEntry)

                            // 写回持久化
                            DataStore.put("guangPanPhoto", existingPhotos)
                            ToastUtil.showToast(this, "写入成功$newPhotoEntry")
                        }
                        .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            )

            //我想在这加一个编辑框，里面支持输入文字，下面的展示随机光盘的字段从编辑框里面取

            extendFunctions.add(
                ExtendFunctionItem("获取DataStore字段") {
                    val inputEditText = EditText(this)
                    AlertDialog.Builder(this)
                        .setTitle("输入字段Key")
                        .setView(inputEditText)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            val key = inputEditText.text.toString()
                            val value: Any? = try {
                                // 若不知道类型，可先按 Map 读；失败时再按 String 读
                                DataStore.getOrCreate(key, object : TypeReference<Map<*, *>>() {})
                            } catch (_: Exception) {
                                DataStore.getOrCreate(key, object : TypeReference<String>() {})
                            }
                            ToastUtil.showToast(this, "$value \n输入内容: $key")
                        }
                        .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            )


            extendFunctions.add(
                ExtendFunctionItem("获取BaseUrl") {
                    val inputEditText = EditText(this)
                    AlertDialog.Builder(this)
                        .setTitle("请输入Key")
                        .setView(inputEditText)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            val inputText = inputEditText.text.toString()
                            Log.debug(tag, "获取BaseUrl：$inputText")
                            val key = inputText.toIntOrNull(16)  // 支持输入 0x11 这样的十六进制
                            Log.debug(tag, "获取BaseUrl key：$key")
                            if (key != null) {
                                val output = getApi(key)
                                ToastUtil.showToast(this, "$output \n输入内容: $inputText")
                            } else {
                                ToastUtil.showToast(this, "输入内容: $inputText , 请输入正确的十六进制数字")
                            }

                        }
                        .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            )
        }
        extendFunctionAdapter.notifyDataSetChanged()
    }

}
