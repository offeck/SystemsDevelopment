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
    }

    public ComputationNode run(ComputationNode computationRoot) {
        // TODO: resolve computation tree step by step until final matrix is produced
        return null;
    }

    public void loadAndCompute(ComputationNode node) {
        // TODO: load operand matrices
        // TODO: create compute tasks & submit tasks to executor
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
