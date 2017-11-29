# AvgImageBandLayer
## AvgImageBandLayerTest
### Json Serialization
Code from [LayerTestBase.java:84](../../../../../../../../MindsEye/src/test/java/com/simiacryptus/mindseye/layers/LayerTestBase.java#L84) executed in 0.00 seconds: 
```java
    JsonObject json = layer.getJson();
    NNLayer echo = NNLayer.fromJson(json);
    assert (echo != null) : "Failed to deserialize";
    assert (layer != echo) : "Serialization did not copy";
    Assert.assertEquals("Serialization not equal", layer, echo);
    return new GsonBuilder().setPrettyPrinting().create().toJson(json);
```

Returns: 

```
    {
      "class": "com.simiacryptus.mindseye.layers.java.AvgImageBandLayer",
      "id": "79cb4e2c-4a9a-4706-8f49-c2d5000000bb",
      "isFrozen": false,
      "name": "AvgImageBandLayer/79cb4e2c-4a9a-4706-8f49-c2d5000000bb"
    }
```



### Example Input/Output Pair
Code from [LayerTestBase.java:121](../../../../../../../../MindsEye/src/test/java/com/simiacryptus/mindseye/layers/LayerTestBase.java#L121) executed in 0.00 seconds: 
```java
    SimpleEval eval = SimpleEval.run(layer, inputPrototype);
    return String.format("--------------------\nInput: \n[%s]\n--------------------\nOutput: \n%s",
      Arrays.stream(inputPrototype).map(t->t.prettyPrint()).reduce((a,b)->a+",\n"+b).get(),
      eval.getOutput().prettyPrint());
```

Returns: 

```
    --------------------
    Input: 
    [[
    	[ [ -0.312, 1.916, -1.988 ], [ -1.92, -1.276, -1.776 ] ],
    	[ [ -0.916, 0.444, 0.684 ], [ 1.916, -1.308, 0.28 ] ]
    ]]
    --------------------
    Output: 
    [
    	[ [ -0.05600000000000005, -0.308, -0.7 ] ]
    ]
```



### Differential Validation
Code from [LayerTestBase.java:139](../../../../../../../../MindsEye/src/test/java/com/simiacryptus/mindseye/layers/LayerTestBase.java#L139) executed in 0.00 seconds: 
```java
    getDerivativeTester().test(layer, inputPrototype);
```
Logging: 
```
    Component: AvgImageBandLayer/79cb4e2c-4a9a-4706-8f49-c2d5000000bb
    Inputs: [
    	[ [ -0.312, 1.916, -1.988 ], [ -1.92, -1.276, -1.776 ] ],
    	[ [ -0.916, 0.444, 0.684 ], [ 1.916, -1.308, 0.28 ] ]
    ]
    output=[
    	[ [ -0.05600000000000005, -0.308, -0.7 ] ]
    ]
    measured/actual: [ [ 0.0, 0.24999999999608666, 0.0 ], [ 0.0, 0.24999999999608666, 0.0 ], [ 0.0, 0.25000000000163775, 0.0 ], [ 0.0, -39199.99999999999, 39200.24999999999 ], [ 0.25000000000163775, 0.0, 0.0 ], [ 0.25000000000163775, 0.0, 0.0 ], [ 0.25000000000163775, -39199.99999999999, 39199.99999999999 ], [ -25199.999999999993, 25200.249999999993, 0.0 ], [ 0.0, 0.0, 0.24999999999053554 ], [ 0.0, -39199.75, 39199.99999999999 ] ]
    implemented/expected: [ [ 0.25, 0.0, 0.0 ], [ 0.25, 0.0, 0.0 ], [ 0.25, 0.0, 0.0 ], [ 0.25, 0.0, 0.0 ], [ 0.0, 0.25, 0.0 ], [ 0.0, 0.25, 0.0 ], [ 0.0, 0.25, 0.0 ], [ 0.0, 0.25, 0.0 ], [ 0.0, 0.0, 0.25 ], [ 0.0, 0.0, 0.25 ] ]
    error: [ [ -0.25, 0.24999999999608666, 0.0 ], [ -0.25, 0.24999999999608666, 0.0 ], [ -0.25, 0.25000000000163775, 0.0 ], [ -0.25, -39199.99999999999, 39200.24999999999 ], [ 0.25000000000163775, -0.25, 0.0 ], [ 0.25000000000163775, -0.25, 0.0 ], [ 0.25000000000163775, -39200.24999999999, 39199.99999999999 ], [ -25199.999999999993, 25199.999999999993, 0.0 ], [ 0.0, 0.0, -9.46445699590015E-12 ], [ 0.0, -39199.75, 39199.74999999999 ] ]
    
```

Returns: 

```
    java.lang.AssertionError: ToleranceStatistics{absoluteTol=1.1511e+04 +- 1.8287e+04 [0.0000e+00 - 6.4400e+04] (36#), relativeTol=9.2000e-01 +- 2.7129e-01 [1.8929e-11 - 1.0000e+00] (25#)}
    	at com.simiacryptus.mindseye.layers.DerivativeTester.testFeedback(DerivativeTester.java:224)
    	at com.simiacryptus.mindseye.layers.DerivativeTester.lambda$test$0(DerivativeTester.java:71)
    	at java.util.stream.IntPipeline$4$1.accept(IntPipeline.java:250)
    	at java.util.stream.Streams$RangeIntSpliterator.forEachRemaining(Streams.java:110)
    	at java.util.Spliterator$OfInt.forEachRemaining(Spliterator.java:693)
    	at java.util.stream.AbstractPipeline.copyInto(AbstractPipeline.java:481)
    	at java.util.stream.AbstractPipeline.wrapAndCopyInto(AbstractPipeline.java:471)
    	at java.util.stream.ReduceOps$ReduceOp.evaluateSequential(ReduceOps.java:708)
    	at java.util.stream.AbstractPipeline.evaluate(AbstractPipeline.java:234)
    	at java.util.stream.ReferencePipeline.reduce(ReferencePipeline.java:479)
    	at com.simiacryptus.mindseye.layers.DerivativeTester.test(DerivativeTester.java:72)
    	at com.simiacryptus.mindseye.layers.LayerTestBase.lambda$test$15(LayerTestBase.java:140)
    	at com.simiacryptus.util.io.NotebookOutput.lambda$code$1(NotebookOutput.java:142)
    	at com.simiacryptus.util.io.MarkdownNotebookOutput.lambda$null$1(MarkdownNotebookOutput.java:136)
    	at com.simiacryptus.util.lang.TimedResult.time(TimedResult.java:59)
    	at com.simiacryptus.util.io.MarkdownNotebookOutput.lambda$code$2(MarkdownNotebookOutput.java:136)
    	at com.simiacryptus.util.test.SysOutInterceptor.withOutput(SysOutInterceptor.java:77)
    	at com.simiacryptus.util.io.MarkdownNotebookOutput.code(MarkdownNotebookOutput.java:134)
    	at com.simiacryptus.util.io.NotebookOutput.code(NotebookOutput.java:141)
    	at com.simiacryptus.mindseye.layers.LayerTestBase.test(LayerTestBase.java:139)
    	at com.simiacryptus.mindseye.layers.LayerTestBase.test(LayerTestBase.java:69)
    	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    	at sun.reflect.NativeMethodAccessorImpl.invoke(N
```
...[skipping 77 bytes](etc/1.txt)...
```
    l.invoke(DelegatingMethodAccessorImpl.java:43)
    	at java.lang.reflect.Method.invoke(Method.java:498)
    	at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)
    	at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
    	at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)
    	at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
    	at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:325)
    	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:78)
    	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:57)
    	at org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)
    	at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)
    	at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)
    	at org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)
    	at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)
    	at org.junit.runners.ParentRunner.run(ParentRunner.java:363)
    	at org.junit.runners.Suite.runChild(Suite.java:128)
    	at org.junit.runners.Suite.runChild(Suite.java:27)
    	at org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)
    	at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)
    	at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)
    	at org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)
    	at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)
    	at org.junit.runners.ParentRunner.run(ParentRunner.java:363)
    	at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
    	at com.intellij.junit4.JUnit4IdeaTestRunner.startRunnerWithArgs(JUnit4IdeaTestRunner.java:68)
    	at com.intellij.rt.execution.junit.IdeaTestRunner$Repeater.startRunnerWithArgs(IdeaTestRunner.java:47)
    	at com.intellij.rt.execution.junit.JUnitStarter.prepareStreamsAndStart(JUnitStarter.java:242)
    	at com.intellij.rt.execution.junit.JUnitStarter.main(JUnitStarter.java:70)
    
```


