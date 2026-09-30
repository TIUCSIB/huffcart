#!/bin/bash
# 编译 FCEUmm libretro 核心 → app/src/main/jniLibs/<abi>/libfceumm_libretro.so
#
# 用法（必须从 ASCII 路径的项目映射盘运行，如 H:，见 design.md Risks）：
#   H:\huffcart> scripts/build-core.sh
# 版本由 scripts/CORE_COMMIT.lock 锁定；首次运行写入，之后按 lock 复现。
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$ROOT_DIR/build/libretro"
FCEUMM_DIR="$BUILD_DIR/libretro-fceumm"
LOCK_FILE="$SCRIPT_DIR/CORE_COMMIT.lock"
JNI_LIBS_DIR="$ROOT_DIR/app/src/main/jniLibs"
API=26

# --- 环境 ---------------------------------------------------------------
case "$ROOT_DIR" in
    *[!\ -~]*) echo "ERROR: 项目路径含非 ASCII 字符：$ROOT_DIR（请经 H: 盘映射运行）" >&2; exit 1 ;;
esac

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-D:/sdk}}"
SDK_U="$(cygpath -u "$SDK_ROOT" 2>/dev/null || echo "$SDK_ROOT")"
NDK_ROOT="$(ls -1d "$SDK_U"/ndk/* 2>/dev/null | sort -V | tail -1)"
[ -n "$NDK_ROOT" ] && [ -d "$NDK_ROOT/toolchains/llvm/prebuilt" ] || { echo "ERROR: 未找到 NDK（$SDK_U/ndk）" >&2; exit 1; }
BIN="$NDK_ROOT/toolchains/llvm/prebuilt/windows-x86_64/bin"
MAKE="$NDK_ROOT/prebuilt/windows-x86_64/bin/make.exe"
[ -x "$MAKE" ] || MAKE=make
JOBS=$(nproc 2>/dev/null || echo 4)
echo "== NDK: $NDK_ROOT"

# --- 源码与版本锁定 ------------------------------------------------------
mkdir -p "$BUILD_DIR"
if [ -f "$LOCK_FILE" ]; then
    COMMIT="$(tr -d ' \r\n' < "$LOCK_FILE")"
    if [ ! -d "$FCEUMM_DIR" ]; then
        git clone --depth 1 https://github.com/libretro/libretro-fceumm.git "$FCEUMM_DIR"
    fi
    CUR="$(git -C "$FCEUMM_DIR" rev-parse HEAD 2>/dev/null || true)"
    if [ "$CUR" != "$COMMIT" ]; then
        echo "== checkout 锁定版本 $COMMIT"
        git -C "$FCEUMM_DIR" fetch --depth 1 origin "$COMMIT"
        git -C "$FCEUMM_DIR" checkout --force "$COMMIT"
    fi
else
    echo "== 首次克隆，记录锁定版本"
    git clone --depth 1 https://github.com/libretro/libretro-fceumm.git "$FCEUMM_DIR"
    git -C "$FCEUMM_DIR" rev-parse HEAD > "$LOCK_FILE"
    cat "$LOCK_FILE"
fi

# --- 逐 ABI 构建 ---------------------------------------------------------
build_abi() { # $1=abi  $2=clang-target
    local abi="$1" target="$2"
    echo "== building $abi ($target) ..."
    "$MAKE" -C "$FCEUMM_DIR" -f Makefile.libretro platform=unix clean >/dev/null 2>&1 || true
    # Windows 下 make 内联链接的命令行（~700 个 .o）超出 32K 上限，容许其失败，
    # 编译产物（.o）就位后改用响应文件 @rsp 由 clang 完成链接
    "$MAKE" -C "$FCEUMM_DIR" -f Makefile.libretro platform=unix -j"$JOBS" \
        CC="$BIN/${target}-clang" \
        AR="$BIN/llvm-ar" \
        LDFLAGS='-lm -Wl,-z,max-page-size=16384' || true
    if [ ! -f "$FCEUMM_DIR/fceumm_libretro.so" ]; then
        {
            echo "-shared"
            echo "-o fceumm_libretro.so"
            echo "-Wl,--version-script=src/drivers/libretro/link.T"
            echo "-Wl,-no-undefined"
            find "$FCEUMM_DIR/src" -name '*.o' | sed "s|^$FCEUMM_DIR/||" | sort
            echo "-lm"
            echo "-Wl,-z,max-page-size=16384"
        } > "$FCEUMM_DIR/link.rsp"
        ( cd "$FCEUMM_DIR" && "$BIN/${target}-clang" @link.rsp )
    fi
    mkdir -p "$JNI_LIBS_DIR/$abi"
    cp "$FCEUMM_DIR/fceumm_libretro.so" "$JNI_LIBS_DIR/$abi/libfceumm_libretro.so"
    ls -la "$JNI_LIBS_DIR/$abi/libfceumm_libretro.so"
}

build_abi arm64-v8a "aarch64-linux-android$API"
build_abi x86_64 "x86_64-linux-android$API"

echo "== DONE: 两份 libfceumm_libretro.so 就绪"
