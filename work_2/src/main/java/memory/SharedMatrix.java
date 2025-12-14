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
    }

    public void loadRowMajor(double[][] matrix) {
        // TODO: replace internal data with new row-major matrix
    }

    public void loadColumnMajor(double[][] matrix) {
        // TODO: replace internal data with new column-major matrix
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
        // TODO: acquire read lock for each vector
    }

    private void releaseAllVectorReadLocks(SharedVector[] vecs) {
        // TODO: release read locks
    }

    private void acquireAllVectorWriteLocks(SharedVector[] vecs) {
        // TODO: acquire write lock for each vector
    }

    private void releaseAllVectorWriteLocks(SharedVector[] vecs) {
        // TODO: release write locks
    }
}
