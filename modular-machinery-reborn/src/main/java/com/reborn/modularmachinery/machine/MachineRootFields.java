package com.reborn.modularmachinery.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.reborn.modularmachinery.config.ModConfig;
import net.minecraft.world.phys.AABB;

/**
 * 机器定义里**根字段**的解析，逐条对应旧版的 {@code DynamicMachinePreDeserializer}。
 *
 * <h2>为什么要单独一个类</h2>
 *
 * <p>旧版把这些「读一个根字段、错了就抛一条明确的话」的方法集中在一个 pre-deserializer 里，本版照做，
 * 理由有两条：
 *
 * <ol>
 *   <li><b>错误文案属于接口的一部分。</b>包作者写错一个字段时看到的那句话，就是他与这个模组之间的
 *       唯一反馈。集中在一处才可能保证它与旧版逐条对应（{@link #color} 的两条就是旧版原文的意思）。</li>
 *   <li><b>可测。</b>这些方法是纯函数（{@code JsonObject} → 值），所以离线复现器可以直接驱动它们，
 *       不必建一台机器、也不必进游戏。波 6 的其它项都做不到这一点。</li>
 * </ol>
 */
public final class MachineRootFields {

    private MachineRootFields() {
    }

    /**
     * 定义里的 {@code color}：**十六进制字符串**，例如 {@code "FF00FF"}。
     *
     * <p>与旧版 {@code DynamicMachinePreDeserializer#getColor} 的对应关系，以及一处**有意差异**，
     * 都写在 {@link MachineDefinition#parseColor} 上——这里只负责把它接到解析链里。
     *
     * @param root  定义的根对象
     * @param where 出错信息里用来指认是哪个文件（作者据此找到那一行）
     * @return 颜色值；没有 {@code color} 字段时返回 {@code general.general-casing-color}
     * @throws IllegalArgumentException 字段存在但不是「恰好六位十六进制」（含旧版的两条错误情形）
     */
    public static int color(JsonObject root, String where) {
        JsonElement element = root.get("color");
        if (element == null) {
            return ModConfig.generalCasingColor();
        }
        return MachineDefinition.parseColor(root, where);
    }

    /**
     * 定义里的 {@code prefix}：预览标题左侧那 36px 的短标签。
     *
     * <p>与旧版 {@code DynamicMachinePreDeserializer#getPrefix} 的对应关系：
     * <ul>
     *   <li>字段缺省 → {@code null}（没有前缀，机器名占满标题行）；</li>
     *   <li>字段存在但不是字符串 → 拒绝，并说明必须是字符串；</li>
     *   <li>字段是空串 → 逐字沿用旧版原文 {@code Invalid/Missing 'prefix' !}。</li>
     * </ul>
     *
     * <p>本版显式要求 JSON 字符串；类型不对就在解析期拒绝，而不是让一个本该写字符串的字段
     * 带着别的类型进入定义。旧版走 {@code net.minecraft.util.JsonUtils.getString}，本轮没有重新验证
     * 它对数字/布尔的处理，因此这里不把“更严”写成与旧版的具体差异。
     *
     * @param root  定义的根对象
     * @param where 出错信息里用来指认是哪个文件
     * @return 前缀字面量；没有该字段时为 {@code null}
     * @throws com.google.gson.JsonParseException 字段存在但不是 JSON 字符串，或是空串
     */
    @javax.annotation.Nullable
    public static String prefix(JsonObject root, String where) {
        JsonElement element = root.get("prefix");
        if (element == null) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new com.google.gson.JsonParseException(
                    "'prefix' must be a string, but " + element + " was given in " + where + ".");
        }
        String text = element.getAsString();
        if (text.isEmpty()) {
            // 旧版原文，不补 where：这条消息本身就是包作者熟悉的句子。
            throw new com.google.gson.JsonParseException("Invalid/Missing 'prefix' !");
        }
        return text;
    }

    /**
     * 定义里的 {@code geo-model}：GeckoLib 自定义控制器模型的五个资源。
     *
     * <p>没有这个字段时返回 {@code null}（这台机器用普通方块模型），与旧版
     * {@code DynamicMachineModelRegistry.getMachineDefaultModel(...) == null} 的「没有自定义模型」是同一状态。
     * 解析规则与命名空间改写都写在 {@link GeoModelSpec#parse} 上。
     *
     * @throws com.google.gson.JsonParseException 字段存在但内容不合法
     */
    @javax.annotation.Nullable
    public static GeoModelSpec geoModel(JsonObject root, String where) {
        return GeoModelSpec.parse(root, where);
    }

    /**
     * 定义里的 {@code hide-components-when-formed}：结构成型后是否隐藏组件。
     *
     * <p>旧版 {@code DynamicMachinePreDeserializer#getHideComponentsWhenFormed} 的默认值是 false，
     * 且**只接受 JSON 布尔**，写成字符串 {@code "true"} 会抛同一条错误。0.62.9–0.70.0 因为不求值而照单全收，
     * 于是同一份定义在旧版被拒、在本版被接受。这里恢复旧版的拒绝，而不是「宽容地读懂」：
     * 一个写错的布尔值不该悄悄变成 false，那会让作者以为开关生效了。错误文案照旧版原文。
     *
     * @return 字段缺省时为 {@code false}
     * @throws com.google.gson.JsonParseException 字段存在但不是 JSON 布尔
     */
    public static boolean hideComponentsWhenFormed(JsonObject root, String where) {
        JsonElement element = root.get("hide-components-when-formed");
        if (element == null) {
            return false;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        throw new com.google.gson.JsonParseException(
                "'hide-components-when-formed' has to be either 'true' or 'false'!");
    }

    /**
     * 定义里的 {@code controller-bounding-box}：控制器选中箱/碰撞箱的六个数字
     * （{@code minX, minY, minZ, maxX, maxY, maxZ}）。
     *
     * <p>与旧版 {@code DynamicMachine.java:595-607 / 660-663} 逐条对应：
     * <ul>
     *   <li>缺省 → {@link MachineDefinition#FULL_CONTROLLER_BOUNDING_BOX}（整块）；</li>
     *   <li>必须是恰好六个元素的数组，否则拒绝，消息以旧版原文 {@code Invalid 'controllerBoundingBox'!} 开头；</li>
     *   <li>每个元素走 {@code getAsDouble()}，所以数字字符串（如 {@code "0.25"}）与旧版一样被接受；</li>
     *   <li><b>不检查</b>范围、也不检查 {@code min <= max}——旧版同样只做类型与长度检查。</li>
     * </ul>
     *
     * <p>非法元素的类型错误被包进同一条消息里，而不是让 Gson 的
     * {@code UnsupportedOperationException} 直接冒到加载日志：包作者需要的是一句能定位字段的话。
     *
     * @param root  定义的根对象
     * @param where 出错信息里用来指认是哪个文件
     * @return 控制器盒子；没有该字段时为整块
     * @throws JsonParseException 字段存在但不是六个数字组成的数组
     */
    public static AABB controllerBoundingBox(JsonObject root, String where) {
        JsonElement element = root.get("controller-bounding-box");
        if (element == null) {
            return MachineDefinition.FULL_CONTROLLER_BOUNDING_BOX;
        }
        if (!element.isJsonArray()) {
            throw new JsonParseException("Invalid 'controllerBoundingBox'! Expected an array of six numbers in "
                    + where + ", but found " + element + ".");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 6) {
            throw new JsonParseException("Invalid 'controllerBoundingBox'! Expected an array of six numbers in "
                    + where + ", but found " + array.size() + " element(s).");
        }
        double[] values = new double[6];
        for (int i = 0; i < values.length; i++) {
            try {
                values[i] = com.reborn.modularmachinery.util.JsonNumbers.finite(array.get(i), true,
                        where + " controller-bounding-box[" + i + "]");
            } catch (RuntimeException e) {
                throw new JsonParseException("Invalid 'controllerBoundingBox'! Expected an array of six numbers in "
                        + where + ", but element " + i + " is " + array.get(i) + ".", e);
            }
        }
        return new AABB(values[0], values[1], values[2], values[3], values[4], values[5]);
    }
}
