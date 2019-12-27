package util.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.hadoop.mapred.*;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.util.List;

public class MapOnlyJob extends Configured implements Tool {

    // args <MapperClass> <Input> <Output>
    // <Input> can be a folder/files in HDFS file system
    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new Configuration(), new MapOnlyJob(), args));
    }

    public int run(String[] args) throws Exception {
        // Test mapper.
        try {
            String2StringMap testMapper = (String2StringMap) Class.forName(args[0]).newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        JobConf conf = new JobConf(getConf(), MapOnlyJob.class);

        conf.set("MapperClass", args[0]);
        conf.setMapperClass(Map.class);

        conf.setNumReduceTasks(0);

        conf.setInputFormat(TextInputFormat.class);
        conf.setOutputFormat(TextOutputFormat.class);

        FileInputFormat.setInputPaths(conf, new Path(args[1]));
        FileOutputFormat.setOutputPath(conf, new Path(args[2]));

        int dot = args[2].lastIndexOf(".");
        String extension = dot == -1 ? null : args[2].substring(dot + 1).toLowerCase();
        if (extension != null) {
            switch (extension) {
                case "gz":
                    FileOutputFormat.setCompressOutput(conf, true);
                    FileOutputFormat.setOutputCompressorClass(conf, GzipCodec.class);
                    break;
                // More extensions here.
                default:
            }
        }

        JobClient.runJob(conf);
        return 0;
    }

    public static class Map extends MapReduceBase implements Mapper<LongWritable, Text, Text, NullWritable> {
        private String2StringMap mapper;
        private Text output = new Text();

        @Override
        public void configure(JobConf job) {
            super.configure(job);
            try {
                this.mapper = (String2StringMap) Class.forName(job.get("MapperClass")).newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void map(LongWritable key, Text value, OutputCollector<Text, NullWritable> outputCollector, Reporter reporter) throws IOException {
            List<String> outputContent = mapper.map(value.toString());
            if (outputContent != null) {
                for (String content : outputContent) {
                    output.set(content);
                    outputCollector.collect(output, NullWritable.get());
                }
            }
        }
    }
}