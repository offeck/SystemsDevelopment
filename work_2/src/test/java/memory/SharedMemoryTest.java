package memory;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SharedMemoryTest {

    // --- SharedVector Tests ---

    @Test
    void testSharedVectorInitialization() {
        double[] data = {1.0, 2.0, 3.0};
        SharedVector v = new SharedVector(data, VectorOrientation.ROW_MAJOR);
        
        assertEquals(3, v.length());
        assertEquals(VectorOrientation.ROW_MAJOR, v.getOrientation());
        assertEquals(1.0, v.get(0));
        assertEquals(2.0, v.get(1));
        assertEquals(3.0, v.get(2));
    }

    @Test
    void testSharedVectorTranspose() {
        double[] data = {1.0, 2.0};
        SharedVector v = new SharedVector(data, VectorOrientation.ROW_MAJOR);
        
        v.transpose();
        assertEquals(VectorOrientation.COLUMN_MAJOR, v.getOrientation());
        
        v.transpose();
        assertEquals(VectorOrientation.ROW_MAJOR, v.getOrientation());
    }

    @Test
    void testSharedVectorAdd() {
        double[] data1 = {1.0, 2.0};
        double[] data2 = {3.0, 4.0};
        SharedVector v1 = new SharedVector(data1, VectorOrientation.ROW_MAJOR);
        SharedVector v2 = new SharedVector(data2, VectorOrientation.ROW_MAJOR);
        
        v1.add(v2);
        
        assertEquals(4.0, v1.get(0));
        assertEquals(6.0, v1.get(1));
        // v2 should remain unchanged
        assertEquals(3.0, v2.get(0));
    }

    @Test
    void testSharedVectorAddMismatch() {
        SharedVector v1 = new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR);
        SharedVector v2 = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);
        
        assertThrows(IllegalArgumentException.class, () -> v1.add(v2));
    }

    @Test
    void testSharedVectorNegate() {
        double[] data = {1.0, -2.0, 0.0};
        SharedVector v = new SharedVector(data, VectorOrientation.ROW_MAJOR);
        
        v.negate();
        
        assertEquals(-1.0, v.get(0));
        assertEquals(2.0, v.get(1));
        assertEquals(-0.0, v.get(2)); // Java distinguishes 0.0 and -0.0 but equals handles it often; checking value.
    }

    @Test
    void testSharedVectorDot() {
        double[] data1 = {1.0, 2.0, 3.0};
        double[] data2 = {4.0, 5.0, 6.0};
        SharedVector v1 = new SharedVector(data1, VectorOrientation.ROW_MAJOR);
        SharedVector v2 = new SharedVector(data2, VectorOrientation.ROW_MAJOR);
        
        double result = v1.dot(v2); // 1*4 + 2*5 + 3*6 = 4 + 10 + 18 = 32
        
        assertEquals(32.0, result, 0.0001);
    }
    @Test
    void testSharedVectorDotLargeValues() {
        double[] data1 = {1e150, 2e150};
        double[] data2 = {3e150, 4e150};
        SharedVector v1 = new SharedVector(data1, VectorOrientation.ROW_MAJOR);
        SharedVector v2 = new SharedVector(data2, VectorOrientation.ROW_MAJOR);
        
        double result = v1.dot(v2); // 1e150*3e150 + 2e150*4e150 = 3e300 + 8e300 = 11e300
        
        assertEquals(11e300, result, 1e290); // Allow some tolerance due to floating point precision
    }   
    @Test
    void testLargeSharedVector() {
        int size = 1000;
        double[] data = new double[size];
        for (int i = 0; i < size; i++) {
            data[i] = i * 1.0;
        }
        SharedVector v = new SharedVector(data, VectorOrientation.ROW_MAJOR);
        
        assertEquals(size, v.length());
        for (int i = 0; i < size; i++) {
            assertEquals(i * 1.0, v.get(i), 0.0001);
        }
    }
    @Test
    void testLongSharedVectorDot() {
        int size = 1000;
        double[] data1 = new double[size];
        double[] data2 = new double[size];
        for (int i = 0; i < size; i++) {
            data1[i] = i * 1.0;
            data2[i] = (size - i) * 1.0;
        }
        SharedVector v1 = new SharedVector(data1, VectorOrientation.ROW_MAJOR);
        SharedVector v2 = new SharedVector(data2, VectorOrientation.ROW_MAJOR);
        
        // Dot product: sum of i * (size - i) for i=0 to size-1
        double expected = 0.0;
        for (int i = 0; i < size; i++) {
            expected += i * (size - i);
        }
        
        double result = v1.dot(v2);
        
        assertEquals(expected, result, 0.0001);
    }
    @Test
    void testSharedVectorDotMismatch() {
        SharedVector v1 = new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR);
        SharedVector v2 = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);

        assertThrows(IllegalArgumentException.class, () -> v1.dot(v2));
    }

    @Test
    void testSharedVectorDotNegative() {
        double[] data1 = {1.0, -2.0};
        double[] data2 = {-3.0, 4.0};
        SharedVector v1 = new SharedVector(data1, VectorOrientation.ROW_MAJOR);
        SharedVector v2 = new SharedVector(data2, VectorOrientation.ROW_MAJOR);
        
        // 1*(-3) + (-2)*4 = -3 - 8 = -11
        double result = v1.dot(v2);
        
        assertEquals(-11.0, result, 0.0001);
    }

    @Test
    void testSharedVectorConcurrency() throws InterruptedException {
        int threads = 10;
        SharedVector v = new SharedVector(new double[]{0.0}, VectorOrientation.ROW_MAJOR);
        SharedVector adder = new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR);
        
        CountDownLatch latch = new CountDownLatch(threads);
        
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                v.add(adder);
                latch.countDown();
            }).start();
        }
        
        latch.await();
        assertEquals((double)threads, v.get(0), 0.0001);
    }
    
    @Test
    void testSharedVectorGetOutOfBounds() {
        SharedVector v = new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR);
        assertThrows(IndexOutOfBoundsException.class, () -> v.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> v.get(-1));
    }

    // --- VecMatMul Tests ---

    @Test
    void testVecMatMulSuccessRowMajorMatrix() {
        // Vector: [1, 2]
        // Matrix (Row Major):
        // [3, 4]
        // [5, 6]
        // Result: [1*3 + 2*5, 1*4 + 2*6] = [3+10, 4+12] = [13, 16]
        
        SharedVector v = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);
        double[][] matData = {
            {3.0, 4.0},
            {5.0, 6.0}
        };
        SharedMatrix m = new SharedMatrix(matData);
        
        v.vecMatMul(m);
        
        assertEquals(2, v.length());
        assertEquals(13.0, v.get(0), 0.0001);
        assertEquals(16.0, v.get(1), 0.0001);
    }

    @Test
    void testVecMatMulSuccessColumnMajorMatrix() {
        // Vector: [1, 2]
        // Matrix (Col Major - Transposed logically):
        // [3, 4]
        // [5, 6]
        // In Col Major internal storage: 
        // Col 0: [3, 5]
        // Col 1: [4, 6]
        
        SharedVector v = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);
        double[][] matData = {
            {3.0, 4.0},
            {5.0, 6.0}
        };
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(matData);
        
        v.vecMatMul(m);
        
        assertEquals(2, v.length());
        assertEquals(13.0, v.get(0), 0.0001);
        assertEquals(16.0, v.get(1), 0.0001);
    }

    @Test
    void testVecMatMulDimensionMismatch() {
        // Vector: [1, 2, 3] (length 3)
        // Matrix: 2x2
        SharedVector v = new SharedVector(new double[]{1.0, 2.0, 3.0}, VectorOrientation.ROW_MAJOR);
        SharedMatrix m = new SharedMatrix(new double[][]{{1, 2}, {3, 4}});
        
        assertThrows(IllegalArgumentException.class, () -> v.vecMatMul(m));
    }

    @Test
    void testVecMatMulInvalidOrientation() {
        // Vector must be ROW_MAJOR for vecMatMul
        SharedVector v = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.COLUMN_MAJOR);
        SharedMatrix m = new SharedMatrix(new double[][]{{1, 2}, {3, 4}});
        
        assertThrows(IllegalArgumentException.class, () -> v.vecMatMul(m));
    }

    @Test
    void testVecMatMulNullOrEmptyMatrix() {
        SharedVector v = new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR);
        
        assertThrows(IllegalArgumentException.class, () -> v.vecMatMul(null));
        assertThrows(IllegalArgumentException.class, () -> v.vecMatMul(new SharedMatrix()));
    }
    @Test
    void testSharedVectorVecMatMulEmptyVector() {
        SharedVector v = new SharedVector(new double[]{}, VectorOrientation.ROW_MAJOR);
        SharedMatrix m = new SharedMatrix(new double[][]{{1, 2}, {3, 4}});
        
        assertThrows(IllegalArgumentException.class, () -> v.vecMatMul(m));
    }

    @Test
    void testSharedVectorVecMatMulRowMajor() {
        // Vector: [1, 2]
        // Matrix: [[3, 4], [5, 6]]
        // Result: [1*3 + 2*5, 1*4 + 2*6] = [3+10, 4+12] = [13, 16]
        
        SharedVector v = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);
        double[][] matData = {
            {3.0, 4.0},
            {5.0, 6.0}
        };
        SharedMatrix m = new SharedMatrix(matData); // Row Major
        
        v.vecMatMul(m);
        
        assertEquals(2, v.length());
        assertEquals(13.0, v.get(0), 0.0001);
        assertEquals(16.0, v.get(1), 0.0001);
    }
    @Test
    void testLargeSharedVectorVecMatMulRowMajor(){ 
        int vectorSize = 1000;
        int matrixCols = 500;
        double[] vectorData = new double[vectorSize];
        double[][] matrixData = new double[vectorSize][matrixCols];
        
        // Initialize vector and matrix with some values
        for (int i = 0; i < vectorSize; i++) {
            vectorData[i] = i + 1; 
            for (int j = 0; j < matrixCols; j++) {
                matrixData[i][j] = (i + 1) * (j + 1); 
            }
        }
        
        SharedVector v = new SharedVector(vectorData, VectorOrientation.ROW_MAJOR);
        SharedMatrix m = new SharedMatrix(matrixData); // Row Major
        
        v.vecMatMul(m);
        
        // Validate a few entries in the resulting vector
        for (int j = 0; j < matrixCols; j++) {
            double expectedValue = 0.0;
            for (int i = 0; i < vectorSize; i++) {
                expectedValue += vectorData[i] * matrixData[i][j];
            }
            assertEquals(expectedValue, v.get(j), 0.0001);
        }
    }
    @Test
    void testSharedVectorVecMatMulColumnMajor() {
        // Vector: [1, 2]
        // Matrix: [[3, 4], [5, 6]]
        // Result: [13, 16]
        
        SharedVector v = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);
        double[][] matData = {
            {3.0, 4.0},
            {5.0, 6.0}
        };
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(matData); // Column Major
        
        v.vecMatMul(m);
        
        assertEquals(2, v.length());
        assertEquals(13.0, v.get(0), 0.0001);
        assertEquals(16.0, v.get(1), 0.0001);
    }
    @Test
    void testLargeSharedVectorVecMatMulColumnMajor() {
        int vectorSize = 1000;
        int matrixCols = 500;
        double[] vectorData = new double[vectorSize];
        double[][] matrixData = new double[vectorSize][matrixCols];
        
        // Initialize vector and matrix with some values
        for (int i = 0; i < vectorSize; i++) {
            vectorData[i] = i + 1; 
            for (int j = 0; j < matrixCols; j++) {
                matrixData[i][j] = (i + 1) * (j + 1); 
            }
        }
        
        SharedVector v = new SharedVector(vectorData, VectorOrientation.ROW_MAJOR);
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(matrixData); // Column Major
        
        v.vecMatMul(m);
        
        // Validate a few entries in the resulting vector
        for (int j = 0; j < matrixCols; j++) {
            double expectedValue = 0.0;
            for (int i = 0; i < vectorSize; i++) {
                expectedValue += vectorData[i] * matrixData[i][j];
            }
            assertEquals(expectedValue, v.get(j), 0.0001);
        }
    }
    @Test
    void testSharedVectorVecMatMulDimensionMismatch() {
        SharedVector v = new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR);
        double[][] matData = {
            {3.0, 4.0},
            {5.0, 6.0}
        };
        SharedMatrix m = new SharedMatrix(matData);
        
        assertThrows(IllegalArgumentException.class, () -> v.vecMatMul(m));
    }

    // --- SharedMatrix Tests ---

    @Test
    void testSharedMatrixLoadRowMajor() {
        double[][] data = {
            {1.0, 2.0},
            {3.0, 4.0}
        };
        SharedMatrix m = new SharedMatrix(data); // defaults to loadRowMajor
        
        assertEquals(2, m.length());
        assertEquals(VectorOrientation.ROW_MAJOR, m.getOrientation());
        
        SharedVector row0 = m.get(0);
        assertEquals(1.0, row0.get(0));
        assertEquals(2.0, row0.get(1));
        assertEquals(VectorOrientation.ROW_MAJOR, row0.getOrientation());
    }

    @Test
    void testSharedMatrixLoadColumnMajor() {
        double[][] data = {
            {1.0, 2.0},
            {3.0, 4.0}
        };
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(data);
        
        // Matrix is 2x2. 
        // Col 0: [1.0, 3.0]
        // Col 1: [2.0, 4.0]
        
        assertEquals(2, m.length()); // 2 columns
        assertEquals(VectorOrientation.COLUMN_MAJOR, m.getOrientation());
        
        SharedVector col0 = m.get(0);
        assertEquals(1.0, col0.get(0));
        assertEquals(3.0, col0.get(1));
        assertEquals(VectorOrientation.COLUMN_MAJOR, col0.getOrientation());
    }

    @Test
    void testSharedMatrixReadRowMajorFromRowMajor() {
        double[][] data = {
            {1.0, 2.0},
            {3.0, 4.0}
        };
        SharedMatrix m = new SharedMatrix(data);
        
        double[][] result = m.readRowMajor();
        
        assertArrayEquals(data[0], result[0]);
        assertArrayEquals(data[1], result[1]);
    }

    @Test
    void testSharedMatrixReadRowMajorFromColumnMajor() {
        double[][] data = {
            {1.0, 2.0},
            {3.0, 4.0}
        };
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(data);
        
        double[][] result = m.readRowMajor();
        
        assertArrayEquals(data[0], result[0]);
        assertArrayEquals(data[1], result[1]);
    }
    
    @Test
    void testSharedMatrixGetOutOfBounds() {
        SharedMatrix m = new SharedMatrix(new double[][]{{1, 2}});
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(-1));
    }
}
