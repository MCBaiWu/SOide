package com.soide.elf;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * ELF 哈希表 (.hash SysV 与 .gnu.hash) 的轻量级解析与查表。
 *
 * 主要用途：把 .dynsym 下标 <-> 名字做交叉验证，或在 .rela.plt 中按
 * r_info 的符号下标拿到对应的 dynsym 名字。
 */
public class HashLookup {

    public static final int HASH_SYSV = 1;
    public static final int HASH_GNU = 2;

    public final int kind;
    public final int nbucket;
    public final int symndx;        // SysV: 第一个动态符号的下标
    public final int maskwords;     // SysV: chain 数; GNU: bloom size
    public final int bloomShift;    // GNU
    public final long[] bloom;      // GNU
    public final long[] buckets;
    public final long[] chains;

    private HashLookup(int kind, int nbucket, int symndx, int maskwords, int bloomShift,
                       long[] bloom, long[] buckets, long[] chains) {
        this.kind = kind;
        this.nbucket = nbucket;
        this.symndx = symndx;
        this.maskwords = maskwords;
        this.bloomShift = bloomShift;
        this.bloom = bloom;
        this.buckets = buckets;
        this.chains = chains;
    }

    /** 解析 SysV .hash 节区。 */
    public static HashLookup parseSysV(byte[] data, long offset, long size, boolean is64Bit) {
        if (offset < 0 || size < 8) return null;
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.position((int) offset);
        int nbucket = buf.getInt();
        int symndx = buf.getInt();
        int maskwords = buf.getInt();
        if (nbucket <= 0 || nbucket > 1_000_000) return null;
        if (maskwords <= 0 || maskwords > 10_000_000) return null;

        long[] buckets = new long[nbucket];
        for (int i = 0; i < nbucket; i++) {
            buckets[i] = buf.getInt() & 0xffffffffL;
        }
        long bytesAfterBuckets = size - (8L + 4L * nbucket);
        int chainCount = (int) (bytesAfterBuckets / 4);
        if (chainCount < 0) chainCount = 0;
        long[] chains = new long[chainCount];
        for (int i = 0; i < chainCount; i++) {
            chains[i] = buf.getInt() & 0xffffffffL;
        }
        return new HashLookup(HASH_SYSV, nbucket, symndx, maskwords, 0, null, buckets, chains);
    }

    /** 解析 GNU .gnu.hash 节区。 */
    public static HashLookup parseGnu(byte[] data, long offset, long size) {
        if (offset < 0 || size < 16) return null;
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.position((int) offset);
        int nbucket = buf.getInt();
        int symndx = buf.getInt();
        int bloomSize = buf.getInt();
        int bloomShift = buf.getInt();
        if (nbucket <= 0 || nbucket > 1_000_000) return null;
        if (bloomSize <= 0 || bloomSize > 10_000_000) return null;

        long[] bloom = new long[bloomSize];
        for (int i = 0; i < bloomSize; i++) {
            bloom[i] = buf.getLong();
        }
        long[] buckets = new long[nbucket];
        for (int i = 0; i < nbucket; i++) {
            buckets[i] = buf.getInt() & 0xffffffffL;
        }
        long consumed = 16L + 8L * bloomSize + 4L * nbucket;
        long remaining = size - consumed;
        int chainCount = (int) (remaining / 4);
        if (chainCount < 0) chainCount = 0;
        long[] chains = new long[chainCount];
        for (int i = 0; i < chainCount; i++) {
            chains[i] = buf.getInt() & 0xffffffffL;
        }
        return new HashLookup(HASH_GNU, nbucket, symndx, bloomSize, bloomShift, bloom, buckets, chains);
    }

    /** SysV Hash 算法。 */
    public static long sysvHash(String name) {
        try {
            long h = com.soide.nativebridge.NativeBridge.sysvHash(name);
            if (h != 0L) return h & 0xffffffffL;
        } catch (Throwable ignored) {}
        return sysvHashJava(name);
    }

    /** GNU Hash 算法。 */
    public static long gnuHash(String name) {
        try {
            long h = com.soide.nativebridge.NativeBridge.gnuHash(name);
            if (h != 0L) return h & 0xffffffffL;
        } catch (Throwable ignored) {}
        return gnuHashJava(name);
    }

    private static long sysvHashJava(String name) {
        long h = 0;
        for (int i = 0; i < name.length(); i++) {
            h = (h << 4) + name.charAt(i);
            long g = h & 0xf0000000L;
            if (g != 0) h ^= g >> 24;
            h &= ~g;
        }
        return h & 0xffffffffL;
    }

    private static long gnuHashJava(String name) {
        long h = 5381L;
        for (int i = 0; i < name.length(); i++) {
            h = (h << 5) + h + (name.charAt(i) & 0xff);
        }
        return h & 0xffffffffL;
    }

    /**
     * GNU: 通过名字快速查 dynsym 下标。失败返回 -1。
     * 利用 bloom filter + bucket + chain。
     */
    public int lookupGnu(String name) {
        if (kind != HASH_GNU || name == null) return -1;
        long h = gnuHash(name);
        if (bloom == null) return -1;
        int bIdx = (int) (h % bloom.length);
        long bit1 = 1L << (h % 64);
        long bit2 = 1L << ((h >> 6) % 64);
        long word = bloom[bIdx];
        if ((word & (bit1 | bit2)) != (bit1 | bit2)) return -1;
        if (nbucket <= 0) return -1;
        int bucket = (int) (h % nbucket);
        long sym = buckets[bucket];
        if (sym == 0) return -1;
        int visited = 0;
        while (sym < chains.length && visited++ < 1_000_000) {
            long chain = chains[(int) sym];
            if ((chain | 1L) == (h | 1L)) {
                return (int) sym;
            }
            if ((chain & 1L) == 1L) {
                return -1;
            }
            sym++;
        }
        return -1;
    }

    /**
     * SysV: 通过名字快速查 dynsym 下标。失败返回 -1。
     */
    public int lookupSysV(String name) {
        if (kind != HASH_SYSV || name == null) return -1;
        long h = sysvHash(name);
        if (nbucket <= 0) return -1;
        int bucket = (int) (h % nbucket);
        long sym = buckets[bucket];
        int visited = 0;
        while (sym != 0 && sym < chains.length && visited++ < 1_000_000) {
            long chain = chains[(int) sym];
            if (chain == h) {
                return (int) sym;
            }
            if ((chain & 1L) == 1L) {
                return -1;
            }
            sym++;
        }
        return -1;
    }

    /** 便捷：尝试两种哈希。 */
    public int lookup(String name) {
        if (kind == HASH_GNU) {
            int i = lookupGnu(name);
            if (i >= 0) return i;
        }
        if (kind == HASH_SYSV) {
            return lookupSysV(name);
        }
        return -1;
    }

    /** 为一组 dynsym 名字建立 name -> index 的 map。 */
    public static Map<String, Integer> buildNameIndex(HashLookup gnu, HashLookup sysv, String[] dynsymNames) {
        Map<String, Integer> out = new HashMap<>();
        if (dynsymNames == null) return out;
        for (int i = 0; i < dynsymNames.length; i++) {
            String n = dynsymNames[i];
            if (n != null && !n.isEmpty()) out.put(n, i);
        }
        return out;
    }
}
