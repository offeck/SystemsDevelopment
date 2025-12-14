package memory;

public class SharedMatrix {

    private volatile SharedVector[] vectors = {}; // underlying vectors 
    private volatile VectorOrientation orientation; // Add this line

    public SharedMatrix() {
        this.vectors = new SharedVector[0];
        this.orientation = VectorOrientation.ROW_MAJOR; // Default orientation
    }

    public SharedMatrix(double[][] matrix) {
        loadRowMajor(matrix);
        orientation = VectorOrientation.ROW_MAJOR;
    }

    public void loadRowMajor(double[][] matrix) {
        // Lock old vectors before replacing
        acquireAllVectorWriteLocks(vectors);
        
        // Create new vectors, one per row
        SharedVector[] newVectors = new SharedVector[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            newVectors[i] = new SharedVector(matrix[i], VectorOrientation.ROW_MAJOR);
        }
        
        // Release old locks
        releaseAllVectorWriteLocks(vectors);
        
        // Replace
        this.vectors = newVectors;
        this.orientation = VectorOrientation.ROW_MAJOR;
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
    
        releaseAllVectorWriteLocks(vectors);
        this.vectors = newVectors;
        this.orientation = VectorOrientation.COLUMN_MAJOR;
    }

    public double[][] readRowMajor() {
        // TODO: return matrix contents as a row-major double[][]
        return null;
    }

    public SharedVector get(int index) {
        return vectors[index];
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
