package com.nabd.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nabd.app.data.MemoryManager
import com.nabd.app.data.SettingsManager
import com.nabd.app.data.local.ChatDatabase
import com.nabd.app.service.NabdForegroundService
import com.nabd.app.ui.chat.ChatApp
import com.nabd.app.ui.theme.NabdTheme
import com.nabd.app.viewmodel.ChatViewModel
import com.nabd.app.viewmodel.ChatViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

/**
 * النشاط الرئيسي لتطبيق "نبض".
 * تم تعديله لفرض اللغة العربية واتجاه RTL بشكل كامل ودائم.
 */
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // 1. فرض اللغة العربية على مستوى موارد النظام (Configuration)
        val locale = Locale("ar")
        Locale.setDefault(locale)
        
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        
        // إنشاء سياق جديد يحتوي على الإعدادات العربية
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // إعدادات الخدمات والبيانات (من الكود الأصلي لـ Agora)
        com.nabd.app.util.DebugLog.init(this)
        NabdForegroundService.createChannel(this)

        // طلب تصاريح الإشعارات لنظام أندرويد 13 فما فوق
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }

        // فرض اتجاه الـ View الرئيسي ليكون RTL (لضمان عمل الـ Dialogs والـ System UI بشكل صحيح)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        enableEdgeToEdge()

        setContent {
            // 2. استخدام التنسيق المخصص لتطبيق نبض
            NabdTheme {
                // 3. فرض اتجاه اليمين إلى اليسار (RTL) داخل بيئة Compose
                // هذا يضمن أن Scaffold, NavHost, و LazyColumn تعكس اتجاهها تلقائياً
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    
                    val applicationContext = android.content.ContextWrapper(this).applicationContext
                    val memoryManager = MemoryManager(applicationContext)
                    val settingsManager = SettingsManager(applicationContext)
                    
                    // إعداد ViewModel باستخدام المصنع المخصص
                    val chatViewModel: ChatViewModel = viewModel(
                        factory = ChatViewModelFactory(
                            ChatDatabase.getInstance(this),
                            settingsManager,
                            memoryManager
                        )
                    )

                    // المكون الرئيسي للتطبيق (يحتوي على الـ Navigation والـ UI)
                    ChatApp(
                        viewModel = chatViewModel,
                        settingsManager = settingsManager
                    )
                }
            }
        }
    }
}
