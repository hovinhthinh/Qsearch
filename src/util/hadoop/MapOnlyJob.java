package util.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.io.IOException;


public class MapOnlyJob {

    public static class Map extends Mapper<LongWritable, Text, Text, IntWritable> {
        private String2StringMap mapper;
        private Text output = new Text();

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            super.setup(context);
            try {
                this.mapper = (String2StringMap) Class.forName(context.getConfiguration().get("MapperClass")).newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }


        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String outputContent = mapper.map(value.toString());
            if (output != null) {
                output.set(outputContent);
                context.write(output, null);
            }
        }
    }

    // args <MapperClass> <Input> <Output>
    // <Input> can be a folder/files in HDFS file system
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        conf.set("MapperClass", args[0]);

        Job job = new Job(conf);

        job.setJarByClass(MapOnlyJob.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        job.setMapperClass(Map.class);
        job.setNumReduceTasks(0);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        FileInputFormat.setInputDirRecursive(job, true);
        FileInputFormat.addInputPath(job, new Path(args[1]));
        FileOutputFormat.setOutputPath(job, new Path(args[2]));

        job.waitForCompletion(true);
    }

}