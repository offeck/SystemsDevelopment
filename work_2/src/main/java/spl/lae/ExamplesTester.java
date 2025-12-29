package spl.lae;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import parser.ComputationNode;
import parser.InputParser;
import parser.OutputWriter;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExamplesTester {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern examplePattern = Pattern.compile("^example(\\d+)\\.json$");

    public static void main(String[] args) throws IOException {
        int threads = 4;
        if (args.length >= 1 && !args[0].isBlank()) {
            threads = Integer.parseInt(args[0]);
        }

        Path examplesDir = Path.of("examples");
        if (!Files.isDirectory(examplesDir)) {
            throw new IllegalArgumentException("Expected examples directory at: " + examplesDir.toAbsolutePath());
        }

        List<Integer> exampleIds = discoverExampleIds(examplesDir);
        if (exampleIds.isEmpty()) {
            throw new IllegalArgumentException("No example*.json files found under: " + examplesDir.toAbsolutePath());
        }

        int passed = 0;
        int failed = 0;
        for (int exampleId : exampleIds) {
            boolean ok = runOneExample(exampleId, threads);
            if (ok) {
                passed++;
            } else {
                failed++;
            }
        }

        System.out.printf("Examples: %d passed, %d failed (threads=%d)%n", passed, failed, threads);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static List<Integer> discoverExampleIds(Path examplesDir) throws IOException {
        List<Integer> ids = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(examplesDir, "example*.json")) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                Matcher matcher = examplePattern.matcher(fileName);
                if (matcher.matches()) {
                    ids.add(Integer.parseInt(matcher.group(1)));
                }
            }
        }
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    private static boolean runOneExample(int exampleId, int threads) throws IOException {
        Path input = Path.of("examples", "example" + exampleId + ".json");
        Path expectedOutput = Path.of("examples", "out" + exampleId + ".json");

        if (!Files.isRegularFile(input)) {
            System.err.println("Missing input file: " + input.toAbsolutePath());
            return false;
        }
        if (!Files.isRegularFile(expectedOutput)) {
            System.err.println("Missing expected output file: " + expectedOutput.toAbsolutePath());
            return false;
        }

        JsonNode expected = mapper.readTree(expectedOutput.toFile());
        JsonNode actual = runPipelineToJson(input.toString(), threads);
        if (!expected.equals(actual)) {
            System.err.printf("Mismatch for example%d.json (threads=%d)%n", exampleId, threads);
            return false;
        }

        System.out.printf("OK example%d.json (threads=%d)%n", exampleId, threads);
        return true;
    }

    private static JsonNode runPipelineToJson(String inputPath, int threads) {
        InputParser parser = new InputParser();
        try {
            ComputationNode root = parser.parse(inputPath);
            LinearAlgebraEngine engine = new LinearAlgebraEngine(threads);
            ComputationNode result = engine.run(root);
            double[][] resultMatrix = result.getMatrix();
            return mapper.valueToTree(new OutputWriter.ResultMatrix(resultMatrix));
        } catch (Exception e) {
            return mapper.valueToTree(new OutputWriter.ErrorMessage(e.getMessage()));
        }
    }
}
