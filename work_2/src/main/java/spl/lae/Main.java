package spl.lae;

import java.io.IOException;
import parser.*;

/**
 * Linear Algebra Engine - Main application entry point.
 * 
 * <p>
 * Processes matrix computation graphs from JSON input files and executes them
 * using a multi-threaded executor for parallel matrix operations.
 * </p>
 * 
 * <h3>Usage:</h3>
 * 
 * <pre>
 * java spl.lae.Main &lt;threads&gt; &lt;input.json&gt; &lt;output.json&gt;
 * </pre>
 * 
 * <h3>Parameters:</h3>
 * <ul>
 * <li><strong>threads</strong> - Number of worker threads for parallel
 * execution</li>
 * <li><strong>input.json</strong> - Input file containing computation graph
 * (JSON format)</li>
 * <li><strong>output.json</strong> - Output file for the resulting matrix (JSON
 * format)</li>
 * </ul>
 * 
 * <h3>Example:</h3>
 * 
 * <pre>
 * java spl.lae.Main 4 examples/example1.json out.json
 * </pre>
 * 
 * <h3>Supported Matrix Operations:</h3>
 * <ul>
 * <li><code>+</code> Addition</li>
 * <li><code>-</code> Subtraction/Negation</li>
 * <li><code>*</code> Multiplication</li>
 * <li><code>T</code> Transpose</li>
 * </ul>
 * 
 * <p>
 * Input files define nested computation trees where each node is either a
 * matrix literal
 * or an operation with operator and operands. The engine evaluates the tree
 * recursively,
 * distributing row-wise computations across the thread pool.
 * </p>
 */
public class Main {
  /**
   * Executes the Linear Algebra Engine pipeline: parse → compute → write.
   * 
   * <p>
   * Workflow:
   * <ol>
   * <li>Validates command line arguments</li>
   * <li>Parses the computation graph from input JSON</li>
   * <li>Initializes the multi-threaded execution engine</li>
   * <li>Evaluates the computation tree</li>
   * <li>Writes the result matrix to output JSON</li>
   * </ol>
   * 
   * @param args [threads, input_path, output_path]
   * @throws IOException              if file I/O operations fail
   * @throws IllegalArgumentException if argument count is invalid
   * @throws NumberFormatException    if thread count is not a valid integer
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      throw new IllegalArgumentException(
          "Expected 3 arguments: <number_of_threads> <path/to/input/file> <path/to/output/file>");
    }
    int numberOfThreads = Integer.parseInt(args[0]);
    String inputPath = args[1];
    String outputPath = args[2];
    InputParser parser = new InputParser();
    try {
      ComputationNode root = parser.parse(inputPath);
      LinearAlgebraEngine engine = new LinearAlgebraEngine(numberOfThreads);
      ComputationNode result = engine.run(root);
      double[][] resultMatrix = result.getMatrix();
      OutputWriter.write(resultMatrix, outputPath);
    } catch (Exception e) {
      OutputWriter.write(e.getMessage(), outputPath);
    }
  }
}
