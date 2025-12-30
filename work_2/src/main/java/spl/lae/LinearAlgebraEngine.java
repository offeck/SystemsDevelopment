package spl.lae;

import parser.*;
import memory.*;
import scheduling.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class LinearAlgebraEngine {

    private final TiredExecutor executor;

    public LinearAlgebraEngine(int numThreads) {
        this.executor = new TiredExecutor(numThreads);
    }

    public ComputationNode run(ComputationNode computationRoot) {
        try {
            while (true) {
                if (computationRoot.getNodeType() == ComputationNodeType.MATRIX) {
                    return computationRoot;
                }

                List<ComputationNode> resolvableNodes = new ArrayList<>();
                computationRoot.findAllResolvable(resolvableNodes);

                if (resolvableNodes.isEmpty()) {
                    throw new IllegalArgumentException("No resolvable node found in computation tree.");
                }

                List<Runnable> batchTasks = new ArrayList<>();
                List<PendingResolution> pendingResolutions = new ArrayList<>();

                for (ComputationNode node : resolvableNodes) {
                    PendingResolution pending = loadAndPrepare(node);
                    batchTasks.addAll(pending.tasks);
                    pendingResolutions.add(pending);
                }

                executor.submitAll(batchTasks);

                for (PendingResolution pending : pendingResolutions) {
                    double[][] resultMatrix = pending.resultMatrixHolder.readRowMajor();
                    pending.node.resolve(resultMatrix);
                }
            }
        } finally {
            this.executor.shutdown();
        }
    }

    private static class PendingResolution {
        ComputationNode node;
        SharedMatrix resultMatrixHolder;
        List<Runnable> tasks;

        PendingResolution(ComputationNode node, SharedMatrix resultMatrixHolder, List<Runnable> tasks) {
            this.node = node;
            this.resultMatrixHolder = resultMatrixHolder;
            this.tasks = tasks;
        }
    }

    private PendingResolution loadAndPrepare(ComputationNode node) {
        SharedMatrix left = new SharedMatrix();
        SharedMatrix right = new SharedMatrix();
        List<Runnable> tasks;

        switch (node.getNodeType()) {
            case ADD:
                loadBinaryOperand(node, left, right);
                tasks = createAddTasks(left, right);
                break;
            case MULTIPLY:
                loadBinaryOperand(node, left, right);
                tasks = createMultiplyTasks(left, right);
                break;
            case NEGATE:
                loadUnaryOperand(node, left);
                tasks = createNegateTasks(left);
                break;
            case TRANSPOSE:
                loadUnaryOperand(node, left);
                tasks = createTransposeTasks(left);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + node.getNodeType());
        }
        return new PendingResolution(node, left, tasks);
    }

    private void loadUnaryOperand(ComputationNode node, SharedMatrix destLeft) {
        if (node.getNodeType() != ComputationNodeType.NEGATE && node.getNodeType() != ComputationNodeType.TRANSPOSE) {
            throw new IllegalArgumentException("Node must be a unary operation (NEGATE or TRANSPOSE).");
        }
        List<ComputationNode> children = node.getChildren();
        if (children.size() != 1) {
            throw new IllegalArgumentException("Node must have exactly one child.");
        }
        ComputationNode child = children.get(0);
        if (child.getNodeType() != ComputationNodeType.MATRIX) {
            throw new IllegalArgumentException("Child must be a MATRIX node.");
        }
        destLeft.loadRowMajor(child.getMatrix());
    }

    private void loadBinaryOperand(ComputationNode node, SharedMatrix destLeft, SharedMatrix destRight) {
        if (node.getNodeType() != ComputationNodeType.ADD && node.getNodeType() != ComputationNodeType.MULTIPLY) {
            throw new IllegalArgumentException("Node must be a binary operation (ADD or MULTIPLY).");
        }
        List<ComputationNode> children = node.getChildren();
        if (children.size() != 2) {
            throw new IllegalArgumentException("Node must have exactly two children.");
        }
        ComputationNode leftChild = children.get(0);
        ComputationNode rightChild = children.get(1);
        if (leftChild.getNodeType() != ComputationNodeType.MATRIX || rightChild.getNodeType() != ComputationNodeType.MATRIX) {
            throw new IllegalArgumentException("Both children must be MATRIX nodes.");
        }
        destLeft.loadRowMajor(leftChild.getMatrix());
        destRight.loadRowMajor(rightChild.getMatrix());
    }

    private List<Runnable> createAddTasks(SharedMatrix leftMatrix, SharedMatrix rightMatrix) {
        if (leftMatrix.length() == 0 || rightMatrix.length() == 0) {
            throw new IllegalStateException("Matrices must not be empty for addition.");
        }
        if (leftMatrix.length() != rightMatrix.length() || leftMatrix.get(0).length() != rightMatrix.get(0).length()) {
            throw new IllegalArgumentException("Incompatible matrix dimensions for addition.");
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

    private List<Runnable> createMultiplyTasks(SharedMatrix leftMatrix, SharedMatrix rightMatrix) {
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

    private List<Runnable> createNegateTasks(SharedMatrix leftMatrix) {
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

    private List<Runnable> createTransposeTasks(SharedMatrix leftMatrix) {
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
        return executor.getWorkerReport();
    }
}
