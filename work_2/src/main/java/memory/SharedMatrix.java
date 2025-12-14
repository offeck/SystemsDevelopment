package memory;

public class SharedMatrix {

    private SharedVector[] vectors = {}; // underlying vectors

    public SharedMatrix() {
    }

    public SharedMatrix(double[][] matrix) {
        loadRowMajor(matrix);
    }

    public void loadRowMajor(double[][] matrix) {
        // Create new vectors, one per row
        acquireAllVectorWriteLocks(vectors);
        SharedVector[] newVectors = new SharedVector[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            newVectors[i] = new SharedVector(matrix[i], VectorOrientation.ROW_MAJOR);
        }
        SharedVector[] oldVectors = this.vectors;
        // Replace
        this.vectors = newVectors;
        releaseAllVectorWriteLocks(oldVectors);
    }

    public void loadColumnMajor(double[][] matrix) {
        acquireAllVectorWriteLocks(vectors);

        int numRows = matrix.length;
        int numCols = matrix[0].length;
        SharedVector[] newVectors = new SharedVector[numCols];
        for (int col = 0; col < numCols; col++) {
            double[] columnData = new double[numRows];
            for (int row = 0; row < numRows; row++) {
                columnData[row] = matrix[row][col];
            }
            newVectors[col] = new SharedVector(columnData, VectorOrientation.COLUMN_MAJOR);
        }
        SharedVector[] oldVectors = this.vectors;
        this.vectors = newVectors;
        releaseAllVectorWriteLocks(oldVectors);
    }

    public double[][] readRowMajor() {
        acquireAllVectorReadLocks(vectors);

        // If already in ROW_MAJOR, extract rows
        if (getOrientation() == VectorOrientation.ROW_MAJOR) {
            double[][] result = new double[vectors.length][];
            for (int i = 0; i < vectors.length; i++) {
                result[i] = new double[vectors[i].length()];
                for (int j = 0; j < vectors[i].length(); j++) {
                    result[i][j] = vectors[i].get(j);
                }
            }
            releaseAllVectorReadLocks(vectors);
            return result;
        }
        // if is in COLUMN_MAJOR: need to transpose
        else {
            int numCols = vectors.length;
            int numRows = vectors[0].length();
            double[][] result = new double[numRows][numCols];

            for (int col = 0; col < numCols; col++) {
                for (int row = 0; row < numRows; row++) {
                    result[row][col] = vectors[col].get(row);
                }
            }
            releaseAllVectorReadLocks(vectors);
            return result;
        }
    }

    public SharedVector get(int index) {
        this.vectors[index].readLock();
        try {
            return this.vectors[index];
        } finally {
            this.vectors[index].readUnlock();
        }
    }

    public int length() {
        return vectors.length;
    }

    public VectorOrientation getOrientation() {
        return (vectors.length == 0) ? VectorOrientation.ROW_MAJOR : vectors[0].getOrientation();
    }

    private void acquireAllVectorReadLocks(SharedVector[] vecs) {
        for (SharedVector v : vecs) {
            v.readLock();
        }
    }

    private void releaseAllVectorReadLocks(SharedVector[] vecs) {
        for (SharedVector v : vecs) {
            v.readUnlock();
        }
    }

    private void acquireAllVectorWriteLocks(SharedVector[] vecs) {
        for (SharedVector v : vecs) {
            v.writeLock();
        }
    }

    private void releaseAllVectorWriteLocks(SharedVector[] vecs) {
        for (SharedVector v : vecs) {
            v.writeUnlock();
        }
    }
}
