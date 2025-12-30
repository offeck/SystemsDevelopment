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
}
