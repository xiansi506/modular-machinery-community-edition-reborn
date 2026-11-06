# P12-a：coremod 原生目标字节码验收

2026-11-01；0.121.0/f94ba299；仅验收。工件`_audit/code-survey-0.121.0/review-p12-a-coremod/`，13条PASS，退出0，P12尚未收口。

将旧专项CoremodCheck.java复制到独立目录，保留其原件；重新读取当前world_hiding.js和coremods.json，以本机实际Forge的过滤Nashorn引擎和CoreMod.initialize/buildTransformers注册8个变换器。逐一读取实际Minecraft 1.20.1官方映射/SRG缓存jar，以及实际安装Embeddium 0.3.31类字节，执行当前脚本变换。所有输入/脚本/依赖都有哈希。

实际12次类变换、27个hook调用：RenderChunkRegion、BlockRenderDispatcher、BlockEntityRenderDispatcher、Block各官方/SRG两份，加Embeddium的WorldSlice、BlockRenderer、FluidRenderer、BlockOcclusionCache四份。每个变换类重写后通过CheckClassAdapter结构/帧检查，每个被修改方法通过BasicVerifier栈操作数分析；生成字节码仅保存审计目录。

本批无新增问题。补足P10/P12留下的可离线验证目标匹配和字节码分支；不能据此把生产游戏中coremod命中标为通过。

边界：未运行完整ModLauncher变换链、其他渲染优化模组组合、类加载后地形编译、资源重载、shader或GPU。仅验证上述固定本机版本，不推广到任意Forge/Embeddium版本。不改生产脚本，不正式构建/reobf或部署。
