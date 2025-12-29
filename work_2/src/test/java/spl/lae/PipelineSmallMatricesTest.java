package spl.lae;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PipelineSmallMatricesTest {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final double EPS = 1e-9;

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

    private static void assertResultMatrix(JsonNode out, double[][] expected) {
        assertTrue(out.has("result"), "Expected output to contain 'result': " + out);
        assertFalse(out.has("error"), "Expected output NOT to contain 'error': " + out);

        double[][] actual = mapper.convertValue(out.get("result"), double[][].class);
        assertEquals(expected.length, actual.length, "Row count mismatch");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i].length, actual[i].length, "Column count mismatch at row " + i);
            for (int j = 0; j < expected[i].length; j++) {
                assertEquals(expected[i][j], actual[i][j], EPS, "Mismatch at [" + i + "][" + j + "]");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void additionProducesExpectedResult(int threads, @TempDir Path tempDir) throws Exception {
        Path input = writeJson(tempDir, "add.json", """
                {"operator":"+","operands":[[[1,2],[3,4]],[[5,6],[7,8]]]}
                """);
        Path output = tempDir.resolve("out.json");

        JsonNode out = runMain(input, threads, output);
        assertResultMatrix(out, new double[][] {{6, 8}, {10, 12}});
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void multiplicationProducesExpectedResult(int threads, @TempDir Path tempDir) throws Exception {
        Path input = writeJson(tempDir, "mul.json", """
                {"operator":"*","operands":[[[1,2,3],[4,5,6]],[[7,8],[9,10],[11,12]]]}
                """);
        Path output = tempDir.resolve("out.json");

        JsonNode out = runMain(input, threads, output);
        assertResultMatrix(out, new double[][] {{58, 64}, {139, 154}});
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void transposeProducesExpectedResult(int threads, @TempDir Path tempDir) throws Exception {
        Path input = writeJson(tempDir, "transpose.json", """
                {"operator":"T","operands":[[[1,2,3],[4,5,6]]]}
                """);
        Path output = tempDir.resolve("out.json");

        JsonNode out = runMain(input, threads, output);
        assertResultMatrix(out, new double[][] {{1, 4}, {2, 5}, {3, 6}});
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void negateProducesExpectedResult(int threads, @TempDir Path tempDir) throws Exception {
        Path input = writeJson(tempDir, "negate.json", """
                {"operator":"-","operands":[[[1,2],[3,4]]]}
                """);
        Path output = tempDir.resolve("out.json");

        JsonNode out = runMain(input, threads, output);
        assertResultMatrix(out, new double[][] {{-1, -2}, {-3, -4}});
    }

    @Test
    void nAryOperatorsAreNestedLeftAssociatively(@TempDir Path tempDir) throws Exception {
        Path input = writeJson(tempDir, "nary.json", """
                {"operator":"+","operands":[[[1]],[[2]],[[3]],[[4]]]}
                """);

        parser.InputParser inputParser = new parser.InputParser();
        parser.ComputationNode root = inputParser.parse(input.toString());
        assertEquals(parser.ComputationNodeType.ADD, root.getNodeType());
        assertEquals(2, root.getChildren().size());
        assertEquals(parser.ComputationNodeType.ADD, root.getChildren().get(0).getNodeType());
        assertEquals(2, root.getChildren().get(0).getChildren().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void nAryAdditionIsHandledViaAssociativeNesting(int threads, @TempDir Path tempDir) throws Exception {
        Path input = writeJson(tempDir, "nary_add.json", """
                {"operator":"+","operands":[[[1]],[[2]],[[3]]]}
                """);
        Path output = tempDir.resolve("out.json");

        JsonNode out = runMain(input, threads, output);
        assertResultMatrix(out, new double[][] {{6}});
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void nAryMultiplicationIsHandledViaAssociativeNesting(int threads, @TempDir Path tempDir) throws Exception {
        Path input = writeJson(tempDir, "nary_mul.json", """
                {"operator":"*","operands":[[[2]],[[3]],[[4]]]}
                """);
        Path output = tempDir.resolve("out.json");

        JsonNode out = runMain(input, threads, output);
        assertResultMatrix(out, new double[][] {{24}});
    }
}
