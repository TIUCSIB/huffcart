# Huffcart release 混淆规则（zip-import-and-release-hygiene，design 决策 4）

# shim.c 经 NewGlobalRef 持有 LibretroCore 并以 GetMethodID 回调：
# 该方法既不可改名也不可移除（R8 视其为无调用者会整体裁掉），
# 否则原生层回调返回 null、全部输入失灵。默认规则已覆盖 native <methods> 符号名，
# 此处只需保住被 C 层调用的 Kotlin 方法（不写访问修饰符以匹配任意可见性）。
-keepclassmembers class com.huffcart.core.libretro.LibretroCore {
    int getInputMaskFromNative(int);
}
