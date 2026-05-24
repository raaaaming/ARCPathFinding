import cc.arccore.api.module.BaseModule
import cc.arccore.api.module.ModuleSpec
import cc.arccore.runtime.context.RuntimeModuleContext

@ModuleSpec(id = "test-module", name = "Test Module", version = "1.0.0")
class TestModule : BaseModule() {

    override fun onEnable() {
        val ctx = context as RuntimeModuleContext

        // 서비스 등록
        val service = TestService()
        ctx.services.register(TestService::class, service)
        logger.info("TestService registered")

        // 비동기 반복 태스크 (100틱마다 = 5초)
        ctx.scheduler.runAsyncRepeating(delayTicks = 100L, periodTicks = 100L) {
            val count = service.getGreetCount()
            logger.info("[AsyncTask] TestService has been called $count time(s) so far")
        }

        logger.info("Test module enabled!")
    }

    override fun onDisable() {
        logger.info("Test module disabled")
    }
}
