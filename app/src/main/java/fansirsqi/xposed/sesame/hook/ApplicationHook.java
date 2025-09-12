package fansirsqi.xposed.sesame.hook;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Method;
import java.lang.reflect.Member;
import java.lang.reflect.InvocationTargetException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import fansirsqi.xposed.sesame.BuildConfig;
import fansirsqi.xposed.sesame.data.Config;
import fansirsqi.xposed.sesame.data.General;
import fansirsqi.xposed.sesame.data.Status;
import fansirsqi.xposed.sesame.entity.AlipayVersion;
import fansirsqi.xposed.sesame.hook.rpc.bridge.NewRpcBridge;
import fansirsqi.xposed.sesame.hook.rpc.bridge.OldRpcBridge;
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcBridge;
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcVersion;
import fansirsqi.xposed.sesame.hook.rpc.intervallimit.RpcIntervalLimit;
import fansirsqi.xposed.sesame.hook.server.ModuleHttpServer;
import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.model.Model;
import fansirsqi.xposed.sesame.newutil.DataStore;
import fansirsqi.xposed.sesame.util.AssetUtil;
import fansirsqi.xposed.sesame.util.Detector;
import fansirsqi.xposed.sesame.util.Files;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.Notify;
import fansirsqi.xposed.sesame.util.PermissionUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;
import fansirsqi.xposed.sesame.util.maps.UserMap;
import fi.iki.elonen.NanoHTTPD;
import io.github.libxposed.api.XposedModuleInterface;
import kotlin.jvm.JvmStatic;
import lombok.Getter;

public class ApplicationHook {
    static final String TAG = ApplicationHook.class.getSimpleName();
    private ModuleHttpServer httpServer;
    private static final String modelVersion = BuildConfig.VERSION_NAME;
    private static final Map<String, PendingIntent> wakenAtTimeAlarmMap = new ConcurrentHashMap<>();
    @Getter
    private static ClassLoader classLoader = null;
    @Getter
    private static Object microApplicationContextObject = null;

    @SuppressLint("StaticFieldLeak")
    static Context appContext = null;

    @JvmStatic
    public static Context getAppContext() {
        return appContext;
    }

    @SuppressLint("StaticFieldLeak")
    static Context moduleContext = null;

    @Getter
    static AlipayVersion alipayVersion = new AlipayVersion("");
    private static volatile boolean hooked = false;

    @JvmStatic
    public static boolean isHooked() {
        return hooked;
    }

    private static volatile boolean init = false;
    static volatile Calendar dayCalendar;
    @Getter
    static volatile boolean offline = false;

    @Getter
    static final AtomicInteger reLoginCount = new AtomicInteger(0);
    @SuppressLint("StaticFieldLeak")
    static Service service;
    @Getter
    static Handler mainHandler;
    static RpcBridge rpcBridge;
    @Getter
    private static RpcVersion rpcVersion;
    private static PowerManager.WakeLock wakeLock;
    private static PendingIntent alarm0Pi;

    public static void setOffline(boolean offline) {
        ApplicationHook.offline = offline;
    }

    private XposedModuleInterface.PackageLoadedParam modelLoadPackageParam;

    private XposedModuleInterface.PackageLoadedParam appLloadPackageParam;

    static {
        dayCalendar = Calendar.getInstance();
        dayCalendar.set(Calendar.HOUR_OF_DAY, 0);
        dayCalendar.set(Calendar.MINUTE, 0);
        dayCalendar.set(Calendar.SECOND, 0);
        Method m = null;
        try {
            m = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class);
        } catch (Throwable t) {
            XposedBridge.log("E/" + TAG + " " + android.util.Log.getStackTraceString(t));
        }
        deoptimizeMethod = m;
    }

    private final static Method deoptimizeMethod;

    static void deoptimizeMethod(Class<?> c) throws InvocationTargetException, IllegalAccessException {
        for (Method m : c.getDeclaredMethods()) {
            if (deoptimizeMethod != null && m.getName().equals("makeApplicationInner")) {
                deoptimizeMethod.invoke(null, m);
                if (BuildConfig.DEBUG)
                    XposedBridge.log("D/" + TAG + " Deoptimized " + m.getName());
            }
        }
    }


    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private void loadNativeLibs(Context context, File soFile) {
        try {
            File finalSoFile = AssetUtil.INSTANCE.copyStorageSoFileToPrivateDir(context, soFile);
            if (finalSoFile != null) {
                System.load(finalSoFile.getAbsolutePath());
                Log.runtime(TAG, "Loading " + soFile.getName() + " from :" + finalSoFile.getAbsolutePath());
            } else {
                Detector.INSTANCE.loadLibrary(soFile.getName().replace(".so", "").replace("lib", ""));
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, "载入so库失败！！", e);
        }
    }

    public void loadModelPackage(XposedModuleInterface.PackageLoadedParam loadPackageParam) {
        if (General.MODULE_PACKAGE_NAME.equals(loadPackageParam.getPackageName())) {
            try {
                Class<?> applicationClass = loadPackageParam.getClassLoader().loadClass("android.app.Application");
                XposedHelpers.findAndHookMethod(applicationClass, "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                        moduleContext = (Context) param.thisObject;
                        HookUtil.INSTANCE.hookActive(loadPackageParam);
                    }
                });
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
        }
    }

    public void loadPackage(XposedModuleInterface.PackageLoadedParam loadPackageParam) {
        if (General.PACKAGE_NAME.equals(loadPackageParam.getPackageName())) {
            try {
                if (hooked) return;
                appLloadPackageParam = loadPackageParam;
                classLoader = appLloadPackageParam.getClassLoader();
                try {
                    @SuppressLint("PrivateApi") Class<?> loadedApkClass = classLoader.loadClass("android.app.LoadedApk");
                    deoptimizeMethod(loadedApkClass);
                } catch (Throwable t) {
                    Log.printStackTrace(TAG, "deoptimize makeApplicationInner err:", t);
                }
                XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        mainHandler = new Handler(Looper.getMainLooper());
                        appContext = (Context) param.args[0];
                        PackageInfo pInfo = appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
                        assert pInfo.versionName != null;
                        alipayVersion = new AlipayVersion(pInfo.versionName);
                        Log.runtime(TAG, "handleLoadPackage alipayVersion: " + alipayVersion.getVersionString());
                        loadNativeLibs(appContext, AssetUtil.INSTANCE.getCheckerDestFile());
                        loadNativeLibs(appContext, AssetUtil.INSTANCE.getDexkitDestFile());
                        HookUtil.INSTANCE.fuckAccounLimit(loadPackageParam);

                        super.afterHookedMethod(param);
                    }
                });
            } catch (Exception e) {
                Log.printStackTrace(e);
            }

            try {
                XposedHelpers.findAndHookMethod("com.alipay.mobile.quinox.LauncherActivity", classLoader, "onResume",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Log.runtime(TAG, "hook onResume after start");
                                String targetUid = getUserId();
                                Log.runtime(TAG, "onResume targetUid: " + targetUid);
                                if (targetUid == null) {
                                    Log.record(TAG, "onResume:用户未登录");
                                    Toast.show("用户未登录");
                                    return;
                                }
                                if (!init) {
                                    if (initHandler()) {
                                        init = true;
                                    }
                                    Log.runtime(TAG, "initHandler success");
                                    return;
                                }
                                String currentUid = UserMap.getCurrentUid();
                                Log.runtime(TAG, "onResume currentUid: " + currentUid);
                                if (!targetUid.equals(currentUid)) {
                                    if (currentUid != null) {
                                        initHandler();
                                        Log.record(TAG, "用户已切换");
                                        Toast.show("用户已切换");
                                        return;
                                    }
                                    HookUtil.INSTANCE.hookUser(appLloadPackageParam);
                                }
                                if (offline) {
                                    offline = false;
                                    ((Activity) param.thisObject).finish();
                                    Log.runtime(TAG, "Activity reLogin");
                                }
                                Log.runtime(TAG, "hook onResume after end");
                            }
                        });
                Log.runtime(TAG, "hook login successfully");
            } catch (Throwable t) {
                Log.runtime(TAG, "hook login err");
                Log.printStackTrace(TAG, t);
            }
            try {
                XposedHelpers.findAndHookMethod("android.app.Service", classLoader, "onCreate",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Service appService = (Service) param.thisObject;
                                if (!General.CURRENT_USING_SERVICE.equals(appService.getClass().getCanonicalName())) {
                                    return;
                                }
                                Log.runtime(TAG, "Service onCreate");
                                appContext = appService.getApplicationContext();
                                boolean isok = Detector.INSTANCE.isLegitimateEnvironment(appContext);
                                if (isok) {
                                    Detector.INSTANCE.dangerous(appContext);
                                    return;
                                }
                                String packageName = loadPackageParam.getPackageName();
                                String apkPath = loadPackageParam.getApplicationInfo().sourceDir;
//                                try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
//                                    Log.runtime(TAG, "hook dexkit successfully");
//                                }
                                service = appService;

                                dayCalendar = Calendar.getInstance();
                                if (initHandler()) {
                                    init = true;
                                }
                            }
                        }

                );
                Log.runtime(TAG, "hook service onCreate successfully");
            } catch (Throwable t) {
                Log.runtime(TAG, "hook service onCreate err");
                Log.printStackTrace(TAG, t);
            }

            try {
                XposedHelpers.findAndHookMethod("android.app.Service", classLoader, "onDestroy",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Service service = (Service) param.thisObject;
                                if (!General.CURRENT_USING_SERVICE.equals(service.getClass().getCanonicalName()))
                                    return;
                                Log.record(TAG, "支付宝前台服务被销毁");
                                Notify.updateStatusText("支付宝前台服务被销毁");
                                destroyHandler();
                                httpServer.stop();
                            }
                        });
            } catch (Throwable t) {
                Log.runtime(TAG, "hook service onDestroy err");
                Log.printStackTrace(TAG, t);
            }

            HookUtil.INSTANCE.hookOtherService(loadPackageParam);

            hooked = true;
            Log.runtime(TAG, "load success: " + loadPackageParam.getPackageName());
        }
    }


    private synchronized Boolean initHandler() {
        try {
            destroyHandler(); // 销毁之前的处理程序
            Model.initAllModel(); //在所有服务启动前装模块配置
            if (service == null) {
                return false;
            }
            String userId = HookUtil.INSTANCE.getUserId(appLloadPackageParam.getClassLoader());
            if (userId == null) {
                Log.record(TAG, "initHandler:用户未登录");
                Toast.show("initHandler:用户未登录");
                return false;
            }
            HookUtil.INSTANCE.hookUser(appLloadPackageParam);
            String startMsg = "芝麻粒-TK 开始初始化...";
            Log.record(TAG, startMsg);
            Log.record(TAG, "⚙️模块版本：" + modelVersion);
            Log.record(TAG, "📦应用版本：" + alipayVersion.getVersionString());
            Config.load(userId);//加载配置
            if (!Config.isLoaded()) {
                Log.record(TAG, "用户模块配置加载失败");
                Toast.show("用户模块配置加载失败");
                return false;
            }
            //闹钟权限申请
            if (!PermissionUtil.checkAlarmPermissions()) {
                Log.record(TAG, "❌ 支付宝无闹钟权限");
                mainHandler.postDelayed(
                        () -> {
                            if (!PermissionUtil.checkOrRequestAlarmPermissions(appContext)) {
                                Toast.show("请授予支付宝使用闹钟权限");
                            }
                        },
                        2000);
                return false;
            }
            // 检查并请求后台运行权限
            if (BaseModel.getBatteryPerm().getValue() && !init && !PermissionUtil.checkBatteryPermissions()) {
                Log.record(TAG, "支付宝无始终在后台运行权限");
                mainHandler.postDelayed(
                        () -> {
                            if (!PermissionUtil.checkOrRequestBatteryPermissions(appContext)) {
                                Toast.show("请授予支付宝始终在后台运行权限");
                            }
                        },
                        2000);
            }
            Notify.start(service);
            // 获取 BaseModel 实例
            BaseModel baseModel = Model.getModel(BaseModel.class);
            if (baseModel == null) {
                Log.error(TAG, "BaseModel 未找到 初始化失败");
                Notify.setStatusTextDisabled();
                return false;
            }
            // 检查 enableField 的值
            if (!baseModel.getEnableField().getValue()) {
                Log.record(TAG, "❌ 芝麻粒已禁用");
                Toast.show("❌ 芝麻粒已禁用");
                Notify.setStatusTextDisabled();
                return false;
            }
            // 保持唤醒锁，防止设备休眠
            if (BaseModel.getStayAwake().getValue()) {
                try {
                    PowerManager pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, service.getClass().getName());
                    wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/); // 确保唤醒锁在前台服务启动前
                } catch (Throwable t) {
                    Log.record(TAG, "唤醒锁申请失败:");
                    Log.printStackTrace(t);
                }
            }


            if (BaseModel.getNewRpc().getValue()) {
                rpcBridge = new NewRpcBridge();
            } else {
                rpcBridge = new OldRpcBridge();
            }
            rpcBridge.load();
            rpcVersion = rpcBridge.getVersion();
//            if (BaseModel.getNewRpc().getValue() && false) {
//                HookUtil.INSTANCE.hookRpcBridgeExtension(appLloadPackageParam, BaseModel.getSendHookData().getValue(), BaseModel.getSendHookDataUrl().getValue());
//                HookUtil.INSTANCE.hookDefaultBridgeCallback(appLloadPackageParam);
//            }
            Model.bootAllModel(classLoader);
            Status.load(userId);
            DataStore.INSTANCE.init(Files.CONFIG_DIR);
            updateDay(userId);
            String successMsg = "芝麻粒-TK 加载成功✨";
            Log.record(successMsg);
//            Toast.show(successMsg);
            if (BuildConfig.DEBUG) {
                try {
                    Log.runtime(TAG, "start service for debug rpc");
                    httpServer = new ModuleHttpServer(8080, "ET3vB^#td87sQqKaY*eMUJXP");
                    httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                    Toast.show("Debug RPC server started");
                } catch (IOException e) {
                    Log.printStackTrace(e);
                }
            } else {
                Log.runtime(TAG, "need not start service for debug rpc");
            }
            offline = false;
            return true;
        } catch (Throwable th) {
            Log.printStackTrace(TAG, "startHandler", th);
            Toast.show("芝麻粒加载失败 🎃");
            return false;
        }
    }

    /**
     * 销毁处理程序
     */
    static synchronized void destroyHandler() {
        try {
            if (service != null) {
                Status.unload();
                Notify.stop();
                RpcIntervalLimit.INSTANCE.clearIntervalLimit();
                Config.unload();
                UserMap.unload();
            }
            if (wakeLock != null) {
                wakeLock.release();
                wakeLock = null;
            }
            if (rpcBridge != null) {
                rpcVersion = null;
                rpcBridge.unload();
                rpcBridge = null;
            }
        } catch (Throwable th) {
            Log.runtime(TAG, "stopHandler err:");
            Log.printStackTrace(TAG, th);
        }
    }


    /**
     * 安排主任务在指定的延迟时间后执行，并更新通知中的下次执行时间。
     *
     * @param delayMillis 延迟执行的毫秒数
     */
    static void execDelayedHandler(long delayMillis) {
        if (mainHandler == null) {

            return;
        }
        try {
            long nextExecTime = System.currentTimeMillis() + delayMillis;
            String nt = nextExecTime > 0 ? "⏰ 下次执行 " + TimeUtil.getTimeStr(nextExecTime) : "";
            Notify.updateNextExecText(nextExecTime);
            Toast.show(nt);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }


    public static void updateDay(String userId) {
        Calendar nowCalendar = Calendar.getInstance();
        try {
            if (dayCalendar == null) {
                dayCalendar = (Calendar) nowCalendar.clone();
                dayCalendar.set(Calendar.HOUR_OF_DAY, 0);
                dayCalendar.set(Calendar.MINUTE, 0);
                dayCalendar.set(Calendar.SECOND, 0);
                Log.record(TAG, "初始化日期为：" + dayCalendar.get(Calendar.YEAR) + "-" + (dayCalendar.get(Calendar.MONTH) + 1) + "-" + dayCalendar.get(Calendar.DAY_OF_MONTH));
                return;
            }

            int nowYear = nowCalendar.get(Calendar.YEAR);
            int nowMonth = nowCalendar.get(Calendar.MONTH);
            int nowDay = nowCalendar.get(Calendar.DAY_OF_MONTH);
            if (dayCalendar.get(Calendar.YEAR) != nowYear || dayCalendar.get(Calendar.MONTH) != nowMonth || dayCalendar.get(Calendar.DAY_OF_MONTH) != nowDay) {
                dayCalendar = (Calendar) nowCalendar.clone();
                dayCalendar.set(Calendar.HOUR_OF_DAY, 0);
                dayCalendar.set(Calendar.MINUTE, 0);
                dayCalendar.set(Calendar.SECOND, 0);
                Log.record(TAG, "日期更新为：" + nowYear + "-" + (nowMonth + 1) + "-" + nowDay);
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }

        try {
            Status.save(nowCalendar);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    @SuppressLint({"ScheduleExactAlarm", "ObsoleteSdkInt", "MissingPermission"})
    private static Boolean setAlarmTask(long triggerAtMillis, PendingIntent operation) {
        try {
            AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            }
            Log.runtime(TAG, "setAlarmTask triggerAtMillis:" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(triggerAtMillis) + " operation:" + operation);
            return true;
        } catch (Throwable th) {
            Log.runtime(TAG, "setAlarmTask err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }

    private static Boolean unsetAlarmTask(PendingIntent operation) {
        try {
            if (operation != null) {
                AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(operation);
            }
            return true;
        } catch (Throwable th) {
            Log.runtime(TAG, "unsetAlarmTask err:");
            Log.printStackTrace(TAG, th);
        }
        return false;
    }


    @SuppressLint("ObsoleteSdkInt")
    private static int getPendingIntentFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        } else {
            return PendingIntent.FLAG_UPDATE_CURRENT;
        }
    }

    public static Object getMicroApplicationContext() {
        if (microApplicationContextObject == null) {
            try {
                Class<?> alipayApplicationClass = XposedHelpers.findClass(
                        "com.alipay.mobile.framework.AlipayApplication", classLoader
                );
                Object alipayApplicationInstance = XposedHelpers.callStaticMethod(
                        alipayApplicationClass, "getInstance"
                );
                if (alipayApplicationInstance == null) {
                    return null;
                }
                microApplicationContextObject = XposedHelpers.callMethod(
                        alipayApplicationInstance, "getMicroApplicationContext"
                );
            } catch (Throwable t) {
                Log.printStackTrace(t);
            }
        }
        return microApplicationContextObject;
    }

    public static Object getServiceObject(String service) {
        try {
            return XposedHelpers.callMethod(getMicroApplicationContext(), "findServiceByInterface", service);
        } catch (Throwable th) {
            Log.runtime(TAG, "getUserObject err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }

    public static Object getUserObject() {
        try {
            return XposedHelpers.callMethod(
                    getServiceObject(
                            XposedHelpers.findClass("com.alipay.mobile.personalbase.service.SocialSdkContactService", classLoader).getName()
                    ),
                    "getMyAccountInfoModelByLocal");
        } catch (Throwable th) {
            Log.runtime(TAG, "getUserObject err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }

    public static String getUserId() {
        try {
            Object userObject = getUserObject();
            Log.runtime(TAG, "getUserObject:" + userObject);
            if (userObject != null) {
                return (String) XposedHelpers.getObjectField(userObject, "userId");
            }
        } catch (Throwable th) {
            Log.runtime(TAG, "getUserId err");
            Log.printStackTrace(TAG, th);
        }
        return null;
    }


}
