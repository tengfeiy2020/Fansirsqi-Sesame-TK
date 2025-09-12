package fansirsqi.xposed.sesame.model;


import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField;
import lombok.Getter;

/**
 * 基础配置模块
 */
public class BaseModel extends Model {
    private static final String TAG = "BaseModel";

    /**
     * 是否保持唤醒状态
     */
    @Getter
    public static final BooleanModelField stayAwake = new BooleanModelField("stayAwake", "保持唤醒", true);

    /**
     * 是否启用新接口（最低支持版本 v10.3.96.8100）
     */
    @Getter
    public static final BooleanModelField newRpc = new BooleanModelField("newRpc", "使用新接口(最低支持v10.3.96.8100)", true);

    /**
     * 是否申请支付宝的后台运行权限
     */
    @Getter
    public static final BooleanModelField batteryPerm = new BooleanModelField("batteryPerm", "为支付宝申请后台运行权限", true);

    /**
     * 是否显示气泡提示
     */
    @Getter
    public static final BooleanModelField showToast = new BooleanModelField("showToast", "气泡提示", true);
    /**
     * 气泡提示的纵向偏移量
     */
    @Getter
    public static final IntegerModelField toastOffsetY = new IntegerModelField("toastOffsetY", "气泡纵向偏移", 99);
    /**
     * 只显示中文并设置时区
     */
    @Getter
    public static final BooleanModelField languageSimplifiedChinese = new BooleanModelField("languageSimplifiedChinese", "只显示中文并设置时区", true);
    /**
     * 是否开启状态栏禁删
     */
    @Getter
    public static final BooleanModelField enableOnGoing = new BooleanModelField("enableOnGoing", "开启状态栏禁删", false);


    @Override
    public String getName() {
        return "基础";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.BASE;
    }

    @Override
    public String getIcon() {
        return "BaseModel.png";
    }

    @Override
    public String getEnableFieldName() {
        return "启用模块";
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(stayAwake);//是否保持唤醒状态
        modelFields.addField(newRpc);//是否启用新接口
        modelFields.addField(batteryPerm);//是否申请支付宝的后台运行权限
        modelFields.addField(showToast);//是否显示气泡提示
        modelFields.addField(enableOnGoing);//是否开启状态栏禁删
        modelFields.addField(languageSimplifiedChinese);//是否只显示中文并设置时区
        modelFields.addField(toastOffsetY);//气泡提示的纵向偏移量
        return modelFields;
    }

}
