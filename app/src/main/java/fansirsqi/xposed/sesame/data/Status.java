package fansirsqi.xposed.sesame.data;

import com.fasterxml.jackson.databind.JsonMappingException;

import java.util.Calendar;

import fansirsqi.xposed.sesame.util.Files;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.maps.UserMap;
import fansirsqi.xposed.sesame.util.StringUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;
import lombok.Data;
import lombok.Getter;

@Data
public class Status {
    private static final String TAG = Status.class.getSimpleName();
    @Getter
    private static final Status INSTANCE = new Status();
    private Long saveTime = 0L;

    /**
     * 加载状态文件
     *
     * @return 状态对象
     */
    public static synchronized Status load(String currentUid) {
//        String currentUid = UserMap.getCurrentUid();
        if (StringUtil.isEmpty(currentUid)) {
            Log.runtime(TAG, "用户为空，状态加载失败");
            throw new RuntimeException("用户为空，状态加载失败");
        }
        try {
            java.io.File statusFile = Files.getStatusFile(currentUid);
            if (statusFile.exists()) {
                Log.runtime(TAG, "加载 status.json");
                String json = Files.readFromFile(statusFile);
                if (!json.trim().isEmpty()) {
                    JsonUtil.copyMapper().readerForUpdating(getINSTANCE()).readValue(json);
                    String formatted = JsonUtil.formatJson(getINSTANCE());
                    if (formatted != null && !formatted.equals(json)) {
                        Log.runtime(TAG, "重新格式化 status.json");
                        Files.write2File(formatted, statusFile);
                    }
                } else {
                    Log.runtime(TAG, "配置文件为空，初始化默认配置");
                    initializeDefaultConfig(statusFile);
                }
            } else {
                Log.runtime(TAG, "配置文件不存在，初始化默认配置");
                initializeDefaultConfig(statusFile);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            Log.runtime(TAG, "状态文件格式有误，已重置");
            resetAndSaveConfig();
        }
        if (getINSTANCE().getSaveTime() == null) {
            getINSTANCE().setSaveTime(System.currentTimeMillis());
        }
        return getINSTANCE();
    }

    /**
     * 初始化默认配置
     *
     * @param statusFile 状态文件
     */
    private static void initializeDefaultConfig(java.io.File statusFile) {
        try {
            JsonUtil.copyMapper().updateValue(getINSTANCE(), new Status());
            Log.runtime(TAG, "初始化 status.json");
            Files.write2File(JsonUtil.formatJson(getINSTANCE()), statusFile);
        } catch (JsonMappingException e) {
            Log.printStackTrace(TAG, e);
            throw new RuntimeException("初始化配置失败", e);
        }
    }

    /**
     * 重置配置并保存
     */
    private static void resetAndSaveConfig() {
        try {
            JsonUtil.copyMapper().updateValue(getINSTANCE(), new Status());
            Files.write2File(JsonUtil.formatJson(getINSTANCE()), Files.getStatusFile(UserMap.getCurrentUid()));
        } catch (JsonMappingException e) {
            Log.printStackTrace(TAG, e);
            throw new RuntimeException("重置配置失败", e);
        }
    }

    public static synchronized void unload() {
        try {
            JsonUtil.copyMapper().updateValue(getINSTANCE(), new Status());
        } catch (JsonMappingException e) {
            Log.printStackTrace(TAG, e);
        }
    }

    public static synchronized void save() {
        save(Calendar.getInstance());
    }

    public static synchronized void save(Calendar nowCalendar) {
        String currentUid = UserMap.getCurrentUid();
        if (StringUtil.isEmpty(currentUid)) {
            Log.record(TAG, "用户为空，状态保存失败");
            throw new RuntimeException("用户为空，状态保存失败");
        }
        if (updateDay(nowCalendar)) {
            Log.runtime(TAG, "重置 statistics.json");
        } else {
            Log.runtime(TAG, "保存 status.json");
        }
        long lastSaveTime = getINSTANCE().getSaveTime();
        try {
            getINSTANCE().setSaveTime(System.currentTimeMillis());
            Files.write2File(JsonUtil.formatJson(getINSTANCE()), Files.getStatusFile(currentUid));
        } catch (Exception e) {
            getINSTANCE().setSaveTime(lastSaveTime);
            throw e;
        }
    }

    public static Boolean updateDay(Calendar nowCalendar) {
        if (TimeUtil.isLessThanSecondOfDays(getINSTANCE().getSaveTime(), nowCalendar.getTimeInMillis())) {
            Status.unload();
            return true;
        } else {
            return false;
        }
    }


}