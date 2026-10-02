package com.novelai.assistant.data.tts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 电池优化白名单检测与授权助手：
 * 检测当前 App 是否已在系统的电池白名单中（无限制后台运行），
 * 并提供一键跳转系统弹窗或电池管理设置页的能力，
 * 解决国内各大厂商（荣耀/华为、小米、OPPO、vivo 等）熄屏后将后台前台服务无差别冻结杀除的问题。
 */
object BatteryOptimizationHelper {

    /** 检查是否已忽略电池优化（即已加入白名单/允许后台无限制） */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    /** 请求忽略电池优化（弹出系统对话框或引导至设置页） */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        try {
            // 优先尝试直接弹出系统的授权白名单确认框
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // 部分定制系统（如特定定制 ROM）拦截了直接请求，降级跳转到“电池优化列表”
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                try {
                    // 若依然失败，跳转到该应用的应用信息详情页，供用户手动配置“耗电管理/后台运行”
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e3: Exception) {
                    e3.printStackTrace()
                }
            }
        }
    }
}
