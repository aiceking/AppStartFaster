# AppStartFaster

[![](https://jitpack.io/v/NoEndToLF/AppStartFaster.svg)](https://jitpack.io/#NoEndToLF/AppStartFaster)

**AppStartFaster**：包含两部分，一部分是冷启动任务分发，一部分是Multdex冷启动优化
- **启动器** ：本质所有任务就是一个有向无环图，通过Systrace确定wallTime和cpuTime，然后选择合适的线程池。
- **Multdex** ：5.0以下开新进程Activity去加载dex，其实就是为了第一时间显示第一个Activity，属于伪优化，其实在加载dex过程中，Multdex先将dex压缩成了zip，然后又解压zip，而他是可以直接去加载dex的，这里多了一个压缩又解压的过程，所以其实真正的优化应该是避免先解压再压缩。

**示例**：Demo中模拟了5个启动任务，且他们的依赖关系为如下所示，每个任务都模拟耗时300ms

![](https://github.com/NoEndToLF/AppStartFaster/blob/master/DemoImage/demo1.jpg)

**运行结果**：日志输出在Android Studio的Error中，如下所示（这个结果的场景是只有主线程的任务是阻塞的，其他任务不阻塞。如需要要保证某个任务阻塞，下文会介绍用法）

![](https://github.com/NoEndToLF/AppStartFaster/blob/master/DemoImage/demo2.jpg)
