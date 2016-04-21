
```
public class PageRankVertex extends <VLongWritable, DoubleWritable, FloatWritable, DoubleWritable> {
    ........
    @Override
    compute(Iterator<DoubleWritable> msgIterator) {
        .......
        sum = 0;
        while (msgIterator.hasNext()) {
          sum += msgIterator.next().get();
        }
        vertexValue.set((0.15 / getNumVertices()) + 0.85 * sum);
        sendMsgToAllNeighbors(vertexValue / getEdges().size());
        ....
    }
}
```