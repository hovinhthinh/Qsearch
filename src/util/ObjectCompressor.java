package util;

import io.airlift.compress.zstd.ZstdCompressor;
import io.airlift.compress.zstd.ZstdDecompressor;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;

public class ObjectCompressor {
    private static final int MAX_N_COMPRESSOR = 1;
    private static final int MAX_N_DECOMPRESSOR = 4;
    private static final int MAX_OUTPUT_SIZE = 100000000;

    private static class CompressorInstance {
        public ZstdCompressor compressor = new ZstdCompressor();
        public byte[] output = new byte[MAX_OUTPUT_SIZE];
    }

    private static class DecompressorInstance {
        public ZstdDecompressor decompressor = new ZstdDecompressor();
        public byte[] output = new byte[MAX_OUTPUT_SIZE];
    }


    private static final ArrayBlockingQueue<CompressorInstance> COMPRESSOR_QUEUE;
    private static final ArrayBlockingQueue<DecompressorInstance> DECOMPRESSOR_QUEUE;

    static {
        COMPRESSOR_QUEUE = new ArrayBlockingQueue<>(MAX_N_COMPRESSOR);
        for (int i = 0; i < MAX_N_COMPRESSOR; ++i) {
            COMPRESSOR_QUEUE.add(new CompressorInstance());
        }
        DECOMPRESSOR_QUEUE = new ArrayBlockingQueue<>(MAX_N_DECOMPRESSOR);
        for (int i = 0; i < MAX_N_DECOMPRESSOR; ++i) {
            DECOMPRESSOR_QUEUE.add(new DecompressorInstance());
        }
    }

    private static final Logger LOG = Logger.getLogger(ObjectCompressor.class.getName());

    public static byte[] compressStringIntoByteArray(String str) {
        CompressorInstance ci = null;
        try {
            byte[] input = str.getBytes("UTF-8");
            ci = COMPRESSOR_QUEUE.take();
            int comLen = ci.compressor.compress(input, 0, input.length, ci.output, 0, MAX_OUTPUT_SIZE);
            return Arrays.copyOf(ci.output, comLen);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.info("error when compressing");
            return null;
        } finally {
            try {
                COMPRESSOR_QUEUE.put(ci);
            } catch (Exception e) {
            }
        }
    }

    public static String decompressByteArrayIntoString(byte[] arr) {
        DecompressorInstance di = null;
        try {
            di = DECOMPRESSOR_QUEUE.take();
            int decomLen = di.decompressor.decompress(arr, 0, arr.length, di.output, 0, MAX_OUTPUT_SIZE);
            return new String(di.output, 0, decomLen, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            LOG.info("error when decompressing");
            return null;
        } finally {
            try {
                DECOMPRESSOR_QUEUE.put(di);
            } catch (Exception e) {
            }
        }
    }

    public static byte[] compressSerializableIntoByteArray(Serializable object) {
        CompressorInstance ci = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(object);
            oos.close();
            byte[] input = bos.toByteArray();
            ci = COMPRESSOR_QUEUE.take();
            int comLen = ci.compressor.compress(input, 0, input.length, ci.output, 0, MAX_OUTPUT_SIZE);
            return Arrays.copyOf(ci.output, comLen);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.info("error when compressing");
            return null;
        } finally {
            try {
                COMPRESSOR_QUEUE.put(ci);
            } catch (Exception e) {
            }
        }
    }

    public static <T extends Serializable> T decompressByteArrayIntoSerializable(byte[] arr, Class<T> cls) {
        DecompressorInstance di = null;
        try {
            di = DECOMPRESSOR_QUEUE.take();
            int decomLen = di.decompressor.decompress(arr, 0, arr.length, di.output, 0, MAX_OUTPUT_SIZE);
            ByteArrayInputStream bis = new ByteArrayInputStream(di.output, 0, decomLen);
            ObjectInputStream ois = new ObjectInputStream(bis);
            T res = (T) ois.readObject();
            ois.close();
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            LOG.info("error when decompressing");
            return null;
        } finally {
            try {
                DECOMPRESSOR_QUEUE.put(di);
            } catch (Exception e) {
            }
        }
    }
}
