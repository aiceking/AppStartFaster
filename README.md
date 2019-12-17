# AppStartFaster

[![](https://jitpack.io/v/NoEndToLF/AppStartFaster.svg)](https://jitpack.io/#NoEndToLF/AppStartFaster)

**AppStartFaster**：包含两部分，一部分是冷启动任务分发，一部分是Multdex冷启动优化
- **启动器** ：本质所有任务就是一个有向无环图，通过Systrace确定wallTime和cpuTime，然后选择合适的线程池。
- **Multdex** ：5.0以下开新进程Activity去加载dex，其实就是为了第一时间显示第一个Activity，属于伪优化，其实在加载dex过程中，Multdex先将dex压缩成了zip，然后又解压zip，而他是可以直接去加载dex的，这里多了一个压缩又解压的过程，所以其实真正的优化应该是避免先解压再压缩。


