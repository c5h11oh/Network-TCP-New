package Buffer;

import Exceptions.*;

public class Buffer {
    protected byte[] buf;
    protected final int bufferSize; // in byte
    protected final int windowSize; // in byte
    public Buffer(int bufferSize, int windowSize) throws BufferSizeException {
        if(bufferSize < 0 || windowSize < 0 || windowSize >= bufferSize){
            throw new BufferSizeException();
        }
        this.bufferSize = bufferSize;
        this.windowSize = windowSize;
        this.buf = new byte[bufferSize];
    }
}
