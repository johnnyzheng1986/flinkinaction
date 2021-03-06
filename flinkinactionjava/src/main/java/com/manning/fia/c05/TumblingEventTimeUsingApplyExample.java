package com.manning.fia.c05;

import com.manning.fia.transformations.media.NewsFeedMapper3;
import com.manning.fia.utils.DataSourceFactory;
import com.manning.fia.transformations.media.NewsFeedMapper10;

import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple5;
import org.apache.flink.api.java.tuple.Tuple6;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.joda.time.format.DateTimeFormat;

import java.util.List;

/**
 * Created by hari on 6/26/16.
 */
public class TumblingEventTimeUsingApplyExample {

    public void executeJob(ParameterTool parameterTool) throws Exception {
        StreamExecutionEnvironment execEnv;
        DataStream<String> dataStream;
        DataStream<Tuple5<Long, String, String, String, String>> selectDS;
        DataStream<Tuple5<Long, String, String, String, String>> timestampsAndWatermarksDS;
        KeyedStream<Tuple5<Long, String, String, String, String>, Tuple> keyedDS;
        WindowedStream<Tuple5<Long, String, String, String, String>, Tuple, TimeWindow> windowedStream;
        DataStream<Tuple6<Long, Long, List<Long>, String, String, Long>> result;

        execEnv = StreamExecutionEnvironment.getExecutionEnvironment();

        execEnv.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        dataStream = execEnv.addSource(DataSourceFactory.getDataSource(parameterTool));

        selectDS = dataStream.map(new NewsFeedMapper3());

        timestampsAndWatermarksDS = selectDS.assignTimestampsAndWatermarks(new TimestampAndWatermarkAssigner());

        keyedDS = timestampsAndWatermarksDS.keyBy(1, 2);

        windowedStream = keyedDS.timeWindow(Time.seconds(10));

        result = windowedStream.apply(new ApplyFunction());

        result.print();
        execEnv.execute("Tumbling Event Time Window Apply");

    }

    private static class TimestampAndWatermarkAssigner
            implements
            AssignerWithPeriodicWatermarks<Tuple5<Long, String, String, String, String>> {
        private static final long serialVersionUID = 1L;
        private long wmTime = 0;
        private long priorWmTime = 0;
        private long lastTimeOfWaterMarking = System.currentTimeMillis();

        @Override
        public Watermark getCurrentWatermark() {
            if (wmTime == priorWmTime) {
                long advance = (System.currentTimeMillis() - lastTimeOfWaterMarking);
                wmTime += advance;// Start advancing
            }
            priorWmTime = wmTime;
            lastTimeOfWaterMarking = System.currentTimeMillis();
            return new Watermark(wmTime);
        }

        @Override
        public long extractTimestamp(
                Tuple5<Long, String, String, String, String> element,
                long previousElementTimestamp) {
            long millis = DateTimeFormat.forPattern("yyyyMMddHHmmss")
                    .parseDateTime(element.f3).getMillis();
            wmTime = Math.max(wmTime, millis);
            return Long.valueOf(millis);
        }
    }

    public static void main(String[] args) throws Exception {
        ParameterTool parameterTool = ParameterTool.fromArgs(args);
        TumblingEventTimeUsingApplyExample window = new TumblingEventTimeUsingApplyExample();
        window.executeJob(parameterTool);
    }
}
