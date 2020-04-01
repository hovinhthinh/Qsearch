package util.distributed.hadoop;

import org.apache.hadoop.io.compress.BZip2Codec;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.FlatMapFunction;
import util.distributed.String2StringMap;

import java.util.LinkedList;
import java.util.List;

public class SparkMapJob {
    private static class Mapper implements FlatMapFunction<String, String> {
        private String mapperClass;
        private transient String2StringMap mapper;

        public Mapper(String mapperClass) {
            this.mapperClass = mapperClass;
        }

        @Override
        public Iterable<String> call(String input) throws Exception {
            if (mapper == null) {
                try {
                    this.mapper = (String2StringMap) Class.forName(mapperClass).newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            List<String> outputContent = mapper.map(input);
            return outputContent == null ? new LinkedList<>() : outputContent;
        }
    }

    // args <MapperClass> <Input> <Output>
    // <Input> can be a folder/files in HDFS file system
    public static void main(String[] args) {
        SparkConf conf = new SparkConf();
//                .setMaster("local[4]");
        JavaRDD<String> inputs = new JavaSparkContext(conf).textFile(args[1]);
        JavaRDD<String> mappedInputs = inputs.flatMap(new Mapper(args[0]));

        int dot = args[2].lastIndexOf(".");
        String extension = dot == -1 ? null : args[2].substring(dot + 1).toLowerCase();
        if (extension != null) {
            switch (extension) {
                case "gz":
                    mappedInputs.saveAsTextFile(args[2], GzipCodec.class);
                    break;
                case "bz2":
                    mappedInputs.saveAsTextFile(args[2], BZip2Codec.class);
                    break;
                // More extensions here.
                default:
                    mappedInputs.saveAsTextFile(args[2]);
            }
        } else {
            mappedInputs.saveAsTextFile(args[2]);
        }
    }
}
