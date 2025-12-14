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

    public void transpose() {
        this.writeLock();
        try {
            this.orientation = (this.orientation == VectorOrientation.ROW_MAJOR) ? VectorOrientation.COLUMN_MAJOR : VectorOrientation.ROW_MAJOR;
        } finally {
            this.writeUnlock();
        }
    }

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

    public double dot(SharedVector other) {
        int size = this.vector.length;
        if (size != other.vector.length) {
            throw new IllegalArgumentException("Vectors must be of the same length to compute dot product.");
        }
        return 0;
    }

    public void vecMatMul(SharedMatrix matrix) {
        // TODO: compute row-vector × matrix
    }
}
