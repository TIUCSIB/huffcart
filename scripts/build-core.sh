#!/bin/bash
# 编译 libretro 核心 → app/src/main/jniLibs/<abi>/lib<core>_libretro.so
# (gb-gbc-platform 任务 2.1:泛化为按核心构建,各核心独立仓库/产物/版本锁)
#
# 用法（必须从 ASCII 路径的项目映射盘运行，如 H:，见 design.md Risks）：
#   H:\huffcart> scripts/build-core.sh            # 全部核心
#   H:\huffcart> scripts/build-core.sh fceumm     # 单核心
#   H:\huffcart> scripts/build-core.sh gambatte
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$ROOT_DIR/build/libretro"
JNI_LIBS_DIR="$ROOT_DIR/app/src/main/jniLibs"
API=26

# --- 核心注册表：name repo_dir lock_file makefile out_so -------------------------
#   name:     逻辑名（jniLibs 产物为 lib<name>_libretro.so）
#   repo_dir: build/libretro 下的克隆目录
#   lock:     版本锁文件（scripts/ 下，首跑记录 commit，此后按锁复现）
#   makefile: 核心仓库内的 libretro Makefile
#   out_so:   构建产物相对名（仓库根或子目录，find 兜底）
CORE_FCEUMM=(fceumm libretro-fceumm CORE_COMMIT.lock Makefile.libretro fceumm_libretro.so)
CORE_GAMBATTE=(gambatte gambatte-libretro GAMBATTE_COMMIT.lock Makefile.libretro gambatte_libretro.so)
REPO_BASE="https://github.com/libretro"

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

# --- 通用按核心构建 ------------------------------------------------------
# $1=name $2=repo_dir $3=lock_file $4=makefile $5=out_so
build_core_abis() {
    local name="$1" repo_dir="$2" lock_file="$3" makefile="$4" out_so="$5"
    build_core "$name" "$repo_dir" "$lock_file" "$makefile" "$out_so" arm64-v8a "aarch64-linux-android$API"
    build_core "$name" "$repo_dir" "$lock_file" "$makefile" "$out_so" x86_64 "x86_64-linux-android$API"
}

# $1=name $2=repo_dir $3=lock_file $4=makefile $5=out_so $6=abi $7=clang-target
build_core() {
    local name="$1" repo_dir="$2" lock_file="$3" makefile="$4" out_so="$5" abi="$6" target="$7"
    local dir="$BUILD_DIR/$repo_dir" lock="$SCRIPT_DIR/$lock_file"

    echo "== building core $name for $abi ($target) ..."
    mkdir -p "$BUILD_DIR"
    if [ -f "$lock" ]; then
        local commit
        commit="$(tr -d ' \r\n' < "$lock")"
        if [ ! -d "$dir" ]; then
            git clone --depth 1 "$REPO_BASE/$repo_dir.git" "$dir"
        fi
        local cur
        cur="$(git -C "$dir" rev-parse HEAD 2>/dev/null || true)"
        if [ "$cur" != "$commit" ]; then
            echo "== checkout 锁定版本 $commit"
            git -C "$dir" fetch --depth 1 origin "$commit"
            git -C "$dir" checkout --force "$commit"
        fi
    else
        echo "== 首次克隆，记录锁定版本"
        git clone --depth 1 "$REPO_BASE/$repo_dir.git" "$dir"
        git -C "$dir" rev-parse HEAD > "$lock"
        cat "$lock"
    fi

    "$MAKE" -C "$dir" -f "$makefile" platform=unix clean >/dev/null 2>&1 || true
    # Windows 下 make 内联链接的命令行（大量 .o）可能超出 32K 上限，容许其失败，
    # 编译产物（.o）就位后改用响应文件 @rsp 由 clang++ 完成链接（C++ 驱动对 C 核心无害）
    "$MAKE" -C "$dir" -f "$makefile" platform=unix -j"$JOBS" \
        CC="$BIN/${target}-clang" \
        CXX="$BIN/${target}-clang++" \
        AR="$BIN/llvm-ar" \
        LDFLAGS='-lm -static-libstdc++ -Wl,-z,max-page-size=16384' || true

    local built_so
    built_so="$(find "$dir" -name "$out_so" 2>/dev/null | head -1)"
    if [ -z "$built_so" ]; then
        local version_script
        version_script="$(find "$dir/src" "$dir/libretro" -name 'link.T' 2>/dev/null | head -1)"
        {
            echo "-shared"
            echo "-o $out_so"
            [ -n "$version_script" ] && echo "-Wl,--version-script=$(cygpath -m "$version_script" 2>/dev/null || echo "$version_script")"
            echo "-Wl,-no-undefined"
            find "$dir" -name '*.o' | sed "s|^$dir/||" | sort
            echo "-lm"
            echo "-static-libstdc++"
            echo "-Wl,-z,max-page-size=16384"
        } > "$dir/link.rsp"
        ( cd "$dir" && "$BIN/${target}-clang++" -static-libstdc++ @link.rsp )
        built_so="$dir/$out_so"
    fi
    mkdir -p "$JNI_LIBS_DIR/$abi"
    cp "$built_so" "$JNI_LIBS_DIR/$abi/lib${name}_libretro.so"
    ls -la "$JNI_LIBS_DIR/$abi/lib${name}_libretro.so"
}

# --- 入口 ---------------------------------------------------------------
SELECT="${1:-all}"
case "$SELECT" in
    fceumm)
        build_core_abis "${CORE_FCEUMM[@]}"
        echo "== DONE: libfceumm_libretro.so 就绪"
        ;;
    gambatte)
        build_core_abis "${CORE_GAMBATTE[@]}"
        echo "== DONE: libgambatte_libretro.so 就绪"
        ;;
    all)
        build_core_abis "${CORE_FCEUMM[@]}"
        build_core_abis "${CORE_GAMBATTE[@]}"
        echo "== DONE: 两套核心 .so 就绪"
        ;;
    *)
        echo "用法: build-core.sh [all|fceumm|gambatte]" >&2
        exit 1
        ;;
esac
