#!/usr/bin/env bash
# Installs the musl cross-toolchain plus a musl-built zlib, as required by
# `native-image --static --libc=musl`, which links porelo's hooks. Run on a snapshot as its build user.
set -euo pipefail

# @pinned.sh@
# porelo-machines puts the rule for pinned downloads in place of the line above; run alone, the
# installer takes it from the pinned.sh beside it.
if ! declare -F pinned_fetch >/dev/null; then
    rule="$(dirname "${BASH_SOURCE[0]}")/pinned.sh"
    [ -f "$rule" ] || {
        echo "install-musl.sh needs pinned.sh beside it: $rule is missing" >&2
        exit 1
    }
    # shellcheck source=/dev/null
    . "$rule"
fi

PREFIX="${MUSL_PREFIX:-$HOME/.local/opt}"
TC="$PREFIX/x86_64-linux-musl-native"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

ZLIB_VERSION=1.3.1

# Both downloads follow the rule for pinned downloads: their digests are what is trusted, and a
# source that does not serve the file - musl.cc's certificate expired once, and GitHub's runners
# cannot reach it at all - gives way to the organization's mirror, which keeps it by its digest.
MUSL_URL=https://musl.cc/x86_64-linux-musl-native.tgz
MUSL_SHA256=eb1db6f0f3c2bdbdbfb993d7ef7e2eeef82ac1259f6a6e1757c33a97dbcef3ad
ZLIB_URL=https://zlib.net/fossils/zlib-$ZLIB_VERSION.tar.gz
ZLIB_SHA256=9a93b2b7dfdac77ceba5a558a580e74667dd6fede4585b91eefb60f03b72df23

mkdir -p "$PREFIX"

if [ ! -x "$TC/bin/x86_64-linux-musl-gcc" ]; then
    echo "==> downloading musl toolchain"
    pinned_fetch "$MUSL_URL" "$MUSL_SHA256" "$WORK/musl.tgz"
    echo "==> extracting to $TC"
    tar -xzf "$WORK/musl.tgz" -C "$PREFIX"
else
    echo "==> musl toolchain already present at $TC"
fi

export PATH="$TC/bin:$PATH"
x86_64-linux-musl-gcc --version | head -1

if [ ! -f "$TC/lib/libz.a" ]; then
    echo "==> building zlib $ZLIB_VERSION against musl"
    pinned_fetch "$ZLIB_URL" "$ZLIB_SHA256" "$WORK/zlib.tar.gz"
    tar -xzf "$WORK/zlib.tar.gz" -C "$WORK"
    cd "$WORK/zlib-$ZLIB_VERSION"
    CC=x86_64-linux-musl-gcc ./configure --static --prefix="$TC"
    make -j"$(nproc)" >/dev/null
    make install >/dev/null
else
    echo "==> musl zlib already present"
fi

echo "==> installed:"
ls -la "$TC/bin/x86_64-linux-musl-gcc" "$TC/lib/libz.a"
echo "==> add to PATH: $TC/bin"
