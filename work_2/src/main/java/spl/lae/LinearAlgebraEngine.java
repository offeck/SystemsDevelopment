package spl.lae;

import parser.*;
import memory.*;
import scheduling.*;

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
        //  Iteratively locate the next resolvable node: a node whose operands are already concrete matrices.
        ComputationNode resolvable = computationRoot.findResolvable();
        if (resolvable == null) {
            // throw error - no resolvable node found
            throw new IllegalArgumentException("No resolvable node found in computation tree.");
        }
        loadAndCompute(resolvable);
    }

    public void loadAndCompute(ComputationNode node) {
        // TODO: load operand matrices
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

        // TODO: create compute tasks & submit tasks to executor
        
    }

    public List<Runnable> createAddTasks() {
        // TODO: return tasks that perform row-wise addition
        return null;
    }

    public List<Runnable> createMultiplyTasks() {
        // TODO: return tasks that perform row × matrix multiplication
        return null;
    }

    public List<Runnable> createNegateTasks() {
        // TODO: return tasks that negate rows
        return null;
    }

    public List<Runnable> createTransposeTasks() {
        // TODO: return tasks that transpose rows
        return null;
    }

    public String getWorkerReport() {
        // TODO: return summary of worker activity
        return null;
    }
}
