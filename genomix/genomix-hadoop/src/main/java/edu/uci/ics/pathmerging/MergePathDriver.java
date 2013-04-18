/*
 * Copyright 2009-2012 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.pathmerging;

import java.io.IOException;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.hadoop.mapred.lib.MultipleOutputs;
import org.apache.hadoop.mapred.lib.MultipleSequenceFileOutputFormat;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import edu.uci.ics.genomix.type.KmerBytesWritable;

@SuppressWarnings("deprecation")
public class MergePathDriver {
    
    private static class Options {
        @Option(name = "-inputpath", usage = "the input path", required = true)
        public String inputPath;

        @Option(name = "-outputpath", usage = "the output path", required = true)
        public String outputPath;

        @Option(name = "-mergeresultpath", usage = "the merging results path", required = true)
        public String mergeResultPath;
        
        @Option(name = "-num-reducers", usage = "the number of reducers", required = true)
        public int numReducers;

        @Option(name = "-kmer-size", usage = "the size of kmer", required = true)
        public int sizeKmer;
        
        @Option(name = "-merge-rounds", usage = "the while rounds of merging", required = true)
        public int mergeRound;

    }


    public void run(String inputPath, String outputPath, String mergeResultPath, int numReducers, int sizeKmer, int mergeRound, String defaultConfPath)
            throws IOException{

        JobConf conf = new JobConf(MergePathDriver.class);
        conf.setInt("sizeKmer", sizeKmer);
        
        if (defaultConfPath != null) {
            conf.addResource(new Path(defaultConfPath));
        }
        conf.setJobName("Initial Path-Starting-Points Table");
        conf.setMapperClass(SNodeInitialMapper.class); 
        conf.setReducerClass(SNodeInitialReducer.class);
        
        conf.setMapOutputKeyClass(KmerBytesWritable.class);
        conf.setMapOutputValueClass(MergePathValueWritable.class);
        
        conf.setInputFormat(SequenceFileInputFormat.class);
        conf.setOutputFormat(SequenceFileOutputFormat.class);
        
        conf.setOutputKeyClass(KmerBytesWritable.class);
        conf.setOutputValueClass(MergePathValueWritable.class);
        
        FileInputFormat.setInputPaths(conf, new Path(inputPath));
        FileOutputFormat.setOutputPath(conf, new Path(inputPath + "-step1"));
        conf.setNumReduceTasks(numReducers);
        FileSystem dfs = FileSystem.get(conf);
        dfs.delete(new Path(inputPath + "-step1"), true);
        JobClient.runJob(conf);
/*----------------------------------------------------------------------*/
        for(int iMerge = 0; iMerge < mergeRound; iMerge ++){
        
            conf = new JobConf(MergePathDriver.class);
            conf.setInt("sizeKmer", sizeKmer);
            conf.setInt("iMerge", iMerge);
            
            if (defaultConfPath != null) {
                conf.addResource(new Path(defaultConfPath));
            }
            conf.setJobName("Path Merge");
            
            conf.setMapperClass(MergePathMapper.class);
            conf.setReducerClass(MergePathReducer.class);
            
            conf.setMapOutputKeyClass(KmerBytesWritable.class);
            conf.setMapOutputValueClass(MergePathValueWritable.class);
            
            conf.setInputFormat(SequenceFileInputFormat.class);
            conf.setOutputFormat(MultipleSequenceFileOutputFormat.class);
            
            String uncomplete = "uncomplete" + iMerge;
            String complete = "complete" + iMerge;
           
            MultipleOutputs.addNamedOutput(conf, uncomplete,
                    MergePathMultiSeqOutputFormat.class, KmerBytesWritable.class,
                    MergePathValueWritable.class);

            MultipleOutputs.addNamedOutput(conf, complete,
                    MergePathMultiSeqOutputFormat.class, KmerBytesWritable.class,
                    MergePathValueWritable.class);
            
            conf.setOutputKeyClass(KmerBytesWritable.class);
            conf.setOutputValueClass(MergePathValueWritable.class);
            
            FileInputFormat.setInputPaths(conf, new Path(inputPath + "-step1"));
            FileOutputFormat.setOutputPath(conf, new Path(outputPath));
            conf.setNumReduceTasks(numReducers);
            dfs.delete(new Path(outputPath), true);
            JobClient.runJob(conf);
            dfs.delete(new Path(inputPath + "-step1"), true);
            dfs.rename(new Path(outputPath + "/" + uncomplete), new Path(inputPath + "-step1"));
            dfs.rename(new Path(outputPath + "/" + complete), new Path(mergeResultPath + "/" + complete));
        }
    }

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        CmdLineParser parser = new CmdLineParser(options);
        parser.parseArgument(args);
        MergePathDriver driver = new MergePathDriver();
        driver.run(options.inputPath, options.outputPath, options.mergeResultPath, options.numReducers, options.sizeKmer, options.mergeRound, null);
    }
}
