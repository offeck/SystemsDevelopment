package spl.lae;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ErrorHandlingTest {
    private static final ObjectMapper mapper = new ObjectMapper();

    private static JsonNode runMain(Path inputJson, int threads, Path outputJson) throws Exception {
        Main.main(new String[] { String.valueOf(threads), inputJson.toString(), outputJson.toString() });
        assertTrue(Files.isRegularFile(outputJson), "Main did not create output file: " + outputJson);
        return mapper.readTree(outputJson.toFile());
    }

    private static Path writeJson(Path tempDir, String fileName, String json) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, json);
        return file;
    }

    private static void assertError(JsonNode out, String expectedSubstring) {
        assertTrue(out.has("error"), "Expected output to contain 'error': " + out);
        assertFalse(out.has("result"), "Expected output NOT to contain 'result': " + out);
        assertTrue(out.get("error").isTextual(), "Expected 'error' to be a string: " + out);
        assertTrue(out.get("error").asText().contains(expectedSubstring),
                "Expected error to contain '" + expectedSubstring + "' but was: " + out.get("error").asText());
    }

    @Test
    void dimensionMismatchInMultiplicationProducesErrorOutput(@TempDir Path tempDir) throws Exception {
        Path input = writeJson(tempDir, "bad_mul.json", """
                {"operator":"*","operands":[[[1,2,3],[4,5,6]],[[1,2],[3,4]]]}
                """);
        Path output = tempDir.resolve("out.json");

        JsonNode out = runMain(input, 2, output);
        assertError(out, "multiplication");
    }

    @Test
    void dimensionMismatchInAdditionProducesErrorOutput(@TempDir Path tempDir) throws Exception {
        Path input = writeJson(tempDir, "bad_add.json", """
                {"operator":"+","operands":[[[1,2],[3,4]],[[1,2,3],[4,5,6]]]}
                """);
        Path output = tempDir.resolve("out.json");

        JsonNode out = runMain(input, 2, output);
        assertError(out, "addition");
    }

    @Test
    void unaryOperatorWithTooManyOperandsProducesErrorOutput(@TempDir Path tempDir) throws Exception {
        Path input = writeJson(tempDir, "bad_unary.json", """
                {"operator":"T","operands":[[[1]],[[2]]]}
                """);
        Path output = tempDir.resolve("out.json");

        JsonNode out = runMain(input, 2, output);
        assertError(out, "exactly one child");
    }

    @Test
    void inconsistentRowSizesProduceErrorOutput(@TempDir Path tempDir) throws Exception {
        Path input = writeJson(tempDir, "bad_matrix.json", """
                [[1,2],[3]]
                """);
        Path output = tempDir.resolve("out.json");

        JsonNode out = runMain(input, 2, output);
        assertError(out, "Inconsistent row sizes");
    }

    @Test
    void unknownOperatorProducesErrorOutput(@TempDir Path tempDir) throws Exception {
        Path input = writeJson(tempDir, "bad_op.json", """
                {"operator":"/","operands":[[[1]],[[2]]]}
                """);
        Path output = tempDir.resolve("out.json");

        JsonNode out = runMain(input, 2, output);
        assertError(out, "Unknown operator");
    }

    @Test
    void invalidThreadCountProducesErrorOutput(@TempDir Path tempDir) throws Exception {
        Path input = writeJson(tempDir, "add.json", """
                {"operator":"+","operands":[[[1]],[[2]]]}
                """);
        Path output = tempDir.resolve("out.json");

        JsonNode out = runMain(input, 0, output);
        assertError(out, "threads");
    }
}
