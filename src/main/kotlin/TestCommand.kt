import cc.arccore.api.command.ARCCommand
import cc.arccore.api.command.CommandContext
import cc.arccore.api.command.CommandResult
import cc.arccore.api.command.CommandSpec

@CommandSpec(
	name = "test",
	description = "A test command",
	aliases = ["t"],
)
class TestCommand : ARCCommand {

	override fun execute(context: CommandContext): CommandResult {
		context.sender.sendMessage("Hello from TestModule!")
		return CommandResult.Success
	}

}