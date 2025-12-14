package memory;

import java.util.concurrent.locks.ReadWriteLock;

public class SharedVector {

    private double[] vector;
    private VectorOrientation orientation;
    private ReadWriteLock lock = new java.util.concurrent.locks.ReentrantReadWriteLock();

    public SharedVector(double[] vector, VectorOrientation orientation) {
        this.vector = vector;
        this.orientation = orientation;
    }

    public double get(int index) {
        this.readLock();
        try {
            return vector[index];
        } finally {
            this.readUnlock();
        }
    }

    public int length() {
        this.readLock();
        try {
            return vector.length;
        } finally {
            this.readUnlock();
        }
    }

    public VectorOrientation getOrientation() {
        this.readLock();
        try {
            return this.orientation;
        } finally {
            this.readUnlock();
        }
    }

    public void writeLock() {
        lock.writeLock().lock();
    }

    public void writeUnlock() {
        lock.writeLock().unlock();
    }

    public void readLock() {
        lock.readLock().lock();
    }

    public void readUnlock() {
        lock.readLock().unlock();
    }
    // transposes the vector (row to column or column to row).
    public void transpose() {
        this.writeLock();
        try {
            this.orientation = (this.orientation == VectorOrientation.ROW_MAJOR) ? VectorOrientation.COLUMN_MAJOR : VectorOrientation.ROW_MAJOR;
        } finally {
            this.writeUnlock();
        }
    }
    // adds another vector to this vector.
    public void add(SharedVector other) {
        int size = this.vector.length;
        if (size != other.vector.length) {
            throw new IllegalArgumentException("Vectors must be of the same length to add.");
        }
        this.writeLock();
        other.readLock();
        try {
            for (int i = 0; i < size; i++) {
                this.vector[i] += other.vector[i];
            }
        } finally {
            other.readUnlock();
            this.writeUnlock();
        }
    }
    // negates the vector.
    public void negate() {
        int size = this.vector.length;
        this.writeLock();
        try {
            for (int i = 0; i < size; i++) {
                this.vector[i] = -this.vector[i];
            }
        } finally {
            this.writeUnlock();
        }
    }
    // multiplies the vector by another vector (dot product).
    public double dot(SharedVector other) {
        int size = this.vector.length;
        this.readLock();
        other.readLock();
        if (size != other.vector.length) {
            throw new IllegalArgumentException("Vectors must be of the same length to compute dot product.");
        }
        try {
            double result = 0.0;
            for (int i = 0; i < size; i++) {
                result += this.vector[i] * other.vector[i];
            }
            return result;
        } finally {
            other.readUnlock();
            this.readUnlock();
        }
    }

    public void vecMatMul(SharedMatrix matrix) {
        if (matrix == null || matrix.length() == 0 || matrix.get(0).length() == 0) {
            throw new IllegalArgumentException("Matrix cannot be emptyfor multiplication.");
        }
        if (this.orientation != VectorOrientation.ROW_MAJOR) {
            throw new IllegalArgumentException("Vector must be in row-major orientation for multiplication.");
        }
        this.writeLock();
        int rows;
        int cols;
        double[] result;
        boolean isRowMajor = (matrix.getOrientation() == VectorOrientation.ROW_MAJOR);
        try {
            if (isRowMajor) {
                rows = matrix.length();
                if (this.vector.length != rows) {
                throw new IllegalArgumentException("Vector length must match matrix row count for multiplication.");
                }
                cols = matrix.get(0).length();
                result = new double[cols];
                for (int j = 0; j < cols; j++) {
                    SharedVector colVector = new SharedVector(new double[rows], VectorOrientation.COLUMN_MAJOR);
                    for (int i = 0; i < rows; i++) {
                        matrix.get(i).readLock();
                        colVector.vector[i] = matrix.get(i).get(j);
                        matrix.get(i).readUnlock();
                    }
                    result[j] = this.dot(colVector);
                }
            } else {
                rows = matrix.get(0).length();
                if (this.vector.length != rows) {
                throw new IllegalArgumentException("Vector length must match matrix row count for multiplication.");
                }   
                cols = matrix.length();
                result = new double[cols];
                for (int i = 0; i < cols; i++) {
                    matrix.get(i).readLock();
                    result[i] = this.dot(matrix.get(i));
                    matrix.get(i).readUnlock();
                }
            }
            this.vector = result;
            this.orientation = VectorOrientation.ROW_MAJOR;
        } finally {
            this.writeUnlock();
        }
    }
}