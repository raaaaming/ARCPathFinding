class TestService {
    private var greetCount = 0

    fun greet(name: String): String {
        greetCount++
        return "§e[TestModule] Hello, $name! (total greets: $greetCount)"
    }

    fun getGreetCount(): Int = greetCount
}
