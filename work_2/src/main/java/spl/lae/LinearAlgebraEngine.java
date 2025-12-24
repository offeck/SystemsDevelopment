package spl.lae;

import parser.*;
import memory.*;
import scheduling.*;

import java.util.LinkedList;
import java.util.List;

public class LinearAlgebraEngine {

    private SharedMatrix leftMatrix = new SharedMatrix();
    private SharedMatrix rightMatrix = new SharedMatrix();
    private TiredExecutor executor;

    public LinearAlgebraEngine(int numThreads) {
        // TODO: create executor with given thread count
        this.executor = new TiredExecutor(numThreads);
    }

    public ComputationNode run(ComputationNode computationRoot) {
        // TODO: resolve computation tree step by step until final matrix is produced
        // Iteratively locate the next resolvable node: a node whose operands are
        // already concrete matrices.
        while (true) {
            if (computationRoot.getNodeType() == ComputationNodeType.MATRIX) {
                return computationRoot;
            }
            ComputationNode resolvable = computationRoot.findResolvable();
            if (resolvable == null) {
                // throw error - no resolvable node found
                throw new IllegalArgumentException("No resolvable node found in computation tree.");
            }
            loadAndCompute(resolvable);
            // read the result from the left shared matrix (M1) and attach it back to the
            // corresponding node in the computation tree.
            double[][] resultMatrix = leftMatrix.readRowMajor();
            resolvable.resolve(resultMatrix);
        }
    }

    private void loadUnaryOperand(ComputationNode node) {
        // Precondition: node must be a unary operation
        if (node.getNodeType() != ComputationNodeType.NEGATE && node.getNodeType() != ComputationNodeType.TRANSPOSE) {
            throw new IllegalArgumentException("Node must be a unary operation (NEGATE or TRANSPOSE).");
        }
        List<ComputationNode> children = node.getChildren();
        if (children.size() != 1) {
            throw new IllegalArgumentException("Node must have exactly two children.");
        }
        ComputationNode left = children.get(0);
        if (left.getNodeType() != ComputationNodeType.MATRIX) {
            throw new IllegalArgumentException("Child must be a MATRIX node.");
        }
        leftMatrix.loadRowMajor(left.getMatrix());
    }

    private void loadBinaryOperand(ComputationNode node) {
        // Precondition: node must be a binary operation
        if (node.getNodeType() != ComputationNodeType.ADD && node.getNodeType() != ComputationNodeType.MULTIPLY) {
            throw new IllegalArgumentException("Node must be a binary operation (ADD or MULTIPLY).");
        }
        List<ComputationNode> children = node.getChildren();
        if (children.size() != 2) {
            throw new IllegalArgumentException("Node must have exactly two children.");
        }
        ComputationNode left = children.get(0);
        ComputationNode right = children.get(1);
        if (left.getNodeType() != ComputationNodeType.MATRIX || right.getNodeType() != ComputationNodeType.MATRIX) {
            throw new IllegalArgumentException("Both children must be MATRIX nodes.");
        }
        leftMatrix.loadRowMajor(left.getMatrix());
        rightMatrix.loadRowMajor(right.getMatrix());
    }

    public void loadAndCompute(ComputationNode node) {
        // TODO: load operand matrices
        List<Runnable> tasks;
        switch (node.getNodeType()) {
            case ADD:
                loadBinaryOperand(node);
                tasks = createAddTasks();
                break;
            case MULTIPLY:
                loadBinaryOperand(node);
                tasks = createMultiplyTasks();
                break;
            case NEGATE:
                loadUnaryOperand(node);
                tasks = createNegateTasks();
                break;
            case TRANSPOSE:
                loadUnaryOperand(node);
                tasks = createTransposeTasks();
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + node.getNodeType());
        }

        // TODO: create compute tasks & submit tasks to executor

        executor.submitAll(tasks);
    }

    public List<Runnable> createAddTasks() {
        // TODO: return tasks that perform row-wise addition
        // Nir:
        if (leftMatrix == null || rightMatrix == null) {
            throw new IllegalStateException("Both left and right matrices must be loaded before multiplication.");
        }
        if (leftMatrix.length() == 0 || rightMatrix.length() == 0) {
            throw new IllegalStateException("Matrices must not be empty for multiplication.");
        }
        if (leftMatrix.get(0).length() != rightMatrix.length()) {
            throw new IllegalArgumentException("Incompatible matrix dimensions for multiplication.");
        }
        List<Runnable> tasks = new LinkedList<>();
        for (int i = 0; i < leftMatrix.length(); i++) {
            final int rowIndex = i;
            tasks.add(() -> {
                SharedVector leftRow = leftMatrix.get(rowIndex);
                SharedVector rightRow = rightMatrix.get(rowIndex);
                leftRow.add(rightRow);
            });
        }
        return tasks;
    }

    public List<Runnable> createMultiplyTasks() {
        // TODO: return tasks that perform row × matrix multiplication
        // Nir:
        if (leftMatrix == null || rightMatrix == null) {
            throw new IllegalStateException("Both left and right matrices must be loaded before multiplication.");
        }
        if (leftMatrix.length() == 0 || rightMatrix.length() == 0) {
            throw new IllegalStateException("Matrices must not be empty for multiplication.");
        }
        if (leftMatrix.get(0).length() != rightMatrix.length()) {
            throw new IllegalArgumentException("Incompatible matrix dimensions for multiplication.");
        }
        List<Runnable> tasks = new LinkedList<>();
        for (int i = 0; i < leftMatrix.length(); i++) {
            final int rowIndex = i;
            tasks.add(() -> {
                SharedVector leftRow = leftMatrix.get(rowIndex);
                leftRow.vecMatMul(rightMatrix);
            });
        }
        return tasks;
    }

    public List<Runnable> createNegateTasks() {
        // TODO: return tasks that negate rows
        // Add exception handling as needed
        if (leftMatrix == null) {
            throw new IllegalStateException("Left matrix must be loaded before negation.");
        }   
        if (leftMatrix.length() == 0) {
            throw new IllegalStateException("Matrix must not be empty for negation.");
        }
        List<Runnable> tasks = new LinkedList<>();
        for (int i = 0; i < leftMatrix.length(); i++) {
            final int rowIndex = i;
            tasks.add(() -> {
                SharedVector row = leftMatrix.get(rowIndex);
                row.negate();
            });
        }
        return tasks;
    }

    public List<Runnable> createTransposeTasks() {
        // TODO: return tasks that transpose rows
        if (leftMatrix == null) {
            throw new IllegalStateException("Left matrix must be loaded before transposition.");
        }
        if (leftMatrix.length() == 0) {
            throw new IllegalStateException("Matrix must not be empty for transposition.");
        }
        List<Runnable> tasks = new LinkedList<>();
        for (int i = 0; i < leftMatrix.length(); i++) {
            final int rowIndex = i;
            tasks.add(() -> {
                SharedVector row = leftMatrix.get(rowIndex);
                row.transpose();
            });
        }
        return tasks;
    }

    public String getWorkerReport() {
        // Nir:
        return executor.getWorkerReport();
    }
}
